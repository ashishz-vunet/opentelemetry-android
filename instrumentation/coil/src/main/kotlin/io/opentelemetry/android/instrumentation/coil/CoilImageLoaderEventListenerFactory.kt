/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import coil.EventListener
import coil.request.ImageRequest

/**
 * Thread-safe [EventListener.Factory] that vends a new [CoilOtelEventListener] per request.
 * Coil calls [create] once per [ImageRequest], so each request gets its own listener instance
 * with no shared mutable state between concurrent requests.
 *
 * ## Consumer registration (one-time setup in Application.onCreate or DI graph)
 * ```kotlin
 * val imageLoader = ImageLoader.Builder(context)
 *     .eventListenerFactory(CoilImageLoaderEventListenerFactory())
 *     .build()
 *
 * // Register as the singleton loader used by the top-level extension functions:
 * Coil.setImageLoader(imageLoader)
 * ```
 *
 * If [CoilInstrumentation] has not been installed (i.e. the SDK has not called [install]),
 * [create] returns [EventListener.NONE] so that zero telemetry overhead is incurred for
 * un-instrumented builds.
 */
class CoilImageLoaderEventListenerFactory : EventListener.Factory {
    override fun create(request: ImageRequest): EventListener {
        val currentTracer = CoilInstrumentation.tracer ?: return EventListener.NONE
        return CoilOtelEventListener(currentTracer)
    }
}
