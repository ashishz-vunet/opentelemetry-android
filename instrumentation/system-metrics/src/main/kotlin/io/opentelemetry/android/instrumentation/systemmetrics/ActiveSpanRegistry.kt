/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * A [SpanProcessor] that tracks all in-flight spans so [SystemMetricsSpanEmitter] can attach
 * `"app.metrics"` events to the most recently started, still-active span.
 *
 * **Semantics**: maintains an ordered deque of spans in start order. At emit time,
 * [mostRecentActiveSpan] scans from newest to oldest and returns the first span that has
 * not yet ended. This correctly handles the case where a short-lived child span B starts
 * and ends while a longer-lived parent span A is still running — A remains discoverable.
 *
 * **Registration**: injected into the already-built [io.opentelemetry.sdk.OpenTelemetrySdk]
 * via reflection in [SystemMetricsInstrumentation]. If injection fails the emitter falls back
 * to standalone `"app.metrics"` spans — a [android.util.Log.w] is emitted so the failure
 * is visible in logcat.
 *
 * Thread safety: [ConcurrentLinkedDeque] operations are individually thread-safe. The
 * [mostRecentActiveSpan] traversal may see a span that ends concurrently; a stale
 * [ReadWriteSpan.addEvent] on an already-ended span is silently discarded by the OTel SDK.
 * [onEnd] uses [removeIf] which is an O(N) scan — acceptable given that typical Android apps
 * have fewer than 20 in-flight spans at any point in time.
 */
internal class ActiveSpanRegistry : SpanProcessor {
    private val spans = ConcurrentLinkedDeque<ReadWriteSpan>()

    /**
     * Returns the most recently started span that has not yet ended, or null if none exist.
     * Lazily removes ended spans from the tail of the deque during the scan.
     */
    fun mostRecentActiveSpan(): ReadWriteSpan? {
        val iter = spans.descendingIterator()
        while (iter.hasNext()) {
            val span = iter.next()
            if (!span.hasEnded()) return span
            iter.remove()
        }
        return null
    }

    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        spans.addLast(span)
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {
        spans.removeIf { it.spanContext == span.spanContext }
    }

    override fun isEndRequired(): Boolean = true
}

