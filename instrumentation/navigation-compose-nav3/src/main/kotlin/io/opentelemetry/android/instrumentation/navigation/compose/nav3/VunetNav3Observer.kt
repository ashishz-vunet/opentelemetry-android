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

/**
 * Installs Compose Navigation 3 back stack observation for the provided [backStack].
 *
 * Call this once per [NavBackStack] from a stable place in your Compose tree.
 * The observer remains active while the composable is in composition.
 */
@Composable
@Suppress("FunctionName")
fun VunetNav3Observer(
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

/**
 * Wraps an [onBack] callback and records a back-press trigger when possible.
 *
 * Use this wrapper for your navigation back action so pop transitions can be
 * attributed to a back press when [VunetNav3Observer] is active for the same [backStack].
 */
@Composable
@Suppress("FunctionName")
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
