/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.instrumentation.common.Constants.INSTRUMENTATION_SCOPE
import io.opentelemetry.android.instrumentation.navigation.common.NavigationSpanEmitter
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationEntryType
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationNode
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationNodeType
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationTransitionCandidate
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationTransitionType

internal class ComposeNav2Collector(
    openTelemetryRum: OpenTelemetryRum,
    private val destinationFilter: (NavDestination) -> Boolean = ComposeNav2DestinationFilter::shouldIgnore,
    private val destinationNameExtractor: (NavDestination) -> String = ComposeNav2DestinationNameExtractor::extract,
    private val backStackSizeProvider: (NavController) -> Int? = ::readBackStackSize,
) : NavController.OnDestinationChangedListener {
    private val emitter: NavigationSpanEmitter =
        NavigationSpanEmitter(openTelemetryRum.openTelemetry.getTracer(INSTRUMENTATION_SCOPE))
    private val clock = openTelemetryRum.clock
    private var currentVisibleNode: NavigationNode? = null
    private var previousBackStackSize: Int? = null

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?,
    ) {
        if (destinationFilter(destination)) {
            return
        }

        val destinationNode =
            NavigationNode(
                type = NavigationNodeType.COMPOSE_ROUTE,
                name = destinationNameExtractor(destination),
            )
        val source = currentVisibleNode
        if (source != null && source == destinationNode) {
            previousBackStackSize = backStackSizeProvider(controller)
            return
        }

        val currentBackStackSize = backStackSizeProvider(controller)
        val transitionType = inferTransitionType(previousBackStackSize, currentBackStackSize, source != null)

        emitter.emit(
            NavigationTransitionCandidate(
                source = source,
                destination = destinationNode,
                transitionType = transitionType,
                entryType = NavigationEntryType.INTERNAL,
                timestampNanos = clock.now(),
            ),
        )

        currentVisibleNode = destinationNode
        previousBackStackSize = currentBackStackSize
    }

    private fun inferTransitionType(
        oldSize: Int?,
        newSize: Int?,
        hasSource: Boolean,
    ): NavigationTransitionType {
        if (oldSize != null && newSize != null) {
            return when {
                newSize > oldSize -> NavigationTransitionType.PUSH
                newSize < oldSize -> NavigationTransitionType.POP
                else -> NavigationTransitionType.REPLACE
            }
        }
        return if (hasSource) NavigationTransitionType.REPLACE else NavigationTransitionType.PUSH
    }

    companion object {
        private fun readBackStackSize(controller: NavController): Int? =
            runCatching {
                val field = controller::class.java.getDeclaredField("backQueue")
                field.isAccessible = true
                val value = field.get(controller)
                (value as? Collection<*>)?.size
            }.getOrNull()
    }
}
