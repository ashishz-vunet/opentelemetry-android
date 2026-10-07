/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import androidx.core.net.toUri
import android.webkit.WebView
import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.sdk.common.Clock
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

/**
 * Hands the RUM session context to browser RUM running in a WebView, without any app code.
 *
 * [beforeLoad] runs from the woven page-load calls, before the page starts loading. The first time
 * it sees a WebView it attaches to it:
 * - a document-start script sets `window.__VUNET_CTX__` before any page script runs;
 * - a `VunetBridge` message listener answers `getContext` and records `brumReady`.
 *
 * Both are limited to the origins the app itself loaded (or the configured allowed hosts), and
 * only main-frame messages are answered. When the session rotates, every attached WebView gets a
 * fresh document-start script for later navigations and the loaded page gets the new context
 * pushed to `window.__VUNET_BRIDGE__.onContext`.
 *
 * Each page load is also recorded as a `webview.load` span through [loadTraces], and its
 * `traceparent` is handed to that page so browser RUM can continue the trace; it is dropped from
 * the context once browser RUM reports `brumReady`.
 *
 * WebViews are held weakly, so nothing needs to be detached. On WebViews without the required
 * `androidx.webkit` features only the `webview.opened` event is recorded, with
 * `webview.context.supported=false`.
 */
internal class WebViewContextBridge(
    private val sessionProvider: SessionProvider,
    private val api: WebViewBridgeApi,
    private val clock: Clock,
    private val config: WebViewHandoffConfig,
    private val app: WebViewContext.AppInfo,
    private val device: WebViewContext.DeviceInfo,
    private val screenName: (WebView) -> String?,
    private val events: WebViewEvents,
    private val loadTraces: WebViewLoadTraces,
    private val newWebViewId: () -> String = { UUID.randomUUID().toString() },
) : SessionObserver {
    private val lock = Any()
    private val attachments = WeakHashMap<WebView, Attachment>()

    @Volatile
    private var sessionStart: SessionStart? = null

    fun beforeLoad(
        webView: WebView,
        url: String?,
    ) {
        val origin = WebOrigin.parse(url) ?: return
        if (config.allowedHosts.isNotEmpty() && config.allowedHosts.none { it.matches(origin) }) {
            return
        }
        val attachment = attachmentFor(webView, origin)
        // A WebView can move between screens, e.g. when it is pooled and reused.
        screenName(webView)?.let { attachment.parentViewName = it }
        val traceparent = loadTraces.recordLoad(attachment.id, origin.rule)
        if (!attachment.supported) {
            return
        }
        val session = currentSession() ?: return
        attachment.pendingTraceparent = traceparent
        attachment.originRules.add(origin.rule)
        val rulesChanged = attachment.registeredRules != attachment.originRules
        if (rulesChanged) {
            registerListener(webView, attachment)
        }
        if (rulesChanged ||
            attachment.scriptSessionId != session.id ||
            attachment.scriptTraceparent != attachment.pendingTraceparent
        ) {
            registerScript(webView, attachment, session)
        }
    }

    /** Reads the session so an inactivity rotation that is due happens now and gets pushed. */
    fun refresh() {
        val any = synchronized(lock) { attachments.isNotEmpty() }
        if (any) {
            sessionProvider.getSessionId()
        }
    }

    override fun onSessionStarted(
        newSession: Session,
        previousSession: Session,
    ) {
        val session = SessionStart(newSession.id, TimeUnit.NANOSECONDS.toMillis(newSession.startTimestamp))
        sessionStart = session
        if (!isUsable(session.id)) {
            return
        }
        val attached = synchronized(lock) { attachments.entries.filter { it.value.supported }.map { it.key to it.value } }
        attached.forEach { (webView, attachment) ->
            api.post(webView) { pushSession(webView, attachment, session) }
        }
    }

    override fun onSessionEnded(session: Session) {
        // The replacement session is pushed by onSessionStarted.
    }

    private fun attachmentFor(
        webView: WebView,
        origin: WebOrigin,
    ): Attachment {
        val created: Attachment
        synchronized(lock) {
            attachments[webView]?.let { return it }
            created = Attachment(newWebViewId(), screenName(webView), api.isSupported())
            config.allowedHosts.forEach { created.originRules.addAll(it.rules()) }
            attachments[webView] = created
        }
        events.emit(
            "webview.opened",
            Attributes
                .builder()
                .put(WEBVIEW_ID, created.id)
                .put(WEBVIEW_ORIGIN, origin.rule)
                .put(WEBVIEW_CONTEXT_SUPPORTED, created.supported)
                .apply { created.parentViewName?.let { put(PARENT_VIEW_NAME, it) } }
                .build(),
        )
        return created
    }

    private fun registerListener(
        webView: WebView,
        attachment: Attachment,
    ) {
        if (attachment.registeredRules.isNotEmpty()) {
            api.removeMessageListener(webView, ContextScripts.BRIDGE_NAME)
        }
        val rules = attachment.originRules.toSet()
        api.addMessageListener(webView, ContextScripts.BRIDGE_NAME, rules) { message, _, isMainFrame, reply ->
            onMessage(webView, attachment, message, isMainFrame, reply)
        }
        attachment.registeredRules = rules
    }

    private fun registerScript(
        webView: WebView,
        attachment: Attachment,
        session: SessionStart,
    ) {
        attachment.script?.remove()
        val traceparent = attachment.pendingTraceparent
        val script = ContextScripts.documentStart(contextFor(attachment, session, traceparent).toJson())
        attachment.script = api.addDocumentStartScript(webView, script, attachment.registeredRules)
        attachment.scriptSessionId = session.id
        attachment.scriptTraceparent = traceparent
    }

    @Suppress("TooGenericExceptionCaught")
    private fun pushSession(
        webView: WebView,
        attachment: Attachment,
        session: SessionStart,
    ) {
        try {
            if (attachment.scriptSessionId == session.id) {
                return
            }
            // The pending page load belongs to the previous session's trace.
            attachment.pendingTraceparent = null
            registerScript(webView, attachment, session)
            val loaded = WebOrigin.parse(api.currentUrl(webView)) ?: return
            if (loaded.rule in attachment.registeredRules) {
                api.evaluate(webView, ContextScripts.push(contextFor(attachment, session).toJson()))
            }
        } catch (t: Throwable) {
            RumDiagnostics.w({ "webview: failed to push the rotated session" }, t)
        }
    }

    private fun onMessage(
        webView: WebView,
        attachment: Attachment,
        message: String?,
        isMainFrame: Boolean,
        reply: (String) -> Unit,
    ) {
        if (!isMainFrame || message == null) {
            return
        }
        val request =
            try {
                JSONObject(message)
            } catch (_: JSONException) {
                return
            }
        val type = request.optString("type")
        if (type != MESSAGE_GET_CONTEXT && type != MESSAGE_BRUM_READY) {
            return
        }
        val session = currentSession() ?: return
        val context = contextFor(attachment, session)
        reply(JSONObject().put("type", "context").put("context", JSONObject(context.toJson())).toString())
        if (type == MESSAGE_BRUM_READY && attachment.pendingTraceparent != null) {
            // Browser RUM has read the traceparent; later navigations start their own traces.
            attachment.pendingTraceparent = null
            registerScript(webView, attachment, session)
        }
        if (type == MESSAGE_BRUM_READY && attachment.brumAttachedSessionId != session.id) {
            attachment.brumAttachedSessionId = session.id
            events.emit(
                "webview.brum_attached",
                Attributes
                    .builder()
                    .put(WEBVIEW_ID, attachment.id)
                    .apply { request.optString("version").takeIf { it.isNotEmpty() }?.let { put(BRUM_VERSION, it) } }
                    .build(),
            )
        }
    }

    private fun contextFor(
        attachment: Attachment,
        session: SessionStart,
        traceparent: String? = null,
    ): WebViewContext =
        WebViewContext(
            sessionId = session.id,
            sessionStartMillis = session.startMillis,
            // Every session is recorded natively; there is no session sampling to mirror yet.
            sampled = true,
            app = app,
            device = device,
            webViewId = attachment.id,
            parentViewName = attachment.parentViewName,
            traceparent = traceparent,
        )

    private fun currentSession(): SessionStart? {
        val sessionId = sessionProvider.getSessionId()
        if (!isUsable(sessionId)) {
            return null
        }
        val known = sessionStart
        if (known != null && known.id == sessionId) {
            return known
        }
        // The session started before this observer was registered; the first sighting is the
        // closest start time available.
        return SessionStart(sessionId, TimeUnit.NANOSECONDS.toMillis(clock.now())).also { sessionStart = it }
    }

    private fun isUsable(sessionId: String): Boolean = sessionId.isNotEmpty() && sessionId.any { it != '0' }

    private class SessionStart(
        val id: String,
        val startMillis: Long,
    )

    /** Mutated only on the WebView's thread, except for construction under [lock]. */
    private class Attachment(
        val id: String,
        var parentViewName: String?,
        val supported: Boolean,
    ) {
        val originRules = linkedSetOf<String>()
        var registeredRules: Set<String> = emptySet()
        var script: RegisteredScript? = null
        var scriptSessionId: String? = null
        var brumAttachedSessionId: String? = null
        var pendingTraceparent: String? = null
        var scriptTraceparent: String? = null
    }

    /** An http(s) origin, rendered as an `androidx.webkit` allowed-origin rule. */
    internal class WebOrigin(
        val scheme: String,
        val host: String,
        private val port: Int,
    ) {
        val effectivePort: Int
            get() = if (port != -1) port else if (scheme == "https") HTTPS_PORT else HTTP_PORT

        val rule: String
            get() {
                val defaultPort = if (scheme == "https") HTTPS_PORT else HTTP_PORT
                val portPart = if (port == -1 || port == defaultPort) "" else ":$port"
                return "$scheme://$host$portPart"
            }

        companion object {
            private const val HTTP_PORT = 80
            private const val HTTPS_PORT = 443

            fun parse(url: String?): WebOrigin? {
                if (url.isNullOrEmpty()) {
                    return null
                }
                val uri = url.toUri()
                val scheme = uri.scheme?.lowercase() ?: return null
                if (scheme != "http" && scheme != "https") {
                    return null
                }
                val host = uri.host?.lowercase()
                if (host.isNullOrEmpty() || host.contains(':') || host.contains('[')) {
                    return null
                }
                return WebOrigin(scheme, host, uri.port)
            }
        }
    }

    companion object {
        const val MESSAGE_GET_CONTEXT = "getContext"
        const val MESSAGE_BRUM_READY = "brumReady"

        val WEBVIEW_ID: AttributeKey<String> = AttributeKey.stringKey("webview.id")
        val WEBVIEW_ORIGIN: AttributeKey<String> = AttributeKey.stringKey("webview.origin")
        val WEBVIEW_CONTEXT_SUPPORTED: AttributeKey<Boolean> = AttributeKey.booleanKey("webview.context.supported")
        val PARENT_VIEW_NAME: AttributeKey<String> = AttributeKey.stringKey("parent.view.name")
        val BRUM_VERSION: AttributeKey<String> = AttributeKey.stringKey("brum.version")
    }
}

/** Records a WebView page load as a span and returns its W3C `traceparent`, if any. */
internal fun interface WebViewLoadTraces {
    fun recordLoad(
        webViewId: String,
        origin: String,
    ): String?
}

internal fun traceparentOf(spanContext: SpanContext): String? =
    if (spanContext.isValid) {
        "00-${spanContext.traceId}-${spanContext.spanId}-${spanContext.traceFlags.asHex()}"
    } else {
        null
    }

internal fun interface WebViewEvents {
    fun emit(
        name: String,
        attributes: Attributes,
    )
}
