/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActiveSpanRegistryTest {
    private lateinit var registry: ActiveSpanRegistry
    private lateinit var tracer: io.opentelemetry.api.trace.Tracer

    @BeforeEach
    fun setUp() {
        registry = ActiveSpanRegistry()
        val tracerProvider =
            SdkTracerProvider
                .builder()
                .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
                .addSpanProcessor(registry)
                .build()
        val sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
        tracer = sdk.tracerProvider.get("test")
    }

    @Test
    fun `returns null when no spans have been started`() {
        assertThat(registry.mostRecentActiveSpan()).isNull()
    }

    @Test
    fun `returns the active span after it is started`() {
        val span = tracer.spanBuilder("A").startSpan()
        assertThat(registry.mostRecentActiveSpan()).isNotNull
        span.end()
    }

    @Test
    fun `returns null after the only span ends`() {
        val span = tracer.spanBuilder("A").startSpan()
        span.end()
        assertThat(registry.mostRecentActiveSpan()).isNull()
    }

    @Test
    fun `returns most recently started span when multiple spans are active`() {
        val spanA = tracer.spanBuilder("A").startSpan()
        val spanB = tracer.spanBuilder("B").startSpan()
        assertThat(registry.mostRecentActiveSpan()?.name).isEqualTo("B")
        spanA.end()
        spanB.end()
    }

    @Test
    fun `long-running span A is still returned after short span B starts and ends`() {
        val spanA = tracer.spanBuilder("A").startSpan()
        val spanB = tracer.spanBuilder("B").startSpan()
        spanB.end() // B ends while A is still running
        assertThat(registry.mostRecentActiveSpan()?.name).isEqualTo("A")
        spanA.end()
    }

    @Test
    fun `returns null after all spans end`() {
        val spanA = tracer.spanBuilder("A").startSpan()
        val spanB = tracer.spanBuilder("B").startSpan()
        spanA.end()
        spanB.end()
        assertThat(registry.mostRecentActiveSpan()).isNull()
    }

    @Test
    fun `handles rapid start and end without error`() {
        repeat(20) {
            val span = tracer.spanBuilder("span-$it").startSpan()
            span.end()
        }
        assertThat(registry.mostRecentActiveSpan()).isNull()
    }
}
