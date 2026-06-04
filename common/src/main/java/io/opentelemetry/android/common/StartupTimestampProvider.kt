/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common

/**
 * Provides timestamps captured before the OTel SDK initializes.
 *
 * Implementations are discovered via [java.util.ServiceLoader] when the startup instrumentation
 * artifact is on the classpath. A value of `0L` for any property means the corresponding
 * milestone was not captured (e.g. the startup artifact is not on the classpath, or the
 * startup-agent ByteBuddy weave was not applied).
 */
interface StartupTimestampProvider {
    /**
     * [android.os.SystemClock.elapsedRealtime] at entry to the first
     * [android.app.Application.attachBaseContext] invocation. Set by startup-agent weave.
     */
    val attachBaseContextStartElapsedRealtime: Long

    /**
     * [android.os.SystemClock.elapsedRealtime] at exit from the first
     * [android.app.Application.attachBaseContext] invocation. Set by startup-agent weave.
     */
    val attachBaseContextEndElapsedRealtime: Long

    /**
     * Wall-clock epoch ms at the start of the ContentProvider initialization phase.
     * Set by [io.opentelemetry.android.instrumentation.startup.AppAnchorContentProvider].
     */
    val contentProvidersPhaseStartEpochMs: Long

    /**
     * Wall-clock epoch ms captured after all ContentProviders have finished initializing,
     * just before [android.app.Application.onCreate] is called.
     * Set by [io.opentelemetry.android.instrumentation.startup.EarlyStartupContentProvider].
     */
    val contentProviderEpochMs: Long
}
