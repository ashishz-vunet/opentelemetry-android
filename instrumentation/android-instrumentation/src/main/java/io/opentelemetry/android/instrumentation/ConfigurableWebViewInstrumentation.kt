/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation

/**
 * Optional capability for instrumentation implementations that hand the RUM session to WebView
 * content.
 */
interface ConfigurableWebViewInstrumentation {
    /**
     * Restricts the session context to these hosts, each optionally with a port (`host:port`;
     * without one any port matches). `*.example.com` covers every subdomain of `example.com`. Empty means the origin of every http(s) page
     * the app itself loads into a WebView. A load of any listed host also exposes the context to
     * the other listed hosts, over http and https, so redirects between them keep the session.
     */
    fun setAllowedHosts(hosts: Set<String>)
}
