/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.ConfigurableWebViewInstrumentation

/**
 * Type-safe config DSL that controls how the RUM session is handed to WebView content.
 *
 * Only takes effect when the `webview-library` artifact is on the classpath and the
 * `webview-agent` Byte Buddy plugin is applied.
 */
@OpenTelemetryDslMarker
class WebViewConfiguration internal constructor(
    private val config: OtelRumConfig,
    instrumentationLoader: AndroidInstrumentationLoader,
) : CanBeEnabledAndDisabled {
    private val webViewInstrumentation: ConfigurableWebViewInstrumentation? by lazy {
        instrumentationLoader
            .getAll()
            .firstOrNull { it.name == WEBVIEW_INSTRUMENTATION_NAME }
            ?.let { it as? ConfigurableWebViewInstrumentation }
    }

    /**
     * Restricts the session context to these exact hosts, e.g. `"bank.example.com"`. By default
     * the origin of every http(s) page the app loads into a WebView receives it. List every host a
     * journey redirects through (such as an OAuth host) so the session survives the redirect.
     */
    fun allowedHosts(vararg hosts: String) {
        hosts.forEach { host ->
            require(host.isNotBlank() && host.none { it == '/' || it == ':' || it.isWhitespace() }) {
                "allowedHosts takes bare hosts such as \"bank.example.com\", but was \"$host\"."
            }
        }
        webViewInstrumentation?.setAllowedHosts(hosts.toSet())
    }

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(WEBVIEW_INSTRUMENTATION_NAME)
        } else {
            config.suppressInstrumentation(WEBVIEW_INSTRUMENTATION_NAME)
        }
    }

    private companion object {
        private const val WEBVIEW_INSTRUMENTATION_NAME = "webview"
    }
}
