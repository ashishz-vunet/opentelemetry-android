/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import android.content.Context
import com.google.auto.service.AutoService
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.api.trace.Tracer

/**
 * [AndroidInstrumentation] entry point that activates OpenTelemetry image-load telemetry for
 * Coil image requests.
 *
 * ## How it works
 * 1. [install] stores a [Tracer] in [tracer] (companion object, `@Volatile`).
 * 2. The consumer wires [CoilOtelEventListener.Factory] into their global [coil.ImageLoader]
 *    via [coil.ImageLoader.Builder.eventListenerFactory] — the factory reads [tracer] and
 *    vends a per-request [CoilOtelEventListener] that manages the "image.load" span lifecycle.
 * 3. [uninstall] clears [tracer] and drains [CoilSpanStore] to close any in-flight scopes,
 *    preventing [ThreadLocal] leaks.
 *
 * ## Consumer registration (one-time setup in Application.onCreate or DI graph)
 * ```kotlin
 * val imageLoader = ImageLoader.Builder(context)
 *     .eventListenerFactory(CoilOtelEventListener.Factory())
 *     .build()
 *
 * // Register as the singleton loader used by the top-level extension functions:
 * Coil.setImageLoader(imageLoader)
 * ```
 *
 * ## Idempotency
 * A second call to [install] while the instrumentation is active is a no-op — the existing
 * [Tracer] is retained. [uninstall] is safe to call even if [install] was never called.
 */
@AutoService(AndroidInstrumentation::class)
class CoilInstrumentation : AndroidInstrumentation {
    override val name: String = "coil"

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        if (tracer != null) {
            return
        }
        tracer =
            openTelemetryRum.openTelemetry
                .tracerProvider
                .tracerBuilder("io.opentelemetry.android.instrumentation.coil")
                .build()
    }

    override fun uninstall(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {
        tracer = null
        // Drain any in-flight scopes to prevent ThreadLocal leaks.
        CoilSpanStore.drain()
    }

    internal companion object {
        /**
         * Shared tracer read by [CoilOtelEventListener.Factory] when it vends per-request
         * [CoilOtelEventListener] instances. Written once on the main thread in [install];
         * `@Volatile` ensures cross-thread visibility without synchronisation overhead.
         */
        @Volatile
        @JvmField
        internal var tracer: Tracer? = null
    }
}
