/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe store shared between [CoilOtelEventListener] instances (which start spans) and the
 * same listener's terminal callbacks (which end them).
 *
 * Keys are the identity hash of the Coil [coil.request.ImageRequest] object so that two concurrent
 * requests to the same URL (same String content, different instances) are tracked independently.
 * [System.identityHashCode] collisions are theoretically possible but astronomically rare;
 * the worst outcome is a missed span — never a crash.
 *
 * [drain] is called during [CoilInstrumentation.uninstall] to guarantee that all in-flight scopes
 * are closed and all orphaned spans are ended, preventing [ThreadLocal] leaks.
 */
internal object CoilSpanStore {
    val spans: ConcurrentHashMap<Int, Span> = ConcurrentHashMap()
    val scopes: ConcurrentHashMap<Int, Scope> = ConcurrentHashMap()

    /**
     * Closes all stored [Scope] entries and ends all stored [Span] entries, then clears both maps.
     * Designed to be called exactly once during SDK teardown.
     */
    fun drain() {
        scopes.values.forEach { scope ->
            try { scope.close() } catch (_: Throwable) {}
        }
        scopes.clear()
        spans.values.forEach { span ->
            try { span.end() } catch (_: Throwable) {}
        }
        spans.clear()
    }
}
