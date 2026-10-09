/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpuMetricsReaderTest {
    @Test
    fun `readCpuUsagePercent first call returns non-negative value`() {
        val reader = CpuMetricsReader()
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
}
