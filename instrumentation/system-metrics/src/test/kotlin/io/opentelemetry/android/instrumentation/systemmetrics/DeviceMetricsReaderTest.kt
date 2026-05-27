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
    private val reader = DefaultDeviceMetricsReader(context)

    @Test
    fun `readDeviceMemoryInfo total RAM is non-negative`() {
        // Robolectric stubs ActivityManager.getMemoryInfo() with totalMem=0;
        // on a real device this will be positive.
        assertThat(reader.readDeviceMemoryInfo().totalBytes).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readDeviceMemoryInfo available RAM is non-negative`() {
        assertThat(reader.readDeviceMemoryInfo().availableBytes).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `available RAM is less than or equal to total`() {
        val info = reader.readDeviceMemoryInfo()
        assertThat(info.availableBytes).isLessThanOrEqualTo(info.totalBytes)
    }

    @Test
    fun `readDeviceMemoryInfo lowMemoryFlag is 0 or 1`() {
        assertThat(reader.readDeviceMemoryInfo().lowMemoryFlag).isIn(0L, 1L)
    }

    @Test
    fun `readDiskInfo free bytes is non-negative`() {
        assertThat(reader.readDiskInfo().freeBytes).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `readDiskInfo total bytes is non-negative`() {
        // Robolectric stubs StatFs with blockCount=0; on a real device this will be positive.
        assertThat(reader.readDiskInfo().totalBytes).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `free disk space is less than or equal to total`() {
        val info = reader.readDiskInfo()
        assertThat(info.freeBytes).isLessThanOrEqualTo(info.totalBytes)
    }
}
