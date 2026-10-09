/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.export

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoggingLogRecordExporterDecoratorTest {
    private val delegate: LogRecordExporter = mockk()
    private val endpoint = "http://127.0.0.1:4318/v1/logs"

    @Before
    fun setUp() {
        RumDiagnostics.verbose = true
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        RumDiagnostics.verbose = false
        unmockkStatic(Log::class)
    }

    @Test
    fun export_logsSuccessWithEndpointAndItemName() {
        val log = mockk<LogRecordData>()
        every { log.eventName } returns "session.start"
        val logs = listOf(log)
        val result = CompletableResultCode.ofSuccess()
        every { delegate.export(logs) } returns result

        LoggingLogRecordExporterDecorator(delegate, endpoint).export(logs)

        verify {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "export logs: success count=1 endpoint=$endpoint")
        }
        verify {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "export logs: log item=session.start")
        }
    }

    @Test
    fun export_logsFailureWithReason() {
        val logs = emptyList<LogRecordData>()
        val error = RuntimeException("connection refused")
        val result = CompletableResultCode.ofExceptionalFailure(error)
        every { delegate.export(logs) } returns result

        LoggingLogRecordExporterDecorator(delegate, endpoint)
            .export(logs)
            .join(1, java.util.concurrent.TimeUnit.SECONDS)

        verify {
            Log.w(
                RumConstants.OTEL_RUM_LOG_TAG,
                "export logs: failure count=0 endpoint=$endpoint reason=connection refused",
                error,
            )
        }
    }
}
