/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.glide

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class GlideInstrumentationTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    private lateinit var instrumentation: GlideInstrumentation
    private lateinit var context: Application
    private lateinit var openTelemetryRum: OpenTelemetryRum

    @BeforeEach
    fun setUp() {
        GlideInstrumentation.tracer = null
        GlideSpanStore.spans.clear()
        GlideSpanStore.startNanos.clear()
        instrumentation = GlideInstrumentation()
        context = mockk(relaxed = true)
        openTelemetryRum = mockk()
        every { openTelemetryRum.openTelemetry } returns otelTesting.openTelemetry
    }

    @AfterEach
    fun tearDown() {
        GlideInstrumentation.tracer = null
        GlideSpanStore.spans.clear()
        GlideSpanStore.startNanos.clear()
    }

    @Test
    fun `install sets shared tracer`() {
        assertThat(GlideInstrumentation.tracer).isNull()
        instrumentation.install(context, openTelemetryRum)
        assertThat(GlideInstrumentation.tracer).isNotNull()
    }

    @Test
    fun `install is idempotent - second call is a no-op`() {
        instrumentation.install(context, openTelemetryRum)
        val firstTracer = GlideInstrumentation.tracer

        instrumentation.install(context, openTelemetryRum)

        assertThat(GlideInstrumentation.tracer).isSameAs(firstTracer)
    }

    @Test
    fun `uninstall clears tracer`() {
        instrumentation.install(context, openTelemetryRum)
        instrumentation.uninstall(context, openTelemetryRum)
        assertThat(GlideInstrumentation.tracer).isNull()
    }

    @Test
    fun `uninstall ends orphaned in-flight spans and clears the store`() {
        val tracer = otelTesting.openTelemetry.tracerProvider.tracerBuilder("test").build()
        val span = tracer.spanBuilder("orphan").startSpan()

        GlideSpanStore.spans[42] = span
        GlideSpanStore.startNanos[42] = System.nanoTime()

        instrumentation.install(context, openTelemetryRum)
        instrumentation.uninstall(context, openTelemetryRum)

        assertThat(span.isRecording).isFalse()
        assertThat(GlideSpanStore.spans).isEmpty()
        assertThat(GlideSpanStore.startNanos).isEmpty()
    }

    @Test
    fun `name returns expected instrumentation name`() {
        assertThat(instrumentation.name).isEqualTo("glide")
    }
}
