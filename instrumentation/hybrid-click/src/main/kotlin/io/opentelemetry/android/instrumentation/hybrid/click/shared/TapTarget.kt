/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click.shared

/**
 * Normalized tap target metadata used to build hybrid click spans.
 *
 * [source] identifies where the target came from (`view` or `compose`).
 */
internal data class TapTarget(
    val source: String,
    val widgetId: String,
    val widgetName: String,
    val label: String,
    val x: Long,
    val y: Long,
)
