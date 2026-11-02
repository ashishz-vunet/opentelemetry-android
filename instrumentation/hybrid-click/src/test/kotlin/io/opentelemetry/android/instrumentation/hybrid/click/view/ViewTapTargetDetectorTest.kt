/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click.view

import android.view.View
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ViewTapTargetDetectorTest {
    @Test
    fun `returns null when no clickable view found`() {
        val detector = ViewTapTargetDetector()
        val plainView = mockk<View>(relaxed = true)

        val target = detector.findTapTarget(plainView, 5f, 5f)
        assertThat(target).isNull()
    }
}
