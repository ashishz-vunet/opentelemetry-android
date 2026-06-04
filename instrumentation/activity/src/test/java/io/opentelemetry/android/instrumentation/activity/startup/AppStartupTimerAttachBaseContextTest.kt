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
class AppStartupTimerAttachBaseContextTest {
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
    fun `attach base context start and end events are emitted on app start`() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val startElapsed = nowElapsed - 50
        val endElapsed = nowElapsed - 10
        val provider =
            fakeProvider(
                attachBaseContextStartElapsedRealtime = startElapsed,
                attachBaseContextEndElapsedRealtime = endElapsed,
            )

        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val startEvent =
            appStart.events.firstOrNull { it.name == AppStartupTimer.EVENT_ATTACH_BASE_CONTEXT_START }
        val endEvent =
            appStart.events.firstOrNull { it.name == AppStartupTimer.EVENT_ATTACH_BASE_CONTEXT_END }

        assertThat(startEvent).isNotNull()
        assertThat(endEvent).isNotNull()
        assertThat(endEvent!!.epochNanos).isGreaterThanOrEqualTo(startEvent!!.epochNanos)
        assertThat(exporter.finishedSpanItems).hasSize(1)
    }

    @Test
    fun `no attach events when timestamps are missing`() {
        val timer = AppStartupTimer(timestampProvider = fakeProvider())
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val eventNames = appStart.events.map { it.name }
        assertThat(eventNames)
            .doesNotContain(
                AppStartupTimer.EVENT_ATTACH_BASE_CONTEXT_START,
                AppStartupTimer.EVENT_ATTACH_BASE_CONTEXT_END,
            )
    }

    @Test
    fun `no attach events when end precedes start`() {
        val provider =
            fakeProvider(
                attachBaseContextStartElapsedRealtime = 100L,
                attachBaseContextEndElapsedRealtime = 50L,
            )
        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val eventNames = appStart.events.map { it.name }
        assertThat(eventNames)
            .doesNotContain(
                AppStartupTimer.EVENT_ATTACH_BASE_CONTEXT_START,
                AppStartupTimer.EVENT_ATTACH_BASE_CONTEXT_END,
            )
    }

    private fun fakeProvider(
        attachBaseContextStartElapsedRealtime: Long = 0L,
        attachBaseContextEndElapsedRealtime: Long = 0L,
    ): StartupTimestampProvider =
        object : StartupTimestampProvider {
            override val attachBaseContextStartElapsedRealtime = attachBaseContextStartElapsedRealtime
            override val attachBaseContextEndElapsedRealtime = attachBaseContextEndElapsedRealtime
            override val contentProvidersPhaseStartEpochMs: Long = 0L
            override val contentProviderEpochMs: Long = 0L
        }
}
