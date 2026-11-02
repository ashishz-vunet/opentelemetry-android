/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation

/**
 * Optional capability for instrumentation implementations that support system metrics configuration.
 */
interface ConfigurableSystemMetricsInstrumentation {
    /**
     * Sets how often (in seconds) system metrics are captured and emitted.
     */
    fun setCollectionIntervalSeconds(value: Long)
}
