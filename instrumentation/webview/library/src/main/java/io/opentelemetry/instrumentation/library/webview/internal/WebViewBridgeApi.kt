/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** The `androidx.webkit` calls used by [WebViewContextBridge], behind an interface for tests. */
internal interface WebViewBridgeApi {
    /** Whether the installed WebView supports both document-start scripts and message listeners. */
    fun isSupported(): Boolean

    fun addDocumentStartScript(
        webView: WebView,
        script: String,
        allowedOriginRules: Set<String>,
    ): RegisteredScript

    fun addMessageListener(
        webView: WebView,
        name: String,
        allowedOriginRules: Set<String>,
        listener: BridgeMessageListener,
    )

    fun removeMessageListener(
        webView: WebView,
        name: String,
    )

    /** Runs [script] in the page currently loaded in [webView]. Must be called on its thread. */
    fun evaluate(
        webView: WebView,
        script: String,
    )

    /** The URL currently loaded in [webView]. Must be called on its thread. */
    fun currentUrl(webView: WebView): String?

    /** Runs [action] on the thread [webView] was created on. */
    fun post(
        webView: WebView,
        action: Runnable,
    )
}

internal fun interface RegisteredScript {
    fun remove()
}

internal fun interface BridgeMessageListener {
    fun onMessage(
        message: String?,
        sourceOrigin: String,
        isMainFrame: Boolean,
        reply: (String) -> Unit,
    )
}

internal object AndroidWebViewBridgeApi : WebViewBridgeApi {
    private val supported: Boolean by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    override fun isSupported(): Boolean = supported

    @SuppressLint("RequiresFeature")
    override fun addDocumentStartScript(
        webView: WebView,
        script: String,
        allowedOriginRules: Set<String>,
    ): RegisteredScript {
        val handler = WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOriginRules)
        return RegisteredScript { handler.remove() }
    }

    @SuppressLint("RequiresFeature")
    override fun addMessageListener(
        webView: WebView,
        name: String,
        allowedOriginRules: Set<String>,
        listener: BridgeMessageListener,
    ) {
        WebViewCompat.addWebMessageListener(webView, name, allowedOriginRules) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            listener.onMessage(message.data, sourceOrigin.toString(), isMainFrame) { replyProxy.postMessage(it) }
        }
    }

    @SuppressLint("RequiresFeature")
    override fun removeMessageListener(
        webView: WebView,
        name: String,
    ) {
        WebViewCompat.removeWebMessageListener(webView, name)
    }

    override fun evaluate(
        webView: WebView,
        script: String,
    ) {
        webView.evaluateJavascript(script, null)
    }

    override fun currentUrl(webView: WebView): String? = webView.url

    /** `View.post` would hold the action until the WebView is attached to a window. */
    override fun post(
        webView: WebView,
        action: Runnable,
    ) {
        val looper =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) looperOf(webView) else Looper.getMainLooper()
        Handler(looper).post(action)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun looperOf(webView: WebView): Looper = webView.webViewLooper
}
