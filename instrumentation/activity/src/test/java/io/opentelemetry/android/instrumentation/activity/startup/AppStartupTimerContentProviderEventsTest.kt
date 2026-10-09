/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity.startup

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
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class AppStartupTimerContentProviderEventsTest {
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
    fun `app_content_providers_start event is emitted with correct timestamp`() {
        val cpStartMs = System.currentTimeMillis() - 500
        val provider = fakeProvider(contentProvidersPhaseStartEpochMs = cpStartMs)

        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val event =
            appStart.events.firstOrNull { it.name == AppStartupTimer.EVENT_CONTENT_PROVIDERS_START }

        assertThat(event).isNotNull()
        assertThat(event!!.epochNanos)
            .isEqualTo(TimeUnit.MILLISECONDS.toNanos(cpStartMs))
    }

    @Test
    fun `content provider start and end events are emitted when both timestamps are set`() {
        val cpStartMs = System.currentTimeMillis() - 500
        val cpEndMs = System.currentTimeMillis() - 300
        val provider =
            fakeProvider(
                contentProvidersPhaseStartEpochMs = cpStartMs,
                contentProviderEpochMs = cpEndMs,
            )

        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val startEvent = appStart.events.first { it.name == AppStartupTimer.EVENT_CONTENT_PROVIDERS_START }
        val endEvent = appStart.events.first { it.name == AppStartupTimer.EVENT_CONTENT_PROVIDERS_END }

        assertThat(startEvent.epochNanos).isEqualTo(TimeUnit.MILLISECONDS.toNanos(cpStartMs))
        assertThat(endEvent.epochNanos).isEqualTo(TimeUnit.MILLISECONDS.toNanos(cpEndMs))
        assertThat(endEvent.epochNanos).isGreaterThanOrEqualTo(startEvent.epochNanos)
    }

    @Test
    fun `app_content_providers_end event is emitted with correct timestamp`() {
        val contentProviderMs = System.currentTimeMillis() - 300
        val provider = fakeProvider(contentProviderEpochMs = contentProviderMs)

        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val event = appStart.events.firstOrNull { it.name == AppStartupTimer.EVENT_CONTENT_PROVIDERS_END }

        assertThat(event).isNotNull()
        assertThat(event!!.epochNanos)
            .isEqualTo(TimeUnit.MILLISECONDS.toNanos(contentProviderMs))
    }

    @Test
    fun `no content provider events are emitted when timestamps are zero`() {
        val provider =
            fakeProvider(contentProvidersPhaseStartEpochMs = 0L, contentProviderEpochMs = 0L)

        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())
        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val eventNames = appStart.events.map { it.name }

        assertThat(eventNames).doesNotContain(AppStartupTimer.EVENT_CONTENT_PROVIDERS_START)
        assertThat(eventNames).doesNotContain(AppStartupTimer.EVENT_CONTENT_PROVIDERS_END)
    }

    @Test
    fun `content provider end events are emitted even if timestamp is set after start`() {
        val cpStartMs = System.currentTimeMillis() - 500
        val cpEndMs = System.currentTimeMillis() - 300

        var lateEndMs = 0L
        val provider =
            object : StartupTimestampProvider {
                override val attachBaseContextStartElapsedRealtime = 0L
                override val attachBaseContextEndElapsedRealtime = 0L
                override val contentProvidersPhaseStartEpochMs = cpStartMs
                override val contentProviderEpochMs: Long
                    get() = lateEndMs
            }

        val timer = AppStartupTimer(timestampProvider = provider)
        timer.start(sdk.getTracer("test"), io.opentelemetry.sdk.common.Clock.getDefault())

        // Simulate EarlyStartupContentProvider setting the timestamp after SDK start().
        lateEndMs = cpEndMs

        timer.end()

        val appStart = exporter.finishedSpanItems.single { it.name == RumConstants.APP_START_SPAN_NAME }
        val startEvent = appStart.events.first { it.name == AppStartupTimer.EVENT_CONTENT_PROVIDERS_START }
        val endEvent = appStart.events.first { it.name == AppStartupTimer.EVENT_CONTENT_PROVIDERS_END }

        assertThat(startEvent.epochNanos).isEqualTo(TimeUnit.MILLISECONDS.toNanos(cpStartMs))
        assertThat(endEvent.epochNanos).isEqualTo(TimeUnit.MILLISECONDS.toNanos(cpEndMs))
        assertThat(appStart.events.map { it.name })
            .doesNotContain("app.init.contentprovider")
            .doesNotContain("applicationPreCreated")
    }

    private fun fakeProvider(
        attachBaseContextStartElapsedRealtime: Long = 0L,
        attachBaseContextEndElapsedRealtime: Long = 0L,
        contentProvidersPhaseStartEpochMs: Long = 0L,
        contentProviderEpochMs: Long = 0L,
    ): StartupTimestampProvider =
        object : StartupTimestampProvider {
            override val attachBaseContextStartElapsedRealtime = attachBaseContextStartElapsedRealtime
            override val attachBaseContextEndElapsedRealtime = attachBaseContextEndElapsedRealtime
            override val contentProvidersPhaseStartEpochMs = contentProvidersPhaseStartEpochMs
            override val contentProviderEpochMs = contentProviderEpochMs
        }
}
