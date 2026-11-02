/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import io.opentelemetry.api.trace.Span
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe store shared between [CoilOtelEventListener] (which starts spans) and
 * [VunetCoilInterceptor] (which reads the span to propagate its context to OkHttp), plus the
 * listener's own terminal callbacks (which end them).
 *
 * Keys are the identity hash of the Coil [coil.request.ImageRequest] object so that two concurrent
 * requests to the same URL (same String content, different instances) are tracked independently.
 * [System.identityHashCode] collisions are theoretically possible but astronomically rare;
 * the worst outcome is a missed span — never a crash.
 *
 * No OTel [io.opentelemetry.context.Scope] is stored here: [CoilOtelEventListener] deliberately
 * does not call `makeCurrent()` (see its KDoc), so there is no cross-thread scope to manage.
 *
 * [drain] is called during [CoilInstrumentation.uninstall] to guarantee that all orphaned in-flight
 * spans are ended.
 */
internal object CoilSpanStore {
    val spans: ConcurrentHashMap<Int, Span> = ConcurrentHashMap()

    /**
     * Ends all stored [Span] entries, then clears the map.
     * Designed to be called exactly once during SDK teardown.
     */
    fun drain() {
        spans.values.forEach { span ->
            try { span.end() } catch (_: Throwable) {}
        }
        spans.clear()
    }
}
