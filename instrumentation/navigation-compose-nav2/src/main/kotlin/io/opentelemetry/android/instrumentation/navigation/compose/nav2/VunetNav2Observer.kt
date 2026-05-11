/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController

/**
 * Installs Compose Navigation 2 destination observation for the provided [navController].
 *
 * Call this once per [NavController] from a stable place in your Compose tree.
 * The listener is attached while the composable is active and removed automatically on dispose.
 */
@Composable
@Suppress("FunctionName")
fun VunetNav2Observer(navController: NavController) {
    val rum = NavObserverRumHolder.current() ?: return
    DisposableEffect(navController, rum) {
        val collector = ComposeNav2Collector(rum)
        navController.addOnDestinationChangedListener(collector)
        onDispose { navController.removeOnDestinationChangedListener(collector) }
    }
}
