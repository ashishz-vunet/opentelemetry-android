# Maven Central publishing and consumption

This fork publishes artifacts to Maven Central under `com.vunetsystems.opentelemetry.android`.

## Published coordinates

| Role | Maven coordinate |
|------|------------------|
| BOM | `com.vunetsystems.opentelemetry.android:opentelemetry-android-bom:0.0.1-SNAPSHOT` |
| Agent entry | `com.vunetsystems.opentelemetry.android:android-agent` |
| Instrumentation | `com.vunetsystems.opentelemetry.android.instrumentation:<artifact>` |

Replace `0.0.1-SNAPSHOT` with a release version (e.g. `0.0.1`) for non-snapshot builds.

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
    api(platform("com.vunetsystems.opentelemetry.android:opentelemetry-android-bom:0.0.1-SNAPSHOT"))
    implementation("com.vunetsystems.opentelemetry.android:android-agent")
}
```

### Releases

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    api(platform("com.vunetsystems.opentelemetry.android:opentelemetry-android-bom:0.0.1"))
    implementation("com.vunetsystems.opentelemetry.android:android-agent")
}
```

Use the BOM so all modules share the same version without listing each explicitly.

## vuTelemetry-android migration

Remove GitHub Packages repository and `gpr.*` credentials. Pin the BOM from Maven Central instead:

```kotlin
api(platform("com.vunetsystems.opentelemetry.android:opentelemetry-android-bom:<version>"))
```

Update `vunet.stack.version` / OTel BOM pin to match the published BOM version.

## Publishing (maintainers)

See [RELEASING.md](../RELEASING.md) for CI triggers, local publish commands, and troubleshooting.
