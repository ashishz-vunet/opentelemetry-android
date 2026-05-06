/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.common.RumConstants.LAST_SCREEN_NAME_KEY
import io.opentelemetry.android.common.RumConstants.SCREEN_NAME_KEY
import io.opentelemetry.android.instrumentation.common.ScreenNameExtractor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ViewNavigationCollectorTest {
    @MockK
    private lateinit var fragmentActivity: FragmentActivity

    @MockK
    private lateinit var fragmentManager: FragmentManager

    @MockK
    private lateinit var activity: Activity

    @MockK
    private lateinit var application: Application

    private val exporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val tracerProvider: SdkTracerProvider =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
    private val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
    private val testClock = object : Clock {
        override fun now(): Long = 1234L

        override fun nanoTime(): Long = 1234L
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
    }

    @Test
    fun emits_activity_to_activity_transition_with_previous_screen() {
        val first = mockk<Activity>(relaxed = true)
        val second = mockk<Activity>(relaxed = true)
        every { first.intent } returns mainIntent()
        every { second.intent } returns mainIntent()

        val collector = createCollector(nameMap = mapOf(first to "HomeActivity", second to "DetailsActivity"))

        collector.onActivityResumed(first)
        collector.onActivityResumed(second)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(2)
        assertThat(spans[1].attributes.get(SCREEN_NAME_KEY)).isEqualTo("DetailsActivity")
        assertThat(spans[1].attributes.get(LAST_SCREEN_NAME_KEY)).isEqualTo("HomeActivity")
        assertThat(spans[1].attributes.get(ViewNavigationConstants.NAVIGATION_ACTION_KEY)).isEqualTo("push")
    }

    @Test
    fun emits_fragment_replace_transition() {
        every { fragmentActivity.supportFragmentManager } returns fragmentManager
        every { fragmentManager.backStackEntryCount } returns 0

        val firstFragment = mockFragment("HomeFragment")
        val secondFragment = mockFragment("DetailsFragment")
        val hostActivity = fragmentActivity
        every { hostActivity.intent } returns mainIntent()

        val collector =
            createCollector(
                nameMap =
                    mapOf(
                        hostActivity to "HostActivity",
                        firstFragment to "HomeFragment",
                        secondFragment to "DetailsFragment",
                    ),
            )

        collector.onActivityCreated(hostActivity, null)
        collector.onActivityResumed(hostActivity)
        val lifecycleSlot = slot<FragmentManager.FragmentLifecycleCallbacks>()
        verify { fragmentManager.registerFragmentLifecycleCallbacks(capture(lifecycleSlot), true) }

        lifecycleSlot.captured.onFragmentResumed(fragmentManager, firstFragment)
        lifecycleSlot.captured.onFragmentResumed(fragmentManager, secondFragment)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(3)
        assertThat(spans[2].attributes.get(ViewNavigationConstants.NAVIGATION_ACTION_KEY)).isEqualTo("replace")
        assertThat(spans[2].attributes.get(SCREEN_NAME_KEY)).isEqualTo("DetailsFragment")
        assertThat(spans[2].attributes.get(LAST_SCREEN_NAME_KEY)).isEqualTo("HomeFragment")
    }

    @Test
    fun instrumentation_install_is_idempotent() {
        val instrumentation = ViewNavigationInstrumentation()
        val openTelemetryRum = mockk<OpenTelemetryRum> {
            every { openTelemetry } returns this@ViewNavigationCollectorTest.openTelemetry
            every { clock } returns testClock
        }

        instrumentation.install(application, openTelemetryRum)
        instrumentation.install(application, openTelemetryRum)
        instrumentation.uninstall(application, openTelemetryRum)

        verify(exactly = 1) { application.registerActivityLifecycleCallbacks(any()) }
        verify(exactly = 1) { application.unregisterActivityLifecycleCallbacks(any()) }
    }

    private fun createCollector(nameMap: Map<Any, String>): ViewNavigationCollector {
        val screenNameExtractor = ScreenNameExtractor { instance -> nameMap[instance] ?: instance::class.java.simpleName }
        val emitter = ViewNavigationSpanEmitter(openTelemetry.getTracer("test-navigation-view"))
        return ViewNavigationCollector(emitter, testClock, screenNameExtractor)
    }

    private fun mockFragment(screenName: String): Fragment {
        val fragment = mockk<Fragment>(relaxed = true)
        every { fragment.isVisible } returns true
        every { fragment.parentFragment } returns null
        every { fragment.toString() } returns screenName
        return fragment
    }

    private fun mainIntent(): Intent =
        mockk {
            every { data } returns null
            every { action } returns Intent.ACTION_MAIN
        }
}
