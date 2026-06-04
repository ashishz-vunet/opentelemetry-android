/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.agent.startup

import android.app.Application
import android.content.Context
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ApplicationAttachPluginTest {
    private val plugin = ApplicationAttachPlugin()

    @Test
    fun `matches concrete Application subclasses`() {
        assertThat(plugin.matches(TypeDescription.ForLoadedType.of(TestApplication::class.java))).isTrue()
    }

    @Test
    fun `does not match abstract Application type`() {
        assertThat(plugin.matches(TypeDescription.ForLoadedType.of(Application::class.java))).isFalse()
    }

    @Test
    fun `skips transform when attachBaseContext is not declared`() {
        val typeDescription = TypeDescription.ForLoadedType.of(TestApplication::class.java)
        assertThat(typeDescription.declaredMethods.map { it.name }).doesNotContain("attachBaseContext")

        val classLoader = TestApplication::class.java.classLoader
        val locator = ClassFileLocator.ForClassLoader.of(classLoader)
        val bytecode =
            plugin
                .apply(
                    ByteBuddy().redefine(TestApplication::class.java),
                    typeDescription,
                    locator,
                )
                .make()
                .allTypes
                .values
                .single()
        assertThat(String(bytecode)).doesNotContain("ProcessStartTimestamps")
    }

    @Test
    fun `advises declared attachBaseContext override`() {
        val typeDescription = TypeDescription.ForLoadedType.of(TestApplicationWithAttach::class.java)
        val classLoader = TestApplicationWithAttach::class.java.classLoader
        val locator = ClassFileLocator.ForClassLoader.of(classLoader)
        val bytecode =
            plugin
                .apply(
                    ByteBuddy().redefine(TestApplicationWithAttach::class.java),
                    typeDescription,
                    locator,
                )
                .make()
                .allTypes
                .values
                .single()
        assertThat(String(bytecode)).contains("ProcessStartTimestamps")
    }

    private class TestApplication : Application()

    private class TestApplicationWithAttach : Application() {
        override fun attachBaseContext(base: Context) {
            super.attachBaseContext(base)
        }
    }
}
