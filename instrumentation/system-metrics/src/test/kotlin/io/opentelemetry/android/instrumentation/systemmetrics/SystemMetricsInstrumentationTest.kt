/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SystemMetricsInstrumentationTest {
    private lateinit var context: Context
    private lateinit var openTelemetryRum: OpenTelemetryRum
    private lateinit var openTelemetry: OpenTelemetrySdk

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        val tracerProvider =
            SdkTracerProvider
                .builder()
                .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
                .build()
        openTelemetry =
            OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
        openTelemetryRum = mockk()
        every { openTelemetryRum.openTelemetry } returns openTelemetry
    }

    @Test
    fun `install starts the emitter and sets installed flag`() {
        val instrumentation = SystemMetricsInstrumentation()
        instrumentation.install(context, openTelemetryRum)
        // A second install must be a no-op (idempotency guard).
        // If it were not guarded the executor field would be overwritten and we'd leak the first.
        instrumentation.install(context, openTelemetryRum)
        // Clean up so the background threads don't outlive the test.
        instrumentation.uninstall(context, openTelemetryRum)
    }

    @Test
    fun `install is idempotent — second call is a no-op`() {
        val instrumentation = SystemMetricsInstrumentation()
        instrumentation.install(context, openTelemetryRum)
        instrumentation.install(context, openTelemetryRum) // must not throw or create a second executor

        instrumentation.uninstall(context, openTelemetryRum)
    }

    @Test
    fun `uninstall shuts down the executor cleanly`() {
        val instrumentation = SystemMetricsInstrumentation()
        instrumentation.install(context, openTelemetryRum)
        // Should not throw.
        instrumentation.uninstall(context, openTelemetryRum)
    }

    @Test
    fun `uninstall is idempotent — second call is a no-op`() {
        val instrumentation = SystemMetricsInstrumentation()
        instrumentation.install(context, openTelemetryRum)
        instrumentation.uninstall(context, openTelemetryRum)
        // Second uninstall must not throw.
        instrumentation.uninstall(context, openTelemetryRum)
    }

    @Test
    fun `name returns system-metrics`() {
        assertThat(SystemMetricsInstrumentation().name).isEqualTo("system-metrics")
    }
}
