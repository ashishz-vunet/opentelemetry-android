/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.agent.startup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.InvocationTargetException
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.utility.OpenedClassReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class ApplicationAttachPluginTest {
    private val plugin = ApplicationAttachPlugin()

    @Before
    fun resetTimestamps() {
        setTimestamp("attachBaseContextStartElapsedRealtime", 0L)
        setTimestamp("attachBaseContextEndElapsedRealtime", 0L)
        setTimestamp("applicationOnCreateStartElapsedRealtime", 0L)
        setTimestamp("applicationOnCreateEndElapsedRealtime", 0L)
    }

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

    @Test
    fun `injected override keeps a public parent attachBaseContext public`() {
        val transformed = transform(TestChildOfPublicAttach::class.java)

        val access = methodAccess(transformed.bytes, "attachBaseContext")
        assertThat(access and Opcodes.ACC_PUBLIC).isNotZero()
        assertThat(access and Opcodes.ACC_PROTECTED).isZero()
    }

    @Test
    fun `injected onCreate and attachBaseContext record timestamps when invoked`() {
        val loaded = loadTransformed(TestApplication::class.java)
        val ctor = loaded.getDeclaredConstructor()
        ctor.isAccessible = true
        val instance = ctor.newInstance() as Application

        invokeDeclared(loaded, instance, "attachBaseContext", Context::class.java, ApplicationProvider.getApplicationContext())
        assertThat(timestamp("attachBaseContextStartElapsedRealtime")).isGreaterThan(0L)
        assertThat(timestamp("attachBaseContextEndElapsedRealtime"))
            .isGreaterThanOrEqualTo(timestamp("attachBaseContextStartElapsedRealtime"))

        invokeDeclared(loaded, instance, "onCreate")
        assertThat(timestamp("applicationOnCreateStartElapsedRealtime")).isGreaterThan(0L)
        assertThat(timestamp("applicationOnCreateEndElapsedRealtime"))
            .isGreaterThanOrEqualTo(timestamp("applicationOnCreateStartElapsedRealtime"))
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

    private fun loadTransformed(type: Class<*>): Class<*> =
        transform(type)
            .load(type.classLoader, ClassLoadingStrategy.Default.CHILD_FIRST)
            .loaded

    private fun invokeDeclared(
        type: Class<*>,
        instance: Any,
        name: String,
        parameterType: Class<*>? = null,
        argument: Any? = null,
    ) {
        val method =
            if (parameterType == null) {
                type.getDeclaredMethod(name)
            } else {
                type.getDeclaredMethod(name, parameterType)
            }
        method.isAccessible = true
        try {
            if (parameterType == null) method.invoke(instance) else method.invoke(instance, argument)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun methodAccess(
        bytes: ByteArray,
        methodName: String,
    ): Int {
        var access = 0
        ClassReader(bytes).accept(
            object : ClassVisitor(OpenedClassReader.ASM_API) {
                override fun visitMethod(
                    acc: Int,
                    name: String,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name == methodName) access = acc
                    return null
                }
            },
            ClassReader.SKIP_CODE,
        )
        return access
    }

    private fun timestamp(field: String): Long = timestampsClass().getField(field).getLong(null)

    private fun setTimestamp(
        field: String,
        value: Long,
    ) {
        timestampsClass().getField(field).setLong(null, value)
    }

    private fun timestampsClass(): Class<*> =
        Class.forName("io.opentelemetry.android.instrumentation.startup.ProcessStartTimestamps")

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

    private open class TestPublicAttachApplication : Application() {
        public override fun attachBaseContext(base: Context) {
            super.attachBaseContext(base)
        }
    }

    private class TestChildOfPublicAttach : TestPublicAttachApplication()
}
