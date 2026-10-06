/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview

import android.webkit.WebView
import io.opentelemetry.instrumentation.library.webview.internal.WebViewSessionHandoff

/**
 * Replacements for `WebView` page-load calls, wired in by the `webview-agent` Byte Buddy plugin.
 * Each one attaches the RUM session context for the target URL, then performs the original call.
 */
object WebViewSubstitutions {
    @JvmStatic
    fun loadUrl(
        webView: WebView,
        url: String,
    ) {
        WebViewSessionHandoff.beforeLoad(webView, url)
        webView.loadUrl(url)
    }

    @JvmStatic
    fun loadUrl(
        webView: WebView,
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        WebViewSessionHandoff.beforeLoad(webView, url)
        webView.loadUrl(url, additionalHttpHeaders)
    }

    @JvmStatic
    fun postUrl(
        webView: WebView,
        url: String,
        postData: ByteArray,
    ) {
        WebViewSessionHandoff.beforeLoad(webView, url)
        webView.postUrl(url, postData)
    }

    @JvmStatic
    fun loadDataWithBaseURL(
        webView: WebView,
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        WebViewSessionHandoff.beforeLoad(webView, baseUrl)
        webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }
}
