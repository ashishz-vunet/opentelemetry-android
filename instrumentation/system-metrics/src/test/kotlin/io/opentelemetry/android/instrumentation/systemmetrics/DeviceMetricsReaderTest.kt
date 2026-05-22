/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class DeviceMetricsReaderTest {
    private val context = RuntimeEnvironment.getApplication()
    private val reader = DeviceMetricsReader(context)

    @Test
    fun `readTotalRamBytes returns non-negative value`() {
        // Robolectric stubs ActivityManager.getMemoryInfo() with totalMem=0;
        // on a real device this will be positive.
        assertThat(reader.readTotalRamBytes()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readAvailableRamBytes returns non-negative value`() {
        assertThat(reader.readAvailableRamBytes()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readAvailableRamBytes is less than or equal to total`() {
        assertThat(reader.readAvailableRamBytes()).isLessThanOrEqualTo(reader.readTotalRamBytes())
    }

    @Test
    fun `readLowMemoryFlag returns 0 or 1`() {
        val flag = reader.readLowMemoryFlag()
        assertThat(flag).isIn(0L, 1L)
    }

    @Test
    fun `readDiskFreeBytes returns non-negative value`() {
        assertThat(reader.readDiskFreeBytes()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readDiskTotalBytes returns non-negative value`() {
        // Robolectric stubs StatFs with blockCount=0; on a real device this will be positive.
        assertThat(reader.readDiskTotalBytes()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readDiskFreeBytes is less than or equal to total`() {
        assertThat(reader.readDiskFreeBytes()).isLessThanOrEqualTo(reader.readDiskTotalBytes())
    }
}
