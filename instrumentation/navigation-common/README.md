# Navigation Common (Internal)

Status: development

This module contains shared navigation telemetry internals used by:

- `instrumentation/navigation-view`
- `instrumentation/navigation-compose-nav2`
- `instrumentation/navigation-compose-nav3`

## Purpose

`navigation-common` centralizes:

- `ui.navigation` span name and attribute keys (`NavigationConstants`)
- span emission logic (`NavigationSpanEmitter`)
- shared navigation models (`NavigationNode`, `NavigationTransitionCandidate`, etc.)

This keeps View and Compose navigation instrumentations aligned on one schema and avoids duplicated logic.

## Internal-Only Module

This module is **internal implementation detail** and is **not intended for direct customer use**.

- Customers should not add `navigation-common` directly.
- Customer apps should depend on leaf modules such as:
  - `navigation-view`
  - `navigation-compose-nav2`
  - `navigation-compose-nav3`
- `navigation-common` is pulled transitively by those modules.

## Why this split exists

View-based and Compose-based navigation can coexist in one app. By sharing constants, emitter, and models in this module, all navigation instrumentations emit a consistent `ui.navigation` schema.

## License

SPDX-License-Identifier: Apache-2.0
