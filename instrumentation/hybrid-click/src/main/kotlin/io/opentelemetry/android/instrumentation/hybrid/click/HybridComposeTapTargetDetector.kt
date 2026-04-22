/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.view.View
import android.view.ViewGroup
import java.util.LinkedList

/**
 * Resolves tap targets from Compose-backed surfaces represented by `ComposeView`.
 */
internal class HybridComposeTapTargetDetector {
    private val viewCoordinates = IntArray(2)

    /**
     * Finds the tapped Compose host view and maps it to a [HybridTapTarget].
     */
    fun findTapTarget(
        decorView: View,
        x: Float,
        y: Float,
    ): HybridTapTarget? {
        val composeView = findComposeViewTapTarget(decorView, x, y) ?: return null
        val className = composeView.javaClass.simpleName
        val fallbackId = composeView.id.toString()
        return HybridTapTarget(
            source = "compose",
            widgetId = fallbackId,
            widgetName = composeViewToName(composeView),
            label = composeViewToLabel(composeView),
            x = composeView.x.toLong(),
            y = composeView.y.toLong(),
        )
    }

    /**
     * Traverses the view tree and returns the last ComposeView that contains the tap.
     */
    private fun findComposeViewTapTarget(
        decorView: View,
        x: Float,
        y: Float,
    ): View? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)

        var target: View? = null
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (isComposeView(view) && hitTest(view, x, y)) {
                target = view
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.add(view.getChildAt(index))
                }
            }
        }

        return target
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
    private fun composeViewToName(view: View): String =
        try {
            view.resources?.getResourceEntryName(view.id) ?: view.id.toString()
        } catch (_: Throwable) {
            view.id.toString()
        }

    /**
     * Resolves a human-readable label for a Compose host view with fallback ordering.
     */
    private fun composeViewToLabel(view: View): String {
        val contentDescription = view.contentDescription?.toString()
        val text = (view as? android.widget.TextView)?.text?.toString()
        return HybridLabelResolver.resolve(
            contentDescription = contentDescription,
            text = text,
            className = view.javaClass.simpleName,
            fallback = view.id.toString(),
        )
    }

    private fun isComposeView(view: View): Boolean =
        view::class.java.name.startsWith(COMPOSE_VIEW_CLASS_NAME)

    private companion object {
        private const val COMPOSE_VIEW_CLASS_NAME = "androidx.compose.ui.platform.ComposeView"
    }
}




