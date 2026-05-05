/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

@Composable
fun VunetNavObserver(
    backStack: NavBackStack,
    nameOf: (NavKey) -> String = { it::class.simpleName.orEmpty() },
) {
    val rum = NavObserverRumHolder.current() ?: return
    val collector = ComposeNav3Collector(rum, nameOf)
    LaunchedEffect(backStack, rum, nameOf) {
        snapshotFlow { backStack.toList() }
            .collect { keys -> collector.onBackStackChanged(keys) }
    }
}
