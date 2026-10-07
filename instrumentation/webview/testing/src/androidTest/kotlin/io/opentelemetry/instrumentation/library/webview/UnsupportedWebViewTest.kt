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
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Runs only on WebViews without document-start scripts or web message listeners (e.g. the
 * WebView 66 of an API 28 emulator): the page must load unchanged and the load still be recorded.
 */
class UnsupportedWebViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = WebViewInstrumentation()
    private val events = CopyOnWriteArrayList<Pair<String, Attributes>>()
    private val spans: InMemorySpanExporter = InMemorySpanExporter.create()
    private lateinit var server: MockWebServer
    private val webViews = mutableListOf<WebView>()

    @Before
    fun setUp() {
        assumeFalse(
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
        onMain { instrumentation.install(context, rum) }
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
    fun pageLoadsWithoutContextAndTheLoadIsStillRecorded() {
        onMain {
            val webView = WebView(context).also { webViews.add(it) }
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = WebViewClient()
            WebViewTestUtil.loadUrl(webView, server.url("/old-webview").toString())
        }

        assertThat(reportedSession()).isEqualTo("none")
        val opened = events.single { it.first == "webview.opened" }.second
        assertThat(opened.get(AttributeKey.booleanKey("webview.context.supported"))).isFalse()
        assertThat(spans.finishedSpanItems.map { it.name }).contains("webview.load")
    }

    private val rum =
        object : OpenTelemetryRum {
            override val openTelemetry: OpenTelemetry =
                OpenTelemetrySdk
                    .builder()
                    .setTracerProvider(
                        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build(),
                    ).build()
            override val sessionProvider: SessionProvider = SessionProvider { SESSION_ID }
            override val clock: Clock = Clock.getDefault()

            override fun emitEvent(
                eventName: String,
                body: String,
                attributes: Attributes,
            ) {
                events.add(eventName to attributes)
            }

            override fun shutdown() {
                // Unused.
            }
        }

    private fun reportedSession(): String? {
        while (true) {
            val request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            checkNotNull(request) { "no context beacon within ${TIMEOUT_SECONDS}s" }
            if (request.url.encodedPath == "/beacon/ctx") {
                return request.url.queryParameter("v")
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private companion object {
        const val SESSION_ID = "0123456789abcdef0123456789abcdef"
        const val TIMEOUT_SECONDS = 15L
    }
}
