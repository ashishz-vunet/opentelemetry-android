/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav3

import android.content.Context
import com.google.auto.service.AutoService
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.instrumentation.AndroidInstrumentation

@AutoService(AndroidInstrumentation::class)
class ComposeNav3Instrumentation : AndroidInstrumentation {
    override val name: String = "navigation.compose.nav3"

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        NavObserverRumHolder.set(openTelemetryRum)
    }

    override fun uninstall(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        NavObserverRumHolder.clear()
    }
}
