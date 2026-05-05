/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController

@Composable
fun VunetNavObserver(navController: NavController) {
    val rum = NavObserverRumHolder.current() ?: return
    DisposableEffect(navController, rum) {
        val collector = ComposeNav2Collector(rum)
        navController.addOnDestinationChangedListener(collector)
        onDispose { navController.removeOnDestinationChangedListener(collector) }
    }
}
