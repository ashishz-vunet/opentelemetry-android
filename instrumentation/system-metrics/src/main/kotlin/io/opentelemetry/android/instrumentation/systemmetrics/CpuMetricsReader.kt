/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import android.os.Process
import android.os.SystemClock

/**
 * Reads CPU usage for the current process using Android OS APIs.
 *
 * Uses [Process.getElapsedCpuTime] which is thread-safe and doesn't require /proc filesystem access
 * (which is restricted on modern Android). Calculates CPU % as the delta since last call,
 * normalized by [availableCores] so the result represents the fraction of total device CPU
 * capacity consumed by this process (0–100), matching the intent of `process.cpu.usage`.
 *
 * **Thread safety:** this class is not thread-safe by design. It is called exclusively from
 * the single-threaded `otel-system-metrics` [java.util.concurrent.ScheduledExecutorService],
 * so no synchronization overhead is needed.
 */
internal class CpuMetricsReader {
    // Cached once at construction — availableProcessors() is a JNI call on every invocation.
    private val availableCores = maxOf(1, Runtime.getRuntime().availableProcessors())

    // Baseline seeded at construction so the first readCpuUsagePercent() call is meaningful.
    private var lastCpuTimeMs = Process.getElapsedCpuTime()
    private var lastWallTimeMs = SystemClock.elapsedRealtime()

    /**
     * Returns CPU usage percentage (0–100) since the last call, normalized across all cores.
     *
     * Formula: `(deltaCpu / deltaWall / availableCores) × 100`
     *
     * A value of 100 means this process consumed every available CPU cycle on the device.
     */
    fun readCpuUsagePercent(): Double {
        val nowCpuMs = Process.getElapsedCpuTime()
        val nowWallMs = SystemClock.elapsedRealtime()

        val deltaCpuMs = nowCpuMs - lastCpuTimeMs
        val deltaWallMs = nowWallMs - lastWallTimeMs

        lastCpuTimeMs = nowCpuMs
        lastWallTimeMs = nowWallMs

        // Guard against a zero wall-clock window or a rare backward CPU-time delta.
        if (deltaWallMs <= 0 || deltaCpuMs < 0) return 0.0

        // Normalize by core count: raw ratio can exceed 100 on multi-core devices because
        // getElapsedCpuTime() sums time across all cores.
        return minOf((deltaCpuMs.toDouble() / deltaWallMs / availableCores) * 100.0, 100.0)
    }
}
