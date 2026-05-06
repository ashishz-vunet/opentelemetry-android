/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.android.instrumentation.hybrid.click.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.annotation.RequiresApi
import androidx.compose.ui.node.LayoutNode

@RequiresApi(24)
internal class ComposeLayoutNodeUtil {
    internal fun getLayoutNodeBoundsInWindow(node: LayoutNode): Rect? =
        try {
            node.layoutDelegate.outerCoordinator.coordinates
                .boundsInWindow()
        } catch (_: Exception) {
            null
        }

    internal fun getLayoutNodePositionInWindow(node: LayoutNode): Offset? =
        try {
            node.layoutDelegate.outerCoordinator.coordinates
                .positionInWindow()
        } catch (_: Exception) {
            null
        }
}
