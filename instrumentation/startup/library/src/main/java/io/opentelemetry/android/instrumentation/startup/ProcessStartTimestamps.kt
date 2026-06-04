/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.startup

/**
 * Volatile timestamps captured before the OTel SDK initializes.
 *
 * Written from early startup hooks (ContentProviders, ByteBuddy advice on
 * [android.app.Application.attachBaseContext]) and read when [io.opentelemetry.android.instrumentation.activity.startup.AppStartupTimer]
 * builds the cold-start trace.
 */
internal object ProcessStartTimestamps {
    @Volatile
    @JvmField
    var attachBaseContextStartElapsedRealtime: Long = 0L

    @Volatile
    @JvmField
    var attachBaseContextEndElapsedRealtime: Long = 0L

    @Volatile
    @JvmField
    var contentProvidersPhaseStartEpochMs: Long = 0L

    @Volatile
    @JvmField
    var contentProviderEpochMs: Long = 0L
}
