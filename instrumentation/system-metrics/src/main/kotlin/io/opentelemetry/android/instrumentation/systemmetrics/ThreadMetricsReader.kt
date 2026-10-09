/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

/**
 * Reads thread-related metrics for the current process.
 */
internal class ThreadMetricsReader {
    /**
     * Total number of live threads in this JVM process.
     *
     * Uses [Thread.activeCount] which returns the count of active threads in the current
     * thread's group and all subgroups — effectively the full process thread count on Android
     * where all app threads share the same root [ThreadGroup].
     */
    fun readThreadCount(): Long =
        try {
            Thread.activeCount().toLong()
        } catch (_: Exception) {
            0L
        }
}
