/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

@Composable
fun VunetNavObserver(
    backStack: NavBackStack<NavKey>,
    nameOf: (NavKey) -> String = { it::class.simpleName.orEmpty() },
) {
    val rum = NavObserverRumHolder.current() ?: return
    val collector = remember(backStack, rum, nameOf) { ComposeNav3Collector(rum, nameOf) }
    DisposableEffect(backStack, collector) {
        NavObserverCollectorHolder.set(backStack, collector)
        onDispose {
            NavObserverCollectorHolder.remove(backStack)
        }
    }

    LaunchedEffect(backStack, rum, nameOf) {
        snapshotFlow { backStack.toList() }
            .collect { keys: List<NavKey> -> collector.onBackStackChanged(keys) }
    }
}

@Composable
fun rememberVunetOnBack(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit,
): () -> Unit {
    val currentOnBack = rememberUpdatedState(onBack)
    return remember(backStack) {
        {
            NavObserverCollectorHolder.current(backStack)?.recordBackPress()
            currentOnBack.value()
        }
    }
}
