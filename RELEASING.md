# Releasing

This fork publishes all artifacts to **Maven Central** under `com.vunetsystems.opentelemetry.android`.

## Version lines

| Type | Version example | Gradle flag | CI trigger |
|------|-----------------|-------------|------------|
| Snapshot | `0.0.1-SNAPSHOT` | default (no `-Pfinal=true`) | PR merged to `develop` |
| Release | `0.0.1` | `-Pfinal=true` | push to `release/*` (e.g. after PR merge) |

Configured in root [`gradle.properties`](gradle.properties):

- `version=0.0.1` — base semver
- `otel.publish.alpha=false` — all modules share one version (no per-module `-alpha` suffix)

## Prerequisites

- Verified namespace `com.vunetsystems` on [central.sonatype.com](https://central.sonatype.com)
- Central Portal user token: `SONATYPE_USER`, `SONATYPE_KEY`
- GPG key on a public keyserver: `GPG_PRIVATE_KEY`, `GPG_PASSWORD`
- `CI=true` for signing and sources/javadoc during publish

Log in to the Central Portal UI with the **same account** used to generate the Sonatype token.

Repository secrets for GitHub Actions: `SONATYPE_USER`, `SONATYPE_KEY`, `GPG_PRIVATE_KEY`, `GPG_PASSWORD`.

## CI workflows

| Workflow | Trigger | Publishes |
|----------|---------|-----------|
| [Publish Maven Central Snapshot](.github/workflows/publish-maven-central-snapshot.yml) | PR merged to `develop` | `0.0.1-SNAPSHOT` |
| [Release](.github/workflows/release.yml) | push to `release/**` | `0.0.1` (with version bump check) |

Manual fallback: both workflows support `workflow_dispatch`.

## Local verification before publish

```bash
./gradlew spotlessApply
./gradlew check
./gradlew apiCheck

./gradlew :android-agent:properties -q | grep '^version:'
# Expected: version: 0.0.1-SNAPSHOT
```

## Publish snapshot locally

```bash
export CI=true
# SONATYPE_USER, SONATYPE_KEY, GPG_PRIVATE_KEY, GPG_PASSWORD

./gradlew --stop
./gradlew publishToSonatype --no-parallel --no-configuration-cache --no-build-cache
```

Do **not** run `closeAndReleaseSonatypeStagingRepository` for snapshots.

## Publish release locally

```bash
export CI=true

.github/scripts/verify-version-greater-than-central.sh

./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository \
  -Pfinal=true \
  --no-parallel --no-configuration-cache --no-build-cache
```

## Verify published artifacts

Snapshots (wait ~15–30 min):

```bash
curl -s "https://central.sonatype.com/repository/maven-snapshots/com/vunetsystems/opentelemetry/android/android-agent/maven-metadata.xml"
```

Releases:

```bash
curl -s "https://repo1.maven.org/maven2/com/vunetsystems/opentelemetry/android/android-agent/maven-metadata.xml"
```

## Consumer coordinates

See [Maven Central consumption](./docs/MAVEN_CENTRAL.md).

## Troubleshooting

| Error | Cause | Action |
|-------|-------|--------|
| **The version cannot be a SNAPSHOT** | Invalid version on staging upload | Use `0.0.1-SNAPSHOT` or `0.0.1` only; never `-SNAPSHOT-dev` |
| **401 / 403** | Bad Sonatype token | Regenerate token; same account in UI and Gradle |
| **Signing failed** | Missing `CI=true` or bad GPG env | Set `CI=true`; verify GPG vars |
| **javaDocReleaseGeneration FAILED** | JVM metaspace exhaustion | Add `--no-parallel`; run `./gradlew --stop` first |
| **Release version must be greater** | Duplicate or downgraded version | Bump `version` in `gradle.properties` |
| **Deployments empty in portal** | Staging not closed (releases only) | Run `closeAndReleaseSonatypeStagingRepository` for releases |

Drop failed deployments at [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments) before republishing.
