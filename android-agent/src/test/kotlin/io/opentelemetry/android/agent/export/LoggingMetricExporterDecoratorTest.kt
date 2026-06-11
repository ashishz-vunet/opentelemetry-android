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
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoggingMetricExporterDecoratorTest {
    private val delegate: MetricExporter = mockk()
    private val endpoint = "http://127.0.0.1:4318/v1/metrics"

    @Before
    fun setUp() {
        RumDiagnostics.verbose = true
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { delegate.getAggregationTemporality(any()) } returns AggregationTemporality.CUMULATIVE
    }

    @After
    fun tearDown() {
        RumDiagnostics.verbose = false
        unmockkStatic(Log::class)
    }

    @Test
    fun export_logsSuccessWithEndpointAndItemName() {
        val metric = mockk<MetricData>()
        every { metric.name } returns "process.cpu.usage"
        val metrics = listOf(metric)
        val result = CompletableResultCode.ofSuccess()
        every { delegate.export(metrics) } returns result

        LoggingMetricExporterDecorator(delegate, endpoint).export(metrics)

        verify {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "export metrics: success count=1 endpoint=$endpoint")
        }
        verify {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "export metrics: metric item=process.cpu.usage")
        }
    }

    @Test
    fun export_logsFailureWithReason() {
        val metrics = emptyList<MetricData>()
        val error = RuntimeException("timeout")
        val result = CompletableResultCode.ofExceptionalFailure(error)
        every { delegate.export(metrics) } returns result

        LoggingMetricExporterDecorator(delegate, endpoint)
            .export(metrics)
            .join(1, java.util.concurrent.TimeUnit.SECONDS)

        verify {
            Log.w(
                RumConstants.OTEL_RUM_LOG_TAG,
                "export metrics: failure count=0 endpoint=$endpoint reason=timeout",
                error,
            )
        }
    }
}
