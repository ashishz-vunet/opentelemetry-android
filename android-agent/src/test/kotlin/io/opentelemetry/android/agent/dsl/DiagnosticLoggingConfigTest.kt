/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl

import io.opentelemetry.android.Incubating
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.android.common.RumDiagnostics
import io.opentelemetry.android.agent.FakeInstrumentationLoader
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(Incubating::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticLoggingConfigTest {
    @After
    fun tearDown() {
        RumDiagnostics.verbose = false
    }

    @Test
    fun diagnosticLogging_defaultsToFalse() {
        val cfg = OpenTelemetryConfiguration(instrumentationLoader = FakeInstrumentationLoader())
        assertFalse(cfg.diagnosticLogging)
    }

    @Test
    fun diagnosticLogging_enablesRumDiagnosticsDuringInitialize() {
        val rum =
            OpenTelemetryRumInitializer.initialize(
                context = RuntimeEnvironment.getApplication(),
                configuration = {
                    diagnosticLogging = true
                    httpExport {
                        baseUrl = "http://127.0.0.1:4318"
                    }
                },
            )
        try {
            assertTrue(RumDiagnostics.verbose)
        } finally {
            rum.shutdown()
            RumDiagnostics.verbose = false
        }
    }
}
