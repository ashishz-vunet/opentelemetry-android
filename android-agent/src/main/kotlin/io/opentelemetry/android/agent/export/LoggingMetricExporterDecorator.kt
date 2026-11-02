/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.export

import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

internal class LoggingMetricExporterDecorator(
    private val delegate: MetricExporter,
    private val endpoint: String,
) : MetricExporter by delegate {

    override fun export(metrics: Collection<MetricData>): CompletableResultCode {
        RumDiagnostics.d { "export metrics: count=${metrics.size} endpoint=$endpoint" }
        ExportDiagnosticsFormatter.logItems("metrics", metrics, ExportDiagnosticsFormatter::formatMetric)
        val result = delegate.export(metrics)
        ExportDiagnosticsFormatter.logExportOutcome("metrics", metrics.size, endpoint, result)
        return result
    }

    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality =
        delegate.getAggregationTemporality(instrumentType)
}
