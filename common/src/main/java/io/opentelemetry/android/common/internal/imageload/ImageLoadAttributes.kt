/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common.internal.imageload

import io.opentelemetry.api.common.AttributeKey

/**
 * Shared span name, attribute keys, and canonical label values for image-load telemetry.
 *
 * Both the Glide and Coil instrumentation modules emit the same `image.load` span shape, so the
 * constants live here in `:common` to keep the two instrumentations from drifting over time.
 *
 * This type is in an `internal`-named package and is **not** part of the stable public API; it is
 * `public` at the language level only because it is referenced across module boundaries. Treat it
 * as an internal SDK detail subject to change.
 */
object ImageLoadAttributes {
    const val IMAGE_LOAD_SPAN_NAME: String = "image.load"

    @JvmField
    val ATTR_IMAGE_URL: AttributeKey<String> = AttributeKey.stringKey("image.url")

    @JvmField
    val ATTR_IMAGE_SOURCE: AttributeKey<String> = AttributeKey.stringKey("image.source")

    @JvmField
    val ATTR_IMAGE_LOAD_STATUS: AttributeKey<String> = AttributeKey.stringKey("image.load.status")

    @JvmField
    val ATTR_IMAGE_MODEL_TYPE: AttributeKey<String> = AttributeKey.stringKey("image.model_type")

    @JvmField
    val ATTR_IMAGE_IS_FIRST_RESOURCE: AttributeKey<Boolean> =
        AttributeKey.booleanKey("image.is_first_resource")

    const val STATUS_SUCCESS: String = "success"
    const val STATUS_ERROR: String = "error"
    const val STATUS_CANCELLED: String = "cancelled"

    const val SOURCE_MEMORY: String = "memory"
    const val SOURCE_DISK: String = "disk"
    const val SOURCE_NETWORK: String = "network"
    const val SOURCE_DISK_CACHE: String = "disk_cache"

    /**
     * Strips query parameters from a raw URL/model string to avoid leaking sensitive tokens
     * (auth, signatures) into telemetry attributes. Critical for BFSI compliance.
     */
    fun sanitizeUrl(raw: String): String {
        val withoutQuery = raw.substringBefore('?')
        return withoutQuery.ifBlank { raw }
    }
}
