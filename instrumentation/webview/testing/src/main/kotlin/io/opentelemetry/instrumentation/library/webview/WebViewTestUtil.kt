/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview

import android.content.Context
import android.webkit.WebView

/**
 * Call sites live in the main source set because Byte Buddy only weaves the app's own classes,
 * not the androidTest APK.
 */
object WebViewTestUtil {
    /** Reports the session id the page's first script sees to `/beacon/ctx?v=`. */
    const val REPORT_CONTEXT =
        "new Image().src='/beacon/ctx?v='+encodeURIComponent(" +
            "window.__VUNET_CTX__?window.__VUNET_CTX__.sessionId:'none');"

    fun loadUrl(
        webView: WebView,
        url: String,
    ) {
        webView.loadUrl(url)
    }

    fun loadUrlWithHeaders(
        webView: WebView,
        url: String,
    ) {
        webView.loadUrl(url, mapOf("X-Test" to "1"))
    }

    fun postUrl(
        webView: WebView,
        url: String,
    ) {
        webView.postUrl(url, "a=1".toByteArray())
    }

    fun loadDataWithBaseUrl(
        webView: WebView,
        baseUrl: String,
    ) {
        webView.loadDataWithBaseURL(baseUrl, "<script>$REPORT_CONTEXT</script>", "text/html", "utf-8", null)
    }

    fun loadUrlOnSubclass(
        webView: PlainSubclassWebView,
        url: String,
    ) {
        webView.loadUrl(url)
    }

    fun loadUrlOnOverridingSubclass(
        webView: OverridingWebView,
        url: String,
    ) {
        webView.loadUrl(url)
    }
}

class PlainSubclassWebView(
    context: Context,
) : WebView(context)

class OverridingWebView(
    context: Context,
) : WebView(context) {
    var overrideCalls = 0

    override fun loadUrl(url: String) {
        overrideCalls++
        super.loadUrl(url)
    }
}

/** Loads from a constructor, which compiles to `<init>` rather than a regular method. */
class LoadsInConstructor(
    webView: WebView,
    url: String,
) {
    init {
        webView.loadUrl(url)
    }
}
