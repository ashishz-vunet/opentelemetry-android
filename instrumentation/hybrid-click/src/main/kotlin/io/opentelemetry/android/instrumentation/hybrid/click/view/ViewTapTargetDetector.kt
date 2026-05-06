/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click.view

import android.view.View
import android.view.ViewGroup
import io.opentelemetry.android.instrumentation.hybrid.click.shared.LabelResolver
import io.opentelemetry.android.instrumentation.hybrid.click.shared.SOURCE_VIEW
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapTarget
import java.util.LinkedList

internal class ViewTapTargetDetector {
    private val viewCoordinates = IntArray(2)

    fun findTapTarget(
        decorView: View,
        x: Float,
        y: Float,
    ): TapTarget? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (isJetpackComposeView(view)) {
                return null
            }

            if (isValidClickTarget(view)) {
                target = view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue)
            }
        }

        val clickTarget = target ?: return null
        return TapTarget(
            source = SOURCE_VIEW,
            widgetId = clickTarget.id.toString(),
            widgetName = viewToName(clickTarget),
            label = viewToLabel(clickTarget),
            x = clickTarget.x.toLong(),
            y = clickTarget.y.toLong(),
        )
    }

    private fun isValidClickTarget(view: View): Boolean = view.isClickable && view.isVisible

    private fun handleViewGroup(
        view: ViewGroup,
        x: Float,
        y: Float,
        stack: LinkedList<View>,
    ) {
        if (!view.isVisible) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (hitTest(child, x, y) && !isJetpackComposeView(child)) {
                stack.add(child)
            }
        }
    }

    private fun hitTest(
        view: View,
        x: Float,
        y: Float,
    ): Boolean {
        view.getLocationInWindow(viewCoordinates)
        val vx = viewCoordinates[0]
        val vy = viewCoordinates[1]

        val w = view.width
        val h = view.height
        return !(x < vx || x > vx + w || y < vy || y > vy + h)
    }

    private fun viewToName(view: View): String =
        try {
            view.resources?.getResourceEntryName(view.id) ?: view.id.toString()
        } catch (_: Throwable) {
            view.id.toString()
        }

    private fun viewToLabel(view: View): String {
        val contentDescription = view.contentDescription?.toString()
        val text = (view as? android.widget.TextView)?.text?.toString()
        return LabelResolver.resolve(
            contentDescription = contentDescription,
            text = text,
            className = view.javaClass.simpleName,
            fallback = view.id.toString(),
        )
    }

    private fun isJetpackComposeView(view: View): Boolean =
        view::class.java.name.startsWith("androidx.compose.ui.platform.ComposeView")

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE
}
