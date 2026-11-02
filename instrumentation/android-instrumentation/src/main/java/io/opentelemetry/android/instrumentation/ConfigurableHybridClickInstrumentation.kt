/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation

/**
 * Optional capability for instrumentation implementations that support hybrid click configuration.
 */
interface ConfigurableHybridClickInstrumentation {
    /**
     * Sets the active-context window duration (in milliseconds) used after a click event.
     */
    fun setActiveContextWindowMillis(value: Long)
}
