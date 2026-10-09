# Startup Instrumentation

Status: development

The startup instrumentation captures early process milestones before the OTel SDK initializes,
and provides initialization log events that describe the steps taken during RUM initialization.

## Telemetry

### Early startup milestones (via activity instrumentation)

When the [activity](../activity) instrumentation is installed, cold-start telemetry on the
`app.start` span includes:

| Telemetry | Type | Name | Source |
|-----------|------|------|--------|
| Process fork | Event | `app.start.phase.process` | Backdated from `Process.getStartElapsedRealtime()` (API 24+) |
| attachBaseContext phase | Event | `app.start.phase.attach_base_context.start` / `.end` | Requires **startup-agent** (see below) |
| ContentProvider phase start | Event | `app.start.phase.content_providers.start` | `AppAnchorContentProvider` |
| ContentProvider phase end | Event | `app.start.phase.content_providers.end` | `EarlyStartupContentProvider` |
| Application.onCreate phase | Event | `app.start.phase.application.start` / `.end` | Requires **startup-agent** (see below) |

### SDK Initialization

* Type: Log event
* Name: { `rum.sdk.init.started` | `rum.sdk.init.net.provider` | `rum.sdk.init.net.monitor` | `rum.sdk.init.anr_monitor` | `rum.sdk.init.jank_monitor` | `rum.sdk.init.crash.reporter` | `rum.sdk.init.span.exporter` }
* Description: These events indicate the progress of various RUM SDK initialization components.
* Attributes:
    * `span.exporter`: *(Only for `rum.sdk.init.span.exporter`)* — Name of the configured span exporter.

## Installation

Runtime instrumentation comes with the [android agent](../../android-agent) out of the box.

### attachBaseContext and onCreate events (startup-agent)

The `app.start.phase.attach_base_context.start` / `.end` and `app.start.phase.application.start` /
`.end` events require compile-time weaving of `Application.attachBaseContext()` and
`Application.onCreate()` via the **startup-agent** artifact.

No code change is needed in your `Application` subclass. When it declares the method, the agent
applies advice to it; when it does not, the agent injects a pass-through override
(`super` call wrapped in the same timing), since decoration mode cannot hook an inherited method
directly. An inherited `final` method is left alone. Apps without their own `Application`
subclass have nothing to weave, so these events are absent there.

If you use the VuNet Gradle plugin (`vunet.telemetry.android`), the agent is wired automatically
when `sdk = true` on an application module.

Otherwise, apply the Byte Buddy plugin and add the agent dependency manually:

```kotlin
plugins {
    id("net.bytebuddy.byte-buddy-gradle-plugin") version "BYTEBUDDY_VERSION"
}

dependencies {
    implementation("io.opentelemetry.android.instrumentation:startup-library:1.3.0-alpha")
    byteBuddy("io.opentelemetry.android.instrumentation:startup-agent:1.3.0-alpha")
}
```

Replace `BYTEBUDDY_VERSION` with the [latest Byte Buddy Gradle plugin](https://plugins.gradle.org/plugin/net.bytebuddy.byte-buddy-gradle-plugin).

### Adding dependencies (runtime only)

```kotlin
implementation("io.opentelemetry.android.instrumentation:startup-library:1.3.0-alpha")
```
