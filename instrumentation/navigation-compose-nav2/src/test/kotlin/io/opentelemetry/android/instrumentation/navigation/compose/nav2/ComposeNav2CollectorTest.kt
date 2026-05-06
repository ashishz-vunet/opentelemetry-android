/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav2

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.instrumentation.navigation.common.NavigationConstants
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ComposeNav2CollectorTest {
    private val exporter: InMemorySpanExporter = InMemorySpanExporter.create()
    private val tracerProvider: SdkTracerProvider =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
    private val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
    private val testClock =
        object : Clock {
            override fun now(): Long = 1234L

            override fun nanoTime(): Long = 1234L
        }
    private val navController = mockk<NavController>(relaxed = true)

    @BeforeEach
    fun setUp() {
        exporter.reset()
    }

    @Test
    fun compose_route_push_emits_span() {
        val collector = createCollector(backStackSizes = listOf(1))
        collector.onDestinationChanged(navController, destination("home"), null)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(1)
        assertThat(spans[0].attributes.get(NavigationConstants.NAVIGATION_DESTINATION_TYPE_KEY)).isEqualTo("compose_route")
        assertThat(spans[0].attributes.get(NavigationConstants.NAVIGATION_TRANSITION_TYPE_KEY)).isEqualTo("push")
    }

    @Test
    fun compose_route_pop_emits_span_when_backstack_shrinks() {
        val collector = createCollector(backStackSizes = listOf(2, 1))
        collector.onDestinationChanged(navController, destination("details/{id}", id = 2), null)
        collector.onDestinationChanged(navController, destination("home", id = 1), null)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(2)
        assertThat(spans[1].attributes.get(NavigationConstants.NAVIGATION_TRANSITION_TYPE_KEY)).isEqualTo("pop")
    }

    @Test
    fun compose_route_replace_emits_span_when_count_unchanged() {
        val collector = createCollector(backStackSizes = listOf(2, 2))
        collector.onDestinationChanged(navController, destination("home"), null)
        collector.onDestinationChanged(navController, destination("settings"), null)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(2)
        assertThat(spans[1].attributes.get(NavigationConstants.NAVIGATION_TRANSITION_TYPE_KEY)).isEqualTo("replace")
    }

    @Test
    fun dialog_destination_is_filtered() {
        val collector =
            createCollector(
                backStackSizes = listOf(1, 2),
                destinationFilter = { it.route == "dialog" },
            )
        collector.onDestinationChanged(navController, destination("home"), null)
        collector.onDestinationChanged(navController, destination("dialog"), null)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(1)
    }

    @Test
    fun multiple_navcontrollers_are_independent() {
        val first = createCollector(backStackSizes = listOf(1, 2))
        val second = createCollector(backStackSizes = listOf(1, 2))
        first.onDestinationChanged(navController, destination("home"), null)
        second.onDestinationChanged(navController, destination("feed"), null)

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(2)
        assertThat(spans[0].attributes.get(NavigationConstants.NAVIGATION_DESTINATION_NAME_KEY)).isEqualTo("home")
        assertThat(spans[1].attributes.get(NavigationConstants.NAVIGATION_DESTINATION_NAME_KEY)).isEqualTo("feed")
    }

    @Test
    fun install_then_uninstall_clears_holder() {
        val instrumentation = ComposeNav2Instrumentation()
        val rum = rum()
        instrumentation.install(mockk(relaxed = true), rum)
        assertThat(NavObserverRumHolder.current()).isNotNull()
        instrumentation.uninstall(mockk(relaxed = true), rum)
        assertThat(NavObserverRumHolder.current()).isNull()
    }

    @Test
    fun composable_is_noop_when_rum_holder_empty() {
        NavObserverRumHolder.clear()
        assertThat(NavObserverRumHolder.current()).isNull()
    }

    private fun destination(route: String, id: Int = 1): NavDestination =
        mockk {
            every { this@mockk.route } returns route
            every { this@mockk.id } returns id
            every { this@mockk.displayName } returns route
        }

    private fun createCollector(
        backStackSizes: List<Int>,
        destinationFilter: (NavDestination) -> Boolean = { false },
    ): ComposeNav2Collector {
        var i = 0
        return ComposeNav2Collector(
            openTelemetryRum = rum(),
            destinationFilter = destinationFilter,
            backStackSizeProvider = { backStackSizes[(i++).coerceAtMost(backStackSizes.lastIndex)] },
        )
    }

    private fun rum(): OpenTelemetryRum =
        mockk {
            every { openTelemetry } returns this@ComposeNav2CollectorTest.openTelemetry
            every { clock } returns testClock
        }
}
