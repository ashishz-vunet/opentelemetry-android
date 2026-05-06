/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.view.MotionEvent
import io.opentelemetry.android.instrumentation.hybrid.click.shared.TapGestureClassifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TapGestureClassifierTest {
    @Test
    fun emits_click_for_valid_tap() {
        val classifier = TapGestureClassifier().apply { touchSlopPx = 8f }

        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_DOWN, 100f, 200f)).isFalse()
        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_MOVE, 103f, 204f)).isFalse()
        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_UP, 103f, 204f)).isTrue()
    }

    @Test
    fun does_not_emit_click_for_drag_scroll_gesture() {
        val classifier = TapGestureClassifier().apply { touchSlopPx = 8f }

        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_DOWN, 100f, 200f)).isFalse()
        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_MOVE, 120f, 230f)).isFalse()
        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_UP, 120f, 230f)).isFalse()
    }

    @Test
    fun does_not_emit_click_after_cancel() {
        val classifier = TapGestureClassifier().apply { touchSlopPx = 8f }

        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_DOWN, 100f, 200f)).isFalse()
        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_CANCEL, 100f, 200f)).isFalse()
        assertThat(classifier.shouldEmitClick(MotionEvent.ACTION_UP, 100f, 200f)).isFalse()
    }
}
