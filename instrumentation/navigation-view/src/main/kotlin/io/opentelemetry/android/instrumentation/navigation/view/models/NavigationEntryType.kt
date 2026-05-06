/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view.models

/**
 * Describes how the user arrived at an Activity for telemetry purposes (deep link vs in-app).
 *
 * @property value Stable string written to the `navigation.entry.type` span attribute.
 */
internal enum class NavigationEntryType(
    val value: String,
) {
    /** Normal in-app navigation or launcher cold start without a special intent. */
    INTERNAL("internal"),

    /** The Activity was opened with a view intent carrying a URI (typical deep link). */
    DEEP_LINK("deep_link"),

    /** A non-main intent action without deep-link URI semantics. */
    EXTERNAL("external"),
}
