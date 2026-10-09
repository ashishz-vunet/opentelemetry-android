/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.export

import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

internal class LoggingLogRecordExporterDecorator(
    private val delegate: LogRecordExporter,
    private val endpoint: String,
) : LogRecordExporter by delegate {

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        RumDiagnostics.d { "export logs: count=${logs.size} endpoint=$endpoint" }
        ExportDiagnosticsFormatter.logItems("logs", logs, ExportDiagnosticsFormatter::formatLog)
        val result = delegate.export(logs)
        ExportDiagnosticsFormatter.logExportOutcome("logs", logs.size, endpoint, result)
        return result
    }
}
