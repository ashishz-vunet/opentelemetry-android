/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.startup

import com.google.auto.service.AutoService
import io.opentelemetry.android.common.StartupTimestampProvider

@AutoService(StartupTimestampProvider::class)
internal class StartupTimestampProviderImpl : StartupTimestampProvider {
    override val attachBaseContextStartElapsedRealtime: Long
        get() = ProcessStartTimestamps.attachBaseContextStartElapsedRealtime

    override val attachBaseContextEndElapsedRealtime: Long
        get() = ProcessStartTimestamps.attachBaseContextEndElapsedRealtime

    override val contentProvidersPhaseStartEpochMs: Long
        get() = ProcessStartTimestamps.contentProvidersPhaseStartEpochMs

    override val contentProviderEpochMs: Long
        get() = ProcessStartTimestamps.contentProviderEpochMs
}
