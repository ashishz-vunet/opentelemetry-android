/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import coil.EventListener
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

/**
 * A Coil [EventListener] that wraps every image request in an OpenTelemetry "image.load" span.
 *
 * ## Lifecycle contract
 * | Callback       | Action                                                              |
 * |----------------|---------------------------------------------------------------------|
 * | [onStart]      | Start span, sanitize URL, `makeCurrent()`, store in [CoilSpanStore]|
 * | [onSuccess]    | Close scope, add source/status attrs, set OK status, end span       |
 * | [onError]      | Close scope, record exception, set ERROR status, end span           |
 *
 * ## Scope lifecycle safety
 * [closeScope] is called inside its own try-catch **before** any attribute enrichment in both
 * [onSuccess] and [onError]. Closing a [io.opentelemetry.context.Scope] that is already closed
 * is a no-op per the OTel spec. Failing to close would corrupt the thread's [ThreadLocal]
 * context and leak memory.
 *
 * ## URL sanitisation
 * Query parameters are stripped from the image URL before it is recorded as an attribute.
 * This prevents authentication tokens, signed URL parameters, and other PII from leaking
 * into the telemetry back-end — a hard requirement for BFSI applications.
 *
 * ## Consumer registration (one-time setup in Application.onCreate or DI graph)
 * ```kotlin
 * val imageLoader = ImageLoader.Builder(context)
 *     .eventListenerFactory(CoilImageLoaderEventListenerFactory())
 *     .build()
 * ```
 *
 * @param tracer the OTel [Tracer] used to create spans.
 */
internal class CoilOtelEventListener(
    private val tracer: Tracer,
) : EventListener {

    /**
     * Called by Coil immediately after an [ImageRequest] is enqueued.
     * This is the earliest hook available from an [EventListener] and marks the true start of
     * the image-loading pipeline. The span is started here and bound to the calling thread's
     * OTel context via [io.opentelemetry.api.trace.Span.makeCurrent].
     */
    override fun onStart(request: ImageRequest) {
        try {
            val key = System.identityHashCode(request)

            // If a span already exists for this request instance (e.g. Coil retry), clean up
            // the stale entries before creating a fresh one.
            CoilSpanStore.scopes.remove(key)?.let { stale ->
                try { stale.close() } catch (_: Throwable) {}
            }
            CoilSpanStore.spans.remove(key)?.let { stale ->
                try { stale.end() } catch (_: Throwable) {}
            }

            val imageUrl = sanitizeUrl(request.data)

            val span =
                tracer
                    .spanBuilder(IMAGE_LOAD_SPAN_NAME)
                    .setAttribute(ATTR_IMAGE_URL, imageUrl)
                    .setAttribute(ATTR_IMAGE_MODEL_TYPE, request.data.javaClass.name)
                    .startSpan()

            val scope = span.makeCurrent()

            CoilSpanStore.spans[key] = span
            CoilSpanStore.scopes[key] = scope
        } catch (_: Throwable) {
            // Telemetry failures must never interrupt the image loading pipeline.
        }
    }

    /**
     * Called by Coil when an image is successfully loaded.
     *
     * The [io.opentelemetry.context.Scope] is closed first inside a protected block to guarantee
     * thread context recovery before attribute enrichment begins. The [DataSource] from
     * [SuccessResult] is mapped to a canonical string label.
     */
    override fun onSuccess(
        request: ImageRequest,
        result: SuccessResult,
    ) {
        val key = System.identityHashCode(request)
        try {
            val scope = CoilSpanStore.scopes.remove(key)
            val span = CoilSpanStore.spans.remove(key)
            closeScope(scope)
            span?.let {
                it.setAttribute(ATTR_IMAGE_SOURCE, result.dataSource.toSourceLabel())
                it.setAttribute(ATTR_IMAGE_LOAD_STATUS, STATUS_SUCCESS)
                it.setStatus(StatusCode.OK)
                it.end()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Called by Coil when an image request fails for any reason.
     *
     * The [io.opentelemetry.context.Scope] is closed first inside a protected block. The
     * throwable from [ErrorResult] is recorded on the span so that the full stack trace is
     * available in the telemetry back-end.
     */
    override fun onError(
        request: ImageRequest,
        result: ErrorResult,
    ) {
        val key = System.identityHashCode(request)
        try {
            val scope = CoilSpanStore.scopes.remove(key)
            val span = CoilSpanStore.spans.remove(key)
            closeScope(scope)
            span?.let {
                it.setAttribute(ATTR_IMAGE_LOAD_STATUS, STATUS_ERROR)
                it.recordException(result.throwable)
                it.setStatus(StatusCode.ERROR)
                it.end()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Closes [scope] defensively in its own try-catch.
     * Even if the outer catch in [onSuccess] or [onError] fires, the scope has already been
     * closed before any attribute-setting begins — preventing ThreadLocal corruption.
     */
    private fun closeScope(scope: io.opentelemetry.context.Scope?) {
        try {
            scope?.close()
        } catch (_: Throwable) {}
    }
}

/**
 * Strips query parameters from the image URL string to prevent token or PII leakage
 * into the telemetry back-end.
 *
 * Coil's [ImageRequest.data] can be a [String], [android.net.Uri], [java.net.URL],
 * [okhttp3.HttpUrl], a resource ID, or any custom type. This function defensively converts
 * [data] to a string and strips everything from the first '?' onward.
 *
 * If the sanitised result is blank (e.g. the raw value was just a query string), the original
 * [data.toString()] is returned so that [ATTR_IMAGE_URL] is never empty.
 */
private fun sanitizeUrl(data: Any): String {
    return try {
        val raw = data.toString()
        val sanitized = raw.substringBefore('?')
        sanitized.ifBlank { raw }
    } catch (_: Throwable) {
        data.javaClass.name
    }
}

/**
 * Maps a Coil [DataSource] to a canonical source-label string.
 *
 * | Coil DataSource   | Label     |
 * |-------------------|-----------|
 * | MEMORY            | "memory"  |
 * | DISK              | "disk"    |
 * | NETWORK           | "network" |
 */
private fun DataSource.toSourceLabel(): String =
    when (this) {
        DataSource.MEMORY, DataSource.MEMORY_CACHE -> SOURCE_MEMORY
        DataSource.DISK -> SOURCE_DISK
        DataSource.NETWORK -> SOURCE_NETWORK
    }
