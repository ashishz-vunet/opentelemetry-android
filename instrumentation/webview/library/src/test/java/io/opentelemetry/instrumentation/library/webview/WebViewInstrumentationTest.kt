/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview

import android.app.Application
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.library.webview.internal.FakeBridgeApi
import io.opentelemetry.sdk.common.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewInstrumentationTest {
    private val sessionId = "0123456789abcdef0123456789abcdef"
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val api = FakeBridgeApi()
    private val instrumentation = WebViewInstrumentation(api)
    private val events = mutableListOf<String>()
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        webView = WebView(application)
    }

    @After
    fun tearDown() {
        instrumentation.uninstall(application, rum)
    }

    @Test
    fun `configured hosts are matched case-insensitively and others are skipped`() {
        instrumentation.setAllowedHosts(setOf("Bank.Example.com"))
        instrumentation.install(application, rum)

        WebViewSubstitutions.loadUrl(webView, "https://ads.other.com/")
        assertThat(api.scripts).isEmpty()

        WebViewSubstitutions.loadUrl(webView, "https://bank.example.com/")
        assertThat(api.scripts.single().rules).containsExactly("https://bank.example.com")
        assertThat(api.scripts.single().code).contains(sessionId).contains(application.packageName)
        assertThat(events).containsExactly("webview.opened")
    }

    @Test
    fun `uninstall stops attaching`() {
        instrumentation.install(application, rum)
        instrumentation.uninstall(application, rum)

        WebViewSubstitutions.loadUrl(webView, "https://bank.example.com/")

        assertThat(api.scripts).isEmpty()
        assertThat(events).isEmpty()
    }

    private val rum =
        object : OpenTelemetryRum {
            override val openTelemetry: OpenTelemetry = OpenTelemetry.noop()
            override val sessionProvider: SessionProvider = SessionProvider { sessionId }
            override val clock: Clock = Clock.getDefault()

            override fun emitEvent(
                eventName: String,
                body: String,
                attributes: Attributes,
            ) {
                events.add(eventName)
            }

            override fun shutdown() {
                // Unused.
            }
        }
}
