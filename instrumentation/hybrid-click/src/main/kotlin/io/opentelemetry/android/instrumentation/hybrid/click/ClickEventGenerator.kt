/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.Window
import io.opentelemetry.android.instrumentation.hybrid.click.shared.ATTR_WIDGET_SOURCE
import io.opentelemetry.android.instrumentation.hybrid.click.shared.SOURCE_COMPOSE
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapGestureClassifier
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapTarget
import io.opentelemetry.android.instrumentation.hybrid.click.shared.UI_CLICK_SPAN_NAME
import io.opentelemetry.android.instrumentation.hybrid.click.view.ViewTapTargetDetector
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import java.lang.reflect.Method
import java.lang.ref.WeakReference

/**
 * Generates `ui.click` spans for qualified tap gestures in hybrid View/Compose screens.
 *
 * ## Why this implementation looks unusual
 * Hybrid click needs Compose node metadata, but direct typed wiring to Compose internals in this
 * module previously produced bytecode that AnimalSniffer flagged (`error.NonExistentClass`).
 *
 * To keep hybrid-click publishable while still resolving Compose targets at runtime:
 * 1) Compose detector returns Compose node info behind a reflection boundary.
 * 2) This class maps that reflected node into the stable hybrid [TapTarget] model.
 * 3) View fallback remains intact when Compose is unavailable or detector resolution fails.
 */
internal class ClickEventGenerator(
    private val tracer: Tracer,
    private val viewTapTargetDetector: ViewTapTargetDetector = ViewTapTargetDetector(),
    private val activeContextWindowMillis: Long = DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS,
) {
    private var windowRef: WeakReference<Window>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tapGestureClassifier = TapGestureClassifier()

    private val composeTapTargetDetector: ComposeDetectorBridge? by lazy {
        loadComposeDetector()
    }

    /**
     * Lazily loads Compose detector wiring via reflection.
     *
     * Hybrid-click keeps Compose internals behind reflection so this module can stay resilient
     * when Compose internals/signatures vary across app/toolchain combinations.
     *
     * Historical context:
     * - Initial direct reflection by exact method names failed at runtime with:
     *   `NoSuchMethodException: ... nodeToName(LayoutNode)`.
     * - Cause: Kotlin `internal` methods may be name-mangled in bytecode.
     * - Resolution: method lookup now supports base-name + mangled-name matching.
     */
    private fun loadComposeDetector(): ComposeDetectorBridge? {
        return try {
            val detectorClass =
                Class.forName(
                    "io.opentelemetry.android.instrumentation.hybrid.click.compose.ComposeTapTargetDetector",
                )
            val detector = detectorClass.getDeclaredConstructor().newInstance()
            val layoutNodeClass = Class.forName("androidx.compose.ui.node.LayoutNode")
            val findTapTargetMethod =
                detectorClass.getMethod(
                    "findTapTarget",
                    View::class.java,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                )
            val nodeToNameMethod =
                findMangledMethod(
                    detectorClass = detectorClass,
                    methodBaseName = "nodeToName",
                    parameterType = layoutNodeClass,
                )
            val nodeToLabelMethod = detectorClass.getDeclaredMethod("nodeToLabel", layoutNodeClass).apply { isAccessible = true }
            val nodeToPositionMethod =
                findMangledMethod(
                    detectorClass = detectorClass,
                    methodBaseName = "nodeToPosition",
                    parameterType = layoutNodeClass,
                )
            ReflectiveComposeDetectorBridge(
                detector = detector,
                findTapTargetMethod = findTapTargetMethod,
                nodeToNameMethod = nodeToNameMethod,
                nodeToLabelMethod = nodeToLabelMethod,
                nodeToPositionMethod = nodeToPositionMethod,
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolves Kotlin-internal method names that may be mangled in bytecode (e.g. `foo$module`).
     *
     * We match by base name + parameter type to avoid hardcoding mangled suffixes.
     *
     * This is required because exact `getDeclaredMethod("nodeToName", ...)` can fail depending on
     * how Kotlin emits internal method names for the consuming toolchain.
     */
    private fun findMangledMethod(
        detectorClass: Class<*>,
        methodBaseName: String,
        parameterType: Class<*>,
    ): Method =
        detectorClass.declaredMethods.firstOrNull {
            (it.name == methodBaseName || it.name.startsWith("$methodBaseName$")) &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == parameterType
        }?.apply {
            isAccessible = true
        } ?: throw NoSuchMethodException("$methodBaseName([${parameterType.name}])")

    /**
     * Installs the wrapped window callback and initializes gesture thresholds.
     */
    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        tapGestureClassifier.touchSlopPx =
            ViewConfiguration.get(window.decorView.context).scaledTouchSlop.toFloat()
        val currentCallback = window.callback
        if (currentCallback is WindowCallbackWrapper) {
            return
        }
        window.callback = WindowCallbackWrapper(currentCallback, this)
    }

    /**
     * Consumes motion events, qualifies tap gestures, resolves a tap target, and emits `ui.click`.
     */
    fun generateClick(motionEvent: MotionEvent?) {
        val window = windowRef?.get() ?: return
        val event = motionEvent ?: return
        if (!tapGestureClassifier.shouldEmitClick(event)) {
            return
        }

        val target =
            findComposeTarget(window.decorView, event.x, event.y)
                ?: viewTapTargetDetector.findTapTarget(window.decorView, event.x, event.y)
                ?: return

        val span =
            tracer.spanBuilder(UI_CLICK_SPAN_NAME)
                .setAttribute(AppIncubatingAttributes.APP_WIDGET_ID, target.widgetId)
                .setAttribute(AppIncubatingAttributes.APP_WIDGET_NAME, target.label)
                .setAttribute(AppIncubatingAttributes.APP_SCREEN_COORDINATE_X, target.x)
                .setAttribute(AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y, target.y)
                .setAttribute(ATTR_WIDGET_SOURCE, target.source)
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
     * Attempts Compose target resolution first; caller can fallback to View resolution.
     */
    private fun findComposeTarget(
        rootView: View,
        x: Float,
        y: Float,
    ): TapTarget? = composeTapTargetDetector?.findTapTarget(rootView, x, y)

    /**
     * Restores original window callback and clears tracking state.
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

    private companion object {
        const val DEFAULT_ACTIVE_CONTEXT_WINDOW_MILLIS = 500L
    }

}

/**
 * Small typed boundary used by [ClickEventGenerator] to query Compose tap targets.
 *
 * This keeps reflection details out of core click-generation flow and avoids exposing `Any`
 * through the main logic.
 *
 * It also documents the intentional separation: Compose detector concerns stay encapsulated while
 * click-span orchestration remains strongly typed.
 */
private interface ComposeDetectorBridge {
    /**
     * Returns a Compose-derived [TapTarget], or `null` when no Compose target is available.
     */
    fun findTapTarget(
        rootView: View,
        x: Float,
        y: Float,
    ): TapTarget?
}

/**
 * Reflection-backed adapter for Compose detector internals.
 *
 * Compose detector methods may be internal/mangled; methods are pre-resolved once in
 * [ClickEventGenerator.loadComposeDetector] and invoked here for runtime target conversion.
 *
 * This is the final form after debugging real app failures where reflection by exact method names
 * caused bridge-load failure and disabled Compose click detection entirely.
 */
private class ReflectiveComposeDetectorBridge(
    private val detector: Any,
    private val findTapTargetMethod: Method,
    private val nodeToNameMethod: Method,
    private val nodeToLabelMethod: Method,
    private val nodeToPositionMethod: Method,
) : ComposeDetectorBridge {
    /**
     * Invokes reflected detector methods and maps the Compose node to hybrid [TapTarget].
     */
    override fun findTapTarget(
        rootView: View,
        x: Float,
        y: Float,
    ): TapTarget? =
        try {
            val node = findTapTargetMethod.invoke(detector, rootView, x, y) ?: return null
            val widgetName = nodeToNameMethod.invoke(detector, node) as? String ?: node.hashCode().toString()
            val label = nodeToLabelMethod.invoke(detector, node) as? String ?: widgetName
            val position = nodeToPositionMethod.invoke(detector, node) as? Pair<*, *>
            val nodeX = (position?.first as? Long) ?: 0L
            val nodeY = (position?.second as? Long) ?: 0L
            TapTarget(
                source = SOURCE_COMPOSE,
                widgetId = node.hashCode().toString(),
                widgetName = widgetName,
                label = label,
                x = nodeX,
                y = nodeY,
            )
        } catch (_: Throwable) {
            null
        }
}
