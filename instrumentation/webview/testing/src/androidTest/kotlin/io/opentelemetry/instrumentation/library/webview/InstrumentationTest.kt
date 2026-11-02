/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.Clock
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Runs against the woven testing app: the call sites in [WebViewTestUtil] are rewritten by the
 * webview-agent plugin, and each test asserts on the session id the page's first script reports.
 */
class InstrumentationTest {
    private val sessionId = "0123456789abcdef0123456789abcdef"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = WebViewInstrumentation()
    private lateinit var server: MockWebServer
    private val webViews = mutableListOf<WebView>()

    @Before
    fun setUp() {
        assumeTrue(
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER),
        )
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse
                        .Builder()
                        .body("<html><head><script>${WebViewTestUtil.REPORT_CONTEXT}</script></head></html>")
                        .build()
            }
        server.start()
    }

    @After
    fun tearDown() {
        onMain {
            webViews.forEach { it.destroy() }
            instrumentation.uninstall(context, rum)
        }
        // Not started when the test was skipped in setUp.
        if (::server.isInitialized) server.close()
    }

    @Test
    fun firstLoadSeesTheContext() {
        install()

        onMain { WebViewTestUtil.loadUrl(newWebView(), server.url("/login").toString()) }

        assertThat(reportedSession()).isEqualTo(sessionId)
    }

    @Test
    fun loadUrlWithHeadersSeesTheContextAndSendsTheHeaders() {
        install()

        onMain { WebViewTestUtil.loadUrlWithHeaders(newWebView(), server.url("/headers").toString()) }

        assertThat(takeRequest("/headers").headers["X-Test"]).isEqualTo("1")
        assertThat(reportedSession()).isEqualTo(sessionId)
    }

    @Test
    fun postUrlSeesTheContext() {
        install()

        onMain { WebViewTestUtil.postUrl(newWebView(), server.url("/submit").toString()) }

        assertThat(takeRequest("/submit").method).isEqualTo("POST")
        assertThat(reportedSession()).isEqualTo(sessionId)
    }

    @Test
    fun loadDataWithBaseUrlSeesTheContext() {
        install()

        onMain { WebViewTestUtil.loadDataWithBaseUrl(newWebView(), server.url("/").toString()) }

        assertThat(reportedSession()).isEqualTo(sessionId)
    }

    @Test
    fun subclassWithoutOverrideIsWoven() {
        install()

        onMain {
            val webView = PlainSubclassWebView(context).also { prepare(it) }
            WebViewTestUtil.loadUrlOnSubclass(webView, server.url("/plain-subclass").toString())
        }

        assertThat(reportedSession()).isEqualTo(sessionId)
    }

    @Test
    fun subclassOverrideIsWovenWithoutRecursing() {
        install()
        lateinit var webView: OverridingWebView

        onMain {
            webView = OverridingWebView(context).also { prepare(it) }
            WebViewTestUtil.loadUrlOnOverridingSubclass(webView, server.url("/overriding-subclass").toString())
        }

        assertThat(reportedSession()).isEqualTo(sessionId)
        assertThat(webView.overrideCalls).isEqualTo(1)
    }

    @Test
    fun loadFromAConstructorIsWoven() {
        install()

        onMain { LoadsInConstructor(newWebView(), server.url("/constructor").toString()) }

        assertThat(reportedSession()).isEqualTo(sessionId)
    }

    @Test
    fun pageSeesNoContextWhenNotInstalled() {
        onMain { WebViewTestUtil.loadUrl(newWebView(), server.url("/not-installed").toString()) }

        assertThat(reportedSession()).isEqualTo("none")
    }

    private val rum =
        object : OpenTelemetryRum {
            override val openTelemetry: OpenTelemetry = OpenTelemetry.noop()
            override val sessionProvider: SessionProvider = SessionProvider { sessionId }
            override val clock: Clock = Clock.getDefault()

            override fun emitEvent(
                eventName: String,
                body: String,
                attributes: Attributes,
            ) {
                // Unused.
            }

            override fun shutdown() {
                // Unused.
            }
        }

    private fun install() {
        onMain { instrumentation.install(context, rum) }
    }

    private fun newWebView(): WebView = WebView(context).also { prepare(it) }

    private fun prepare(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webViews.add(webView)
    }

    private fun reportedSession(): String? = takeRequest("/beacon/ctx").url.queryParameter("v")

    /** Skips incidental requests such as `/favicon.ico`. */
    private fun takeRequest(path: String): RecordedRequest {
        while (true) {
            val request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            checkNotNull(request) { "no request for $path within ${TIMEOUT_SECONDS}s" }
            if (request.url.encodedPath == path) {
                return request
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
