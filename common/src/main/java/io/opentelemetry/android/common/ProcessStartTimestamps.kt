/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common

/**
 * Holds wall-clock timestamps (epoch milliseconds) captured before the OTel SDK is initialized.
 *
 * Fields are written by ContentProviders that run early in the app lifecycle and read by
 * [io.opentelemetry.android.instrumentation.activity.startup.AppStartupTimer] when the SDK
 * starts up. A value of `0L` means the corresponding ContentProvider did not run (e.g. the
 * artifact is not on the classpath).
 */
object ProcessStartTimestamps {
    /**
     * Set by AppAnchorContentProvider (initOrder = Int.MAX_VALUE) immediately after
     * Application.attachBaseContext() completes and before any other ContentProvider runs.
     */
    @Volatile
    @JvmField
    var attachBaseContextEpochMs: Long = 0L

    /**
     * Set by EarlyStartupContentProvider (initOrder = Int.MIN_VALUE) after all ContentProviders
     * have finished initializing, just before Application.onCreate() is called.
     */
    @Volatile
    @JvmField
    var contentProviderEpochMs: Long = 0L
}
