/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view

import android.content.Intent

internal enum class NavigationNodeType {
    ACTIVITY,
    FRAGMENT,
}

internal enum class NavigationAction(
    val value: String,
) {
    PUSH("push"),
    REPLACE("replace"),
    POP("pop"),
    FINISH("finish"),
}

internal enum class NavigationTrigger(
    val value: String,
) {
    BACK_PRESS("back_press"),
    PROGRAMMATIC("programmatic"),
    UNKNOWN("unknown"),
}

internal enum class NavigationEntryType(
    val value: String,
) {
    INTERNAL("internal"),
    DEEP_LINK("deep_link"),
    EXTERNAL("external"),
}

internal data class NavigationNode(
    val type: NavigationNodeType,
    val name: String,
)

internal data class NavigationTransitionCandidate(
    val source: NavigationNode?,
    val destination: NavigationNode,
    val action: NavigationAction,
    val trigger: NavigationTrigger,
    val entryType: NavigationEntryType,
    val timestampMillis: Long,
)

internal fun resolveEntryType(intent: Intent?): NavigationEntryType {
    if (intent == null) {
        return NavigationEntryType.INTERNAL
    }
    if (intent.data != null || intent.action == Intent.ACTION_VIEW) {
        return NavigationEntryType.DEEP_LINK
    }
    if (intent.action != null && intent.action != Intent.ACTION_MAIN) {
        return NavigationEntryType.EXTERNAL
    }
    return NavigationEntryType.INTERNAL
}
