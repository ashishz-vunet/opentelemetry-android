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
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.utility.OpenedClassReader
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
    fun `injects advised overrides when attachBaseContext and onCreate are not declared`() {
        val typeDescription = TypeDescription.ForLoadedType.of(TestApplication::class.java)
        assertThat(typeDescription.declaredMethods.map { it.name })
            .doesNotContain("attachBaseContext", "onCreate")

        val transformed = transform(TestApplication::class.java)

        assertThat(declaredMethodNames(transformed.bytes)).contains("attachBaseContext", "onCreate")
        // Injected overrides call the advice's static onEnter/onExit around super.
        assertThat(String(transformed.bytes)).contains("ApplicationAttachAdvice", "ApplicationOnCreateAdvice")
    }

    @Test
    fun `advises declared onCreate override`() {
        val transformed = transform(TestApplicationWithOnCreate::class.java)

        assertThat(declaredMethodNames(transformed.bytes).filter { it == "onCreate" }).hasSize(1)
        assertThat(String(transformed.bytes)).contains("applicationOnCreateStartElapsedRealtime")
    }

    @Test
    fun `advises declared attachBaseContext override`() {
        val transformed = transform(TestApplicationWithAttach::class.java)

        assertThat(declaredMethodNames(transformed.bytes).filter { it == "attachBaseContext" }).hasSize(1)
        assertThat(String(transformed.bytes)).contains("ProcessStartTimestamps")
    }

    private fun transform(type: Class<*>) =
        plugin
            .apply(
                // decorate, not redefine: it is what the Android Gradle plugin uses, and it rejects
                // method interception, which redefine silently allows.
                ByteBuddy().decorate(type),
                TypeDescription.ForLoadedType.of(type),
                ClassFileLocator.ForClassLoader.of(type.classLoader),
            ).make()

    @Test
    fun `injected override calls the direct superclass so an app base class still runs`() {
        val transformed = transform(TestChildApplication::class.java)

        val superCalls = invokeSpecialOwners(transformed.bytes, "onCreate")
        // super.onCreate() must dispatch to the app's own base class, not skip to Application.
        assertThat(superCalls)
            .containsExactly(Type.getInternalName(TestBaseApplication::class.java))
    }

    @Test
    fun `does not inject over an inherited final method`() {
        val transformed = transform(TestChildOfFinalOnCreate::class.java)

        val names = declaredMethodNames(transformed.bytes)
        assertThat(names).doesNotContain("onCreate")
        // The other method is unaffected and still injected.
        assertThat(names).contains("attachBaseContext")
    }

    /** Owners of `invokespecial <methodName>` instructions inside the class's own `<methodName>`. */
    private fun invokeSpecialOwners(
        bytes: ByteArray,
        methodName: String,
    ): List<String> {
        val owners = mutableListOf<String>()
        ClassReader(bytes).accept(
            object : ClassVisitor(OpenedClassReader.ASM_API) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name != methodName) return null
                    return object : MethodVisitor(OpenedClassReader.ASM_API) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String?,
                            isInterface: Boolean,
                        ) {
                            if (opcode == Opcodes.INVOKESPECIAL && name == methodName) owners += owner
                        }
                    }
                }
            },
            0,
        )
        return owners
    }

    /** Method names as written in the class file; the unloaded type description does not list injected ones. */
    private fun declaredMethodNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ClassReader(bytes).accept(
            object : ClassVisitor(OpenedClassReader.ASM_API) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    names += name
                    return null
                }
            },
            ClassReader.SKIP_CODE,
        )
        return names
    }

    private class TestApplication : Application()

    private open class TestBaseApplication : Application() {
        override fun onCreate() {
            super.onCreate()
        }
    }

    private class TestChildApplication : TestBaseApplication()

    private open class TestFinalOnCreateApplication : Application() {
        final override fun onCreate() {
            super.onCreate()
        }
    }

    private class TestChildOfFinalOnCreate : TestFinalOnCreateApplication()

    private class TestApplicationWithOnCreate : Application() {
        override fun onCreate() {
            super.onCreate()
        }
    }

    private class TestApplicationWithAttach : Application() {
        override fun attachBaseContext(base: Context) {
            super.attachBaseContext(base)
        }
    }
}
