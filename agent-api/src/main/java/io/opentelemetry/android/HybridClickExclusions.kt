/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import android.view.View
import java.util.Collections
import java.util.WeakHashMap

/**
 * Views whose interactions are recorded by another telemetry layer — for example a WebView running
 * a web RUM SDK, which sees the element actually tapped. Hybrid click instrumentation emits no
 * `ui.interaction` for a tap inside an excluded view, so one tap is not reported twice.
 *
 * Views are held weakly: a destroyed view drops out on its own.
 */
object HybridClickExclusions {
    private val views: MutableSet<View> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** Stops hybrid click instrumentation from recording taps inside [view]. */
    @JvmStatic
    fun exclude(view: View) {
        views.add(view)
    }

    /** Records taps inside [view] again. */
    @JvmStatic
    fun include(view: View) {
        views.remove(view)
    }

    /** True when taps inside [view] are currently excluded. */
    @JvmStatic
    fun isExcluded(view: View): Boolean = views.contains(view)

    /**
     * True when ([x], [y]), in the coordinates of the window whose root is [root], falls inside a
     * visible excluded view of that same window.
     */
    @JvmStatic
    fun contains(
        root: View,
        x: Float,
        y: Float,
    ): Boolean {
        if (views.isEmpty()) return false
        val loc = IntArray(2)
        synchronized(views) {
            for (v in views) {
                if (v.rootView !== root || v.visibility != View.VISIBLE || v.width == 0 || v.height == 0) continue
                v.getLocationInWindow(loc)
                if (x >= loc[0] && x < loc[0] + v.width && y >= loc[1] && y < loc[1] + v.height) return true
            }
        }
        return false
    }
}
