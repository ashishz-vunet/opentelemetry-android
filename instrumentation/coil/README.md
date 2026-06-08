# Coil Image-Loading Instrumentation

Status: development

The Coil instrumentation automatically generates OpenTelemetry `image.load` spans for every
image request made through [Coil](https://coil-kt.github.io/coil/) (version 2.x). It captures
the image source, load outcome, and a sanitised URL without requiring any change to existing
image-loading call sites.

Image URLs are automatically sanitised before recording: everything after the first `?` is
stripped, so authentication tokens, signed parameters, and other sensitive query-string data
never reach the telemetry back-end — a hard requirement for BFSI and banking applications.

This instrumentation is **not** included in the `android-agent` by default and must be
declared as an explicit dependency.

## Telemetry

Data produced by this instrumentation uses instrumentation scope name
`io.opentelemetry.android.instrumentation.coil`.

### Image Load

* Type: Span
* Name: `image.load`
* Description: A span that covers the full lifecycle of a single Coil image request, from
  the moment Coil enqueues the request to the point where the drawable is delivered or the
  request fails.
* Attributes:
    * `image.url` — sanitised image URL (query parameters stripped)
    * `image.model_type` — fully-qualified class name of the Coil data model (e.g. `java.lang.String`)
    * `image.source` — where Coil resolved the image from:
        * `"memory"` — served from Coil's memory cache or in-memory bitmap pool
        * `"disk"` — served from Coil's disk cache
        * `"network"` — fetched from the network
    * `image.load.status` — `"success"` or `"error"`

On failure, the span status is set to `ERROR` and the throwable is recorded on the span
via `span.recordException`.

## Installation

### Adding dependencies

```kotlin
implementation("io.opentelemetry.android.instrumentation:coil:1.3.0-alpha")
```

### One-time setup in Application.onCreate or your DI graph

Register `CoilImageLoaderEventListenerFactory` with Coil's `ImageLoader` builder so that the
instrumentation receives lifecycle callbacks for every request:

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .eventListenerFactory(CoilImageLoaderEventListenerFactory())
    .build()

// Make this the singleton loader used by Coil's top-level extension functions:
Coil.setImageLoader(imageLoader)
```

This is a one-time setup. After this, all Coil requests are traced automatically with no
further code changes required.

> [!NOTE]
> If you construct `ImageLoader` instances manually rather than using the singleton, register
> the factory on each builder individually.

> [!NOTE]
> The factory returns `EventListener.NONE` (zero overhead) when
> `CoilInstrumentation` has not yet been installed by the OpenTelemetry RUM SDK, so it is
> safe to register it unconditionally during application startup.
