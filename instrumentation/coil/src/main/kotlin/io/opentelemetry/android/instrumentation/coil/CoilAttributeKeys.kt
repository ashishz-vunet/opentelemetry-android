/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.coil

import io.opentelemetry.api.common.AttributeKey

internal const val IMAGE_LOAD_SPAN_NAME = "image.load"

internal val ATTR_IMAGE_URL: AttributeKey<String> = AttributeKey.stringKey("image.url")
internal val ATTR_IMAGE_SOURCE: AttributeKey<String> = AttributeKey.stringKey("image.source")
internal val ATTR_IMAGE_LOAD_STATUS: AttributeKey<String> = AttributeKey.stringKey("image.load.status")
internal val ATTR_IMAGE_MODEL_TYPE: AttributeKey<String> = AttributeKey.stringKey("image.model_type")

internal const val STATUS_SUCCESS = "success"
internal const val STATUS_ERROR = "error"

internal const val SOURCE_MEMORY = "memory"
internal const val SOURCE_DISK = "disk"
internal const val SOURCE_NETWORK = "network"
