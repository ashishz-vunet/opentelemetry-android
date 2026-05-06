/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.common.models

/**
 * All inputs required to emit one `ui.navigation` span for a detected screen transition.
 *
 * @property source Previously visible screen, if any.
 * @property destination Screen that became visible.
 * @property transitionType Inferred direction of the transition ([NavigationTransitionType]).
 * @property entryType How the destination was entered.
 * @property timestampNanos Wall-clock time from [io.opentelemetry.sdk.common.Clock.now] (nanoseconds since epoch).
 */
data class NavigationTransitionCandidate(
    val source: NavigationNode?,
    val destination: NavigationNode,
    val transitionType: NavigationTransitionType,
    val entryType: NavigationEntryType,
    val timestampNanos: Long,
)
