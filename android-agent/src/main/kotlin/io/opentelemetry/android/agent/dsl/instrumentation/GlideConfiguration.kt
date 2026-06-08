/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader

/**
 * Type-safe config DSL that controls whether Glide image-loading instrumentation is enabled.
 *
 * When on the classpath, [io.opentelemetry.android.instrumentation.glide.GlideInstrumentation]
 * is auto-discovered via [java.util.ServiceLoader] and installed by default. Use [enabled] to
 * opt out.
 */
@OpenTelemetryDslMarker
class GlideConfiguration internal constructor(
    private val config: OtelRumConfig,
    instrumentationLoader: AndroidInstrumentationLoader,
) : CanBeEnabledAndDisabled {

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(GLIDE_INSTRUMENTATION_NAME)
        } else {
            config.suppressInstrumentation(GLIDE_INSTRUMENTATION_NAME)
        }
    }

    private companion object {
        private const val GLIDE_INSTRUMENTATION_NAME = "glide"
    }
}
