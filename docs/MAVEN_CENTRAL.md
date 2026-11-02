# Maven Central publishing and consumption

This fork publishes artifacts to Maven Central under `com.vunetsystems.agent.android`. Published under the VuNet SDK's group: core modules are `com.vunetsystems.agent.android:agent-android-*` and instrumentations `com.vunetsystems.agent.android.instrumentation:<artifact>`. Six modules are built but not published: `android-log-agent`, `android-log-library`, `compose-click`, `okhttp3-websocket-agent`, `okhttp3-websocket-library`, `view-click` (nothing in the SDK uses them).

## Published coordinates

| Role | Maven coordinate |
|------|------------------|
| BOM | `com.vunetsystems.agent.android:agent-android-bom:1.0.0-rc.1-SNAPSHOT` |
| Agent entry | `com.vunetsystems.agent.android:agent-android-runtime` |
| Instrumentation | `com.vunetsystems.agent.android.instrumentation:<artifact>` |

Replace `1.0.0-rc.1-SNAPSHOT` with a release version (e.g. `1.0.0-rc.1`) for non-snapshot builds.

## Consuming in Android apps

### Snapshots

```kotlin
repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencies {
    api(platform("com.vunetsystems.agent.android:agent-android-bom:1.0.0-rc.1-SNAPSHOT"))
    implementation("com.vunetsystems.agent.android:agent-android-runtime")
}
```

### Releases

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    api(platform("com.vunetsystems.agent.android:agent-android-bom:1.0.0-rc.1"))
    implementation("com.vunetsystems.agent.android:agent-android-runtime")
}
```

Use the BOM so all modules share the same version without listing each explicitly.

## vuTelemetry-android migration

Remove GitHub Packages repository and `gpr.*` credentials. Pin the BOM from Maven Central instead:

```kotlin
api(platform("com.vunetsystems.agent.android:agent-android-bom:<version>"))
```

Update `vunetStackVersion` in vuTelemetry-android's `gradle/libs.versions.toml` to match the published BOM version.

## Publishing (maintainers)

See [RELEASING.md](../RELEASING.md) for CI triggers, local publish commands, and troubleshooting.
