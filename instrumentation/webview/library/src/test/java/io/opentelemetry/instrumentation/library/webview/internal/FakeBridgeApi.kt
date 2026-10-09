/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import android.webkit.WebView
import org.assertj.core.api.Assertions.assertThat

internal class FakeScript(
    val code: String,
    val rules: Set<String>,
) {
    var removed = false
}

internal open class FakeBridgeApi : WebViewBridgeApi {
    var supported = true
    var loadedUrl: String? = null
    val scripts = mutableListOf<FakeScript>()
    val listeners = linkedMapOf<Set<String>, BridgeMessageListener>()
    var removedListeners = 0
    val evaluated = mutableListOf<String>()
    val cookies = mutableListOf<Pair<String, String>>()

    override fun isSupported(): Boolean = supported

    override fun addDocumentStartScript(
        webView: WebView,
        script: String,
        allowedOriginRules: Set<String>,
    ): RegisteredScript {
        val registered = FakeScript(script, allowedOriginRules)
        scripts.add(registered)
        return RegisteredScript { registered.removed = true }
    }

    override fun addMessageListener(
        webView: WebView,
        name: String,
        allowedOriginRules: Set<String>,
        listener: BridgeMessageListener,
    ) {
        assertThat(name).isEqualTo("VunetBridge")
        listeners[allowedOriginRules] = listener
    }

    override fun removeMessageListener(
        webView: WebView,
        name: String,
    ) {
        removedListeners++
    }

    override fun evaluate(
        webView: WebView,
        script: String,
    ) {
        evaluated.add(script)
    }

    override fun currentUrl(webView: WebView): String? = loadedUrl

    override fun post(
        webView: WebView,
        action: Runnable,
    ) {
        action.run()
    }

    override fun setCookie(
        url: String,
        cookie: String,
    ) {
        cookies.add(url to cookie)
    }
}
