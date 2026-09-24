/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.DelegatingSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.atomic.AtomicReference

/**
 * Entry point for spans that were created and ended outside this SDK — by a first-party
 * wrapper (Flutter, React Native) that runs its own tracer — so they leave the device through
 * the same pipeline as native spans instead of a second exporter.
 *
 * Spans are handed to the batch span processor the SDK already drains, never to an exporter
 * directly: exporters are not safe to call concurrently with the batch worker, and a side queue
 * would be missed by the crash flush, which force-flushes the tracer provider. From the batch
 * processor on, bridged spans go through disk buffering, action summary, the export-loop marker,
 * exporter customizers and the one OTLP client, exactly like native spans.
 *
 * Each span is given the SDK's resource, fixed at process start, merged with the caller's
 * `resourceOverrides` (for example the wrapper's `telemetry.sdk.language`). Trace and span ids,
 * timestamps and attributes are kept as the wrapper recorded them; `session.id` and global
 * attributes are the caller's to stamp, because the processors that add them only run when a
 * span starts inside this SDK.
 *
 * Same visibility model as [AppStartSpans]: anyone can export, only the builder can publish the
 * target. Not populated when the host app supplies its own pre-built `OpenTelemetrySdk` via
 * `SdkPreconfiguredRumBuilder` — the SDK does not own a batch processor on that path.
 */
object BridgedSpans {
    private val target = AtomicReference<Target?>()

    /**
     * Queues [spans] for export through the SDK's span pipeline. Safe to call from any thread.
     *
     * @return false when the SDK has not been built, in which case the spans are dropped.
     */
    @JvmStatic
    fun export(
        spans: Collection<SpanData>,
        resourceOverrides: Attributes,
    ): Boolean {
        val t = target.get() ?: return false
        val resource = t.resourceWith(resourceOverrides)
        for (span in spans) {
            t.processor.onEnd(BridgedReadableSpan(span, resource))
        }
        return true
    }

    /** Flushes the span pipeline, including bridged spans already passed to [export]. */
    @JvmStatic
    fun forceFlush(): CompletableResultCode = target.get()?.processor?.forceFlush() ?: CompletableResultCode.ofSuccess()

    internal fun publish(
        processor: SpanProcessor,
        resource: Resource,
    ) {
        target.set(Target(processor, resource))
    }

    internal fun clear() {
        target.set(null)
    }

    private class Target(
        val processor: SpanProcessor,
        private val resource: Resource,
    ) {
        // The overrides are constant for a process in practice, so the merge is done once and
        // reused for as long as the caller passes the same instance.
        @Volatile
        private var merged: Pair<Attributes, Resource>? = null

        fun resourceWith(overrides: Attributes): Resource {
            merged?.let { (key, value) -> if (key === overrides) return value }
            val value = if (overrides.isEmpty) resource else resource.merge(Resource.create(overrides))
            merged = overrides to value
            return value
        }
    }

    /** Presents an already-ended [SpanData] to a [SpanProcessor], with the SDK's resource. */
    private class BridgedReadableSpan(
        span: SpanData,
        resource: Resource,
    ) : ReadableSpan {
        private val data: SpanData =
            object : DelegatingSpanData(span) {
                override fun getResource(): Resource = resource
            }

        override fun getSpanContext(): SpanContext = data.spanContext

        override fun getParentSpanContext(): SpanContext = data.parentSpanContext

        override fun getName(): String = data.name

        override fun toSpanData(): SpanData = data

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = data.instrumentationLibraryInfo

        override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo = data.instrumentationScopeInfo

        override fun hasEnded(): Boolean = true

        override fun getLatencyNanos(): Long = data.endEpochNanos - data.startEpochNanos

        override fun getKind(): SpanKind = data.kind

        override fun <T : Any?> getAttribute(key: AttributeKey<T>): T? = data.attributes.get(key)

        override fun getAttributes(): Attributes = data.attributes
    }
}
