/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.internal.services.network.detector

import android.content.Context
import android.net.Network
import io.opentelemetry.android.common.internal.features.networkattributes.data.CurrentNetwork

/**
 * This class is internal and not for public use. Its APIs are unstable and can change at any time.
 */
interface NetworkDetector {
    fun detectCurrentNetwork(): CurrentNetwork

    /** Classifies a specific [Network], e.g. the one a NetworkCallback was handed. */
    fun detectCurrentNetwork(network: Network): CurrentNetwork

    companion object {
        @JvmStatic
        fun create(context: Context): NetworkDetector = NetworkDetectorImpl(context)
    }
}
