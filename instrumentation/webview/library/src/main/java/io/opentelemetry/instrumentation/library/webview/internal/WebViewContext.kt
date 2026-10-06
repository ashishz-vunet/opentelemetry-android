/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import org.json.JSONObject

/**
 * The context handed to browser RUM as `window.__VUNET_CTX__`. Version 1 of the contract:
 *
 * ```
 * {
 *   "v": 1,
 *   "sessionId": "<32 hex>",
 *   "sessionStartTs": <epoch millis>,
 *   "sampled": true,
 *   "app": { "id": "<package>", "version": "<versionName>" },
 *   "device": { "manufacturer": "...", "model": "...", "os": "Android", "osVersion": "14" },
 *   "host": { "platform": "android", "webviewId": "<uuid>", "parentViewName": "<screen>" },
 *   "traceparent": "00-<trace id>-<span id>-<flags>"
 * }
 * ```
 *
 * `traceparent` is the native `webview.load` span of the page load that was just requested, so
 * browser RUM can make that page's `documentLoad` its child. It is only present until browser RUM
 * reports `brumReady`, so later navigations and reloads do not join an old trace.
 *
 * Fields are only ever added within a version; renaming or removing one requires a new `v`.
 */
internal class WebViewContext(
    val sessionId: String,
    val sessionStartMillis: Long,
    val sampled: Boolean,
    val app: AppInfo,
    val device: DeviceInfo,
    val webViewId: String,
    val parentViewName: String?,
    val traceparent: String? = null,
) {
    fun toJson(): String =
        JSONObject()
            .put("v", VERSION)
            .put("sessionId", sessionId)
            .put("sessionStartTs", sessionStartMillis)
            .put("sampled", sampled)
            .put(
                "app",
                JSONObject()
                    .put("id", app.id)
                    .putOpt("version", app.version),
            ).put(
                "device",
                JSONObject()
                    .put("manufacturer", device.manufacturer)
                    .put("model", device.model)
                    .put("os", "Android")
                    .put("osVersion", device.osVersion),
            ).put(
                "host",
                JSONObject()
                    .put("platform", "android")
                    .put("webviewId", webViewId)
                    .putOpt("parentViewName", parentViewName),
            ).putOpt("traceparent", traceparent)
            .toString()

    class AppInfo(
        val id: String,
        val version: String?,
    )

    class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val osVersion: String,
    )

    companion object {
        const val VERSION = 1
    }
}
