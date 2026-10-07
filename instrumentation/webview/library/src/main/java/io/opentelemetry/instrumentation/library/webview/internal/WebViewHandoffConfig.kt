/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

/**
 * @param allowedHosts exact hosts, optionally with a port (`host` or `host:port`), that receive the
 * session context. Empty means the origin of every http(s) page the app loads. When non-empty, a
 * load of any listed host also exposes the context to the other listed hosts, over http and https,
 * so redirects between them (e.g. an OAuth round-trip) keep the session. A host without a port
 * matches any port.
 */
internal class WebViewHandoffConfig(
    allowedHosts: Set<String> = emptySet(),
) {
    val allowedHosts: List<AllowedHost> = allowedHosts.mapNotNull { AllowedHost.parse(it) }
}

internal class AllowedHost(
    val host: String,
    val port: Int?,
) {
    fun matches(origin: WebViewContextBridge.WebOrigin): Boolean =
        host == origin.host && (port == null || port == origin.effectivePort)

    /** Both schemes: a journey may switch between them, e.g. an http page redirecting to https. */
    fun rules(): List<String> = SCHEMES.map { WebViewContextBridge.WebOrigin(it, host, port ?: -1).rule }

    companion object {
        private val SCHEMES = listOf("http", "https")
        private const val MAX_PORT = 65535

        fun parse(entry: String): AllowedHost? {
            val value = entry.trim().lowercase()
            val host = value.substringBefore(':')
            if (host.isEmpty() || host.any { it == '/' || it.isWhitespace() }) {
                return null
            }
            if (!value.contains(':')) {
                return AllowedHost(host, null)
            }
            val port = value.substringAfter(':').toIntOrNull() ?: return null
            return if (port in 1..MAX_PORT) AllowedHost(host, port) else null
        }
    }
}
