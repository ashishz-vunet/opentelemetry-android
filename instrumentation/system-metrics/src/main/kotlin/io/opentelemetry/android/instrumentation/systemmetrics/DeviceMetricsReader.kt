/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

internal data class DeviceMemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemoryFlag: Long,
)

internal data class BatteryInfo(
    val percent: Double,
    val temperatureCelsius: Double,
)

internal data class DiskInfo(
    val freeBytes: Long,
    val totalBytes: Long,
)

/**
 * Reads device-level (system-wide) metrics.
 */
internal interface DeviceMetricsReader {
    /** Reads total RAM, available RAM, and low-memory flag in a single binder call. */
    fun readDeviceMemoryInfo(): DeviceMemoryInfo

    /** Reads battery percentage and temperature in a single sticky broadcast query. */
    fun readBatteryInfo(): BatteryInfo

    /** Reads free and total disk space on the internal data partition in a single [StatFs] call. */
    fun readDiskInfo(): DiskInfo
}

