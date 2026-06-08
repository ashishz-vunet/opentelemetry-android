/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.context.Scope
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
        CoilSpanStore.scopes.clear()
        instrumentation = CoilInstrumentation()
        context = mockk(relaxed = true)
        openTelemetryRum = mockk()
        every { openTelemetryRum.openTelemetry } returns otelTesting.openTelemetry
    }

    @AfterEach
    fun tearDown() {
        CoilInstrumentation.tracer = null
        CoilSpanStore.spans.clear()
        CoilSpanStore.scopes.clear()
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
    fun `uninstall closes in-flight scopes and ends orphaned spans`() {
        val tracer = otelTesting.openTelemetry.tracerProvider.tracerBuilder("test").build()
        val span = tracer.spanBuilder("orphan").startSpan()
        var scopeClosed = false
        val fakeScope = Scope { scopeClosed = true }

        CoilSpanStore.spans[42] = span
        CoilSpanStore.scopes[42] = fakeScope

        instrumentation.install(context, openTelemetryRum)
        instrumentation.uninstall(context, openTelemetryRum)

        assertThat(scopeClosed).isTrue()
        assertThat(CoilSpanStore.spans).isEmpty()
        assertThat(CoilSpanStore.scopes).isEmpty()
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
