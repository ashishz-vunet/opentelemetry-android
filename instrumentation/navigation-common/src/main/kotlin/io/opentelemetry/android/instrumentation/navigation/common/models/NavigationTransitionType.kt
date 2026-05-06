/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.common.models

/**
 * Semantic navigation direction inferred from destination transitions.
 *
 * @property value Stable string written to the `navigation.transition.type` span attribute.
 */
enum class NavigationTransitionType(
    val value: String,
) {
    /** A new screen was shown on top of the previous one. */
    PUSH("push"),

    /** The visible screen was swapped without growing the back stack (e.g. `replace`). */
    REPLACE("replace"),

    /** A screen was removed and the user returned to a previous destination. */
    POP("pop"),
}
