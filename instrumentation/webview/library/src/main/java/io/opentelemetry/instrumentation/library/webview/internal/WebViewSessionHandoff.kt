/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import android.webkit.WebView
import io.opentelemetry.android.common.RumDiagnostics

/**
 * Holds the installed [WebViewContextBridge] for the static substitutions. Until the
 * instrumentation is installed, and whenever attaching fails, page loads proceed unchanged.
 */
internal object WebViewSessionHandoff {
    @Volatile
    private var bridge: WebViewContextBridge? = null

    fun install(bridge: WebViewContextBridge) {
        this.bridge = bridge
    }

    fun uninstall() {
        bridge = null
    }

    @Suppress("TooGenericExceptionCaught")
    fun beforeLoad(
        webView: WebView,
        url: String?,
    ) {
        val current = bridge ?: return
        try {
            current.beforeLoad(webView, url)
        } catch (t: Throwable) {
            RumDiagnostics.w({ "webview: failed to attach the session context" }, t)
        }
    }
}
