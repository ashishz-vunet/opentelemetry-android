/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import android.content.Context
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.FakeClock
import io.opentelemetry.android.agent.FakeInstrumentationLoader
import io.opentelemetry.android.agent.dsl.OpenTelemetryConfiguration
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.ConfigurableSystemMetricsInstrumentation
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

internal class SystemMetricsConfigurationTest {
    @Test
    fun `collectionIntervalSeconds sets value when instrumentation is present`() {
        val instrumentation = FakeSystemMetricsInstrumentation()
        val loader = FakeInstrumentationLoader().apply { register(instrumentation) }
        val configuration = OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = loader)

        configuration.instrumentations {
            systemMetrics {
                collectionIntervalSeconds(60L)
            }
        }

        assertEquals(60L, instrumentation.collectionIntervalSeconds)
    }

    @Test
    fun `collectionIntervalSeconds does nothing when instrumentation is absent`() {
        val configuration = OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())

        configuration.instrumentations {
            systemMetrics {
                collectionIntervalSeconds(60L)
            }
        }
    }

    @Test
    fun `collectionIntervalSeconds rejects non-positive values`() {
        val configuration = OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())

        assertThrows(IllegalArgumentException::class.java) {
            configuration.instrumentations {
                systemMetrics {
                    collectionIntervalSeconds(0L)
                }
            }
        }
    }

    @Test
    fun `enabled false suppresses system metrics`() {
        val configuration = OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())

        configuration.instrumentations {
            systemMetrics {
                enabled(false)
            }
        }

        assertTrue(configuration.rumConfig.isSuppressed("system-metrics"))
    }

    @Test
    fun `enabled true allows system metrics`() {
        val configuration = OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())
        configuration.rumConfig.suppressInstrumentation("system-metrics")

        configuration.instrumentations {
            systemMetrics {
                enabled(true)
            }
        }

        assertFalse(configuration.rumConfig.isSuppressed("system-metrics"))
    }
}

private class FakeSystemMetricsInstrumentation : AndroidInstrumentation, ConfigurableSystemMetricsInstrumentation {
    var collectionIntervalSeconds: Long? = null

    override val name: String = "system-metrics"

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {}

    override fun setCollectionIntervalSeconds(value: Long) {
        collectionIntervalSeconds = value
    }
}
