/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.startup;

import android.os.SystemClock;
import net.bytebuddy.asm.Advice;

/**
 * Byte Buddy advice applied to the app's {@link android.app.Application#onCreate}. Loaded by {@link
 * io.opentelemetry.instrumentation.agent.startup.ApplicationAttachPlugin} from startup-agent at
 * compile time. Same semantics as {@link ApplicationAttachAdvice}: the first entry wins and the
 * last exit wins, so nested {@code super.onCreate()} calls through woven superclasses still bound
 * the outermost call.
 */
public class ApplicationOnCreateAdvice {

    @Advice.OnMethodEnter
    public static void onEnter() {
        if (ProcessStartTimestamps.applicationOnCreateStartElapsedRealtime == 0L) {
            ProcessStartTimestamps.applicationOnCreateStartElapsedRealtime =
                    SystemClock.elapsedRealtime();
        }
    }

    @Advice.OnMethodExit
    public static void onExit() {
        ProcessStartTimestamps.applicationOnCreateEndElapsedRealtime =
                SystemClock.elapsedRealtime();
    }
}
