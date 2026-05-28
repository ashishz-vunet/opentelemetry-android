/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.startup

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import io.opentelemetry.android.common.ProcessStartTimestamps

/**
 * A no-op [ContentProvider] that records the wall-clock time immediately after
 * [android.app.Application.attachBaseContext] completes.
 *
 * Android initializes ContentProviders in descending [android:initOrder] order, before
 * [android.app.Application.onCreate] is called. By using [Int.MAX_VALUE] as the init order,
 * this provider is guaranteed to run first — before any third-party library provider — giving
 * the earliest possible post-attachBaseContext timestamp.
 *
 * The captured value is stored in [ProcessStartTimestamps.attachBaseContextEpochMs] and later
 * emitted as the `app.base_context` event on the AppStart span by AppStartupTimer.
 *
 * This provider is registered in the startup artifact's AndroidManifest and is automatically
 * merged into the app's manifest when the artifact is on the classpath. No manual wiring is
 * required.
 */
class AppAnchorContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        ProcessStartTimestamps.attachBaseContextEpochMs = System.currentTimeMillis()
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
