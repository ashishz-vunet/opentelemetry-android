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
    private var firstPossibleTimestamp: Long = 0

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
        firstPossibleTimestamp = startupClock.now()

        // On API 24+, compute the process fork time once and reuse it for both the span
        // start timestamp and the app.process.creation event, so they carry the same value.
        // On API 23, fall back to firstPossibleTimestamp (SDK init time).
        val processStartMs =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) processStartEpochMs() else -1L
        val spanStartNanos =
            if (processStartMs > 0L) processStartMs * 1_000_000L else firstPossibleTimestamp

        val appStart =
            tracer
                .spanBuilder(RumConstants.APP_START_SPAN_NAME)
                .setStartTimestamp(spanStartNanos, TimeUnit.NANOSECONDS)
                .setAttribute(RumConstants.START_TYPE_KEY, "cold")
                .startSpan()
        this.startupSpan = appStart

        if (processStartMs > 0L) {
            appStart.addEvent(
                EVENT_PROCESS_CREATION,
                Attributes.empty(),
                processStartMs,
                TimeUnit.MILLISECONDS,
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
     * Converts [Process.getStartElapsedRealtime] to a wall-clock epoch value:
     *   processStartEpochMs = nowMs − (elapsedRealtime − processStartElapsedRealtime)
     *
     * Assumes the wall clock was not adjusted between process fork and now, which is safe
     * for the typical sub-second cold-start window.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun processStartEpochMs(): Long {
        val nowMs = System.currentTimeMillis()
        return nowMs - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
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
        if (firstPossibleTimestamp + MAX_TIME_TO_UI_INIT < startupClock.now()) {
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
