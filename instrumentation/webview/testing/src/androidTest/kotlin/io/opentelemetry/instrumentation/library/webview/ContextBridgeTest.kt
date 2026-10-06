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
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Runs the context bridge in a real WebView: pages served by [MockWebServer] report what they see
 * through image beacons, and each test asserts on those beacon requests.
 */
class ContextBridgeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = WebViewInstrumentation()
    private val rum = FakeRum()
    private lateinit var server: MockWebServer
    private lateinit var otherServer: MockWebServer
    private val webViews = mutableListOf<WebView>()

    @Before
    fun setUp() {
        assumeTrue(
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER),
        )
        server = MockWebServer().apply { dispatcher = pages() }
        server.start()
        otherServer = MockWebServer().apply { dispatcher = pages() }
        otherServer.start()
        onMain { instrumentation.install(context, rum) }
    }

    @After
    fun tearDown() {
        onMain {
            webViews.forEach { it.destroy() }
            instrumentation.uninstall(context, rum)
        }
        server.close()
        otherServer.close()
    }

    @Test
    fun contextIsSetBeforeTheFirstPageScript() {
        load(server.url("/first-script").toString())

        val ctx = JSONObject(takeBeacon(server, "/beacon/ctx").url.queryParameter("v")!!)
        assertThat(ctx.getInt("v")).isEqualTo(1)
        assertThat(ctx.getString("sessionId")).isEqualTo(SESSION_A)
        assertThat(ctx.getBoolean("sampled")).isTrue()
        assertThat(ctx.getJSONObject("app").getString("id")).isEqualTo(context.packageName)
        assertThat(ctx.getJSONObject("host").getString("platform")).isEqualTo("android")
        assertThat(ctx.getJSONObject("host").getString("webviewId")).isNotEmpty()
    }

    @Test
    fun bridgeAnswersAndRecordsBrumReady() {
        load(server.url("/bridge").toString())

        val reply = JSONObject(takeBeacon(server, "/beacon/reply").url.queryParameter("v")!!)
        assertThat(reply.getString("type")).isEqualTo("context")
        assertThat(reply.getJSONObject("context").getString("sessionId")).isEqualTo(SESSION_A)

        val names = rum.events.map { it.first }
        assertThat(names).contains("webview.opened", "webview.brum_attached")
        val attached = rum.events.first { it.first == "webview.brum_attached" }.second
        assertThat(attached.get(AttributeKey.stringKey("brum.version"))).isEqualTo("test")
    }

    @Test
    fun rotationIsPushedToTheLoadedPage() {
        load(server.url("/rotation").toString())
        takeBeacon(server, "/beacon/ready")

        rum.rotateTo(SESSION_B)

        val pushed = takeBeacon(server, "/beacon/pushed").url
        assertThat(pushed.queryParameter("callback")).isEqualTo(SESSION_B)
        assertThat(pushed.queryParameter("global")).isEqualTo(SESSION_B)
        assertThat(takeBeacon(server, "/beacon/event").url.queryParameter("v")).isEqualTo(SESSION_B)
    }

    @Test
    fun pageLoadCarriesTheTraceparentOfTheNativeLoadSpan() {
        load(server.url("/first-script").toString())

        val ctx = JSONObject(takeBeacon(server, "/beacon/ctx").url.queryParameter("v")!!)
        val loadSpan = rum.spans.finishedSpanItems.single { it.name == "webview.load" }
        assertThat(ctx.getString("traceparent"))
            .isEqualTo("00-${loadSpan.traceId}-${loadSpan.spanId}-${loadSpan.spanContext.traceFlags.asHex()}")
        assertThat(loadSpan.attributes.get(AttributeKey.stringKey("webview.id")))
            .isEqualTo(ctx.getJSONObject("host").getString("webviewId"))
    }

    @Test
    fun navigationAfterBrumReadyHasNoTraceparent() {
        load(server.url("/bridge-then-next").toString())

        assertThat(takeBeacon(server, "/beacon/ctx").url.queryParameter("v")).doesNotContain("traceparent")
    }

    @Test
    fun anotherOriginNavigatedToFromThePageGetsNoContext() {
        val other = otherServer.url("/first-script").toString()
        load(server.url("/redirect?to=" + java.net.URLEncoder.encode(other, "UTF-8")).toString())

        assertThat(takeBeacon(otherServer, "/beacon/ctx").url.queryParameter("v")).isEqualTo("null")
    }

    private fun load(url: String) {
        onMain {
            val webView = WebView(context).also { webViews.add(it) }
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = WebViewClient()
            WebViewTestUtil.loadUrl(webView, url)
        }
    }

    private fun pages(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val script =
                    when (request.url.encodedPath) {
                        "/first-script" -> "beacon('ctx',{v:JSON.stringify(window.__VUNET_CTX__||null)});"
                        "/bridge" ->
                            "VunetBridge.onmessage=function(e){beacon('reply',{v:e.data});};" +
                                "VunetBridge.postMessage(JSON.stringify({type:'brumReady',version:'test'}));"
                        "/rotation" ->
                            "window.__VUNET_BRIDGE__={onContext:function(c){" +
                                "beacon('pushed',{callback:c.sessionId,global:window.__VUNET_CTX__.sessionId});}};" +
                                "window.addEventListener('vunet:context',function(e){beacon('event',{v:e.detail.sessionId});});" +
                                "beacon('ready',{});"
                        "/bridge-then-next" ->
                            "VunetBridge.onmessage=function(){location.href='/first-script';};" +
                                "VunetBridge.postMessage(JSON.stringify({type:'brumReady'}));"
                        "/redirect" -> "location.href=" + JSONObject.quote(request.url.queryParameter("to")) + ";"
                        else -> ""
                    }
                return MockResponse
                    .Builder()
                    .body("<html><head><script>$BEACON$script</script></head><body>ok</body></html>")
                    .build()
            }
        }

    private fun takeBeacon(
        target: MockWebServer,
        path: String,
    ): RecordedRequest {
        while (true) {
            val request = target.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            checkNotNull(request) { "no request for $path within ${TIMEOUT_SECONDS}s" }
            if (request.url.encodedPath == path) {
                return request
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class FakeRum : OpenTelemetryRum {
        @Volatile
        private var current: Session = TestSession(SESSION_A)
        private val observers = CopyOnWriteArrayList<SessionObserver>()
        val events = CopyOnWriteArrayList<Pair<String, Attributes>>()
        val spans: InMemorySpanExporter = InMemorySpanExporter.create()

        override val openTelemetry: OpenTelemetry =
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(
                    SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build(),
                ).build()
        override val sessionProvider: SessionProvider =
            object : SessionProvider, SessionPublisher {
                override fun getSessionId(): String = current.id

                override fun addObserver(observer: SessionObserver) {
                    observers.add(observer)
                }
            }
        override val clock: Clock = Clock.getDefault()

        fun rotateTo(id: String) {
            val previous = current
            current = TestSession(id)
            observers.forEach {
                it.onSessionEnded(previous)
                it.onSessionStarted(current, previous)
            }
        }

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

    private class TestSession(
        override val id: String,
    ) : Session {
        override val startTimestamp: Long = Clock.getDefault().now()
    }

    private companion object {
        const val SESSION_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SESSION_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val TIMEOUT_SECONDS = 15L
        const val BEACON =
            "function beacon(n,q){var s=Object.keys(q).map(function(k){" +
                "return k+'='+encodeURIComponent(q[k]);}).join('&');new Image().src='/beacon/'+n+'?'+s;}"
    }
}
