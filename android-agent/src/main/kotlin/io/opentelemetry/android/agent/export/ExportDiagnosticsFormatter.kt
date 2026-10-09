/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.export

import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.trace.data.SpanData

internal object ExportDiagnosticsFormatter {
    const val MAX_ITEMS: Int = 5

    fun formatSpan(span: SpanData): String = "span item=${span.name}"

    fun formatLog(log: LogRecordData): String {
        val eventName = log.eventName?.takeIf { it.isNotEmpty() } ?: "log"
        return "log item=$eventName"
    }

    fun formatMetric(metric: MetricData): String = "metric item=${metric.name}"

    fun <T> logItems(
        signal: String,
        items: Collection<T>,
        formatter: (T) -> String,
    ) {
        if (items.isEmpty()) {
            return
        }
        items.take(MAX_ITEMS).forEach { item ->
            RumDiagnostics.d { "export $signal: ${formatter(item)}" }
        }
        val remaining = items.size - MAX_ITEMS
        if (remaining > 0) {
            RumDiagnostics.d { "export $signal: +$remaining more" }
        }
    }

    fun logExportOutcome(
        signal: String,
        count: Int,
        endpoint: String,
        result: CompletableResultCode,
    ) {
        result.whenComplete {
            if (result.isSuccess) {
                RumDiagnostics.d { "export $signal: success count=$count endpoint=$endpoint" }
            } else {
                val throwable = result.failureThrowable
                val reason = throwable?.message ?: "unknown"
                if (throwable != null) {
                    RumDiagnostics.w(
                        { "export $signal: failure count=$count endpoint=$endpoint reason=$reason" },
                        throwable,
                    )
                } else {
                    RumDiagnostics.w { "export $signal: failure count=$count endpoint=$endpoint reason=$reason" }
                }
            }
        }
    }
}
