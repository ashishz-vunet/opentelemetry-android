/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.android.instrumentation.hybrid.click.compose

import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ComposeTapTargetDetectorTest {
    lateinit var detector: ComposeTapTargetDetector

    @MockK
    lateinit var composeLayoutNodeUtil: ComposeLayoutNodeUtil

    @MockK
    lateinit var semanticsModifier: SemanticsModifier

    @MockK
    lateinit var modifier: Modifier

    @MockK
    lateinit var semanticsConfiguration: SemanticsConfiguration

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        detector = ComposeTapTargetDetector(composeLayoutNodeUtil)
    }

    @Test
    fun `name from onClick label`() {
        val name = detector.nodeToName(createMockLayoutNode(clickable = true))
        assertThat(name).isEqualTo("click")
    }

    @Test
    fun `name from content description`() {
        val name =
            detector.nodeToName(
                createMockLayoutNode(clickable = true, useDescription = true),
            )
        assertThat(name).isEqualTo("clickMe")
    }

    @Test
    fun `name falls back to hashCode on exception`() {
        val mockNode = mockkClass(LayoutNode::class)
        every { mockNode.getModifierInfo() } throws RuntimeException("test")

        val name = detector.nodeToName(mockNode)
        assertThat(name).isNotBlank()
    }

    @Test
    fun `name falls back to hashCode when no modifiers`() {
        val mockNode = mockkClass(LayoutNode::class)
        every { mockNode.getModifierInfo() } returns listOf()

        val name = detector.nodeToName(mockNode)
        assertThat(name).isNotBlank()
    }

    private fun createMockLayoutNode(
        targetX: Float = 0f,
        targetY: Float = 0f,
        hitOffset: IntArray = intArrayOf(10, 20),
        hit: Boolean = false,
        clickable: Boolean = false,
        useDescription: Boolean = false,
    ): LayoutNode {
        val mockNode = mockkClass(LayoutNode::class)
        every { mockNode.isPlaced } returns true

        val bounds =
            if (hit) {
                Rect(
                    left = targetX - hitOffset[0],
                    right = targetX + hitOffset[0],
                    top = targetY - hitOffset[1],
                    bottom = targetY + hitOffset[1],
                )
            } else {
                Rect(
                    left = targetX + hitOffset[0],
                    right = targetX + hitOffset[0],
                    top = targetY + hitOffset[1],
                    bottom = targetY + hitOffset[1],
                )
            }

        val mockModifierInfo = mockkClass(ModifierInfo::class)
        every { mockNode.getModifierInfo() } returns listOf(mockModifierInfo)
        if (clickable) {
            every { mockModifierInfo.modifier } returns semanticsModifier
            every { semanticsModifier.semanticsConfiguration } returns semanticsConfiguration
            every { semanticsConfiguration.contains(eq(SemanticsActions.OnClick)) } returns true

            if (useDescription) {
                every { semanticsConfiguration.getOrNull(eq(SemanticsActions.OnClick)) } returns null
                every { semanticsConfiguration.getOrNull(eq(SemanticsProperties.ContentDescription)) } returns
                    listOf("clickMe")
            } else {
                every { semanticsConfiguration.getOrNull(eq(SemanticsActions.OnClick)) } returns
                    AccessibilityAction<() -> Boolean>("click") { true }
            }
        } else {
            every { mockModifierInfo.modifier } returns modifier
        }

        every { mockNode.zSortedChildren } returns mutableVectorOf()
        every { composeLayoutNodeUtil.getLayoutNodeBoundsInWindow(mockNode) } returns bounds
        every { composeLayoutNodeUtil.getLayoutNodePositionInWindow(mockNode) } returns
            Offset(x = bounds.left, y = bounds.top)

        return mockNode
    }
}
