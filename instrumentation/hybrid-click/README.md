# Hybrid Click Instrumentation

Status: development

This instrumentation captures click interactions for both Android Views and Jetpack Compose
using a single `Window.Callback` wrapper to avoid callback wrapping conflicts.

This instrumentation is not currently enabled by default.

## Telemetry

Data produced by this instrumentation uses instrumentation scope name
`io.opentelemetry.android.instrumentation.hybrid.click`.

### Clicks

* Type: Span
* Name: `ui.interaction`
* Description: Span emitted when a clickable view or composable is tapped.

## Installation

```kotlin
implementation("io.opentelemetry.android.instrumentation:hybrid-click:1.2.0-alpha")
```

## Configuration

When using `android-agent`, you can configure how long the click interaction context stays
active for downstream span parenting:

```kotlin
OpenTelemetryRumInitializer.initialize(
    context = applicationContext,
) {
    instrumentations {
        hybridClick {
            activeContextWindowMillis(500)
        }
    }
}
```

This controls the parenting window for async work triggered by a click (for example network
requests or navigation), not the `ui.interaction` span duration. The span itself ends immediately
after the tap.

## Attaching your own spans to a tap

A View `OnClickListener` runs after the tap's context scope has already closed, so
`Span.current()` is not the tap. Parent a hand-started span to it with:

```kotlin
tracer.spanBuilder("checkout")
    .setParent(InteractionContext.parentForManualSpan())
    .startSpan()
```

This is read-only: it never makes a context current. The tap stays available as a parent for
`activeContextWindowMillis` (default 500 ms). Work started after that window gets its own trace.
