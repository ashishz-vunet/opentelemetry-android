/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.ConfigurableSystemMetricsInstrumentation

/**
 * Type-safe config DSL that controls how system metrics instrumentation should behave.
 */
@OpenTelemetryDslMarker
class SystemMetricsConfiguration internal constructor(
    private val config: OtelRumConfig,
    instrumentationLoader: AndroidInstrumentationLoader,
) : CanBeEnabledAndDisabled {
    private val systemMetricsInstrumentation: ConfigurableSystemMetricsInstrumentation? by lazy {
        instrumentationLoader
            .getAll()
            .firstOrNull { it.name == SYSTEM_METRICS_INSTRUMENTATION_NAME }
            ?.let { it as? ConfigurableSystemMetricsInstrumentation }
    }

    /**
     * Sets how often (in seconds) system metrics are captured and emitted as a span.
     */
    fun collectionIntervalSeconds(value: Long) {
        require(value > 0) { "collectionIntervalSeconds must be > 0, but was $value." }
        systemMetricsInstrumentation?.setCollectionIntervalSeconds(value)
    }

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(SYSTEM_METRICS_INSTRUMENTATION_NAME)
        } else {
            config.suppressInstrumentation(SYSTEM_METRICS_INSTRUMENTATION_NAME)
        }
    }

    private companion object {
        private const val SYSTEM_METRICS_INSTRUMENTATION_NAME = "system-metrics"
    }
}
