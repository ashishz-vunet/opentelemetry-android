/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import android.webkit.WebView

/**
 * Shares the app's user identity with WebView pages that receive the RUM session context from the
 * WebView instrumentation (`webview-library`), in both directions: the [host] identity is copied
 * into every page context, and a page signing a user in or out is reported back to the [host].
 *
 * Everything here is a no-op until the WebView instrumentation is installed.
 */
object WebViewHandoff {
    /**
     * Implemented by the app or a wrapper SDK. Called on WebView threads; implementations must not
     * throw. Values from pages are already trimmed, non-blank and at most 128 characters, but are
     * otherwise untrusted.
     */
    interface Host {
        /** Identity copied into every page context. */
        fun identity(): Identity

        /** A page signed a user in. */
        fun setUser(
            id: String,
            type: String?,
        )

        /** A page signed the user out. */
        fun clearUser()

        /** A page set a session property. Unknown keys should be ignored. */
        fun setSessionProperty(
            key: String,
            value: String,
        )
    }

    class Identity(
        val userId: String? = null,
        val userType: String? = null,
        val sessionProperties: Map<String, String> = emptyMap(),
    )

    /** Implemented by the installed WebView instrumentation. Not for app use. */
    interface Runtime {
        fun identityChanged()

        fun attach(webView: WebView)
    }

    @Volatile
    @JvmStatic
    var host: Host? = null

    /** Set by the WebView instrumentation while it is installed. */
    @Volatile
    @JvmStatic
    var runtime: Runtime? = null

    /** Pushes the current [Host.identity] to every attached page. Call after it changes. */
    @JvmStatic
    fun identityChanged() {
        runtime?.identityChanged()
    }

    /**
     * Hands the session context to [webView] when its page loads are not rewritten by the
     * `webview-agent` plugin, e.g. a WebView created inside a third-party library. Requires
     * allowed hosts to be configured. Call on the WebView's thread, before its first page loads.
     */
    @JvmStatic
    fun attach(webView: WebView) {
        runtime?.attach(webView)
    }
}
