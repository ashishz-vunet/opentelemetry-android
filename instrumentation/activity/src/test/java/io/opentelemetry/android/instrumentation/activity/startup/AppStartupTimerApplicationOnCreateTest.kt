/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity.startup

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.common.StartupTimestampProvider
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class AppStartupTimerApplicationOnCreateTest {
    private lateinit var exporter: InMemorySpanExporter
    private lateinit var sdk: OpenTelemetrySdk

    @Before
    fun setUp() {
        exporter = InMemorySpanExporter.create()
        sdk =
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(
                    SdkTracerProvider
                        .builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build(),
                ).build()
    }

    @Test
    fun `application onCreate start and end events are emitted on app start`() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val startElapsed = nowElapsed - 80
        val endElapsed = nowElapsed - 30

        val appStart = runStartup(fakeProvider(startElapsed, endElapsed))

        val start = appStart.events.single { it.name == AppStartupTimer.EVENT_APPLICATION_START }
        val end = appStart.events.single { it.name == AppStartupTimer.EVENT_APPLICATION_END }
        // Converted from elapsedRealtime without losing the 50 ms gap (±1 ms rounding).
        assertThat((end.epochNanos - start.epochNanos) / 1_000_000).isBetween(49L, 51L)
        assertThat(start.epochNanos).isGreaterThanOrEqualTo(appStart.startEpochNanos)
    }

    @Test
    fun `equal start and end timestamps emit a zero duration not an inversion`() {
        val elapsed = SystemClock.elapsedRealtime() - 10
        val appStart = runStartup(fakeProvider(elapsed, elapsed))

        val start = appStart.events.single { it.name == AppStartupTimer.EVENT_APPLICATION_START }
        val end = appStart.events.single { it.name == AppStartupTimer.EVENT_APPLICATION_END }
        assertThat(end.epochNanos).isGreaterThanOrEqualTo(start.epochNanos)
        assertThat((end.epochNanos - start.epochNanos) / 1_000_000).isEqualTo(0L)
    }

    @Test
    fun `no application events when timestamps are missing`() {
        val appStart = runStartup(fakeProvider(0L, 0L))

        assertThat(appStart.events.map { it.name })
            .doesNotContain(AppStartupTimer.EVENT_APPLICATION_START, AppStartupTimer.EVENT_APPLICATION_END)
    }

    @Test
    fun `no application events when onCreate has not finished`() {
        val appStart = runStartup(fakeProvider(SystemClock.elapsedRealtime() - 10, 0L))

        assertThat(appStart.events.map { it.name })
            .doesNotContain(AppStartupTimer.EVENT_APPLICATION_START, AppStartupTimer.EVENT_APPLICATION_END)
    }

    private fun runStartup(provider: StartupTimestampProvider) =
        AppStartupTimer(timestampProvider = provider).run {
            start(sdk.getTracer("test"), Clock.getDefault())
            end()
            exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        }

    private fun fakeProvider(
        onCreateStartElapsedRealtime: Long,
        onCreateEndElapsedRealtime: Long,
    ): StartupTimestampProvider =
        object : StartupTimestampProvider {
            override val attachBaseContextStartElapsedRealtime = 0L
            override val attachBaseContextEndElapsedRealtime = 0L
            override val applicationOnCreateStartElapsedRealtime = onCreateStartElapsedRealtime
            override val applicationOnCreateEndElapsedRealtime = onCreateEndElapsedRealtime
            override val contentProvidersPhaseStartEpochMs = 0L
            override val contentProviderEpochMs = 0L
        }
}
