/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.startup;

import android.os.SystemClock;
import net.bytebuddy.asm.Advice;

/**
 * Byte Buddy advice applied to the app's declared {@link
 * android.app.Application#attachBaseContext}. Loaded by {@link
 * io.opentelemetry.instrumentation.agent.startup.ApplicationAttachPlugin} from startup-agent at
 * compile time.
 */
public class ApplicationAttachAdvice {

    @Advice.OnMethodEnter
    public static void onEnter() {
        if (ProcessStartTimestamps.attachBaseContextStartElapsedRealtime == 0L) {
            ProcessStartTimestamps.attachBaseContextStartElapsedRealtime =
                    SystemClock.elapsedRealtime();
        }
    }

    @Advice.OnMethodExit
    public static void onExit() {
        ProcessStartTimestamps.attachBaseContextEndElapsedRealtime = SystemClock.elapsedRealtime();
    }
}
