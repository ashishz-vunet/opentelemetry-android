/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import io.opentelemetry.android.instrumentation.hybrid.click.shared.LabelResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LabelResolverTest {
    @Test
    fun resolve_prefers_content_description() {
        val label =
            LabelResolver.resolve(
                contentDescription = "Pay now",
                text = "Pay",
                className = "android.widget.Button",
                fallback = "fallback",
            )

        assertThat(label).isEqualTo("Pay now")
    }

    @Test
    fun resolve_uses_text_when_content_description_is_blank() {
        val label =
            LabelResolver.resolve(
                contentDescription = "   ",
                text = "Continue",
                className = "android.widget.Button",
                fallback = "fallback",
            )

        assertThat(label).isEqualTo("Continue")
    }

    @Test
    fun resolve_uses_class_name_when_content_description_and_text_are_blank() {
        val label =
            LabelResolver.resolve(
                contentDescription = null,
                text = "",
                className = "android.widget.ImageView",
                fallback = "fallback",
            )

        assertThat(label).isEqualTo("android.widget.ImageView")
    }

    @Test
    fun resolve_uses_fallback_when_all_values_are_blank() {
        val label =
            LabelResolver.resolve(
                contentDescription = null,
                text = null,
                className = " ",
                fallback = "semantics-id",
            )

        assertThat(label).isEqualTo("semantics-id")
    }
}
