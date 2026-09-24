/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common.interaction

import io.opentelemetry.android.common.internal.instrumentation.ActiveInteractionContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context

/**
 * Supported entry point for code that starts spans by hand (SDK wrappers, app code) and wants them
 * attached to the user tap that caused them.
 *
 * Instrumentations keep using [ActiveInteractionContext] directly; this object is the stable,
 * read-only view for everyone else.
 */
object InteractionContext {
    /**
     * Parent for a span started by hand at this moment:
     * 1. [current] already holds a valid span → [current] (the caller's own nesting wins);
     * 2. a `ui.interaction` is inside its active window → [current] with that interaction's span
     *    (the tap itself, never a later `ui.navigation` that took over as HTTP parent);
     * 3. otherwise → [current].
     *
     * Read-only: never makes a context current and never extends the interaction window, so it
     * cannot leak context. Safe from any thread.
     */
    @JvmStatic
    @JvmOverloads
    fun parentForManualSpan(current: Context = Context.current()): Context {
        if (Span.fromContext(current).spanContext.isValid) return current
        val root = ActiveInteractionContext.rootContext() ?: return current
        val tap = Span.fromContext(root)
        return if (tap.spanContext.isValid) current.with(tap) else current
    }
}
