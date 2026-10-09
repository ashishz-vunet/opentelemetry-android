/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.internal.services.network

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.android.common.internal.features.networkattributes.data.CurrentNetwork
import io.opentelemetry.android.internal.services.network.detector.NetworkDetector
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

// note: based on ideas from stack overflow:
// https://stackoverflow.com/questions/32547006/connectivitymanager-getnetworkinfoint-deprecated

/**
 * A provider of [CurrentNetwork] information. Registers itself in the Android [ ] and listens for network changes.
 *
 * This class is internal and not for public use. Its APIs are unstable and can change at any
 * time.
 */
internal class CurrentNetworkProviderImpl(
    private val networkDetector: NetworkDetector,
    private val connectivityManager: ConnectivityManager,
    createNetworkMonitoringRequest: () -> NetworkRequest = ::createNetworkMonitoringRequest,
) : CurrentNetworkProvider {
    @Volatile
    override var currentNetwork: CurrentNetwork = CurrentNetworkProvider.UNKNOWN_NETWORK
        private set

    /**
     * `registerDefaultNetworkCallback` (API 24+) only ever reports the *default* network, so the
     * [Network] a callback hands us is by definition the one to classify. The API 23 path registers
     * a transport-matching [NetworkRequest] instead, which fires for **any** matching network —
     * a Wi-Fi or VPN coming up while cellular is the default included — so there the
     * ConnectivityManager stays the only source of truth for which network is actually active.
     */
    private val callbackIsDefaultScoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private val callbackRef = AtomicReference<NetworkCallback>()
    private val listeners: MutableList<NetworkChangeListener> = CopyOnWriteArrayList()

    init {
        startMonitoring(createNetworkMonitoringRequest)
    }

    private fun startMonitoring(createNetworkMonitoringRequest: () -> NetworkRequest) {
        refreshNetworkStatus()
        // A cold start can beat the radio: getActiveNetwork() stays null until the default
        // network is VALIDATED, and "unavailable" would land in dashboards as a real offline
        // session. But a device that is genuinely offline has no networks at all, while one still
        // validating already has a Network object that simply is not the default yet — so only
        // downgrade to "unknown" in the second case. This matters because callbacks are
        // edge-triggered: a session that is offline throughout receives none, so this seed is the
        // only value it will ever have, and it must stay truthful.
        if (currentNetwork == CurrentNetworkProvider.NO_NETWORK && hasAnyNetwork()) {
            currentNetwork = CurrentNetworkProvider.UNKNOWN_NETWORK
        }
        try {
            registerNetworkCallbacks(createNetworkMonitoringRequest)
        } catch (e: Exception) {
            // if this fails, we'll go without network change events.
            Log.w(
                RumConstants.OTEL_RUM_LOG_TAG,
                "Failed to register network callbacks. Automatic network monitoring is disabled.",
                e,
            )
        }
    }

    private fun registerNetworkCallbacks(createNetworkMonitoringRequest: () -> NetworkRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerNetworkCallbackApi24()
        } else {
            val networkRequest = createNetworkMonitoringRequest()
            val callback: NetworkCallback = ConnectionMonitor()
            connectivityManager.registerNetworkCallback(networkRequest, callback)
            callbackRef.set(callback)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun registerNetworkCallbackApi24() {
        val callback: NetworkCallback = ConnectionMonitor()
        connectivityManager.registerDefaultNetworkCallback(callback)
        callbackRef.set(callback)
    }

    /** Returns up-to-date [current network information][CurrentNetwork].  */
    override fun refreshNetworkStatus(): CurrentNetwork {
        currentNetwork =
            try {
                networkDetector.detectCurrentNetwork()
            } catch (e: Exception) {
                // guard against security issues/bugs when accessing the Android connectivityManager.
                // see: https://issuetracker.google.com/issues/175055271
                CurrentNetworkProvider.UNKNOWN_NETWORK
            }
        return currentNetwork
    }

    override fun addNetworkChangeListener(listener: NetworkChangeListener) {
        listeners.add(listener)
    }

    override fun removeNetworkChangeListener(listener: NetworkChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(activeNetwork: CurrentNetwork) {
        for (listener in listeners) {
            listener.onNetworkChange(activeNetwork)
        }
    }

    override fun close() {
        val callback = callbackRef.get()
        if (callback != null) {
            connectivityManager.unregisterNetworkCallback(callback)
            listeners.clear()
            callbackRef.set(null)
        }
    }

    /**
     * Guarded for the same reason as [detect]: the ConnectivityManager can throw on some devices
     * (https://issuetracker.google.com/issues/175055271), and this runs inside a system callback
     * where an escaping exception has nowhere sensible to go.
     */
    private fun activeNetworkOrNull(): Network? =
        try {
            connectivityManager.activeNetwork
        } catch (e: Exception) {
            null
        }

    /** Whether the system knows of any network at all, used to tell "offline" from "not yet default". */
    @Suppress("DEPRECATION") // no replacement answers "does any network exist", only "is one active"
    private fun hasAnyNetwork(): Boolean =
        try {
            connectivityManager.allNetworks.isNotEmpty()
        } catch (e: Exception) {
            false
        }

    /**
     * Turns the [Network] a callback handed us into a [CurrentNetwork]. On API 24+ that network is
     * the default, so classify it directly — the whole point of the fix, since `getActiveNetwork()`
     * is frequently still null at this moment. On API 23 the callback is not default-scoped
     * ([callbackIsDefaultScoped]), so defer to the ConnectivityManager there rather than reporting
     * a network that merely came up.
     */
    private fun classify(network: Network): CurrentNetwork =
        if (callbackIsDefaultScoped) {
            detect(network)
        } else {
            activeNetworkOrNull()?.let { detect(it) } ?: CurrentNetworkProvider.NO_NETWORK
        }

    private fun detect(network: Network): CurrentNetwork =
        try {
            networkDetector.detectCurrentNetwork(network)
        } catch (e: Exception) {
            // guard against security issues/bugs when accessing the Android connectivityManager.
            // see: https://issuetracker.google.com/issues/175055271
            CurrentNetworkProvider.UNKNOWN_NETWORK
        }

    private fun publish(network: CurrentNetwork) {
        // onCapabilitiesChanged is chatty; don't wake listeners for a value that didn't move.
        if (network == currentNetwork) return
        currentNetwork = network
        RumDiagnostics.d { "network: state=${network.state}" }

        notifyListeners(network)
    }

    private inner class ConnectionMonitor : NetworkCallback() {
        // Classify the Network we were handed rather than re-querying getActiveNetwork(), which
        // is often still null at this point and would pin the cache to NO_NETWORK for good.
        override fun onAvailable(network: Network) {
            publish(classify(network))
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            // onAvailable often fires before the network is VALIDATED and classifiable; this is
            // where the real transport shows up, and Android will not re-fire onAvailable.
            publish(classify(network))
        }

        override fun onLost(network: Network) {
            // The ConnectivityManager may still report the *lost* network as active here, so only
            // trust a different default; otherwise assume we're offline.
            val activeNetwork = activeNetworkOrNull()
            publish(
                if (activeNetwork != null && activeNetwork != network) {
                    detect(activeNetwork)
                } else {
                    CurrentNetworkProvider.NO_NETWORK
                },
            )
        }
    }

    companion object {
        private fun createNetworkMonitoringRequest(): NetworkRequest {
            // note: this throws an NPE when running in junit without robolectric, due to Android
            return NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
        }
    }
}
