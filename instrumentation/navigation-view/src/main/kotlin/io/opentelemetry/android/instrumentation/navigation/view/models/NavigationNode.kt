/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view.models

/**
 * A single point in the navigation graph: a screen name plus whether it is hosted by an Activity
 * or a Fragment.
 *
 * @property type Whether this node refers to an Activity or a Fragment host.
 * @property name Human-readable screen name, usually from [io.opentelemetry.android.instrumentation.common.ScreenNameExtractor].
 */
internal data class NavigationNode(
    val type: NavigationNodeType,
    val name: String,
)
