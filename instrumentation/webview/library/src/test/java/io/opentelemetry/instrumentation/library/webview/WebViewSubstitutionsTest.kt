/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.instrumentation.library.webview.internal.FakeBridgeApi
import io.opentelemetry.instrumentation.library.webview.internal.WebViewContext
import io.opentelemetry.instrumentation.library.webview.internal.WebViewContextBridge
import io.opentelemetry.instrumentation.library.webview.internal.WebViewHandoffConfig
import io.opentelemetry.instrumentation.library.webview.internal.WebViewSessionHandoff
import io.opentelemetry.sdk.common.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class WebViewSubstitutionsTest {
    private val sessionId = "0123456789abcdef0123456789abcdef"
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        webView = WebView(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        WebViewSessionHandoff.uninstall()
    }

    @Test
    fun `loadUrl attaches the context and still loads the page`() {
        val api = install()

        WebViewSubstitutions.loadUrl(webView, "https://bank.example.com/login")

        assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://bank.example.com/login")
        assertThat(api.scripts.single().rules).containsExactly("https://bank.example.com")
        assertThat(api.scripts.single().code).contains(sessionId)
    }

    @Test
    fun `loadUrl with headers, postUrl and loadDataWithBaseURL attach for their url`() {
        val api = install()

        WebViewSubstitutions.loadUrl(webView, "https://a.example.com/", mapOf("X-Test" to "1"))
        WebViewSubstitutions.postUrl(webView, "https://b.example.com/submit", "a=1".toByteArray())
        WebViewSubstitutions.loadDataWithBaseURL(webView, "https://c.example.com/", "<p/>", "text/html", "utf-8", null)

        assertThat(shadowOf(webView).lastAdditionalHttpHeaders).containsEntry("X-Test", "1")
        assertThat(api.scripts.last().rules)
            .containsExactly("https://a.example.com", "https://b.example.com", "https://c.example.com")
    }

    @Test
    fun `page loads unchanged when the instrumentation is not installed`() {
        WebViewSubstitutions.loadUrl(webView, "https://bank.example.com/")

        assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://bank.example.com/")
    }

    @Test
    fun `page still loads when attaching throws`() {
        val api =
            object : FakeBridgeApi() {
                override fun isSupported(): Boolean = error("WebView provider missing")
            }
        install(api)

        WebViewSubstitutions.loadUrl(webView, "https://bank.example.com/")

        assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://bank.example.com/")
    }

    private fun install(api: FakeBridgeApi = FakeBridgeApi()): FakeBridgeApi {
        WebViewSessionHandoff.install(
            WebViewContextBridge(
                sessionProvider = SessionProvider { sessionId },
                api = api,
                clock = Clock.getDefault(),
                config = WebViewHandoffConfig(),
                app = WebViewContext.AppInfo("com.example.bank", null),
                device = WebViewContext.DeviceInfo("Google", "Pixel 8", "14"),
                screenName = { null },
                events = { _, _ -> },
            ),
        )
        return api
    }
}
