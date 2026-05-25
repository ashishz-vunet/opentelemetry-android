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
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock

/**
 * Reads memory-related metrics for the current process.
 *
 * - Heap used: Java heap bytes currently in use.
 * - Native heap: Native allocations tracked by [Debug].
 * - PSS: Proportional Set Size read from [Debug.MemoryInfo] (shared memory counted proportionally).
 */
internal class MemoryMetricsReader {
    /** Java heap bytes currently used (total - free). */
    fun readHeapUsedBytes(): Long {
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    /** Java heap bytes committed (allocated) from the OS — includes used + free portions. */
    fun readHeapAllocatedBytes(): Long = Runtime.getRuntime().totalMemory()

    /** Java heap bytes currently free (committed but not in use). */
    fun readHeapFreeBytes(): Long = Runtime.getRuntime().freeMemory()

    /** Native heap bytes allocated via malloc/JNI. */
    fun readNativeHeapUsedBytes(): Long = Debug.getNativeHeapAllocatedSize()

    /**
     * Proportional Set Size in kB.
     * [Debug.getMemoryInfo] is a blocking binder call — only run this at a longer
     * interval (≥ 30 s) to avoid overhead.
     */
    fun readPssKb(): Long {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        return mi.totalPss.toLong()
    }
}

/**
 * Reads CPU usage for the current process using Android OS APIs (works on Android 10+).
 *
 * Uses [Process.getElapsedCpuTime] which is thread-safe and doesn't require /proc filesystem access
 * (which is restricted on modern Android). Calculates CPU % as the delta since last call.
 */
internal class CpuMetricsReader {
    private var lastCpuTimeMs = 0L
    private var lastWallTimeMs = 0L

    /**
     * Returns CPU usage percentage (0–100) since the last call.
     * Thread-safe and works on all Android versions including API 29+.
     *
     * Formula:
     *   CPU % = (CPU time delta / Wall time delta) × 100
     */
    fun readCpuUsagePercent(): Double {
        val nowCpuMs = Process.getElapsedCpuTime()
        val nowWallMs = SystemClock.elapsedRealtime()

        val deltaCpuMs = nowCpuMs - lastCpuTimeMs
        val deltaWallMs = nowWallMs - lastWallTimeMs

        lastCpuTimeMs = nowCpuMs
        lastWallTimeMs = nowWallMs

        if (deltaWallMs <= 0) return 0.0

        return (deltaCpuMs.toDouble() / deltaWallMs) * 100.0
    }
}

/**
 * Reads device-level (system-wide) metrics.
 *
 * These reflect the state of the whole device, not just this app's process.
 */
internal open class DeviceMetricsReader(private val context: Context) {
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /** Total physical RAM on the device in bytes. */
    open fun readTotalRamBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.totalMem
    }

    /** Available (free) RAM on the device in bytes. */
    open fun readAvailableRamBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem
    }

    /** 1 if the device is in a low-memory state, 0 otherwise. */
    open fun readLowMemoryFlag(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return if (info.lowMemory) 1L else 0L
    }

    /** Battery level as a percentage (0–100). Returns -1 if unavailable. */
    open fun readBatteryPercent(): Double {
        val intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return -1.0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1.0
        return (level.toDouble() / scale) * 100.0
    }

    /** Battery temperature in degrees Celsius. Returns -1 if unavailable. */
    open fun readBatteryTemperatureCelsius(): Double {
        val intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return -1.0
        val tenthsDegrees = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tenthsDegrees < 0) return -1.0
        return tenthsDegrees / 10.0
    }

    /**
     * Free disk space on the internal data partition in bytes.
     */
    open fun readDiskFreeBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            if (blockSize <= 0) return -1L
            stat.availableBlocksLong * blockSize
        } catch (_: Exception) {
            -1L
        }
    }

    /** Total disk space on the internal data partition in bytes. */
    open fun readDiskTotalBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            if (blockSize <= 0) return -1L
            stat.blockCountLong * blockSize
        } catch (_: Exception) {
            -1L
        }
    }
}

/**
 * Reads thread-related metrics for the current process.
 *
 * Uses lightweight APIs:
 * - [Thread.activeCount] for regular thread count (fast)
 * - Reserves [Thread.getAllStackTraces] only for error diagnosis (expensive)
 */
internal class ThreadMetricsReader {
    /**
     * Total number of live threads in this JVM process.
     *
     * Uses [ThreadGroup.activeCount] which is thread-safe and lightweight.
     * Much faster than [Thread.getAllStackTraces] which triggers a full thread dump.
     */
    fun readThreadCount(): Long {
        return try {
            Thread.currentThread().threadGroup?.activeCount()?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Returns a map of [Thread.State] name → count for all currently live threads.
     *
     * **WARNING:** This method triggers a full JVM thread dump, pausing all threads.
     * Only call this as a fallback for error diagnosis, not in normal metric collection.
     *
     * Use case: When an ANR is detected or thread count exceeds critical threshold (>80 threads).
     */
    fun readThreadCountByState(): Map<String, Long> =
        Thread
            .getAllStackTraces()
            .keys
            .groupingBy { it.state.name }
            .eachCount()
            .mapValues { it.value.toLong() }
}
