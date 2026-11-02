/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RumDiagnosticsTest {
    @Before
    fun setUp() {
        RumDiagnostics.verbose = false
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        RumDiagnostics.verbose = false
        unmockkStatic(Log::class)
    }

    @Test
    fun d_doesNotLogWhenVerboseDisabled() {
        RumDiagnostics.d { "should not appear" }
        verify(exactly = 0) { Log.d(any(), any<String>()) }
    }

    @Test
    fun d_logsWhenVerboseEnabled() {
        RumDiagnostics.verbose = true
        RumDiagnostics.d { "diagnostic line" }
        verify(exactly = 1) { Log.d(RumConstants.OTEL_RUM_LOG_TAG, "diagnostic line") }
    }
}
