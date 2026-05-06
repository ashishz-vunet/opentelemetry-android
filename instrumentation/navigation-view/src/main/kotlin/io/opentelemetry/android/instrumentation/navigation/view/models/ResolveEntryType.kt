/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view.models

import android.content.Intent
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationEntryType

/**
 * Maps an [Intent] from an Activity launch or resume to a [NavigationEntryType] for RUM.
 *
 * Heuristics (conservative):
 * - [NavigationEntryType.DEEP_LINK] only when both `data` is non-null and `action` is
 *   [Intent.ACTION_VIEW], matching the usual App Links / deep-link delivery shape.
 * - [NavigationEntryType.EXTERNAL] when the action is set and not [Intent.ACTION_MAIN].
 * - Otherwise [NavigationEntryType.INTERNAL].
 */
internal fun resolveEntryType(intent: Intent?): NavigationEntryType {
    if (intent == null) {
        return NavigationEntryType.INTERNAL
    }
    if (intent.data != null && intent.action == Intent.ACTION_VIEW) {
        return NavigationEntryType.DEEP_LINK
    }
    if (intent.action != null && intent.action != Intent.ACTION_MAIN) {
        return NavigationEntryType.EXTERNAL
    }
    return NavigationEntryType.INTERNAL
}
