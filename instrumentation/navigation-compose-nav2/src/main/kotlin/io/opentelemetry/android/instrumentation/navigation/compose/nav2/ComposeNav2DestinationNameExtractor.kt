/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import androidx.navigation.NavDestination

internal object ComposeNav2DestinationNameExtractor {
    fun extract(destination: NavDestination): String {
        val route = destination.route
        if (!route.isNullOrBlank()) {
            return route
        }
        return destination.id.toString()
    }
}
