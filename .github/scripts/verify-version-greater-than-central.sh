#!/bin/bash -e

new_version=$(.github/scripts/get-version.sh)
metadata_url="https://repo1.maven.org/maven2/com/vunetsystems/opentelemetry/android/android-agent/maven-metadata.xml"

echo "Checking release version '$new_version' against Maven Central..."

if ! .github/scripts/parse-version.sh "$new_version" > /dev/null; then
  echo "Release version must be a final semver (set -Pfinal=true when publishing releases)."
  exit 1
fi

metadata=$(curl -sf "$metadata_url" || true)
if [[ -z "$metadata" ]]; then
  echo "No existing release found on Maven Central; first publish is allowed."
  exit 0
fi

latest=$(echo "$metadata" | sed -n 's:.*<latest>\([^<]*\)</latest>.*:\1:p' | head -1)
if [[ -z "$latest" ]]; then
  latest=$(echo "$metadata" | sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' | grep -v SNAPSHOT | sort -V | tail -1)
fi

if [[ -z "$latest" ]]; then
  echo "No existing non-SNAPSHOT release found on Maven Central; first publish is allowed."
  exit 0
fi

echo "Latest release on Maven Central: $latest"

if [[ "$(printf '%s\n%s\n' "$latest" "$new_version" | sort -V | tail -1)" == "$new_version" && "$new_version" != "$latest" ]]; then
  echo "Release version $new_version is greater than $latest."
  exit 0
fi

echo "Release version $new_version must be greater than latest Maven Central release $latest."
exit 1
