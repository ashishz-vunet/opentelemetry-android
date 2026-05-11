/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.compose.nav3

import java.util.WeakHashMap

internal object NavObserverCollectorHolder {
    private val collectors: MutableMap<Any, ComposeNav3Collector> = WeakHashMap()

    @Synchronized
    fun set(
        backStack: Any,
        collector: ComposeNav3Collector,
    ) {
        collectors[backStack] = collector
    }

    @Synchronized
    fun current(backStack: Any): ComposeNav3Collector? = collectors[backStack]

    @Synchronized
    fun remove(backStack: Any) {
        collectors.remove(backStack)
    }

    @Synchronized
    fun clear() {
        collectors.clear()
    }
}
