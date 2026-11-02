#!/usr/bin/env bash
set -euo pipefail

# Fails when `version` in gradle.properties is the wrong shape for its branch, or is already
# released on Maven Central. Same rules as vuTelemetry-android's preflight, so both repos move on
# one version train: every PR into rc/* or release/* carries a version that is not on Central yet,
# and merging it publishes that version (release.yml).
#
# gradle.properties holds the version without -SNAPSHOT; the build appends it unless -Pfinal=true.
#
# Run locally from the repo root: PREFLIGHT_BRANCH=rc/1.0 bash .github/scripts/preflight.sh
# In CI the branch is the PR's base branch, or the pushed branch.

RELEASES="https://repo1.maven.org/maven2/com/vunetsystems/agent/android/agent-android-runtime"

# Prints why <version> is the wrong shape for <branch>; prints nothing when it is fine.
# Branches with no rule are not checked.
version_format_error() {
  local branch="$1" version="$2"
  case "$branch" in
    develop|working)
      [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
        echo "on $branch the version must be x.y.z (published as x.y.z-SNAPSHOT)" ;;
    rc/*)
      [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-rc\.[0-9]+$ ]] ||
        echo "on $branch the version must be x.y.z-rc.N" ;;
    release/*)
      [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
        echo "on $branch the version must be x.y.z, with no suffix" ;;
  esac
}

# Versions that must not already be on Central for <branch>: the version itself on rc/* and
# release/*, and the plain x.y.z on develop, working and rc/* (that final already shipped: bump).
versions_that_must_not_exist() {
  local branch="$1" version="$2"
  case "$branch" in
    develop|working) echo "$version" ;;
    rc/*) echo "$version"; echo "${version%%-rc.*}" ;;
    release/*) echo "$version" ;;
  esac
}

main() {
  local version branch problem v
  version=$("$(dirname "$0")/get-version.sh")
  if [ -z "$version" ]; then
    echo "preflight: version not found in gradle.properties" >&2
    exit 1
  fi

  branch="${PREFLIGHT_BRANCH:-${GITHUB_BASE_REF:-${GITHUB_REF_NAME:-}}}"
  if [ -z "$branch" ]; then
    echo "preflight: no branch given (set PREFLIGHT_BRANCH)" >&2
    exit 1
  fi

  problem=$(version_format_error "$branch" "$version")
  if [ -n "$problem" ]; then
    echo "preflight: ${version}: ${problem}" >&2
    exit 1
  fi
  for v in $(versions_that_must_not_exist "$branch" "$version"); do
    if curl -sfI "${RELEASES}/${v}/agent-android-runtime-${v}.pom" > /dev/null; then
      echo "preflight: ${v} is already released on Maven Central; bump version in gradle.properties" >&2
      exit 1
    fi
  done
  echo "preflight: ${version} fits ${branch} and is not released yet."
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
