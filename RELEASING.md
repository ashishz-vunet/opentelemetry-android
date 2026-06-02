# Releasing

This documents describes the manual steps required to publish a release to maven central.

## Fork policy

For this fork, do not publish to Maven Central.

- Publish fork artifacts to GitHub Packages (`vunetsystems/opentelemetry-android`).
- Use `-PpublishTarget=github` for all publication from this repository (default in `build.gradle.kts`).
- Sonatype release flow in `.github/workflows/release.yml` is reserved for upstream and is blocked for forks.

## Publishing to GitHub Packages (VuNet dev line)

### Prerequisites

- `gradle.properties`: `version`, `otel.version.suffix=dev`, `otel.publish.alpha=false`
- GitHub credentials with `packages:write` for `vunetsystems/opentelemetry-android`
- Set in `local.properties` or environment:
  - `gpr.user` / `GITHUB_ACTOR`
  - `gpr.key` / `GITHUB_TOKEN`

### Local verification before publish

```bash
./gradlew spotlessApply
./gradlew check
./gradlew apiCheck

# Optional: inspect local POM versions
./gradlew publishToMavenLocal -PpublishTarget=github
```

Confirm modules report the same version, e.g. `0.0.1-SNAPSHOT-dev`:

```bash
./gradlew :android-agent:properties :instrumentation:crash:properties -q | grep '^version:'
```

### Publish all modules

```bash
./gradlew publish -PpublishTarget=github
```

CI may also run `.github/workflows/publish-github-packages.yml` when a PR merges to `develop`.

### Troubleshooting publish failures

| HTTP status | Meaning | Action |
|-------------|---------|--------|
| **401** | Bad or missing token | Regenerate a PAT with `write:packages` / `read:packages`; set `gpr.user` and `gpr.key` in `local.properties` |
| **403** | Token lacks permission or repo access | Ensure the user can publish to `vunetsystems/opentelemetry-android` |
| **402 Payment Required** | GitHub Packages storage/billing limit for the org | Org admin: [GitHub billing](https://github.com/settings/billing) → increase Packages quota or free storage; delete old package versions under **Packages** |

If publish fails partway through, fix the org issue and re-run `./gradlew publish -PpublishTarget=github` (Gradle may skip unchanged artifacts).

### Consumer BOM (vuTelemetry and apps)

```kotlin
dependencies {
    api(platform("com.vunetsystems.opentelemetry.android:opentelemetry-android-bom:0.0.1-SNAPSHOT-dev"))
    implementation("com.vunetsystems.opentelemetry.android:android-agent")
    // other artifacts without version — BOM aligns versions
}
```

After each publish, record the BOM version and update vuTelemetry-android `vunet.stack.version` / OTel BOM pin to match.

### Pinned QA build (optional)

Non-SNAPSHOT artifacts for a fixed test stack:

```bash
./gradlew publish -PpublishTarget=github -Pfinal=true
```

Produces `0.0.1-dev` (no `-SNAPSHOT`) for all modules.

## Release cadence

This repository roughly targets monthly minor releases from the `main` branch.
These releases are generally cut on the Tuesday after the third Monday of the month, roughly a
week after the monthly minor release of [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation).

## Update Milestones

First, let's deal with any milestones:

- Open [the list of active milestones](https://github.com/open-telemetry/opentelemetry-android/milestones)
  to see if there is a milestone open for the version you are about to release.
- If there is no milestone for this version, begin prepping the release (below)
- If there is a milestone for this release:
    - If there are no remaining issue or PRs associated with the milestone, you may close the
      milestone and proceed to prepping the release (below).
    - If there is active work remaining, you should probably stop the release.
      Alternately, you could move the work to a future milestone, but probably want to discuss this
      with other contributors first.


## Prepare the release

Next, we need to prepare the release. This creates a versioned release branch, These are the steps to follow:

- Review the recent [list of open PRs](https://github.com/open-telemetry/opentelemetry-android/pulls)
  to determine if any need to be merged before cutting a release.
- Make sure that the `gradle.properties` version property is set to the value you want to release.
  This must be different than the most recent release number (typically one minor version increase).
- Merge a pull request to `main` branch that updates the `CHANGELOG.md`.
    - The heading for the unreleased entries must be `## Unreleased`.
    - Use [this action](https://github.com/open-telemetry/opentelemetry-android/actions/workflows/draft-change-log-entries.yaml) as a starting point for writing the change log entries. It will print a draft in the console that you can copy to create your PR.
- Go to the
  [prepare-release-branch action](https://github.com/open-telemetry/opentelemetry-android/actions/workflows/prepare-release-branch.yml)
  in Github and click on "Run workflow". This creates the release branch and does some prep.
- After the workflow finishes, it will have created 2 PRs -- one against `main` branch and
  one against the release branch. Review and merge these two PRs before running the release
  job (below).

## Run the release

Ensure that the preparation PR (created above) has been first merged into the release branch.

- The "prepare" step above should have created a PR that updates the version number in
  `gradle.properties`. This PR must be approved and merged before the release workflow is started,
  otherwise the release job will fail (the process explicitly checks for the version in the
  CHANGELOG.md). Because the release workflow runs against a release branch, it is safe to
  merge the `gradle.properties` into `main`.
- Run the [Release workflow](https://github.com/open-telemetry/opentelemetry-android/actions/workflows/release.yml).
  - Press the "Run workflow" button, then select the release branch from the dropdown list,
    e.g. `release/v0.6.x`, and click the "Run workflow" button below that.
  - This workflow will publish the artifacts to maven central and will publish a GitHub release.
    The release will have release notes based on the CHANGELOG and will include `.zip` and
    `.tar.gz` bundles of the source code.

> Please note that the artifacts are published into maven central, which tends to have a delay of
> roughly half an hour, more or less, before making the newly published artifacts actually available
> for fetching them.
