/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.glide

import io.opentelemetry.api.trace.Span
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe store shared between [OtelSideEffectModelLoader] (which starts spans) and
 * [GlideOtelRequestListener] (which ends them).
 *
 * Keys are the identity hash of the Glide `model` object so that two concurrent requests to
 * the same URL (same String content, different instances) are tracked independently.
 * [System.identityHashCode] collisions are theoretically possible but astronomically rare;
 * the worst outcome is a missed span — never a crash.
 *
 * Unlike Coil, Glide spans are started on the main thread but the [io.opentelemetry.context.Scope]
 * is opened and closed on Glide's background executor thread inside [OtelContextDataFetcher]
 * via `capturedContext.makeCurrent().use { ... }`. No scope is stored here because it never
 * crosses thread boundaries — it is always closed by the same thread that opens it.
 *
 * ## Memory-cache timing
 * [startNanos] stores the [System.nanoTime] captured when [OtelContextModelLoader.buildLoadData]
 * is called (disk / network path). This timestamp is the true start of the image-load operation
 * and is used as the span start time so the span duration reflects real fetch time.
 *
 * For memory-cache hits Glide bypasses [OtelContextModelLoader] entirely, so no entry is stored
 * here. [GlideOtelRequestListener] captures [System.nanoTime] at the top of [onResourceReady]
 * instead — accurate to within microseconds because the memory-cache path is fully synchronous.
 */
internal object GlideSpanStore {
    val spans: ConcurrentHashMap<Int, Span> = ConcurrentHashMap()
    val startNanos: ConcurrentHashMap<Int, Long> = ConcurrentHashMap()
}


