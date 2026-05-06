# Fork publishing and consumption

This fork is published to GitHub Packages and is intended for internal consumption.

## Publishing this fork

This repository publishes artifacts to:

- `https://maven.pkg.github.com/vunetsystems/opentelemetry-android`

Publishing from this repository's GitHub Actions uses the built-in `GITHUB_TOKEN`.
No personal access token is required for this publish path.

Run locally only when needed:

```bash
./gradlew publish -PpublishTarget=github -Pgithub.packages.owner=vunetsystems -Pgithub.packages.repo=opentelemetry-android
```

## Consuming in vuTelemetry-android

In `vunetsystems/vuTelemetry-android`, add the fork repository in dependency resolution:

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/vunetsystems/opentelemetry-android")
    credentials {
        username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
        password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
    }
}
```

For CI in the SDK repository, use a token that can read packages from
`vunetsystems/opentelemetry-android`.

## Android Studio local setup

Add credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<token-with-read-packages>
```

This allows local sync and builds to resolve forked dependencies.

