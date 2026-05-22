/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stub DeviceMetricsReader that avoids real Android context dependencies.
 */
internal class StubDeviceMetricsReader : DeviceMetricsReader(mockk(relaxed = true)) {
    override fun readTotalRamBytes() = 1_000_000L
    override fun readAvailableRamBytes() = 500_000L
    override fun readLowMemoryFlag() = 0L
    override fun readBatteryPercent() = 75.0
    override fun readBatteryTemperatureCelsius() = 30.0
    override fun readDiskFreeBytes() = 10_000_000L
    override fun readDiskTotalBytes() = 100_000_000L
}

class SystemMetricsSpanEmitterTest {
    private lateinit var spanExporter: InMemorySpanExporter
    private lateinit var registry: ActiveSpanRegistry
    private lateinit var tracerProvider: SdkTracerProvider
    private lateinit var openTelemetry: OpenTelemetrySdk

    @BeforeEach
    fun setUp() {
        spanExporter = InMemorySpanExporter.create()
        registry = ActiveSpanRegistry()
        tracerProvider =
            SdkTracerProvider
                .builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .addSpanProcessor(registry)
                .build()
        openTelemetry =
            OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
    }

    @Test
    fun `emits standalone app-metrics span when no user span is active`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val emitter =
            SystemMetricsSpanEmitter(
                openTelemetry = openTelemetry,
                scheduler = scheduler,
                intervalSeconds = 2L,
                activeSpanRegistry = registry,
                deviceReader = StubDeviceMetricsReader(),
            )

        emitter.start()
        // sub-sampler fires at t=1s, emitter fires at t=2s — window has ≥1 sample
        scheduler.awaitTermination(3_500, TimeUnit.MILLISECONDS)
        scheduler.shutdownNow()

        val spans = spanExporter.finishedSpanItems
        assertThat(spans).isNotEmpty
        val metricsSpan = spans.first { it.name == "app.metrics" }
        val event = metricsSpan.events.first { it.name == "app.metrics" }
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_USAGE)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_MIN)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_MAX)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_HEAP_USED)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_THREAD_COUNT)).isNotNull
    }

    @Test
    fun `attaches app-metrics event to active user span instead of emitting standalone`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val emitter =
            SystemMetricsSpanEmitter(
                openTelemetry = openTelemetry,
                scheduler = scheduler,
                intervalSeconds = 2L,
                activeSpanRegistry = registry,
                deviceReader = StubDeviceMetricsReader(),
            )

        // Start a user span BEFORE the emitter fires — registry picks it up via onStart().
        val userSpan =
            openTelemetry.tracerProvider
                .get("test")
                .spanBuilder("UserAction")
                .startSpan()

        emitter.start()
        scheduler.awaitTermination(3_500, TimeUnit.MILLISECONDS)
        scheduler.shutdownNow()

        userSpan.end()

        val finishedSpans = spanExporter.finishedSpanItems
        val userSpanData = finishedSpans.first { it.name == "UserAction" }

        // Metrics event must be on the user span, not emitted as a separate span.
        assertThat(userSpanData.events).anyMatch { it.name == "app.metrics" }
        assertThat(finishedSpans.none { it.name == "app.metrics" }).isTrue

        // Verify CPU min/max are present on the event.
        val event = userSpanData.events.first { it.name == "app.metrics" }
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_MIN)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_MAX)).isNotNull
    }

    @Test
    fun `standalone span carries all process and device metric attributes including min and max`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val emitter =
            SystemMetricsSpanEmitter(
                openTelemetry = openTelemetry,
                scheduler = scheduler,
                intervalSeconds = 2L,
                activeSpanRegistry = registry,
                deviceReader = StubDeviceMetricsReader(),
            )

        emitter.start()
        scheduler.awaitTermination(3_500, TimeUnit.MILLISECONDS)
        scheduler.shutdownNow()

        val metricsSpan = spanExporter.finishedSpanItems.first { it.name == "app.metrics" }
        val event = metricsSpan.events.first { it.name == "app.metrics" }

        // Process attrs — current values
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_USAGE)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_HEAP_USED)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_HEAP_ALLOCATED)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_HEAP_FREE)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_NATIVE_USED)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_THREAD_COUNT)).isNotNull

        // CPU min/max window attrs
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_MIN)).isNotNull
        assertThat(event.attributes.get(SystemMetricsSpanEmitter.ATTR_CPU_MAX)).isNotNull
    }
}
