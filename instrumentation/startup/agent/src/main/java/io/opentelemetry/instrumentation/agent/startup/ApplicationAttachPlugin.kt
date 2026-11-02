/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.agent.startup

import android.app.Application
import android.content.Context
import io.opentelemetry.android.instrumentation.startup.ApplicationAttachAdvice
import io.opentelemetry.android.instrumentation.startup.ApplicationOnCreateAdvice
import java.io.IOException
import net.bytebuddy.asm.Advice
import net.bytebuddy.build.Plugin
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.asm.AsmVisitorWrapper
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.field.FieldList
import net.bytebuddy.description.method.MethodList
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import net.bytebuddy.pool.TypePool
import net.bytebuddy.utility.OpenedClassReader
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers

internal class ApplicationAttachPlugin : Plugin {

    /**
     * Weaves timing advice into `attachBaseContext` and `onCreate`. When the app's class does not
     * declare one of them, a pass-through override that calls the advice around `super` is
     * injected, so the phase is timed for every app rather than only for apps that happen to
     * override the method. An inherited `final` method is left alone.
     */
    override fun apply(
        builder: DynamicType.Builder<*>,
        typeDescription: TypeDescription,
        classFileLocator: ClassFileLocator,
    ): DynamicType.Builder<*> {
        var result = builder
        result =
            weave(
                result,
                typeDescription,
                attachBaseContextMatcher(),
                ApplicationAttachAdvice::class.java,
                OverrideSpec("attachBaseContext", "(Landroid/content/Context;)V", Opcodes.ACC_PROTECTED, 1),
            )
        result =
            weave(
                result,
                typeDescription,
                onCreateMatcher(),
                ApplicationOnCreateAdvice::class.java,
                OverrideSpec("onCreate", "()V", Opcodes.ACC_PUBLIC, 0),
            )
        return result
    }

    private fun weave(
        builder: DynamicType.Builder<*>,
        typeDescription: TypeDescription,
        matcher: ElementMatcher<MethodDescription>,
        advice: Class<*>,
        override: OverrideSpec,
    ): DynamicType.Builder<*> {
        if (typeDescription.declaredMethods.any { matcher.matches(it) }) {
            return builder.visit(Advice.to(advice).on(matcher))
        }
        val inherited = inheritedMethod(typeDescription, matcher)
        if (inherited?.isFinal == true) {
            return builder
        }
        // Copy the inherited visibility so a public parent override is not narrowed to
        // protected (illegal, and ART rejects the class at load).
        val access =
            inherited
                ?.modifiers
                ?.and(Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED or Opcodes.ACC_PRIVATE)
                ?.takeIf { it != 0 }
                ?: override.access
        // The Android Gradle plugin transforms in decorate mode, which rejects method
        // definition/interception; only visitors are allowed, so the override is written in ASM.
        return builder.visit(
            InjectAdvisedOverride(
                OverrideSpec(override.name, override.descriptor, access, override.argCount),
                Type.getInternalName(advice),
            ),
        )
    }

    private fun inheritedMethod(
        typeDescription: TypeDescription,
        matcher: ElementMatcher<MethodDescription>,
    ): MethodDescription? {
        var current = typeDescription.superClass?.asErasure()
        while (current != null) {
            current.declaredMethods.firstOrNull { matcher.matches(it) }?.let { return it }
            current = current.superClass?.asErasure()
        }
        return null
    }

    /** `void <name>(<one Context arg or none>)` override to inject into the app's Application. */
    private class OverrideSpec(
        val name: String,
        val descriptor: String,
        val access: Int,
        val argCount: Int,
    )

    /**
     * Appends `<name>(args) { Advice.onEnter(); super.<name>(args); Advice.onExit(); }` to the
     * class. No branches, so no stack-map frames are needed; max stack/locals are `1 + argCount`.
     */
    private class InjectAdvisedOverride(
        private val spec: OverrideSpec,
        private val adviceInternalName: String,
    ) : AsmVisitorWrapper.AbstractBase() {
        override fun wrap(
            instrumentedType: TypeDescription,
            classVisitor: ClassVisitor,
            implementationContext: Implementation.Context,
            typePool: TypePool,
            fields: FieldList<FieldDescription.InDefinedShape>,
            methods: MethodList<*>,
            writerFlags: Int,
            readerFlags: Int,
        ): ClassVisitor =
            object : ClassVisitor(OpenedClassReader.ASM_API, classVisitor) {
                override fun visitEnd() {
                    val superName = instrumentedType.superClass!!.asErasure().internalName
                    val mv = super.visitMethod(spec.access, spec.name, spec.descriptor, null, null)
                    mv.visitCode()
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, adviceInternalName, "onEnter", "()V", false)
                    for (slot in 0..spec.argCount) {
                        mv.visitVarInsn(Opcodes.ALOAD, slot)
                    }
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, spec.name, spec.descriptor, false)
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, adviceInternalName, "onExit", "()V", false)
                    mv.visitInsn(Opcodes.RETURN)
                    mv.visitMaxs(1 + spec.argCount, 1 + spec.argCount)
                    mv.visitEnd()
                    super.visitEnd()
                }
            }
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

    private fun onCreateMatcher(): ElementMatcher<MethodDescription> =
        ElementMatchers
            .named<NamedElement>("onCreate")
            .and(ElementMatchers.takesNoArguments())
}
