/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader

/**
 * Type-safe config DSL that controls whether Coil image-loading instrumentation is enabled.
 *
 * When on the classpath, [io.opentelemetry.android.instrumentation.coil.CoilInstrumentation]
 * is auto-discovered via [java.util.ServiceLoader] and installed by default. Use [enabled] to
 * opt out.
 */
@OpenTelemetryDslMarker
class CoilConfiguration internal constructor(
    private val config: OtelRumConfig,
    instrumentationLoader: AndroidInstrumentationLoader,
) : CanBeEnabledAndDisabled {

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(COIL_INSTRUMENTATION_NAME)
        } else {
            config.suppressInstrumentation(COIL_INSTRUMENTATION_NAME)
        }
    }

    private companion object {
        private const val COIL_INSTRUMENTATION_NAME = "coil"
    }
}
