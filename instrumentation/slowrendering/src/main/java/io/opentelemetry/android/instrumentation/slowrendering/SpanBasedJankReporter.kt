/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.slowrendering

import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import java.time.Instant

internal const val SLOW_THRESHOLD_MS = 16
internal const val FROZEN_THRESHOLD_MS = 700

internal class SpanBasedJankReporter(
    private val tracer: Tracer,
) : JankReporter {
    override fun reportSlow(
        durationToCountHistogram: Map<Int, Int>,
        periodSeconds: Double,
        activityName: String,
    ) {
        var slowCount = 0
        var frozenCount = 0
        for (entry in durationToCountHistogram) {
            val duration = entry.key
            val count = entry.value
            if (duration > FROZEN_THRESHOLD_MS) {
                RumDiagnostics.d { "slowRendering: frozen frame ${duration}ms count=$count" }
                frozenCount += count
            } else if (duration > SLOW_THRESHOLD_MS) {
                RumDiagnostics.d { "slowRendering: slow frame ${duration}ms count=$count" }
                slowCount += count
            }
        }

        val now = Instant.now()
        if (slowCount > 0) {
            makeSpan("slowRenders", activityName, slowCount, now)
        }
        if (frozenCount > 0) {
            makeSpan("frozenRenders", activityName, frozenCount, now)
        }
    }

    private fun makeSpan(
        spanName: String,
        activityName: String,
        slowCount: Int,
        now: Instant,
    ) {
        val span: Span =
            tracer
                .spanBuilder(spanName)
                .setAttribute("count", slowCount.toLong())
                .setAttribute("activity.name", activityName)
                .setStartTimestamp(now)
                .startSpan()
        span.end(now)
    }
}
