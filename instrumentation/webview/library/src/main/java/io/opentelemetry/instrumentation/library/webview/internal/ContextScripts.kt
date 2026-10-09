/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

/**
 * JavaScript run inside the page. The context only ever lives in page memory: nothing is written
 * to cookies or web storage, so a reload or a new launch can never pick up a stale session.
 */
internal object ContextScripts {
    const val BRIDGE_NAME = "VunetBridge"
    const val CONTEXT_EVENT = "vunet:context"

    /** Registered as a document-start script, so it runs before any script of the page. */
    fun documentStart(contextJson: String): String = "(function(){window.__VUNET_CTX__=Object.freeze($contextJson);})();"

    /**
     * Replaces the context of an already loaded page and notifies browser RUM through
     * `window.__VUNET_BRIDGE__.onContext` and a `vunet:context` window event.
     */
    fun push(contextJson: String): String =
        "(function(){var c=Object.freeze($contextJson);window.__VUNET_CTX__=c;" +
            "var b=window.__VUNET_BRIDGE__;" +
            "if(b&&typeof b.onContext==='function'){try{b.onContext(c);}catch(e){}}" +
            "try{window.dispatchEvent(new CustomEvent('$CONTEXT_EVENT',{detail:c}));}catch(e){}" +
            "})();"
}
