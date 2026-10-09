/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CoilInstrumentationTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    private lateinit var instrumentation: CoilInstrumentation
    private lateinit var context: android.app.Application
    private lateinit var openTelemetryRum: OpenTelemetryRum

    @BeforeEach
    fun setUp() {
        CoilInstrumentation.tracer = null
        CoilSpanStore.spans.clear()
        instrumentation = CoilInstrumentation()
        context = mockk(relaxed = true)
        openTelemetryRum = mockk()
        every { openTelemetryRum.openTelemetry } returns otelTesting.openTelemetry
    }

    @AfterEach
    fun tearDown() {
        CoilInstrumentation.tracer = null
        CoilSpanStore.spans.clear()
    }

    @Test
    fun `install sets shared tracer`() {
        assertThat(CoilInstrumentation.tracer).isNull()
        instrumentation.install(context, openTelemetryRum)
        assertThat(CoilInstrumentation.tracer).isNotNull()
    }

    @Test
    fun `install is idempotent - second call is a no-op`() {
        instrumentation.install(context, openTelemetryRum)
        val firstTracer = CoilInstrumentation.tracer

        instrumentation.install(context, openTelemetryRum)

        assertThat(CoilInstrumentation.tracer).isSameAs(firstTracer)
    }

    @Test
    fun `uninstall clears tracer`() {
        instrumentation.install(context, openTelemetryRum)
        instrumentation.uninstall(context, openTelemetryRum)
        assertThat(CoilInstrumentation.tracer).isNull()
    }

    @Test
    fun `uninstall ends orphaned in-flight spans and clears the store`() {
        val tracer = otelTesting.openTelemetry.tracerProvider.tracerBuilder("test").build()
        val span = tracer.spanBuilder("orphan").startSpan()

        CoilSpanStore.spans[42] = span

        instrumentation.install(context, openTelemetryRum)
        instrumentation.uninstall(context, openTelemetryRum)

        assertThat(span.isRecording).isFalse()
        assertThat(CoilSpanStore.spans).isEmpty()
    }

    @Test
    fun `name returns expected instrumentation name`() {
        assertThat(instrumentation.name).isEqualTo("coil")
    }

    @Test
    fun `uninstall is safe when not installed`() {
        // Must not throw even if install was never called
        instrumentation.uninstall(context, openTelemetryRum)
        assertThat(CoilInstrumentation.tracer).isNull()
    }
}
