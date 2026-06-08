/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.LibraryGlideModule
import java.io.InputStream

/**
 * Glide library-module hook that injects OTel span lifecycle management into Glide's component
 * registry at application startup.
 *
 * [Registry.prepend] places [OtelSideEffectModelLoaderFactory] ahead of all built-in loaders.
 * [OtelContextModelLoader] delegates to the real loader chain, wrapping its [DataFetcher] in
 * [OtelContextDataFetcher] to propagate the "image.load" span context onto Glide's background
 * thread — making OkHttp HTTP spans children of the image.load span.
 *
 * A `null` tracer (written by [GlideInstrumentation.install] before this method is called) is
 * handled gracefully: the factory is not registered and Glide is left completely unaffected.
 */
@GlideModule
class GlideOtelModule : LibraryGlideModule() {
    override fun registerComponents(
        context: Context,
        glide: Glide,
        registry: Registry,
    ) {
        GlideInstrumentation.registerGlideComponents(registry)
    }
}
