/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Periodically captures a snapshot of all system + device metrics and delivers it via
 * one of two paths depending on whether a user span is currently in flight:
 *
 * **Path A — Event on active span (preferred):**
 * When [ActiveSpanRegistry] reports a live span, the metrics snapshot is attached as a
 * named event `"app.metrics"` directly on that span.
 *
 * **Path B — Standalone span (fallback):**
 * When no user span is active, a dedicated instant span named `"app.metrics"` is emitted.
 *
 * **CPU min/max tracking:**
 * A 1-second sub-sampler feeds a rolling window for CPU usage. Each emission includes
 * the min and max CPU observed since the last emission.
 *
 * **Device metrics caching:**
 * Expensive device metrics (PSS, battery, disk, RAM) are refreshed every 60 seconds on
 * a background cache timer to keep emit latency under 1ms.
 */
internal class SystemMetricsSpanEmitter(
    private val openTelemetry: OpenTelemetry,
    private val scheduler: ScheduledExecutorService,
    private val intervalSeconds: Long,
    private val activeSpanRegistry: ActiveSpanRegistry,
    private val cpuReader: CpuMetricsReader = CpuMetricsReader(),
    private val memoryReader: MemoryMetricsReader = MemoryMetricsReader(),
    private val threadReader: ThreadMetricsReader = ThreadMetricsReader(),
    private val deviceReader: DeviceMetricsReader,
) {

    private val tracer =
        openTelemetry.tracerProvider.get("io.opentelemetry.android.system-metrics")

    // Device metrics cache — refreshed every 60 s by the cache timer.
    @Volatile private var cachedPssKb = 0L
    @Volatile private var cachedTotalRamBytes = 0L
    @Volatile private var cachedAvailableRamBytes = 0L
    @Volatile private var cachedLowMemoryFlag = 0L
    @Volatile private var cachedBatteryPercent = 0.0
    @Volatile private var cachedBatteryTempCelsius = 0.0
    @Volatile private var cachedDiskFreeBytes = 0L
    @Volatile private var cachedDiskTotalBytes = 0L

    // CPU sub-sampling window — accessed exclusively from the single scheduler thread.
    private var lastCpuSample = 0.0
    private var cpuMin = Double.MAX_VALUE
    private var cpuMax = 0.0

    fun start() {
        // Seed baseline so first CPU delta has a valid reference.
        cpuReader.readCpuUsagePercent()

        // 1-second sub-sampler — populates the CPU min/max window between emissions.
        @Suppress("DiscouragedApi")
        scheduler.scheduleAtFixedRate(::sampleCpu, 1L, 1L, TimeUnit.SECONDS)

        // Main emit timer: attach-or-standalone every intervalSeconds.
        @Suppress("DiscouragedApi")
        scheduler.scheduleAtFixedRate(::emitMetrics, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)

        // Cache refresh timer: expensive binder calls every 60 seconds.
        @Suppress("DiscouragedApi")
        scheduler.scheduleAtFixedRate(::refreshDeviceCache, 60L, 60L, TimeUnit.SECONDS)
    }

    /** Runs every 1 second to track CPU min/max between emissions. */
    private fun sampleCpu() {
        val cpu = cpuReader.readCpuUsagePercent()
        lastCpuSample = cpu
        if (cpu < cpuMin) cpuMin = cpu
        if (cpu > cpuMax) cpuMax = cpu
    }

    private fun resetCpuWindow() {
        cpuMin = Double.MAX_VALUE
        cpuMax = 0.0
    }

    private fun emitMetrics() {
        val sample =
            ProcessSample(
                cpuUsage = lastCpuSample,
                cpuMin = cpuMin.takeIf { it != Double.MAX_VALUE } ?: 0.0,
                cpuMax = cpuMax,
                heapUsed = memoryReader.readHeapUsedBytes(),
                heapAllocated = memoryReader.readHeapAllocatedBytes(),
                heapFree = memoryReader.readHeapFreeBytes(),
                nativeUsed = memoryReader.readNativeHeapUsedBytes(),
                threadCount = threadReader.readThreadCount(),
            )
        resetCpuWindow()

        val activeSpan = activeSpanRegistry.mostRecentActiveSpan()
        if (activeSpan != null) {
            activeSpan.addEvent("app.metrics", buildAttributes(sample))
        } else {
            emitStandaloneSpan(sample)
        }
    }

    private fun emitStandaloneSpan(sample: ProcessSample) {
        val span =
            tracer
                .spanBuilder("app.metrics")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan()
        try {
            span.addEvent("app.metrics", buildAttributes(sample))
        } finally {
            span.end()
        }
    }

    private fun buildAttributes(sample: ProcessSample): Attributes =
        Attributes
            .builder()
            .put(ATTR_CPU_USAGE, sample.cpuUsage)
            .put(ATTR_CPU_MIN, sample.cpuMin)
            .put(ATTR_CPU_MAX, sample.cpuMax)
            .put(ATTR_HEAP_USED, sample.heapUsed)
            .put(ATTR_HEAP_ALLOCATED, sample.heapAllocated)
            .put(ATTR_HEAP_FREE, sample.heapFree)
            .put(ATTR_NATIVE_USED, sample.nativeUsed)
            .put(ATTR_THREAD_COUNT, sample.threadCount)
            .put(ATTR_PSS_KB, cachedPssKb)
            .put(ATTR_SYS_MEM_TOTAL, cachedTotalRamBytes)
            .put(ATTR_SYS_MEM_AVAILABLE, cachedAvailableRamBytes)
            .put(ATTR_SYS_MEM_LOW, cachedLowMemoryFlag)
            .put(ATTR_BATTERY_LEVEL, cachedBatteryPercent)
            .put(ATTR_BATTERY_TEMP, cachedBatteryTempCelsius)
            .put(ATTR_DISK_FREE, cachedDiskFreeBytes)
            .put(ATTR_DISK_TOTAL, cachedDiskTotalBytes)
            .build()

    private fun refreshDeviceCache() {
        try {
            cachedPssKb = memoryReader.readPssKb()
            cachedTotalRamBytes = deviceReader.readTotalRamBytes()
            cachedAvailableRamBytes = deviceReader.readAvailableRamBytes()
            cachedLowMemoryFlag = deviceReader.readLowMemoryFlag()
            cachedBatteryPercent = deviceReader.readBatteryPercent()
            cachedBatteryTempCelsius = deviceReader.readBatteryTemperatureCelsius()
            cachedDiskFreeBytes = deviceReader.readDiskFreeBytes()
            cachedDiskTotalBytes = deviceReader.readDiskTotalBytes()
        } catch (_: Exception) {
            // Silent fail — stale cache values are used until next refresh
        }
    }

    private data class ProcessSample(
        val cpuUsage: Double,
        val cpuMin: Double,
        val cpuMax: Double,
        val heapUsed: Long,
        val heapAllocated: Long,
        val heapFree: Long,
        val nativeUsed: Long,
        val threadCount: Long,
    )

    companion object {
        // Process — CPU
        val ATTR_CPU_USAGE: AttributeKey<Double> = AttributeKey.doubleKey("process.cpu.usage")
        val ATTR_CPU_MIN: AttributeKey<Double> = AttributeKey.doubleKey("process.cpu.usage.min")
        val ATTR_CPU_MAX: AttributeKey<Double> = AttributeKey.doubleKey("process.cpu.usage.max")

        // Process — heap (current values only)
        val ATTR_HEAP_USED: AttributeKey<Long> = AttributeKey.longKey("process.memory.heap.used")
        val ATTR_HEAP_ALLOCATED: AttributeKey<Long> = AttributeKey.longKey("process.memory.heap.allocated")
        val ATTR_HEAP_FREE: AttributeKey<Long> = AttributeKey.longKey("process.memory.heap.free")
        val ATTR_NATIVE_USED: AttributeKey<Long> = AttributeKey.longKey("process.memory.native.used")
        val ATTR_PSS_KB: AttributeKey<Long> = AttributeKey.longKey("process.memory.pss")
        val ATTR_THREAD_COUNT: AttributeKey<Long> = AttributeKey.longKey("process.thread.count")

        // Device
        val ATTR_SYS_MEM_TOTAL: AttributeKey<Long> = AttributeKey.longKey("system.memory.total")
        val ATTR_SYS_MEM_AVAILABLE: AttributeKey<Long> = AttributeKey.longKey("system.memory.available")
        val ATTR_SYS_MEM_LOW: AttributeKey<Long> = AttributeKey.longKey("system.memory.low")
        val ATTR_BATTERY_LEVEL: AttributeKey<Double> = AttributeKey.doubleKey("system.battery.level")
        val ATTR_BATTERY_TEMP: AttributeKey<Double> = AttributeKey.doubleKey("system.battery.temperature")
        val ATTR_DISK_FREE: AttributeKey<Long> = AttributeKey.longKey("system.disk.free")
        val ATTR_DISK_TOTAL: AttributeKey<Long> = AttributeKey.longKey("system.disk.total")
    }
}
