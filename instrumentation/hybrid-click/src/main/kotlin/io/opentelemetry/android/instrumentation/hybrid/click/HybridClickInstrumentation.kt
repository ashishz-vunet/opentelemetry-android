/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.app.Application
import com.google.auto.service.AutoService
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.ConfigurableHybridClickInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext

@AutoService(AndroidInstrumentation::class)
/**
 * Android instrumentation entry point that installs hybrid click tracking.
 *
 * This module captures click/tap interactions from both traditional View hierarchies and
 * Jetpack Compose by registering activity lifecycle callbacks.
 */
class HybridClickInstrumentation : AndroidInstrumentation, ConfigurableHybridClickInstrumentation {
    override val name: String = "hybrid.click"
    private var activeContextWindowMillis: Long = DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS
    private var activityLifecycleCallback: Application.ActivityLifecycleCallbacks? = null

    /**
     * Creates the tracer used for hybrid click spans and registers lifecycle callbacks that
     * attach/detach per-window touch interception as activities resume/pause.
     */
    override fun install(ctx: InstallationContext) {
        if (activityLifecycleCallback != null) {
            return
        }

        val tracer =
            ctx.openTelemetry
                .tracerProvider
                .tracerBuilder("io.opentelemetry.android.instrumentation.hybrid.click")
                .build()

        ctx.application?.let { application ->
            val callback =
                HybridClickActivityCallback(
                    HybridClickEventGenerator(
                        tracer = tracer,
                        activeContextWindowMillis = activeContextWindowMillis,
                    ),
                )
            activityLifecycleCallback = callback
            application.registerActivityLifecycleCallbacks(callback)
        }
    }

    override fun uninstall(ctx: InstallationContext) {
        activityLifecycleCallback?.let { callback ->
            ctx.application?.unregisterActivityLifecycleCallbacks(callback)
        }
        activityLifecycleCallback = null
    }

    override fun setActiveContextWindowMillis(value: Long) {
        activeContextWindowMillis = value
    }

    private companion object {
        const val DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS = 500L
    }
}

