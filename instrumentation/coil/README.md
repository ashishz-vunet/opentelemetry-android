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
        * `"network"` — fetched from the network (OkHttp child spans will appear under this span)
        * `"disk"` — served from Coil's disk cache
        * `"memory"` — served from Coil's memory cache or in-memory bitmap pool
    * `image.load.status` — `"success"` or `"error"`

On failure, the span status is set to `ERROR` and the throwable is recorded on the span
via `span.recordException`.

### OkHttp child spans

When an image is fetched over the network, the OkHttp instrumentation automatically creates
child `GET` spans under the `image.load` span. This works because `VunetCoilInterceptor` wraps
the downstream chain execution in `withContext(span.asContextElement())`, propagating the
`image.load` span into the coroutine context before OkHttp executes. Both the interceptor
and the event listener factory must be registered (see Installation below).

## Installation

### 1. Add the dependency

```kotlin
implementation("com.vunetsystems.opentelemetry.android.instrumentation:coil:0.0.1-SNAPSHOT-dev")
```

### 2. One-time setup in Application.onCreate or your DI graph

Register both `VunetCoilEventListenerFactory` (span lifecycle) and `VunetCoilInterceptor`
(OkHttp context propagation) with Coil's `ImageLoader` builder:

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .eventListenerFactory(VunetCoilEventListenerFactory())
    .components {
        add(VunetCoilInterceptor())
    }
    .build()

// Make this the singleton loader used by Coil's top-level extension functions:
Coil.setImageLoader(imageLoader)
```

This is a one-time setup. After this, all Coil requests are traced automatically with no
further code changes required.

> [!NOTE]
> Both `VunetCoilEventListenerFactory` **and** `VunetCoilInterceptor` must be registered.
> The factory manages the span lifecycle; the interceptor propagates the span context into
> the coroutine so OkHttp child spans are correctly parented.

> [!NOTE]
> If you construct `ImageLoader` instances manually rather than using the singleton, register
> both on each builder individually.

> [!NOTE]
> `VunetCoilEventListenerFactory` returns `EventListener.NONE` (zero overhead) when
> `CoilInstrumentation` has not yet been installed by the OpenTelemetry RUM SDK, so it is
> safe to register it unconditionally during application startup.

## How it works

| Phase | Class | Action |
|---|---|---|
| Request enqueued (any thread) | `CoilOtelEventListener.onStart` | Starts span, calls `makeCurrent()`, stores in `CoilSpanStore` |
| Fetch runs (coroutine dispatcher) | `VunetCoilInterceptor` | Restores span via `withContext(span.asContextElement())` → OkHttp spans parent to image.load |
| Request finished | `CoilOtelEventListener.onSuccess` / `onError` | Closes scope, adds attributes, ends span |

## Disabling via the agent DSL

If you need to turn off Coil instrumentation without removing the dependency:

```kotlin
OpenTelemetryRumInitializer.initialize(application) {
    instrumentations {
        coil {
            enabled(false)
        }
    }
}
```
