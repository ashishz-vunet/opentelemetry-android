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
import io.opentelemetry.android.instrumentation.ConfigurableWebViewInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class WebViewConfigurationTest {
    @Test
    fun `allowedHosts reach the instrumentation`() {
        val instrumentation = FakeWebViewInstrumentation()
        val loader = FakeInstrumentationLoader().apply { register(instrumentation) }
        val configuration = OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = loader)

        configuration.instrumentations {
            webView {
                allowedHosts("id.example.com", "oauth.example.com:8443")
            }
        }

        assertThat(instrumentation.receivedHosts).containsExactlyInAnyOrder("id.example.com", "oauth.example.com:8443")
    }

    @Test
    fun `configuring does nothing when the instrumentation is absent`() {
        val configuration =
            OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())

        configuration.instrumentations {
            webView {
                allowedHosts("bank.example.com")
            }
        }
    }

    @Test
    fun `allowedHosts rejects urls, paths, bad ports and blanks`() {
        val configuration =
            OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())

        listOf(
            "https://bank.example.com",
            "bank.example.com/login",
            "bank.example.com:",
            "bank.example.com:0",
            "bank.example.com:70000",
            "bank.example.com:https",
            " ",
        ).forEach { host ->
            assertThatThrownBy {
                configuration.instrumentations { webView { allowedHosts(host) } }
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `enabled toggles suppression of the webview instrumentation`() {
        val configuration =
            OpenTelemetryConfiguration(clock = FakeClock(), instrumentationLoader = FakeInstrumentationLoader())

        configuration.instrumentations { webView { enabled(false) } }
        assertThat(configuration.rumConfig.isSuppressed("webview")).isTrue()

        configuration.instrumentations { webView { enabled(true) } }
        assertThat(configuration.rumConfig.isSuppressed("webview")).isFalse()
    }
}

private class FakeWebViewInstrumentation :
    AndroidInstrumentation,
    ConfigurableWebViewInstrumentation {
    var receivedHosts: Set<String> = emptySet()

    override val name: String = "webview"

    override fun install(
        context: Context,
        openTelemetryRum: OpenTelemetryRum,
    ) {}

    override fun setAllowedHosts(hosts: Set<String>) {
        receivedHosts = hosts
    }
}
