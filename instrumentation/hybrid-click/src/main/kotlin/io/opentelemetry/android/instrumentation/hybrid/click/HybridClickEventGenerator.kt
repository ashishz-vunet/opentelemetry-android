/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import java.lang.ref.WeakReference

/**
 * Generates `ui.click` spans for qualified tap gestures in hybrid View/Compose screens.
 */
internal class HybridClickEventGenerator(
    private val tracer: Tracer,
    private val viewTapTargetDetector: HybridViewTapTargetDetector = HybridViewTapTargetDetector(),
    private val activeContextWindowMillis: Long = DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS,
) {
    private var windowRef: WeakReference<Window>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tapGestureClassifier = HybridTapGestureClassifier()

    private val composeTapTargetDetector: HybridComposeTapTargetDetector? by lazy {
        if (!isComposeAvailable()) {
            null
        } else {
            try {
                HybridComposeTapTargetDetector()
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Starts tracking touch events for the given [window] by wrapping its [Window.Callback].
     *
     * If a wrapper is already installed, this method is a no-op.
     */
    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        tapGestureClassifier.touchSlopPx = ViewConfiguration.get(window.decorView.context).scaledTouchSlop.toFloat()
        val currentCallback = window.callback
        if (currentCallback is WindowCallbackWrapper) {
            return
        }
        window.callback = WindowCallbackWrapper(currentCallback, this)
    }

    /**
     * Emits a `ui.click` span for tap-up events (`ACTION_UP`) when a target is detected.
     *
     * Target resolution prefers Compose views when available and falls back to View-based
     * detection. The span includes semantic app widget/location attributes and custom
     * attributes for `view.label` and `view.source`.
     */
    fun generateClick(motionEvent: MotionEvent?) {
        val window = windowRef?.get() ?: return
        val event = motionEvent ?: return
        if (!tapGestureClassifier.shouldEmitClick(event)) {
            return
        }

        val target =
            composeTapTargetDetector?.findTapTarget(window.decorView, event.x, event.y)
                ?: viewTapTargetDetector.findTapTarget(window.decorView, event.x, event.y)
                ?: return

        val span =
            tracer.spanBuilder("ui.click")
                .setAttribute(APP_WIDGET_ID, target.widgetId)
                .setAttribute(APP_WIDGET_NAME, target.widgetName)
                .setAttribute(APP_SCREEN_COORDINATE_X, target.x)
                .setAttribute(APP_SCREEN_COORDINATE_Y, target.y)
                .setAttribute("view.label", target.label)
                .setAttribute("view.source", target.source)
                .startSpan()

        val scope = span.makeCurrent()
        mainHandler.postDelayed(
            {
                scope.close()
                span.end()
            },
            activeContextWindowMillis,
        )
    }

    /**
     * Stops tracking by restoring the original [Window.Callback] and clearing the window reference.
     */
    fun stopTracking() {
        windowRef?.get()?.run {
            if (callback is WindowCallbackWrapper) {
                callback = (callback as WindowCallbackWrapper).unwrap()
            }
        }
        tapGestureClassifier.reset()
        windowRef = null
    }

    /**
     * Performs a safe runtime check so this module can run in apps without Compose on classpath.
     */
    private fun isComposeAvailable(): Boolean =
        try {
            Class.forName(COMPOSE_VIEW_CLASS_NAME)
            true
        } catch (_: Throwable) {
            false
        }

    private companion object {
        const val DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS = 500L
        const val COMPOSE_VIEW_CLASS_NAME = "androidx.compose.ui.platform.ComposeView"
    }
}

