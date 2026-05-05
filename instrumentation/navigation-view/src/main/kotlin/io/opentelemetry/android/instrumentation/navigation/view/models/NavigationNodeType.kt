/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view.models

/**
 * Classifies which Android UI host owns the navigation destination being reported.
 */
internal enum class NavigationNodeType {
    /** The destination is an [android.app.Activity] (or subclass). */
    ACTIVITY,

    /** The destination is an [androidx.fragment.app.Fragment] (or subclass). */
    FRAGMENT,
}
