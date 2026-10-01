/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.common.interaction.InteractionContext
import io.opentelemetry.android.common.internal.instrumentation.ActiveInteractionContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Documents that a View [android.view.View.OnClickListener] runs after
 * [WindowCallbackWrapper] has already closed the tap's [io.opentelemetry.context.Scope], so
 * [Span.current] is invalid there — and that [InteractionContext.parentForManualSpan] still
 * parents a hand-started span to that tap.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class ManualSpanParentingTest {
    private lateinit var exporter: InMemorySpanExporter
    private lateinit var tracer: Tracer
    private lateinit var generator: ClickEventGenerator

    @Before
    fun setUp() {
        exporter = InMemorySpanExporter.create()
        val sdk =
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(
                    SdkTracerProvider
                        .builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build(),
                ).build()
        tracer = sdk.getTracer("test")
        generator =
            ClickEventGenerator(
                tracer = tracer,
                activeContextWindowMillis = ACTIVE_CONTEXT_WINDOW_MILLIS,
            )
    }

    @After
    fun tearDown() {
        ActiveInteractionContext.clear()
        exporter.reset()
    }

    @Test
    fun `View OnClickListener sees no current span`() {
        var captured: SpanContext? = null
        val host =
            host {
                captured = Span.current().spanContext
            }

        tapThroughWrapper(host)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(captured).isNotNull()
        assertThat(captured!!.isValid).isFalse()
    }

    @Test
    fun `View OnClickListener manual span parents to the tap`() {
        val host =
            host {
                tracer
                    .spanBuilder("manual")
                    .setParent(InteractionContext.parentForManualSpan())
                    .startSpan()
                    .end()
            }

        tapThroughWrapper(host)
        shadowOf(Looper.getMainLooper()).idle()

        val tap =
            exporter.finishedSpanItems.single { it.name == RumConstants.UI_INTERACTION_SPAN_NAME }
        val manual = exporter.finishedSpanItems.single { it.name == "manual" }
        assertThat(manual.parentSpanId).isEqualTo(tap.spanContext.spanId)
        assertThat(manual.traceId).isEqualTo(tap.traceId)
    }

    /**
     * A real Activity window is required: [View.post] on a detached view goes to the ViewRoot
     * run queue (never the main looper), and a mockk [android.view.Window] drops
     * [ClickEventGenerator.startTracking]'s callback wrap.
     */
    private fun host(onClick: () -> Unit): Host {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button =
            Button(activity).apply {
                isClickable = true
                contentDescription = "Pay"
                setOnClickListener { onClick() }
            }
        val root =
            FrameLayout(activity).apply {
                addView(button, FrameLayout.LayoutParams(VIEW_SIZE, VIEW_SIZE))
            }
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
        generator.startTracking(activity.window)
        return Host(activity, button)
    }

    private fun tapThroughWrapper(host: Host) {
        val location = IntArray(2)
        host.button.getLocationInWindow(location)
        val x = location[0] + host.button.width / 2f
        val y = location[1] + host.button.height / 2f
        val callback = requireNotNull(host.activity.window.callback)
        callback.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y))
        callback.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y))
    }

    private fun motion(
        action: Int,
        x: Float,
        y: Float,
    ): MotionEvent = MotionEvent.obtain(0L, 0L, action, x, y, 0)

    private class Host(
        val activity: Activity,
        val button: Button,
    )

    private companion object {
        const val ACTIVE_CONTEXT_WINDOW_MILLIS = 500L
        const val VIEW_SIZE = 500
    }
}
