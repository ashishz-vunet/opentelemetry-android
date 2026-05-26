/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import android.content.Context
import com.google.auto.service.AutoService
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SpanProcessor
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry point for system metrics instrumentation.
 *
 * Registers observable gauges for CPU, memory, and thread metrics using the
 * OpenTelemetry [io.opentelemetry.sdk.metrics.SdkMeterProvider] already configured
 * in the SDK. Metrics are collected at the configured [collectionInterval] by the
 * SDK's [io.opentelemetry.sdk.metrics.export.PeriodicMetricReader].
 */
@AutoService(AndroidInstrumentation::class)
class SystemMetricsInstrumentation : AndroidInstrumentation {
    /** Interval at which all system metrics are sampled. Default: 30 seconds. */
    var collectionInterval: Duration = Duration.ofSeconds(30)

    override val name: String = "system-metrics"

    private val installed = AtomicBoolean(false)

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        if (!installed.compareAndSet(false, true)) return

        val meter =
            openTelemetryRum.openTelemetry
                .meterProvider
                .get("io.opentelemetry.android.system-metrics")

        val cpuReader = CpuMetricsReader()
        val memoryReader = MemoryMetricsReader()
        val threadReader = ThreadMetricsReader()
        val deviceReader = DeviceMetricsReader(context)

        registerProcessMetrics(meter, cpuReader, memoryReader, threadReader)
        registerDeviceMetrics(meter, deviceReader)

        val registry = ActiveSpanRegistry()
        val sdk = openTelemetryRum.openTelemetry as? OpenTelemetrySdk
        sdk?.let { injectSpanProcessor(it, registry) }
        SystemMetricsSpanEmitter(
            openTelemetry = openTelemetryRum.openTelemetry,
            scheduler = Executors.newSingleThreadScheduledExecutor(),
            intervalSeconds = collectionInterval.seconds,
            activeSpanRegistry = registry,
            deviceReader = deviceReader,
        ).start()
    }

    private fun registerProcessMetrics(
        meter: Meter,
        cpuReader: CpuMetricsReader,
        memoryReader: MemoryMetricsReader,
        threadReader: ThreadMetricsReader,
    ) {
        meter
            .gaugeBuilder("process.cpu.usage")
            .setDescription("CPU usage of this process as a percentage (0–100)")
            .setUnit("%")
            .buildWithCallback { measurement ->
                measurement.record(cpuReader.readCpuUsagePercent())
            }

        meter
            .gaugeBuilder("process.runtime.jvm.memory.heap.used")
            .setDescription("Java heap memory currently used by this process")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(memoryReader.readHeapUsedBytes())
            }

        meter
            .gaugeBuilder("process.runtime.jvm.memory.native.used")
            .setDescription("Native heap memory currently allocated by this process")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(memoryReader.readNativeHeapUsedBytes())
            }

        meter
            .gaugeBuilder("process.memory.pss")
            .setDescription("Proportional Set Size memory for this process")
            .setUnit("kBy")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(memoryReader.readPssKb())
            }

        meter
            .gaugeBuilder("process.thread.count")
            .setDescription("Total number of threads currently active in this process")
            .setUnit("{thread}")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(threadReader.readThreadCount())
            }

        meter
            .gaugeBuilder("process.thread.count.by_state")
            .setDescription("Thread count grouped by thread state")
            .setUnit("{thread}")
            .ofLongs()
            .buildWithCallback { measurement ->
                threadReader.readThreadCountByState().forEach { (state, count) ->
                    measurement.record(
                        count,
                        Attributes.of(
                            AttributeKey.stringKey("thread.state"),
                            state,
                        ),
                    )
                }
            }
    }

    private fun registerDeviceMetrics(
        meter: Meter,
        deviceReader: DeviceMetricsReader,
    ) {
        meter
            .gaugeBuilder("system.memory.available")
            .setDescription("Available (free) RAM on the device")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readAvailableRamBytes())
            }

        meter
            .gaugeBuilder("system.memory.total")
            .setDescription("Total physical RAM on the device")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readTotalRamBytes())
            }

        meter
            .gaugeBuilder("system.memory.low")
            .setDescription("1 if the device is in a low-memory state, 0 otherwise")
            .setUnit("1")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readLowMemoryFlag())
            }

        meter
            .gaugeBuilder("system.battery.level")
            .setDescription("Battery charge level as a percentage (0–100)")
            .setUnit("%")
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readBatteryPercent())
            }

        meter
            .gaugeBuilder("system.battery.temperature")
            .setDescription("Battery temperature in degrees Celsius")
            .setUnit("Cel")
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readBatteryTemperatureCelsius())
            }

        meter
            .gaugeBuilder("system.disk.free")
            .setDescription("Free disk space on the internal data partition")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readDiskFreeBytes())
            }

        meter
            .gaugeBuilder("system.disk.total")
            .setDescription("Total disk space on the internal data partition")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { measurement ->
                measurement.record(deviceReader.readDiskTotalBytes())
            }
    }

    /**
     * Injects [processor] into the already-built [OpenTelemetrySdk]'s TracerProvider using
     * reflection. This is necessary because [AndroidInstrumentation.install] is called after
     * the SDK is fully constructed, so the standard builder API is no longer available.
     *
     * Reflection targets are both package-private JVM classes in the OTel SDK — not Android
     * platform internals — so this works across all supported Android API levels.
     * A failure here degrades gracefully: span events fall back to standalone spans.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun injectSpanProcessor(
        sdk: OpenTelemetrySdk,
        processor: SpanProcessor,
    ) {
        try {
            val sharedStateField =
                sdk.sdkTracerProvider.javaClass.getDeclaredField("sharedState")
            sharedStateField.isAccessible = true
            val sharedState = sharedStateField.get(sdk.sdkTracerProvider)

            val processorField = sharedState.javaClass.getDeclaredField("activeSpanProcessor")
            processorField.isAccessible = true
            val existing = processorField.get(sharedState) as SpanProcessor

            processorField.set(sharedState, SpanProcessor.composite(existing, processor))
        } catch (e: Exception) {
            // Graceful degradation: metrics will emit as standalone spans instead of events.
        }
    }
}
