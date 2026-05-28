/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity.startup

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import io.opentelemetry.android.common.StartupTimestampProvider
import java.util.ServiceLoader
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.internal.services.visiblescreen.activities.DefaultingActivityLifecycleCallbacks
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit

internal class AppStartupTimer(
    timestampProvider: StartupTimestampProvider? = null,
) {
    private lateinit var startupClock: AnchoredClock
    private var spanStartNanos: Long = 0

    private val startupTimestamps: StartupTimestampProvider? by lazy {
        timestampProvider ?: ServiceLoader.load(
            StartupTimestampProvider::class.java,
            StartupTimestampProvider::class.java.classLoader,
        ).firstOrNull()
    }

    @Volatile
    var startupSpan: Span? = null
        private set

    // whether activity has been created
    // accessed only from UI thread
    private var uiInitStarted = false

    // whether MAX_TIME_TO_UI_INIT has been exceeded
    // accessed only from UI thread
    private var uiInitTooLate = false

    fun start(
        tracer: Tracer,
        clock: Clock,
    ): Span {
        // guard against a double-start and just return what's already in flight.
        startupSpan?.let {
            return it
        }
        startupClock = AnchoredClock(clock)
        val sdkInitNanos = startupClock.now()

        // On API 24+, compute the process fork time once and reuse it for both the span
        // start timestamp and the app.process.creation event, so they carry the same value.
        // Anchored to the injected clock so start and end timestamps share the same time domain.
        // On API 23, fall back to SDK init time.
        val processStartNanos =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) processStartNanos(sdkInitNanos) else -1L
        spanStartNanos =
            if (processStartNanos > 0L) processStartNanos else sdkInitNanos

        val appStart =
            tracer
                .spanBuilder(RumConstants.APP_START_SPAN_NAME)
                .setStartTimestamp(spanStartNanos, TimeUnit.NANOSECONDS)
                .setAttribute(RumConstants.START_TYPE_KEY, "cold")
                .startSpan()
        this.startupSpan = appStart

        if (processStartNanos > 0L) {
            appStart.addEvent(
                EVENT_PROCESS_CREATION,
                Attributes.empty(),
                processStartNanos,
                TimeUnit.NANOSECONDS,
            )
        }

        // app.base_context — captured by AppAnchorContentProvider in the startup artifact.
        // Value is 0 when the startup artifact is not on the classpath.
        val baseContextMs = startupTimestamps?.attachBaseContextEpochMs ?: 0L
        if (baseContextMs > 0L) {
            appStart.addEvent(
                EVENT_BASE_CONTEXT,
                Attributes.empty(),
                baseContextMs,
                TimeUnit.MILLISECONDS,
            )
        }

        // app.init.contentprovider — captured by EarlyStartupContentProvider in the startup artifact.
        // Marks the end of ContentProvider init, just before Application.onCreate().
        // Value is 0 when the startup artifact is not on the classpath.
        val contentProviderMs = startupTimestamps?.contentProviderEpochMs ?: 0L
        if (contentProviderMs > 0L) {
            appStart.addEvent(
                EVENT_CONTENT_PROVIDER_INIT,
                Attributes.empty(),
                contentProviderMs,
                TimeUnit.MILLISECONDS,
            )
        }

        return appStart
    }

    /**
     * Computes the process fork timestamp in the injected clock's time domain by subtracting
     * the elapsed realtime delta from [clockNowNanos]:
     *   processStartNanos = clockNowNanos − (elapsedRealtime − processStartElapsedRealtime) × 1_000_000
     *
     * Using [clockNowNanos] (from the injected [Clock]) as the reference — rather than
     * [System.currentTimeMillis] — keeps the span start and span end in the same time domain,
     * which is required for correct duration calculation with custom clocks.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun processStartNanos(clockNowNanos: Long): Long {
        val elapsedSinceStartNanos =
            (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) * 1_000_000L
        return clockNowNanos - elapsedSinceStartNanos
    }

    /**
     * Creates a lifecycle listener that starts the UI init when an activity is created.
     *
     * @return a new Application.ActivityLifecycleCallbacks instance
     */
    fun createLifecycleCallback(): ActivityLifecycleCallbacks =
        object : DefaultingActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) {
                startUiInit()
            }
        }

    /** Called when Activity is created.  */
    private fun startUiInit() {
        if (uiInitStarted) {
            return
        }
        uiInitStarted = true
        if (spanStartNanos + MAX_TIME_TO_UI_INIT < startupClock.now()) {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "Max time to UI init exceeded")
            uiInitTooLate = true
            clear()
        }
    }

    fun end() {
        val overallAppStartSpan = this.startupSpan
        if (overallAppStartSpan != null && !uiInitTooLate) {
            overallAppStartSpan.end(startupClock.now(), TimeUnit.NANOSECONDS)
        }
        clear()
    }

    private fun clear() {
        this.startupSpan = null
    }

    companion object {
        // Maximum time from app start to creation of the UI. If this time is exceeded we will not
        // create the app start span. Long app startup could indicate that the app was really started in
        // background, in which case the measured startup time is misleading.
        private val MAX_TIME_TO_UI_INIT = TimeUnit.MINUTES.toNanos(1)

        /** Milestone: Linux process was forked. Backdated via [Process.getStartElapsedRealtime]. */
        internal const val EVENT_PROCESS_CREATION = "app.process.creation"

        /**
         * Milestone: [android.app.Application.attachBaseContext] completed; captured by
         * [io.opentelemetry.android.instrumentation.startup.AppAnchorContentProvider].
         * Present only when the startup artifact is on the classpath.
         */
        internal const val EVENT_BASE_CONTEXT = "app.base_context"

        /**
         * Milestone: all ContentProviders finished initializing; captured by
         * [io.opentelemetry.android.instrumentation.startup.EarlyStartupContentProvider].
         * Present only when the startup artifact is on the classpath.
         */
        internal const val EVENT_CONTENT_PROVIDER_INIT = "app.init.contentprovider"
    }
}
