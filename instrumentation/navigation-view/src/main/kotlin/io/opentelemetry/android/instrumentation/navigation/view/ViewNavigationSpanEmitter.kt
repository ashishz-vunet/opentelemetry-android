/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view

import io.opentelemetry.android.common.RumConstants.SCREEN_NAME_KEY
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_ACTION_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_DESTINATION_NAME_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_DESTINATION_TYPE_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_ENTRY_TYPE_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_SOURCE_NAME_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_SOURCE_TYPE_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_TIMESTAMP_MS_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.NAVIGATION_TRIGGER_KEY
import io.opentelemetry.android.instrumentation.navigation.view.ViewNavigationConstants.SPAN_NAME

internal class ViewNavigationSpanEmitter(
    private val tracer: Tracer,
) {
    fun emit(candidate: NavigationTransitionCandidate) {
        val spanBuilder =
            tracer
                .spanBuilder(SPAN_NAME)
                .setAttribute(NAVIGATION_DESTINATION_TYPE_KEY, candidate.destination.type.name.lowercase())
                .setAttribute(NAVIGATION_DESTINATION_NAME_KEY, candidate.destination.name)
                .setAttribute(NAVIGATION_ACTION_KEY, candidate.action.value)
                .setAttribute(NAVIGATION_ENTRY_TYPE_KEY, candidate.entryType.value)
                .setAttribute(NAVIGATION_TRIGGER_KEY, candidate.trigger.value)
                .setAttribute(NAVIGATION_TIMESTAMP_MS_KEY, candidate.timestampMillis)

        candidate.source?.let {
            spanBuilder
                .setAttribute(NAVIGATION_SOURCE_TYPE_KEY, it.type.name.lowercase())
                .setAttribute(NAVIGATION_SOURCE_NAME_KEY, it.name)
        }

        val span = spanBuilder.startSpan()
        // Set screen.name after start so it wins over default attribute appenders.
        span.setAttribute(SCREEN_NAME_KEY, candidate.destination.name)
        span.end()
    }
}
