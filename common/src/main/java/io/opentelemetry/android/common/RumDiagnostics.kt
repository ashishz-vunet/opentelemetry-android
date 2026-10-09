/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common

import android.util.Log

/**
 * Global verbose diagnostics for the OpenTelemetry Android RUM stack.
 * Enabled via [verbose] during agent initialization when diagnostic logging is on.
 */
object RumDiagnostics {

    @Volatile
    var verbose: Boolean = false

    inline fun d(message: () -> String) {
        if (verbose) {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, message())
        }
    }

    inline fun i(message: () -> String) {
        if (verbose) {
            Log.i(RumConstants.OTEL_RUM_LOG_TAG, message())
        }
    }

    inline fun w(message: () -> String) {
        if (verbose) {
            Log.w(RumConstants.OTEL_RUM_LOG_TAG, message())
        }
    }

    inline fun w(
        message: () -> String,
        throwable: Throwable,
    ) {
        if (verbose) {
            Log.w(RumConstants.OTEL_RUM_LOG_TAG, message(), throwable)
        }
    }
}
