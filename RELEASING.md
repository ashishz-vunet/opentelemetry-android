# Releasing

This fork publishes to **Maven Central** under `com.vunetsystems.agent.android`. Published under the VuNet SDK's group: core modules are `com.vunetsystems.agent.android:agent-android-*` and instrumentations `com.vunetsystems.agent.android.instrumentation:<artifact>`. Six modules are built but not published: `android-log-agent`, `android-log-library`, `compose-click`, `okhttp3-websocket-agent`, `okhttp3-websocket-library`, `view-click` (nothing in the SDK uses them).

## Version lines

Same lanes and version rules as vuTelemetry-android, so the fork and the SDK ship one version train. The fork releases a version first; the SDK's preflight waits until that fork BOM is on Maven Central.

| Branch | `version` in `gradle.properties` | Published on merge | Permanent |
|--------|----------------------------------|--------------------|-----------|
| `working` | `x.y.z` (next train, e.g. `1.1.0`) | `x.y.z-SNAPSHOT` | no, overwritten |
| `rc/*` | `x.y.z-rc.N` | `x.y.z-rc.N` + tag `vx.y.z-rc.N` + GitHub pre-release | yes |
| `release/*` | `x.y.z` | `x.y.z` + tag `vx.y.z` + GitHub release | yes |

`working` is the development line for the next release; `rc/1.0` only takes patch fixes, which are then merged into `working` (merge commit, not squash). `develop` is no longer used.

Every PR into `rc/*` or `release/*` must carry a version that is not on Maven Central yet, so it bumps `version` (`1.0.0-rc.1` → `1.0.0-rc.2`). [`preflight.sh`](.github/scripts/preflight.sh) checks this on the PR and again before publishing; Central also refuses to overwrite a release. Nobody creates tags by hand: CI tags the commit it published.

Configured in root [`gradle.properties`](gradle.properties):

- `version=1.0.0-rc.1` — the version without `-SNAPSHOT`; `-Pfinal=true` publishes it as is
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
| [PR build](.github/workflows/pr-check.yaml) | every PR | nothing; runs `preflight` against the PR's base branch, then `check` |
| [Publish Maven Central Snapshot](.github/workflows/publish-maven-central-snapshot.yml) | push (merge) to `working` (or manual run) | `verify` (preflight + `check`), then `publish` of `x.y.z-SNAPSHOT` after a `maven-central` reviewer approves |
| [Release](.github/workflows/release.yml) | push (merge) to `rc/**` or `release/**` | `verify` (preflight + `check`), then `publish` after approval: the release, the tag and the GitHub release |

`publish` runs in the `maven-central` environment: one of its required reviewers (`ashishz-vunet`, `gopal-vunet`, `ganeshnk-vunet`, `sid-vunet`) approves in the Actions run before anything is uploaded. The environment exists with those required reviewers (Settings → Environments); do not delete it: a missing environment is recreated unprotected and the publish would run unapproved.

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

PREFLIGHT_BRANCH=rc/1.0 bash .github/scripts/preflight.sh

./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository \
  -Pfinal=true \
  --no-parallel --no-configuration-cache --no-build-cache
```

## Verify published artifacts

Snapshots (wait ~15–30 min):

```bash
curl -s "https://central.sonatype.com/repository/maven-snapshots/com/vunetsystems/agent/android/agent-android-runtime/maven-metadata.xml"
```

Releases:

```bash
curl -s "https://repo1.maven.org/maven2/com/vunetsystems/agent/android/agent-android-runtime/maven-metadata.xml"
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
