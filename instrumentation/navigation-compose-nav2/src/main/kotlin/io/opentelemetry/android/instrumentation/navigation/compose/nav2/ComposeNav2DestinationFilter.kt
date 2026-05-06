/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import androidx.navigation.NavDestination

internal object ComposeNav2DestinationFilter {
    fun shouldIgnore(destination: NavDestination): Boolean {
        val className = destination::class.java.name
        return className.contains("DialogFragmentNavigator\$Destination") ||
            className.contains("BottomSheetNavigator\$Destination")
    }
}
