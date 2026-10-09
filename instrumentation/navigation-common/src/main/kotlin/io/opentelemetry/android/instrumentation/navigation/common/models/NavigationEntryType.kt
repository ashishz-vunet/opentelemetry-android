/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.common.models

/**
 * Describes how the user arrived at a destination for telemetry purposes (deep link vs in-app).
 *
 * @property value Stable string written to the `navigation.entry.type` span attribute.
 */
enum class NavigationEntryType(
    val value: String,
) {
    /** Normal in-app navigation or launcher cold start without a special intent. */
    INTERNAL("internal"),

    /** The destination was opened with a view intent carrying a URI (typical deep link). */
    DEEP_LINK("deep_link"),

    /** A non-main intent action without deep-link URI semantics. */
    EXTERNAL("external"),
}
