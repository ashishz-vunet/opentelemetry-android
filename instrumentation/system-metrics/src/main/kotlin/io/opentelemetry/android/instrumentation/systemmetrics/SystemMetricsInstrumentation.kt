/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.systemmetrics

import android.content.Context
import android.util.Log
import com.google.auto.service.AutoService
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SpanProcessor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Entry point for system metrics instrumentation.
 *
 * Periodically captures a snapshot of CPU, memory, thread, and device metrics and
 * delivers it as an `"app.metrics"` event on the currently active span, or as a
 * standalone `"app.metrics"` span when no user span is in flight.
 */
@AutoService(AndroidInstrumentation::class)
internal class SystemMetricsInstrumentation : AndroidInstrumentation {
    override val name: String = "system-metrics"

    @Volatile private var scheduler: ScheduledExecutorService? = null

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        if (scheduler != null) return
        val newScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "otel-system-metrics").apply {
                isDaemon = true
                setUncaughtExceptionHandler { _, e ->
                    Log.e("OpenTelemetryRum", "SystemMetrics: uncaught exception on scheduler thread", e)
                }
            }
        }
        scheduler = newScheduler

        val registry = ActiveSpanRegistry()
        val sdk = openTelemetryRum.openTelemetry as? OpenTelemetrySdk
        sdk?.let { injectSpanProcessor(it, registry) }

        SystemMetricsSpanEmitter(
            openTelemetry = openTelemetryRum.openTelemetry,
            scheduler = newScheduler,
            intervalSeconds = COLLECTION_INTERVAL_SECONDS,
            activeSpanRegistry = registry,
            deviceReader = DefaultDeviceMetricsReader(context),
        ).start()
    }

    override fun uninstall(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        scheduler?.shutdownNow()
        scheduler = null
    }

    private companion object {
        const val COLLECTION_INTERVAL_SECONDS = 30L
    }

    /**
     * Injects [processor] into the already-built [OpenTelemetrySdk]'s TracerProvider using
     * reflection. This is necessary because [AndroidInstrumentation.install] is called after
     * the SDK is fully constructed, so the standard builder API is no longer available.
     *
     * Reflection targets package-private JVM fields in the OTel SDK, not Android platform
     * internals. A failure degrades gracefully: [Log.w] is emitted and metrics fall back to
     * standalone `"app.metrics"` spans.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun injectSpanProcessor(
        sdk: OpenTelemetrySdk,
        processor: SpanProcessor,
    ) {
        try {
            val sharedStateField =
                sdk.sdkTracerProvider.javaClass.getDeclaredField("sharedState")
            sharedStateField.isAccessible = true
            val sharedState = sharedStateField.get(sdk.sdkTracerProvider)

            val processorField = sharedState.javaClass.getDeclaredField("activeSpanProcessor")
            processorField.isAccessible = true
            val existing = processorField.get(sharedState) as SpanProcessor

            processorField.set(sharedState, SpanProcessor.composite(existing, processor))
        } catch (e: Throwable) {
            // Catches both Exception and Error subclasses (e.g. LinkageError from R8 obfuscation)
            // so that no failure path during reflection can crash the host app at startup.
            Log.w(
                "OpenTelemetryRum",
                "SystemMetrics: span processor injection failed — metrics fall back to standalone spans. " +
                    "Check the OTel SDK version or report this as a bug.",
                e,
            )
        }
    }
}
