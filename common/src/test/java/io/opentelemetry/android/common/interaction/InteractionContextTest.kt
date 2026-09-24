/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common.interaction

import io.opentelemetry.android.common.internal.instrumentation.ActiveInteractionContext
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InteractionContextTest {
    private val exporter = InMemorySpanExporter.create()
    private val tracerProvider =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
    private val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
    private val tracer = openTelemetry.getTracer("test-interaction-context")

    @AfterEach
    fun tearDown() {
        ActiveInteractionContext.clear()
        exporter.reset()
    }

    @Test
    fun no_interaction_returns_current() {
        val current = Context.root()
        val result = InteractionContext.parentForManualSpan(current)

        assertThat(result).isEqualTo(current)
        assertThat(Span.fromContext(result).spanContext.isValid).isFalse()
    }

    @Test
    fun begin_tap_parents_to_tap() {
        val tap = beginTap()

        val result = InteractionContext.parentForManualSpan(Context.root())
        val parent = Span.fromContext(result)

        assertThat(parent.spanContext.spanId).isEqualTo(tap.spanContext.spanId)
        assertThat(parent.spanContext.traceId).isEqualTo(tap.spanContext.traceId)
    }

    @Test
    fun valid_span_on_different_trace_is_unchanged() {
        beginTap()
        val manual = tracer.spanBuilder("manual").setNoParent().startSpan()
        manual.end()
        val current = Context.root().with(manual)

        val result = InteractionContext.parentForManualSpan(current)

        assertThat(result).isEqualTo(current)
        assertThat(Span.fromContext(result).spanContext.spanId).isEqualTo(manual.spanContext.spanId)
    }

    @Test
    fun valid_span_in_tap_trace_is_unchanged() {
        val tap = beginTap()
        val nested =
            tracer
                .spanBuilder("nested")
                .setParent(Context.root().with(tap))
                .startSpan()
        nested.end()
        val current = Context.root().with(nested)

        val result = InteractionContext.parentForManualSpan(current)

        assertThat(result).isEqualTo(current)
        assertThat(Span.fromContext(result).spanContext.spanId).isEqualTo(nested.spanContext.spanId)
    }

    @Test
    fun end_clears_tap_parent() {
        val tap = tracer.spanBuilder("ui.interaction").setNoParent().startSpan()
        val token = ActiveInteractionContext.begin(tap)
        tap.end()
        ActiveInteractionContext.end(token)

        val current = Context.root()
        val result = InteractionContext.parentForManualSpan(current)

        assertThat(result).isEqualTo(current)
        assertThat(Span.fromContext(result).spanContext.isValid).isFalse()
    }

    @Test
    fun activate_navigation_still_parents_to_tap() {
        val tap = beginTap()
        val nav =
            tracer
                .spanBuilder("ui.navigation")
                .setParent(Context.root().with(tap))
                .startSpan()
        nav.end()
        ActiveInteractionContext.activate(nav)

        val result = InteractionContext.parentForManualSpan(Context.root())
        val parent = Span.fromContext(result)

        assertThat(parent.spanContext.spanId).isEqualTo(tap.spanContext.spanId)
        assertThat(parent.spanContext.spanId).isNotEqualTo(nav.spanContext.spanId)
    }

    @Test
    fun preserves_context_key_and_baggage() {
        val tap = beginTap()
        val key = ContextKey.named<String>("test-key")
        val baggage = Baggage.builder().put("bag", "value").build()
        val current =
            Context
                .root()
                .with(key, "keep-me")
                .with(baggage)

        val result = InteractionContext.parentForManualSpan(current)

        assertThat(result.get(key)).isEqualTo("keep-me")
        assertThat(Baggage.fromContext(result).getEntryValue("bag")).isEqualTo("value")
        assertThat(Span.fromContext(result).spanContext.spanId).isEqualTo(tap.spanContext.spanId)
    }

    @Test
    fun does_not_make_context_current() {
        val before = Context.current()
        InteractionContext.parentForManualSpan()
        assertThat(Context.current()).isSameAs(before)

        beginTap()
        InteractionContext.parentForManualSpan()
        assertThat(Context.current()).isSameAs(before)
    }

    @Test
    fun readable_from_another_thread() {
        val tap = beginTap()
        val latch = CountDownLatch(1)
        var parentSpanId: String? = null

        Thread {
            val result = InteractionContext.parentForManualSpan()
            parentSpanId = Span.fromContext(result).spanContext.spanId
            latch.countDown()
        }.start()

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(parentSpanId).isEqualTo(tap.spanContext.spanId)
    }

    @Test
    fun clear_drops_tap_parent() {
        beginTap()
        ActiveInteractionContext.clear()

        val current = Context.root()
        val result = InteractionContext.parentForManualSpan(current)

        assertThat(result).isEqualTo(current)
        assertThat(Span.fromContext(result).spanContext.isValid).isFalse()
    }

    private fun beginTap(): Span {
        val tap = tracer.spanBuilder("ui.interaction").setNoParent().startSpan()
        ActiveInteractionContext.begin(tap)
        tap.end()
        return tap
    }
}
