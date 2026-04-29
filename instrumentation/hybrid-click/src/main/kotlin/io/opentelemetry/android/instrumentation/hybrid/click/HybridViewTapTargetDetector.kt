/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.view.View
import android.view.ViewGroup
import java.util.LinkedList

/**
 * Resolves tap targets from classic Android View hierarchies.
 */
internal class HybridViewTapTargetDetector {
    private val viewCoordinates = IntArray(2)

    /**
     * Performs a breadth-first traversal under [decorView] and returns the deepest clickable,
     * visible View that contains the tap coordinates.
     */
    fun findTapTarget(
        decorView: View,
        x: Float,
        y: Float,
    ): HybridTapTarget? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (isValidClickTarget(view)) {
                target = view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue)
            }
        }

        val clickTarget = target ?: return null
        return HybridTapTarget(
            source = "view",
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
            if (hitTest(child, x, y)) {
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

    /**
     * Returns a stable widget name when possible by using the resource entry name.
     */
    private fun viewToName(view: View): String =
        try {
            view.resources?.getResourceEntryName(view.id) ?: view.id.toString()
        } catch (_: Throwable) {
            view.id.toString()
        }

    /**
     * Resolves a human-readable label for a View target with fallback ordering.
     */
    private fun viewToLabel(view: View): String {
        val contentDescription = view.contentDescription?.toString()
        val text = (view as? android.widget.TextView)?.text?.toString()
        return HybridLabelResolver.resolve(
            contentDescription = contentDescription,
            text = text,
            className = view.javaClass.simpleName,
            fallback = view.id.toString(),
        )
    }

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE
}

