/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.agent.webview

import android.webkit.WebView
import io.opentelemetry.instrumentation.library.webview.WebViewSubstitutions
import net.bytebuddy.asm.MemberSubstitution
import net.bytebuddy.build.AndroidDescriptor
import net.bytebuddy.build.Plugin
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers
import java.io.IOException

/**
 * Replaces page-load calls on `WebView` (and subclasses) with [WebViewSubstitutions].
 *
 * Only virtual calls are replaced. A subclass override that calls `super.loadUrl(...)` keeps that
 * super call, otherwise the substitution would re-enter the override and recurse.
 */
internal class WebViewPlugin(
    private val androidDescriptor: AndroidDescriptor,
) : Plugin {
    override fun apply(
        builder: DynamicType.Builder<*>,
        typeDescription: TypeDescription,
        classFileLocator: ClassFileLocator,
    ): DynamicType.Builder<*> {
        val string = String::class.java
        return builder.visit(
            MemberSubstitution
                .relaxed()
                .method(webViewMethod("loadUrl", string))
                .onVirtualCall()
                .replaceWith(substitution("loadUrl", WebView::class.java, string))
                .method(webViewMethod("loadUrl", string, Map::class.java))
                .onVirtualCall()
                .replaceWith(substitution("loadUrl", WebView::class.java, string, Map::class.java))
                .method(webViewMethod("postUrl", string, ByteArray::class.java))
                .onVirtualCall()
                .replaceWith(substitution("postUrl", WebView::class.java, string, ByteArray::class.java))
                .method(webViewMethod("loadDataWithBaseURL", string, string, string, string, string))
                .onVirtualCall()
                .replaceWith(
                    substitution(
                        "loadDataWithBaseURL",
                        WebView::class.java,
                        string,
                        string,
                        string,
                        string,
                        string,
                    ),
                ).on(ElementMatchers.any()),
        )
    }

    private fun webViewMethod(
        name: String,
        vararg parameterTypes: Class<*>,
    ): ElementMatcher<MethodDescription> =
        ElementMatchers
            .named<MethodDescription>(name)
            .and(ElementMatchers.takesArguments(*parameterTypes))
            .and(ElementMatchers.isDeclaredBy(ElementMatchers.isSubTypeOf(WebView::class.java)))

    private fun substitution(
        name: String,
        vararg parameterTypes: Class<*>,
    ) = WebViewSubstitutions::class.java.getDeclaredMethod(name, *parameterTypes)

    @Throws(IOException::class)
    override fun close() {
        // No operation.
    }

    override fun matches(target: TypeDescription): Boolean =
        androidDescriptor.getTypeScope(target) != AndroidDescriptor.TypeScope.EXTERNAL &&
            // The substitutions call the real WebView methods; rewriting them would recurse.
            target.name != WebViewSubstitutions::class.java.name
}
