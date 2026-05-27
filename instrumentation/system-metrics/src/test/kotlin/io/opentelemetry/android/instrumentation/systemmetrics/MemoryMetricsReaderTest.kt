/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemoryMetricsReaderTest {
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
    fun `readHeapUsedBytes is less than or equal to readHeapAllocatedBytes`() {
        val reader = MemoryMetricsReader()
        assertThat(reader.readHeapUsedBytes()).isLessThanOrEqualTo(reader.readHeapAllocatedBytes())
    }

    @Test
    fun `readHeapFreeBytes is non-negative`() {
        val reader = MemoryMetricsReader()
        assertThat(reader.readHeapFreeBytes()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `heap reader integrates with OTel gauge callback`() {
        val metricReader = InMemoryMetricReader.create()
        val meterProvider =
            SdkMeterProvider
                .builder()
                .registerMetricReader(metricReader)
                .build()
        val meter = meterProvider.get("io.opentelemetry.android.system-metrics")
        val reader = MemoryMetricsReader()

        meter
            .gaugeBuilder("test.heap.used")
            .setUnit("By")
            .ofLongs()
            .buildWithCallback { m -> m.record(reader.readHeapUsedBytes()) }

        metricReader.forceFlush()
        val metrics = metricReader.collectAllMetrics()

        assertThat(metrics).anyMatch { it.name == "test.heap.used" }
        val heapMetric = metrics.first { it.name == "test.heap.used" }
        assertThat(heapMetric.longGaugeData.points).isNotEmpty
        assertThat(heapMetric.longGaugeData.points.first().value).isGreaterThan(0L)
    }
}
