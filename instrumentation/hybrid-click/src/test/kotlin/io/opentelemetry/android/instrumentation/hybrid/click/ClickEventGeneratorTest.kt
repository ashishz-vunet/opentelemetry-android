/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.view.View
import android.view.Window
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.instrumentation.hybrid.click.compose.ComposeTapTargetDetector
import io.opentelemetry.android.instrumentation.hybrid.click.shared.SOURCE_VIEW
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapTarget
import io.opentelemetry.android.instrumentation.hybrid.click.view.ViewTapTargetDetector
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
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
        val composeDet = mockk<ComposeTapTargetDetector>()
        val viewDet = mockk<ViewTapTargetDetector>()
        val decorView = mockk<View>(relaxed = true)

        every { composeDet.findTapTarget(decorView, 10f, 20f) } returns null
        every { viewDet.findTapTarget(decorView, 10f, 20f) } returns viewTarget

        val result =
            composeDet.findTapTarget(decorView, 10f, 20f)
                ?: viewDet.findTapTarget(decorView, 10f, 20f)

        assertThat(result).isEqualTo(viewTarget)
        assertThat(result?.source).isEqualTo(SOURCE_VIEW)
        verify(exactly = 1) { viewDet.findTapTarget(decorView, 10f, 20f) }
    }

    @Test
    fun `view detector not called when compose detector returns target`() {
        val composeDet = mockk<ComposeTapTargetDetector>()
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

        every { composeDet.findTapTarget(decorView, 5f, 5f) } returns composeTarget

        val result =
            composeDet.findTapTarget(decorView, 5f, 5f)
                ?: viewDet.findTapTarget(decorView, 5f, 5f)

        assertThat(result).isEqualTo(composeTarget)
        assertThat(result?.source).isEqualTo("compose")
        verify(exactly = 0) { viewDet.findTapTarget(any(), any(), any()) }
    }
}
