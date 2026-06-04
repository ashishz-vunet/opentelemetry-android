/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class StartupContentProviderTimestampsTest {
    @Before
    fun resetTimestamps() {
        ProcessStartTimestamps.contentProvidersPhaseStartEpochMs = 0L
        ProcessStartTimestamps.contentProviderEpochMs = 0L
    }

    @Test
    fun `AppAnchorContentProvider onCreate records content provider phase start`() {
        assertThat(AppAnchorContentProvider().onCreate()).isTrue()
        assertThat(ProcessStartTimestamps.contentProvidersPhaseStartEpochMs).isGreaterThan(0L)
    }

    @Test
    fun `EarlyStartupContentProvider onCreate records content provider phase end`() {
        assertThat(EarlyStartupContentProvider().onCreate()).isTrue()
        assertThat(ProcessStartTimestamps.contentProviderEpochMs).isGreaterThan(0L)
    }
}
