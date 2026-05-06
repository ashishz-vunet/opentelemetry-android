/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click.shared

/**
 * Utility that resolves the best display label for click targets.
 */
internal object LabelResolver {
    /**
     * Resolves in priority order:
     * 1) non-blank content description
     * 2) non-blank text
     * 3) non-blank class name
     * 4) fallback identifier
     */
    fun resolve(
        contentDescription: String?,
        text: String?,
        className: String?,
        fallback: String,
    ): String {
        val normalizedContentDescription = contentDescription?.takeIf { it.isNotBlank() }
        if (normalizedContentDescription != null) {
            return normalizedContentDescription
        }

        val normalizedText = text?.takeIf { it.isNotBlank() }
        if (normalizedText != null) {
            return normalizedText
        }

        val normalizedClassName = className?.takeIf { it.isNotBlank() }
        if (normalizedClassName != null) {
            return normalizedClassName
        }

        return fallback
    }
}
