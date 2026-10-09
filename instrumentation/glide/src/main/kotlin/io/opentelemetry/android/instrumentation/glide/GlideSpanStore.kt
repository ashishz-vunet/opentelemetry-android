/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.glide

import io.opentelemetry.api.trace.Span
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe store shared between [OtelSideEffectModelLoader] (which starts spans) and
 * [VunetGlideRequestListener] (which ends them).
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
 * ## Span timing
 * The span start timestamp is set in [OtelContextModelLoader.buildLoadData] via
 * `setStartTimestamp(System.currentTimeMillis() * 1_000_000, …)`. This is wall-clock time at
 * **millisecond** resolution (the `* 1_000_000` only rescales ms → ns; it does not add sub-ms
 * precision). For RUM this is acceptable, but very fast loads such as memory-cache hits may
 * report a near-zero duration in the backend. See the README "Known limitations" section.
 */
internal object GlideSpanStore {
    val spans: ConcurrentHashMap<Int, Span> = ConcurrentHashMap()
}
