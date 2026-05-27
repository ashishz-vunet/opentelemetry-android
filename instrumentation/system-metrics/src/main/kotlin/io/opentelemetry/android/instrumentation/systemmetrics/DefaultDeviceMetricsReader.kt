/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/**
 * Default implementation of [DeviceMetricsReader] backed by real Android system APIs.
 *
 * Each of the three batch methods performs exactly one IPC or system call per invocation.
 * Callers must use the batch methods directly — do not call multiple individual field accessors
 * in sequence, as each would trigger a separate binder transaction.
 */
internal class DefaultDeviceMetricsReader(context: Context) : DeviceMetricsReader {
    // Store applicationContext to avoid leaking an Activity if a non-application context is passed.
    private val context = context.applicationContext

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override fun readDeviceMemoryInfo(): DeviceMemoryInfo {
        return try {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            DeviceMemoryInfo(
                totalBytes = info.totalMem,
                availableBytes = info.availMem,
                lowMemoryFlag = if (info.lowMemory) 1L else 0L,
            )
        } catch (_: Exception) {
            DeviceMemoryInfo(-1L, -1L, -1L)
        }
    }

    override fun readBatteryInfo(): BatteryInfo {
        return try {
            val intent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?: return BatteryInfo(-1.0, -1.0)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level < 0 || scale <= 0) -1.0 else (level.toDouble() / scale) * 100.0
            val tenthsDegrees = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempCelsius = if (tenthsDegrees < 0) -1.0 else tenthsDegrees / 10.0
            BatteryInfo(percent, tempCelsius)
        } catch (_: Exception) {
            BatteryInfo(-1.0, -1.0)
        }
    }

    override fun readDiskInfo(): DiskInfo {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            if (blockSize <= 0) return DiskInfo(-1L, -1L)
            DiskInfo(
                freeBytes = stat.availableBlocksLong * blockSize,
                totalBytes = stat.blockCountLong * blockSize,
            )
        } catch (_: Exception) {
            DiskInfo(-1L, -1L)
        }
    }
}
