/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.webview.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextScriptsTest {
    @Test
    fun `document start script only sets the context`() {
        assertThat(ContextScripts.documentStart("""{"v":1}"""))
            .isEqualTo("(function(){window.__VUNET_CTX__=Object.freeze({\"v\":1});})();")
    }

    @Test
    fun `push script replaces the context and notifies the page`() {
        val script = ContextScripts.push("""{"v":1}""")

        assertThat(script)
            .contains("window.__VUNET_CTX__=c")
            .contains("b.onContext(c)")
            .contains("new CustomEvent('vunet:context',{detail:c})")
            .doesNotContain("cookie")
            .doesNotContain("localStorage")
    }
}
