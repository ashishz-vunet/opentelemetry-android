/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.Clock
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewContextBridgeTest {
    private val sessionA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val sessionB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    private var currentSessionId = sessionA
    private var nowMillis = 10_000L
    private val sessionProvider = SessionProvider { currentSessionId }
    private val clock =
        object : Clock {
            override fun now(): Long = TimeUnit.MILLISECONDS.toNanos(nowMillis)

            override fun nanoTime(): Long = TimeUnit.MILLISECONDS.toNanos(nowMillis)
        }
    private val api = FakeBridgeApi()
    private val events = mutableListOf<Pair<String, Attributes>>()
    private val webView = WebView(ApplicationProvider.getApplicationContext())
    private var ids = 0

    private fun bridge(config: WebViewHandoffConfig = WebViewHandoffConfig()) =
        WebViewContextBridge(
            sessionProvider = sessionProvider,
            api = api,
            clock = clock,
            config = config,
            app = WebViewContext.AppInfo("com.example.bank", "2.1.0"),
            device = WebViewContext.DeviceInfo("Google", "Pixel 8", "14"),
            screenName = { "DashboardActivity" },
            events = { name, attributes -> events.add(name to attributes) },
            newWebViewId = { "webview-${++ids}" },
        )

    @Test
    fun `attaches the context script and bridge for the loaded origin`() {
        bridge().beforeLoad(webView, "https://bank.example.com:8443/home?x=1")

        assertThat(api.listeners.keys).containsExactly(setOf("https://bank.example.com:8443"))
        val script = api.scripts.single()
        assertThat(script.rules).containsExactly("https://bank.example.com:8443")
        assertThat(script.code).startsWith("(function(){window.__VUNET_CTX__=Object.freeze(")
        val context = contextOf(script.code)
        assertThat(context.getInt("v")).isEqualTo(1)
        assertThat(context.getString("sessionId")).isEqualTo(sessionA)
        assertThat(context.getLong("sessionStartTs")).isEqualTo(10_000L)
        assertThat(context.getBoolean("sampled")).isTrue()
        assertThat(context.getJSONObject("app").getString("id")).isEqualTo("com.example.bank")
        assertThat(context.getJSONObject("app").getString("version")).isEqualTo("2.1.0")
        assertThat(context.getJSONObject("device").getString("model")).isEqualTo("Pixel 8")
        val host = context.getJSONObject("host")
        assertThat(host.getString("platform")).isEqualTo("android")
        assertThat(host.getString("webviewId")).isEqualTo("webview-1")
        assertThat(host.getString("parentViewName")).isEqualTo("DashboardActivity")
    }

    @Test
    fun `emits webview opened once per webview`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/a")
        bridge.beforeLoad(webView, "https://bank.example.com/b")

        assertThat(events.map { it.first }).containsExactly("webview.opened")
        val attributes = events.single().second
        assertThat(attributes.get(WebViewContextBridge.WEBVIEW_ID)).isEqualTo("webview-1")
        assertThat(attributes.get(WebViewContextBridge.WEBVIEW_ORIGIN)).isEqualTo("https://bank.example.com")
        assertThat(attributes.get(WebViewContextBridge.PARENT_VIEW_NAME)).isEqualTo("DashboardActivity")
        assertThat(attributes.get(WebViewContextBridge.WEBVIEW_CONTEXT_SUPPORTED)).isTrue()
    }

    @Test
    fun `reloading the same origin and session registers nothing new`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/a")
        bridge.beforeLoad(webView, "https://bank.example.com/b")

        assertThat(api.scripts).hasSize(1)
        assertThat(api.removedListeners).isEqualTo(0)
    }

    @Test
    fun `a new origin re-registers the bridge and script with both origins`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/")
        bridge.beforeLoad(webView, "https://id.example.com/")

        assertThat(api.removedListeners).isEqualTo(1)
        assertThat(api.listeners.keys.last()).containsExactly("https://bank.example.com", "https://id.example.com")
        assertThat(api.scripts.first().removed).isTrue()
        assertThat(api.scripts.last().rules).containsExactly("https://bank.example.com", "https://id.example.com")
    }

    @Test
    fun `ignores non-http urls and hosts outside the allowlist`() {
        val bridge = bridge(WebViewHandoffConfig(allowedHosts = setOf("bank.example.com", "id.example.com")))
        bridge.beforeLoad(webView, "file:///android_asset/index.html")
        bridge.beforeLoad(webView, null)
        bridge.beforeLoad(webView, "https://ads.other.com/")

        assertThat(api.scripts).isEmpty()
        assertThat(events).isEmpty()

        bridge.beforeLoad(webView, "https://bank.example.com/")
        assertThat(api.scripts.single().rules).containsExactly("https://bank.example.com", "https://id.example.com")
    }

    @Test
    fun `unsupported webviews only record the opened event`() {
        api.supported = false
        bridge().beforeLoad(webView, "https://bank.example.com/")

        assertThat(api.scripts).isEmpty()
        assertThat(api.listeners).isEmpty()
        assertThat(events.single().second.get(WebViewContextBridge.WEBVIEW_CONTEXT_SUPPORTED)).isFalse()
    }

    @Test
    fun `answers getContext from the main frame only`() {
        bridge().beforeLoad(webView, "https://bank.example.com/")
        val listener = api.listeners.values.single()

        val replies = mutableListOf<String>()
        listener.onMessage("""{"type":"getContext"}""", "https://bank.example.com", false) { replies.add(it) }
        listener.onMessage("not json", "https://bank.example.com", true) { replies.add(it) }
        listener.onMessage("""{"type":"other"}""", "https://bank.example.com", true) { replies.add(it) }
        assertThat(replies).isEmpty()

        listener.onMessage("""{"type":"getContext"}""", "https://bank.example.com", true) { replies.add(it) }
        val reply = JSONObject(replies.single())
        assertThat(reply.getString("type")).isEqualTo("context")
        assertThat(reply.getJSONObject("context").getString("sessionId")).isEqualTo(sessionA)
    }

    @Test
    fun `brumReady replies with the context and is recorded once per session`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/")
        val listener = api.listeners.values.single()
        val replies = mutableListOf<String>()

        listener.onMessage("""{"type":"brumReady","version":"3.3.0"}""", "https://bank.example.com", true) { replies.add(it) }
        listener.onMessage("""{"type":"brumReady","version":"3.3.0"}""", "https://bank.example.com", true) { replies.add(it) }

        assertThat(replies).hasSize(2)
        val attached = events.filter { it.first == "webview.brum_attached" }
        assertThat(attached).hasSize(1)
        assertThat(attached.single().second.get(WebViewContextBridge.WEBVIEW_ID)).isEqualTo("webview-1")
        assertThat(attached.single().second.get(WebViewContextBridge.BRUM_VERSION)).isEqualTo("3.3.0")

        currentSessionId = sessionB
        listener.onMessage("""{"type":"brumReady"}""", "https://bank.example.com", true) { replies.add(it) }
        assertThat(events.filter { it.first == "webview.brum_attached" }).hasSize(2)
    }

    @Test
    fun `session rotation re-registers the script and pushes to the loaded page`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/")
        api.loadedUrl = "https://bank.example.com/home"

        currentSessionId = sessionB
        bridge.onSessionStarted(session(sessionB, startMillis = 20_000L), session(sessionA, 10_000L))

        assertThat(api.scripts.first().removed).isTrue()
        val latest = contextOf(api.scripts.last().code)
        assertThat(latest.getString("sessionId")).isEqualTo(sessionB)
        assertThat(latest.getLong("sessionStartTs")).isEqualTo(20_000L)
        val push = api.evaluated.single()
        assertThat(push).contains("window.__VUNET_BRIDGE__").contains("onContext").contains(sessionB)
    }

    @Test
    fun `session rotation does not push into a page from another origin`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/")
        api.loadedUrl = "https://ads.other.com/"

        bridge.onSessionStarted(session(sessionB, startMillis = 20_000L), session(sessionA, 10_000L))

        assertThat(contextOf(api.scripts.last().code).getString("sessionId")).isEqualTo(sessionB)
        assertThat(api.evaluated).isEmpty()
    }

    @Test
    fun `a load after a missed rotation refreshes the script`() {
        val bridge = bridge()
        bridge.beforeLoad(webView, "https://bank.example.com/")
        currentSessionId = sessionB
        bridge.beforeLoad(webView, "https://bank.example.com/next")

        assertThat(api.scripts).hasSize(2)
        assertThat(contextOf(api.scripts.last().code).getString("sessionId")).isEqualTo(sessionB)
    }

    @Test
    fun `refresh reads the session only once a webview is attached`() {
        var reads = 0
        val counting = SessionProvider { reads++.let { currentSessionId } }
        val bridge =
            WebViewContextBridge(
                sessionProvider = counting,
                api = api,
                clock = clock,
                config = WebViewHandoffConfig(),
                app = WebViewContext.AppInfo("com.example.bank", null),
                device = WebViewContext.DeviceInfo("Google", "Pixel 8", "14"),
                screenName = { null },
                events = { _, _ -> },
            )
        bridge.refresh()
        assertThat(reads).isEqualTo(0)

        bridge.beforeLoad(webView, "https://bank.example.com/")
        val afterLoad = reads
        bridge.refresh()
        assertThat(reads).isEqualTo(afterLoad + 1)
    }

    @Test
    fun `parses origin rules`() {
        assertThat(WebViewContextBridge.WebOrigin.parse("HTTPS://Bank.Example.com:443/x")?.rule)
            .isEqualTo("https://bank.example.com")
        assertThat(WebViewContextBridge.WebOrigin.parse("http://10.0.2.2:8090/")?.rule)
            .isEqualTo("http://10.0.2.2:8090")
        assertThat(WebViewContextBridge.WebOrigin.parse("http://[::1]:8080/")).isNull()
        assertThat(WebViewContextBridge.WebOrigin.parse("about:blank")).isNull()
    }

    private fun contextOf(script: String): JSONObject {
        val json = script.substringAfter("Object.freeze(").substringBeforeLast(");")
        return JSONObject(json.removeSuffix(")"))
    }

    private fun session(
        id: String,
        startMillis: Long,
    ): Session =
        object : Session {
            override val id: String = id
            override val startTimestamp: Long = TimeUnit.MILLISECONDS.toNanos(startMillis)
        }
}
