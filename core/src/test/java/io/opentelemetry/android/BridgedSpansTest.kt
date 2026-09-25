/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

internal class BridgedSpansTest {
    private val exporter = InMemorySpanExporter.create()

    // Exports on the calling thread: a batch worker thread would leak across tests and load
    // ContextStorage through the wrong classloader when Robolectric tests share the JVM.
    private val processor = SimpleSpanProcessor.create(exporter)
    private val rumResource =
        Resource.create(
            Attributes.of(
                stringKey("service.name"), "app",
                stringKey("device.model.name"), "Pixel",
                stringKey("vunet.sdk.version"), "0.0.12",
            ),
        )

    @BeforeEach
    fun setUp() {
        BridgedSpans.clear()
    }

    @AfterEach
    fun tearDown() {
        BridgedSpans.clear()
        processor.shutdown()
    }

    @Test
    fun `drops spans until the SDK publishes a target`() {
        assertThat(BridgedSpans.export(listOf(span()))).isFalse()
        assertThat(BridgedSpans.forceFlush().isSuccess).isTrue()
    }

    @Test
    fun `exports through the published processor with the RUM resource`() {
        BridgedSpans.publish(processor, rumResource)
        val original = span()

        assertThat(BridgedSpans.export(listOf(original))).isTrue()
        assertThat(BridgedSpans.forceFlush().join(5, TimeUnit.SECONDS).isSuccess).isTrue()

        val exported = exporter.finishedSpanItems.single()
        assertThat(exported.spanContext).isEqualTo(original.spanContext)
        assertThat(exported.parentSpanContext).isEqualTo(original.parentSpanContext)
        assertThat(exported.startEpochNanos).isEqualTo(original.startEpochNanos)
        assertThat(exported.endEpochNanos).isEqualTo(original.endEpochNanos)
        assertThat(exported.attributes).isEqualTo(original.attributes)
        assertThat(exported.resource).isSameAs(rumResource)
    }

    @Test
    fun `stops accepting spans once its processor is unpublished`() {
        BridgedSpans.publish(processor, rumResource)

        BridgedSpans.clearIfPublished(processor)

        assertThat(BridgedSpans.export(listOf(span()))).isFalse()
        assertThat(BridgedSpans.forceFlush().isSuccess).isTrue()
    }

    @Test
    fun `shutting down an older instance does not unpublish a newer one`() {
        val older = SimpleSpanProcessor.create(InMemorySpanExporter.create())
        BridgedSpans.publish(processor, rumResource)

        BridgedSpans.clearIfPublished(older)

        assertThat(BridgedSpans.export(listOf(span()))).isTrue()
        older.shutdown()
    }

    @Test
    fun `unsampled spans are dropped by the processor`() {
        BridgedSpans.publish(processor, rumResource)

        BridgedSpans.export(listOf(span(TraceFlags.getDefault())))
        BridgedSpans.forceFlush().join(5, TimeUnit.SECONDS)

        assertThat(exporter.finishedSpanItems).isEmpty()
    }

    private fun span(flags: TraceFlags = TraceFlags.getSampled()): SpanData =
        TestSpanData
            .builder()
            .setName("ui.navigation")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(SpanContext.create(TRACE_ID, SPAN_ID, flags, TraceState.getDefault()))
            .setParentSpanContext(SpanContext.create(TRACE_ID, PARENT_ID, flags, TraceState.getDefault()))
            .setStatus(StatusData.unset())
            .setHasEnded(true)
            .setStartEpochNanos(1_000)
            .setEndEpochNanos(5_000)
            .setAttributes(Attributes.of(stringKey("session.id"), "s-1"))
            .build()

    private companion object {
        const val TRACE_ID = "0123456789abcdef0123456789abcdef"
        const val SPAN_ID = "0123456789abcdef"
        const val PARENT_ID = "fedcba9876543210"
    }
}
