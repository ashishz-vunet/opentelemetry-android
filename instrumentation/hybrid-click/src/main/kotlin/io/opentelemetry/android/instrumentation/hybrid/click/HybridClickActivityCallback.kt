/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.app.Activity
import io.opentelemetry.android.internal.services.visiblescreen.activities.DefaultingActivityLifecycleCallbacks

/**
 * Activity lifecycle bridge that enables/disables window touch tracking for hybrid click capture.
 */
internal class HybridClickActivityCallback(
    private val hybridClickEventGenerator: HybridClickEventGenerator,
) : DefaultingActivityLifecycleCallbacks {
    /**
     * Begins tracking when the activity enters foreground and has an active window.
     */
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        hybridClickEventGenerator.startTracking(activity.window)
    }

    /**
     * Stops tracking when the activity leaves foreground.
     */
    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        hybridClickEventGenerator.stopTracking()
    }
}

