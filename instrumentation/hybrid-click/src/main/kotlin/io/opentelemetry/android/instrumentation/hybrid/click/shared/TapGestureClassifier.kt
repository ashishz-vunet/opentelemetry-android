/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click.shared

import android.view.MotionEvent
import kotlin.math.pow

/**
 * Classifies pointer sequences as either tap-like or non-tap gestures.
 *
 * A click is emitted only when an active gesture reaches [MotionEvent.ACTION_UP] without moving
 * beyond [touchSlopPx] from the original [MotionEvent.ACTION_DOWN] position.
 */
internal class TapGestureClassifier {
    /**
     * Maximum movement allowed between down and up for a gesture to still count as a tap.
     */
    var touchSlopPx: Float = DEFAULT_TOUCH_SLOP_PX

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var hasActiveGesture: Boolean = false
    private var isTapCandidate: Boolean = false

    /**
     * Consumes a [MotionEvent] and returns `true` only when it represents a valid tap end.
     */
    fun shouldEmitClick(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }
        return shouldEmitClick(event.actionMasked, event.x, event.y)
    }

    /**
     * Test-friendly overload that accepts primitive event data instead of a [MotionEvent].
     */
    fun shouldEmitClick(
        actionMasked: Int,
        x: Float,
        y: Float,
    ): Boolean =
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                hasActiveGesture = true
                isTapCandidate = true
                false
            }

            MotionEvent.ACTION_MOVE -> {
                if (hasActiveGesture && isTapCandidate && isOutsideTapSlop(x, y)) {
                    isTapCandidate = false
                }
                false
            }

            MotionEvent.ACTION_CANCEL -> {
                reset()
                false
            }

            MotionEvent.ACTION_UP -> {
                val shouldEmit =
                    hasActiveGesture &&
                        isTapCandidate &&
                        !isOutsideTapSlop(x, y)
                reset()
                shouldEmit
            }

            else -> false
        }

    private fun isOutsideTapSlop(x: Float, y: Float): Boolean {
        val distanceSquared = (x - downX).pow(2) + (y - downY).pow(2)
        val slopSquared = touchSlopPx.pow(2)
        return distanceSquared > slopSquared
    }

    /**
     * Clears all in-progress gesture state, used on teardown and cancelled gestures.
     */
    fun reset() {
        hasActiveGesture = false
        isTapCandidate = false
    }

    private companion object {
        const val DEFAULT_TOUCH_SLOP_PX = 8f
    }
}
