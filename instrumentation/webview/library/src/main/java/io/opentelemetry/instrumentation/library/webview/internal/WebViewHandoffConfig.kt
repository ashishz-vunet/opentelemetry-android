/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

/**
 * @param allowedHosts exact hosts that receive the session context. Empty means the origin of
 * every http(s) page the app loads. When non-empty, a load of any listed host also exposes the
 * context to the other listed hosts, so redirects between them (e.g. an OAuth round-trip) keep the
 * session.
 */
internal class WebViewHandoffConfig(
    val allowedHosts: Set<String> = emptySet(),
)
