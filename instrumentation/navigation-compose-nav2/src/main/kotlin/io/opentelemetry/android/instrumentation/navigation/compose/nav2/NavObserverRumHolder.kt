/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import io.opentelemetry.android.OpenTelemetryRum

internal object NavObserverRumHolder {
    @Volatile
    private var rum: OpenTelemetryRum? = null

    fun set(openTelemetryRum: OpenTelemetryRum) {
        rum = openTelemetryRum
    }

    fun clear() {
        rum = null
    }

    fun current(): OpenTelemetryRum? = rum
}
