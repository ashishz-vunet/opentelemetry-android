# Compose Navigation 2 Instrumentation

Status: development

This module emits `ui.navigation` spans for Jetpack Compose Navigation 2 (`androidx.navigation:navigation-compose`).

## Installation

Add the module dependency:

```kotlin
implementation("io.opentelemetry.android.instrumentation:navigation-compose-nav2:<version>")
```

Then attach the observer once for each `NavController`:

```kotlin
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    VunetNavObserver(navController)
    NavHost(navController = navController, startDestination = "home") { /* ... */ }
}
```

## API

- Public entrypoint: `VunetNavObserver(navController: NavController)`
- No explicit `OpenTelemetryRum` parameter is required from app code.

## Behavior

- Destination type is emitted as `compose_route`.
- Destination name prefers `destination.route` (template value), with `displayName` as fallback.
- Transition inference:
  - back stack grows -> `push`
  - back stack shrinks -> `pop`
  - same depth with different destination -> `replace`
- Dialog and bottom-sheet destination types are filtered by default.

## Schema

This module uses shared constants/emitter/models from `instrumentation/navigation-common`, matching `navigation-view` so backends can query both with the same `ui.navigation` attribute shape.

## License

SPDX-License-Identifier: Apache-2.0
