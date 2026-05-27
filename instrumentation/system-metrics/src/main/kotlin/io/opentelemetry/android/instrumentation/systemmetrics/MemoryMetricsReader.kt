/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import android.os.Debug

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
