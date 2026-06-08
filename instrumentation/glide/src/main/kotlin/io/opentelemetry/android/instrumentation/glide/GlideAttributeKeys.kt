/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.glide

import io.opentelemetry.api.common.AttributeKey

internal const val IMAGE_LOAD_SPAN_NAME = "image.load"

internal val ATTR_IMAGE_URL: AttributeKey<String> = AttributeKey.stringKey("image.url")
internal val ATTR_IMAGE_SOURCE: AttributeKey<String> = AttributeKey.stringKey("image.source")
internal val ATTR_IMAGE_LOAD_STATUS: AttributeKey<String> = AttributeKey.stringKey("image.load.status")
internal val ATTR_IMAGE_MODEL_TYPE: AttributeKey<String> = AttributeKey.stringKey("image.model_type")
internal val ATTR_IMAGE_IS_FIRST_RESOURCE: AttributeKey<Boolean> = AttributeKey.booleanKey("image.is_first_resource")

internal const val STATUS_SUCCESS = "success"
internal const val STATUS_ERROR = "error"

internal const val SOURCE_MEMORY = "memory"
internal const val SOURCE_DISK = "disk"
internal const val SOURCE_NETWORK = "network"
internal const val SOURCE_DISK_CACHE = "disk_cache"

/**
 * Strips query parameters from a raw URL/model string to avoid leaking sensitive tokens
 * (auth, signatures) into telemetry attributes. Critical for BFSI compliance.
 */
internal fun sanitizeModel(raw: String): String {
    val withoutQuery = raw.substringBefore('?')
    return withoutQuery.ifBlank { raw }
}
