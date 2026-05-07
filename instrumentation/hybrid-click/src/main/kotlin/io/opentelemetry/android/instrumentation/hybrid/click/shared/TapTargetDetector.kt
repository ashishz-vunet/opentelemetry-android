/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click.shared

import android.view.View

internal interface TapTargetDetector {
    fun findTapTarget(
        rootView: View,
        x: Float,
        y: Float,
    ): TapTarget?
}

