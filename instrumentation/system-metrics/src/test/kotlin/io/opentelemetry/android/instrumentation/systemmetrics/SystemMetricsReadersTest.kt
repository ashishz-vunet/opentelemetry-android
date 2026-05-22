/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SystemMetricsReadersTest {
    @Test
    fun `readHeapUsedBytes returns positive value`() {
        val reader = MemoryMetricsReader()
        assertThat(reader.readHeapUsedBytes()).isGreaterThan(0L)
    }

    @Test
    fun `readNativeHeapUsedBytes returns non-negative value`() {
        val reader = MemoryMetricsReader()
        assertThat(reader.readNativeHeapUsedBytes()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readCpuUsagePercent first call returns zero or non-negative value`() {
        val reader = CpuMetricsReader()
        // First call: no prior sample delta, should return ~0.0 (no CPU was used yet)
        // Uses Process.getElapsedCpuTime() which is thread-safe and always available
        assertThat(reader.readCpuUsagePercent()).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(100.0)
    }

    @Test
    fun `readCpuUsagePercent second call returns percentage between 0-100`() {
        val reader = CpuMetricsReader()
        reader.readCpuUsagePercent() // seed the baseline

        // Do some work so CPU time advances
        var sum = 0L
        for (i in 0 until 100_000) {
            sum += i
        }

        val usage = reader.readCpuUsagePercent()
        assertThat(usage).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(100.0)
    }

    @Test
    fun `readThreadCount returns positive value`() {
        val reader = ThreadMetricsReader()
        assertThat(reader.readThreadCount()).isGreaterThan(0L)
    }

    @Test
    fun `readThreadCountByState includes RUNNABLE state`() {
        val reader = ThreadMetricsReader()
        val states = reader.readThreadCountByState()
        assertThat(states).containsKey("RUNNABLE")
        assertThat(states["RUNNABLE"]).isGreaterThan(0L)
    }

    @Test
    fun `gauges are registered and produce metrics`() {
        val metricReader = InMemoryMetricReader.create()
        val meterProvider =
            SdkMeterProvider
                .builder()
                .registerMetricReader(metricReader)
                .build()

        // Register gauges using the SDK meter directly
        val meter = meterProvider.get("io.opentelemetry.android.system-metrics")
        val memoryReader = MemoryMetricsReader()

        meter
            .gaugeBuilder("test.heap.used")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { m -> m.record(memoryReader.readHeapUsedBytes()) }

        metricReader.forceFlush()
        val metrics = metricReader.collectAllMetrics()

        assertThat(metrics).anyMatch { it.name == "test.heap.used" }
        val heapMetric = metrics.first { it.name == "test.heap.used" }
        assertThat(heapMetric.longGaugeData.points).isNotEmpty
        assertThat(heapMetric.longGaugeData.points.first().value).isGreaterThan(0L)
    }
}
