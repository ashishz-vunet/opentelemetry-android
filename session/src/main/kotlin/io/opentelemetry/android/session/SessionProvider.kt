/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.session

/**
 * Provides information about the current session.
 */
fun interface SessionProvider {

    /**
     * Retrieves the current session ID.
     */
    fun getSessionId(): String

    /**
     * Ends the current session immediately and starts a new one with a fresh session ID.
     * Default implementation is a no-op for custom providers that do not support rotation.
     */
    fun endSession() {
        // default noop
    }

    companion object {

        /**
         * A no-op implementation of [SessionProvider].
         */
        @JvmStatic
        fun getNoop(): SessionProvider = NO_OP

        private val NO_OP: SessionProvider by lazy {
            SessionProvider { "" }
        }
    }
}
