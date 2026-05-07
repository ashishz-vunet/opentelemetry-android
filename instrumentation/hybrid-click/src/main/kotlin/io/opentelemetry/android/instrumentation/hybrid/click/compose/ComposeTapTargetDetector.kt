/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.android.instrumentation.hybrid.click.compose

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.opentelemetry.android.instrumentation.hybrid.click.shared.LabelResolver
import java.util.LinkedList

/**
 * Resolves Compose tap targets from a hybrid screen's root [View].
 *
 * This detector stays focused on Compose-node traversal and metadata extraction. The conversion to
 * hybrid click model objects is intentionally handled outside this class.
 */
internal class ComposeTapTargetDetector(
    private val composeLayoutNodeUtil: ComposeLayoutNodeUtil = ComposeLayoutNodeUtil(),
) {
    /**
     * Finds the deepest eligible [LayoutNode] at the provided window coordinates.
     */
    fun findTapTarget(
        rootView: View,
        x: Float,
        y: Float,
    ): LayoutNode? = findLayoutNodeTarget(rootView, x, y)

    /**
     * Resolves a stable display name for telemetry from node semantics/modifier metadata.
     */
    internal fun nodeToName(node: LayoutNode): String =
        try {
            getNodeName(node) ?: nodeId(node)
        } catch (_: Throwable) {
            nodeId(node)
        }

    /**
     * Resolves node coordinates in window space for span attributes.
     */
    internal fun nodeToPosition(node: LayoutNode): Pair<Long, Long> {
        val position = composeLayoutNodeUtil.getLayoutNodePositionInWindow(node)
        return Pair(position?.x?.toLong() ?: 0L, position?.y?.toLong() ?: 0L)
    }

    /**
     * Scans the Android view tree to locate Compose [Owner] roots and delegates node hit-testing.
     */
    private fun findLayoutNodeTarget(
        decorView: View,
        x: Float,
        y: Float,
    ): LayoutNode? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)

        var target: LayoutNode? = null
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.add(view.getChildAt(index))
                }
                // Owner is the Compose-internal root; cast succeeds only for AndroidComposeView.
                (view as? Owner)?.let {
                    try {
                        target = findTapTarget(view as Owner, x, y)
                    } catch (_: Throwable) {
                        // Visibility-suppressed internals may throw at runtime.
                    }
                }
            }
        }
        return target
    }

    /**
     * Breadth-first traversal over Compose layout tree to keep the deepest matching node.
     */
    private fun findTapTarget(
        owner: Owner,
        x: Float,
        y: Float,
    ): LayoutNode? {
        val queue = LinkedList<LayoutNode>()
        queue.addFirst(owner.root)
        var target: LayoutNode? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isPlaced && hitTest(node, x, y)) {
                target = node
            }
            queue.addAll(node.zSortedChildren.asMutableList())
        }
        return target
    }

    /**
     * Checks coordinate bounds and clickability constraints.
     */
    private fun hitTest(
        node: LayoutNode,
        x: Float,
        y: Float,
    ): Boolean {
        val bounded =
            composeLayoutNodeUtil.getLayoutNodeBoundsInWindow(node)?.let { bounds ->
                x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
            } == true

        return bounded && isValidClickTarget(node)
    }

    /**
     * Determines whether node semantics/modifiers represent a clickable element.
     */
    private fun isValidClickTarget(node: LayoutNode): Boolean {
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is SemanticsModifier) {
                with(modifier.semanticsConfiguration) {
                    if (contains(SemanticsActions.OnClick)) {
                        return true
                    }
                }
            } else {
                val className = modifier::class.qualifiedName
                if (
                    className == CLASS_NAME_CLICKABLE_ELEMENT ||
                    className == CLASS_NAME_COMBINED_CLICKABLE_ELEMENT ||
                    className == CLASS_NAME_TOGGLEABLE_ELEMENT
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Produces a user-facing label with fallback resolution strategy.
     */
    private fun nodeToLabel(node: LayoutNode): String {
        val extractedLabel = getNodeName(node)
        return LabelResolver.resolve(
            contentDescription = extractedLabel,
            text = null,
            className = "ComposeNode",
            fallback = nodeId(node),
        )
    }

    // Label precedence: OnClick label -> ContentDescription -> last modifier simpleName
    private fun getNodeName(node: LayoutNode): String? {
        var className: String? = null
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is SemanticsModifier) {
                with(modifier.semanticsConfiguration) {
                    val onClickAction = getOrNull(SemanticsActions.OnClick)
                    if (onClickAction != null) {
                        val label = onClickAction.label
                        if (label != null) {
                            return label
                        }
                    }

                    val contentDescriptionList =
                        getOrNull(SemanticsProperties.ContentDescription)
                    if (contentDescriptionList != null) {
                        val contentDescription = contentDescriptionList.getOrNull(0)
                        if (contentDescription != null) {
                            return contentDescription
                        }
                    }
                }
            } else {
                className = modifier::class.qualifiedName
            }
        }
        return className
    }

    /**
     * Returns a stable fallback node identifier used in telemetry.
     */
    private fun nodeId(node: LayoutNode): String = node.hashCode().toString()

    companion object {
        private const val CLASS_NAME_CLICKABLE_ELEMENT =
            "androidx.compose.foundation.ClickableElement"
        private const val CLASS_NAME_COMBINED_CLICKABLE_ELEMENT =
            "androidx.compose.foundation.CombinedClickableElement"
        private const val CLASS_NAME_TOGGLEABLE_ELEMENT =
            "androidx.compose.foundation.selection.ToggleableElement"
    }
}
