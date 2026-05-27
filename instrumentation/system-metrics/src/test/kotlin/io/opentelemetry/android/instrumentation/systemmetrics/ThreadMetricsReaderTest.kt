/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThreadMetricsReaderTest {
    @Test
    fun `readThreadCount returns positive value`() {
        val reader = ThreadMetricsReader()
        assertThat(reader.readThreadCount()).isGreaterThan(0L)
    }
}
