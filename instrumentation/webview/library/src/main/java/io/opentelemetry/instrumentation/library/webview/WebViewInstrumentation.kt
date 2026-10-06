/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import com.google.auto.service.AutoService
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.ConfigurableWebViewInstrumentation
import io.opentelemetry.android.internal.services.visiblescreen.activities.DefaultingActivityLifecycleCallbacks
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.instrumentation.library.webview.internal.AndroidWebViewBridgeApi
import io.opentelemetry.instrumentation.library.webview.internal.WebViewBridgeApi
import io.opentelemetry.instrumentation.library.webview.internal.WebViewContext
import io.opentelemetry.instrumentation.library.webview.internal.WebViewContextBridge
import io.opentelemetry.instrumentation.library.webview.internal.WebViewHandoffConfig
import io.opentelemetry.instrumentation.library.webview.internal.WebViewSessionHandoff
import io.opentelemetry.instrumentation.library.webview.internal.traceparentOf

private const val TRACER_NAME = "io.opentelemetry.webview"

/**
 * Hands the RUM session to WebView content as `window.__VUNET_CTX__` and a `VunetBridge` message
 * channel, so browser RUM joins the same session. Each page load is also recorded as a
 * `webview.load` span whose `traceparent` is handed to the page, so browser RUM's page load
 * continues the native trace.
 *
 * Both are set up from [WebViewSubstitutions], which the `webview-agent` Byte Buddy plugin
 * substitutes for `WebView.loadUrl` and related calls at build time, so no app code is needed.
 */
@AutoService(AndroidInstrumentation::class)
class WebViewInstrumentation internal constructor(
    private val bridgeApi: WebViewBridgeApi,
) : AndroidInstrumentation,
    ConfigurableWebViewInstrumentation {
    constructor() : this(AndroidWebViewBridgeApi)

    private var allowedHosts: Set<String> = emptySet()
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    override val name: String = "webview"

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        if (lifecycleCallbacks != null) {
            return
        }
        RumDiagnostics.d { "webview: substitution install" }
        val sessionProvider = openTelemetryRum.sessionProvider
        val tracer = openTelemetryRum.openTelemetry.getTracer(TRACER_NAME)
        val bridge =
            WebViewContextBridge(
                sessionProvider = sessionProvider,
                api = bridgeApi,
                clock = openTelemetryRum.clock,
                config = WebViewHandoffConfig(allowedHosts),
                app = appInfo(context),
                device = WebViewContext.DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE),
                screenName = { webView -> hostActivityName(webView.context) },
                events = { name, attributes -> openTelemetryRum.emitEvent(name, attributes = attributes) },
                loadTraces = { webViewId, origin ->
                    val span =
                        tracer
                            .spanBuilder("webview.load")
                            .setAttribute(WebViewContextBridge.WEBVIEW_ID, webViewId)
                            .setAttribute(WebViewContextBridge.WEBVIEW_ORIGIN, origin)
                            .startSpan()
                    span.end()
                    traceparentOf(span.spanContext)
                },
            )
        if (sessionProvider is SessionPublisher) {
            sessionProvider.addObserver(bridge)
        }
        val callbacks =
            object : DefaultingActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    bridge.refresh()
                }
            }
        (context as? Application)?.registerActivityLifecycleCallbacks(callbacks)
        lifecycleCallbacks = callbacks
        WebViewSessionHandoff.install(bridge)
    }

    private tailrec fun hostActivityName(context: Context?): String? =
        when (context) {
            is Activity -> context.javaClass.simpleName
            is ContextWrapper -> hostActivityName(context.baseContext)
            else -> null
        }

    private fun appInfo(context: Context): WebViewContext.AppInfo {
        val version =
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        return WebViewContext.AppInfo(context.packageName, version)
    }

    override fun setAllowedHosts(hosts: Set<String>) {
        allowedHosts = hosts.map { it.lowercase() }.toSet()
    }

    override fun uninstall(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        lifecycleCallbacks?.let { (context as? Application)?.unregisterActivityLifecycleCallbacks(it) }
        lifecycleCallbacks = null
        WebViewSessionHandoff.uninstall()
    }
}
