# Glide Image-Loading Instrumentation

Status: development

The Glide instrumentation automatically generates OpenTelemetry `image.load` spans for every
image request made through [Glide](https://github.com/bumptech/glide). It captures the image
source, load outcome, and a sanitised URL without requiring any change to existing image-loading
call sites.

Image URLs are automatically sanitised before recording: everything after the first `?` is
stripped, so authentication tokens, signed parameters, and other sensitive query-string data
never reach the telemetry back-end.

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
        * `"memory"` — served from Glide's active-resources or memory cache
        * `"disk"` — served from Glide's disk cache
        * `"disk_cache"` — served from Glide's data or resource disk cache
        * `"network"` — fetched from the network
    * `image.load.status` — `"success"` or `"error"`
    * `image.is_first_resource` — `true` if this was the first resource loaded for the target

On failure, the span status is set to `ERROR` and the `GlideException` is recorded on the
span via `span.recordException`.

## Installation

### Adding dependencies

```kotlin
implementation("io.opentelemetry.android.instrumentation:glide:1.3.0-alpha")
```

### One-time setup in your AppGlideModule

Because Glide's `LibraryGlideModule` hook starts the span, the span can only be **ended**
from a `RequestListener`. Register `GlideOtelRequestListener` globally in your
`AppGlideModule` so that it receives the terminal callbacks for every request:

```kotlin
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.addGlobalRequestListener(GlideOtelRequestListener())
    }
}
```

This is a one-time setup. After this, all Glide requests are traced automatically with no
further code changes required.

> [!NOTE]
> `GlideOtelRequestListener` must be registered **before** Glide is initialised.
> Registering it inside `applyOptions` is the correct and guaranteed-safe location.
