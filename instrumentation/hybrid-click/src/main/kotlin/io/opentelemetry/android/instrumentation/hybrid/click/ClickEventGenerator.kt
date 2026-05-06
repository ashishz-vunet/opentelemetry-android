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
import io.opentelemetry.android.instrumentation.hybrid.click.compose.ComposeTapTargetDetector
import io.opentelemetry.android.instrumentation.hybrid.click.shared.ATTR_VIEW_LABEL
import io.opentelemetry.android.instrumentation.hybrid.click.shared.ATTR_VIEW_SOURCE
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapGestureClassifier
import io.opentelemetry.android.instrumentation.hybrid.click.shared.UI_CLICK_SPAN_NAME
import io.opentelemetry.android.instrumentation.hybrid.click.view.ViewTapTargetDetector
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import java.lang.ref.WeakReference

/**
 * Generates `ui.click` spans for qualified tap gestures in hybrid View/Compose screens.
 */
internal class ClickEventGenerator(
    private val tracer: Tracer,
    private val viewTapTargetDetector: ViewTapTargetDetector = ViewTapTargetDetector(),
    private val activeContextWindowMillis: Long = DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS,
) {
    private var windowRef: WeakReference<Window>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tapGestureClassifier = TapGestureClassifier()

    private val composeTapTargetDetector: ComposeTapTargetDetector? by lazy {
        if (!isComposeAvailable()) {
            null
        } else {
            try {
                ComposeTapTargetDetector()
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        tapGestureClassifier.touchSlopPx = ViewConfiguration.get(window.decorView.context).scaledTouchSlop.toFloat()
        val currentCallback = window.callback
        if (currentCallback is WindowCallbackWrapper) {
            return
        }
        window.callback = WindowCallbackWrapper(currentCallback, this)
    }

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
            tracer.spanBuilder(UI_CLICK_SPAN_NAME)
                .setAttribute(AppIncubatingAttributes.APP_WIDGET_ID, target.widgetId)
                .setAttribute(AppIncubatingAttributes.APP_WIDGET_NAME, target.widgetName)
                .setAttribute(AppIncubatingAttributes.APP_SCREEN_COORDINATE_X, target.x)
                .setAttribute(AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y, target.y)
                .setAttribute(ATTR_VIEW_LABEL, target.label)
                .setAttribute(ATTR_VIEW_SOURCE, target.source)
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

    fun stopTracking() {
        windowRef?.get()?.run {
            if (callback is WindowCallbackWrapper) {
                callback = (callback as WindowCallbackWrapper).unwrap()
            }
        }
        tapGestureClassifier.reset()
        windowRef = null
    }

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
