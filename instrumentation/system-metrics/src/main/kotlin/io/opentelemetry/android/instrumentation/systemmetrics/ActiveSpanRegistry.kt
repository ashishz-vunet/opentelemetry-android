/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import java.util.concurrent.atomic.AtomicReference

/**
 * A [SpanProcessor] that tracks the most recently started span across all threads.
 *
 * Used by [SystemMetricsSpanEmitter] to decide at each collection tick whether to:
 * - Attach an `"app.metrics"` event to the currently active span, OR
 * - Emit a standalone `"app.metrics"` span when no user span is in flight.
 *
 * Thread safety: [AtomicReference] guarantees visibility across the UI thread (span
 * creation) and the background [java.util.concurrent.ScheduledExecutorService] thread
 * (metrics emission) without locking.
 */
internal class ActiveSpanRegistry : SpanProcessor {
    private val mostRecentRef = AtomicReference<ReadWriteSpan?>(null)

    /**
     * Returns the most recently started span if it has not yet ended, or null otherwise.
     *
     * Safe to call from any thread. If the span ended between the registry check and the
     * subsequent [ReadWriteSpan.addEvent] call, the OTel SDK silently discards the event
     * on an ended span — no crash, no data corruption.
     */
    fun mostRecentActiveSpan(): ReadWriteSpan? = mostRecentRef.get()?.takeIf { !it.hasEnded() }

    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        mostRecentRef.set(span)
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {
        // Only null out the ref if this is the same span we are tracking.
        // CAS prevents a race where a new span starts between the get() and compareAndSet().
        val current = mostRecentRef.get() ?: return
        if (current.spanContext == span.spanContext) {
            mostRecentRef.compareAndSet(current, null)
        }
    }

    override fun isEndRequired(): Boolean = true
}
