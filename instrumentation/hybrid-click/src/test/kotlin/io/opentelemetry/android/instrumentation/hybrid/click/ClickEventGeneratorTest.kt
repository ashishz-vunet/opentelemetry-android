/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.view.View
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.instrumentation.hybrid.click.shared.SOURCE_VIEW
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapTarget
import io.opentelemetry.android.instrumentation.hybrid.click.view.ViewTapTargetDetector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClickEventGeneratorTest {
    private val viewTarget =
        TapTarget(
            source = SOURCE_VIEW,
            widgetId = "42",
            widgetName = "btn_ok",
            label = "OK",
            x = 10L,
            y = 20L,
        )

    @Test
    fun `view detector used when compose detector returns null`() {
        val viewDet = mockk<ViewTapTargetDetector>()
        val decorView = mockk<View>(relaxed = true)

        every { viewDet.findTapTarget(decorView, 10f, 20f) } returns viewTarget

        val result =
            viewDet.findTapTarget(decorView, 10f, 20f)

        assertThat(result).isEqualTo(viewTarget)
        assertThat(result?.source).isEqualTo(SOURCE_VIEW)
        verify(exactly = 1) { viewDet.findTapTarget(decorView, 10f, 20f) }
    }

    @Test
    fun `view detector returns compose target when queried`() {
        val viewDet = mockk<ViewTapTargetDetector>()
        val decorView = mockk<View>(relaxed = true)
        val composeTarget =
            TapTarget(
                source = "compose",
                widgetId = "99",
                widgetName = "Pay now",
                label = "Pay now",
                x = 5L,
                y = 5L,
            )

        every { viewDet.findTapTarget(decorView, 5f, 5f) } returns composeTarget

        val result =
            viewDet.findTapTarget(decorView, 5f, 5f)

        assertThat(result).isEqualTo(composeTarget)
        assertThat(result?.source).isEqualTo("compose")
        verify(exactly = 1) { viewDet.findTapTarget(decorView, 5f, 5f) }
    }
}
