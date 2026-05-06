# Compose Navigation 3 Instrumentation

Status: development

This module emits `ui.navigation` spans for Jetpack Compose Navigation 3 (`androidx.navigation3:navigation3-runtime`).

## Installation

Add the module dependency:

```kotlin
implementation("io.opentelemetry.android.instrumentation:navigation-compose-nav3:<version>")
```

Then attach the observer once for each `NavBackStack`:

```kotlin
@Composable
fun AppNavigation(backStack: NavBackStack) {
    VunetNavObserver(backStack = backStack)
}
```

## API

- Public entrypoint: `VunetNavObserver(backStack: NavBackStack, nameOf: (NavKey) -> String = ...)`
- No explicit `OpenTelemetryRum` parameter is required from app code.

## Behavior

- Destination type is emitted as `compose_route`.
- Transition inference from back stack snapshots:
  - stack grows -> `push`
  - stack shrinks -> `pop`
  - same size with different top -> `replace`
  - identical top -> no-op
- `nameOf` can be overridden for typed keys; default uses `simpleName`.

## Schema

This module uses shared constants/emitter/models from `instrumentation/navigation-common`, matching `navigation-view` so backends can query both with the same `ui.navigation` attribute shape.

## License

SPDX-License-Identifier: Apache-2.0
