package io.opentelemetry.android.agent

import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader

class FakeInstrumentationLoader : AndroidInstrumentationLoader {
    private val instrumentations: MutableList<AndroidInstrumentation> = mutableListOf()

    fun register(instrumentation: AndroidInstrumentation) {
        instrumentations.add(instrumentation)
    }

    override fun <T : AndroidInstrumentation> getByType(
        type: Class<out T>
    ): T? = instrumentations.firstOrNull { type.isAssignableFrom(it.javaClass) } as? T

    override fun getAll(): Collection<AndroidInstrumentation> = instrumentations.toList()
}
