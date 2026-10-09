/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.hybrid.click

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.opentelemetry.android.HybridClickExclusions
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Taps inside a view registered in [HybridClickExclusions] (e.g. an instrumented WebView whose web
 * SDK records the real element) must not produce a native `ui.interaction`; taps elsewhere still do.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class ClickEventGeneratorExclusionsTest {
    private lateinit var context: Context
    private lateinit var exporter: InMemorySpanExporter
    private lateinit var generator: ClickEventGenerator
    private lateinit var window: Window
    private lateinit var excluded: Button

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exporter = InMemorySpanExporter.create()
        val sdk =
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(
                    SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build(),
                ).build()
        generator = ClickEventGenerator(tracer = sdk.getTracer("test"), activeContextWindowMillis = 0)

        // Left half: a normal button. Right half: the excluded view (stands in for a WebView).
        val normal = button("Native")
        excluded = button("Web")
        val root =
            FrameLayout(context).apply {
                addView(normal, FrameLayout.LayoutParams(HALF, SIZE))
                addView(excluded, FrameLayout.LayoutParams(HALF, SIZE).apply { leftMargin = HALF })
            }
        // A real, attached activity: hit-testing and getLocationInWindow need an attached view tree.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(root, FrameLayout.LayoutParams(SIZE, SIZE))
        shadowOf(Looper.getMainLooper()).idle()
        window = activity.window
        generator.startTracking(window)
    }

    @After
    fun tearDown() {
        HybridClickExclusions.include(excluded)
    }

    @Test
    fun `tap inside an excluded view emits nothing`() {
        HybridClickExclusions.exclude(excluded)

        tap(HALF + 10f, 10f)

        assertThat(finishedWidgetNames()).isEmpty()
    }

    @Test
    fun `tap outside the excluded view is still recorded`() {
        HybridClickExclusions.exclude(excluded)

        tap(10f, 10f)

        assertThat(finishedWidgetNames()).containsExactly("Native")
    }

    @Test
    fun `including the view again restores recording`() {
        HybridClickExclusions.exclude(excluded)
        HybridClickExclusions.include(excluded)

        tap(HALF + 10f, 10f)

        assertThat(finishedWidgetNames()).containsExactly("Web")
    }

    @Test
    fun `hidden excluded view does not swallow taps`() {
        HybridClickExclusions.exclude(excluded)
        excluded.visibility = View.GONE

        tap(HALF + 10f, 10f)

        // Nothing is under the point once the view is gone, but the exclusion must not be what drops it.
        val (wx, wy) = inWindow(HALF + 10f, 10f)
        assertThat(HybridClickExclusions.contains(window.decorView, wx, wy)).isFalse()
    }

    private fun button(label: String) =
        Button(context).apply {
            isClickable = true
            contentDescription = label
        }

    /** Taps at ([x], [y]) relative to the content root, converted to window coordinates. */
    private fun tap(
        x: Float,
        y: Float,
    ) {
        val (wx, wy) = inWindow(x, y)
        generator.generateClick(window, MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, wx, wy, 0))
        generator.generateClick(window, MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, wx, wy, 0))
    }

    private fun inWindow(
        x: Float,
        y: Float,
    ): Pair<Float, Float> {
        val loc = IntArray(2)
        (excluded.parent as View).getLocationInWindow(loc)
        return (loc[0] + x) to (loc[1] + y)
    }

    private fun finishedWidgetNames(): List<String?> {
        shadowOf(Looper.getMainLooper()).idle()
        return exporter.finishedSpanItems.map { it.attributes.get(AppIncubatingAttributes.APP_WIDGET_NAME) }
    }

    private companion object {
        const val SIZE = 400
        const val HALF = 200
    }
}
