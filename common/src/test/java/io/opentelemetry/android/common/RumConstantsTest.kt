/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RumConstantsTest {
    @Test
    fun otelRumLogTag_isVuNetBranded() {
        assertEquals("VuNetRUM", RumConstants.OTEL_RUM_LOG_TAG)
    }
}
