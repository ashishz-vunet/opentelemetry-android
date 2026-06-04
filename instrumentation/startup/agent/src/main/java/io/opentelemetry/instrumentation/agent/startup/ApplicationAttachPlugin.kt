/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.agent.startup

import android.app.Application
import android.content.Context
import io.opentelemetry.android.instrumentation.startup.ApplicationAttachAdvice
import java.io.IOException
import net.bytebuddy.asm.Advice
import net.bytebuddy.build.Plugin
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers

internal class ApplicationAttachPlugin : Plugin {

    override fun apply(
        builder: DynamicType.Builder<*>,
        typeDescription: TypeDescription,
        classFileLocator: ClassFileLocator,
    ): DynamicType.Builder<*> {
        val attachBaseContextMatcher = attachBaseContextMatcher()
        if (!declaresAttachBaseContext(typeDescription, attachBaseContextMatcher)) {
            return builder
        }
        return builder.visit(Advice.to(ApplicationAttachAdvice::class.java).on(attachBaseContextMatcher))
    }

    @Throws(IOException::class)
    override fun close() {
        // No operation.
    }

    override fun matches(target: TypeDescription): Boolean {
        if (target.isAbstract || target.isInterface) {
            return false
        }
        if (target.name == Application::class.java.name) {
            return false
        }
        var current: TypeDescription? = target
        while (current != null) {
            if (current.name == Application::class.java.name) {
                return true
            }
            if (current.name == "java.lang.Object") {
                break
            }
            current =
                try {
                    current.superClass?.asErasure()
                } catch (_: Exception) {
                    return false
                }
        }
        return false
    }

    private fun attachBaseContextMatcher(): ElementMatcher<MethodDescription> =
        ElementMatchers
            .named<NamedElement>("attachBaseContext")
            .and(ElementMatchers.takesArgument(0, Context::class.java))

    private fun declaresAttachBaseContext(
        typeDescription: TypeDescription,
        attachBaseContextMatcher: ElementMatcher<MethodDescription>,
    ): Boolean =
        typeDescription.declaredMethods.any { attachBaseContextMatcher.matches(it) }
}
