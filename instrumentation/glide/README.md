# Glide Image-Loading Instrumentation

Status: development

The Glide instrumentation automatically generates OpenTelemetry `image.load` spans for every
image request made through [Glide](https://github.com/bumptech/glide). It captures the image
source, load outcome, and a sanitised URL without requiring any change to existing image-loading
call sites.

Image URLs are automatically sanitised before recording: everything after the first `?` is
stripped, so authentication tokens, signed parameters, and other sensitive query-string data
never reach the telemetry back-end — a hard requirement for BFSI and banking applications.

This instrumentation is **not** included in the `android-agent` by default and must be
declared as an explicit dependency.

## Telemetry

Data produced by this instrumentation uses instrumentation scope name
`io.opentelemetry.android.instrumentation.glide`.

### Image Load

* Type: Span
* Name: `image.load`
* Description: A span that covers the full lifecycle of a single Glide image request, from
  the moment Glide begins resolving the model to the point where the resource is delivered
  or the request fails.
* Attributes:
    * `image.url` — sanitised image URL (query parameters stripped)
    * `image.model_type` — fully-qualified class name of the Glide model (e.g. `java.lang.String`)
    * `image.source` — where Glide resolved the image from:
        * `"network"` — fetched from the network (OkHttp child spans will appear under this span)
        * `"disk_cache"` — served from Glide's data or resource disk cache
        * `"disk"` — served from Glide's local disk (e.g. file URI)
        * `"memory"` — served from Glide's active-resources or memory LRU cache
    * `image.load.status` — `"success"` or `"error"`
    * `image.is_first_resource` — `true` if this was the first resource loaded for the target

On failure, the span status is set to `ERROR` and the `GlideException` is recorded on the
span via `span.recordException`.

### OkHttp child spans

When an image is fetched over the network, the OkHttp instrumentation automatically creates
child `GET` spans under the `image.load` span. This works because `OtelContextDataFetcher`
restores the `image.load` span context on Glide's background thread before OkHttp executes.
No additional configuration is required.

## Installation

### 1. Add the dependency

```kotlin
implementation("com.vunetsystems.opentelemetry.android.instrumentation:glide:0.0.1-SNAPSHOT-dev")
```

### 2. One-time setup in your AppGlideModule

Register `GlideOtelRequestListener` globally in your `AppGlideModule` so that it receives
the terminal callbacks for every request and ends the span:

```kotlin
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.addGlobalRequestListener(GlideOtelRequestListener())
    }
    // registerComponents() override is NOT required —
    // GlideOtelModule (LibraryGlideModule) handles component registration automatically.
}
```

This is a one-time setup. After this, all Glide requests are traced automatically with no
further code changes required.

> [!NOTE]
> `GlideOtelRequestListener` must be registered **before** Glide is initialised.
> Registering it inside `applyOptions` is the correct and guaranteed-safe location.

> [!NOTE]
> `GlideOtelModule` is a `LibraryGlideModule` that Glide discovers automatically at startup.
> It injects the `OtelContextModelLoader` into Glide's component registry. You do **not** need
> to call `GlideInstrumentation.registerGlideComponents()` manually from your own
> `AppGlideModule.registerComponents()`.

## How it works

| Phase | Class | Action |
|---|---|---|
| Request started (main thread) | `OtelContextModelLoader` | Starts span, captures OTel context |
| Fetch runs (background thread) | `OtelContextDataFetcher` | Restores context → OkHttp spans parent to image.load |
| Request finished | `GlideOtelRequestListener` | Adds attributes, ends span |
| Memory cache hit | `GlideOtelRequestListener` | Synthesises a span (Glide bypasses ModelLoader for memory hits) |

## Expected test sequence

To verify each `image.source` value in order:

1. Fresh install (or clear app data) → press Network button → `image.source = "network"`, OkHttp child spans visible
2. Press Disk Cache button (skipMemoryCache=true) → `image.source = "disk_cache"`
3. Press Memory Cache button (DiskCacheStrategy.NONE) → `image.source = "memory"`

If the Network button shows `disk_cache` instead of `network`, Glide's disk cache is already
populated from a prior session. Clear app storage via **Settings → Apps → [App] → Clear Storage**
and retry.
