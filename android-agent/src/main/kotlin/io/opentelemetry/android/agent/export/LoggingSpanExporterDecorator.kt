/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.export

import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class LoggingSpanExporterDecorator(
    private val delegate: SpanExporter,
    private val endpoint: String,
) : SpanExporter by delegate {

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        RumDiagnostics.d { "export spans: count=${spans.size} endpoint=$endpoint" }
        ExportDiagnosticsFormatter.logItems("spans", spans, ExportDiagnosticsFormatter::formatSpan)
        val result = delegate.export(spans)
        ExportDiagnosticsFormatter.logExportOutcome("spans", spans.size, endpoint, result)
        return result
    }
}
