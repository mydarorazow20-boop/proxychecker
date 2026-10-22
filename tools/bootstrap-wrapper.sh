#!/bin/sh
# Generates the OFFICIAL Gradle Wrapper (gradle-wrapper.jar, gradlew, gradlew.bat,
# gradle-wrapper.properties) for this project. Needs network access, curl, unzip,
# sha256sum (or shasum) and JDK 17+.
#
# What it does:
#   1. downloads the Gradle distribution and its published .sha256 file from
#      services.gradle.org and verifies the download against that checksum,
#   2. runs `gradle wrapper` from that distribution in an EMPTY temporary
#      directory (so the project's own plugins are not resolved yet),
#   3. copies the generated wrapper files into this project.
# The wrapper JAR therefore comes from Gradle itself; nothing is hand-written.
#
# Usage: sh tools/bootstrap-wrapper.sh
# Optional: GRADLE_VERSION (default 8.9), GRADLE_DIST_BASE_URL (mirror, for testing),
#           GRADLE_DIST_SHA256 (a pinned checksum; defaults to the 8.9 value from
#           https://gradle.org/release-checksums/. The download must match BOTH this
#           pin and the .sha256 file published next to it).
set -eu

GRADLE_VERSION="${GRADLE_VERSION:-8.9}"
# Pinned checksum of gradle-8.9-bin.zip (gradle.org/release-checksums). For another
# GRADLE_VERSION, supply GRADLE_DIST_SHA256 or the pin check is skipped.
if [ "$GRADLE_VERSION" = "8.9" ]; then
  PINNED_SHA256="${GRADLE_DIST_SHA256:-d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab}"
else
  PINNED_SHA256="${GRADLE_DIST_SHA256:-}"
fi
BASE_URL="${GRADLE_DIST_BASE_URL:-https://services.gradle.org/distributions}"
ROOT=$(cd "$(dirname "$0")/.." && pwd -P)

for tool in curl unzip java; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: '$tool' is required." >&2; exit 1; }
done

if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
  sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
  echo "ERROR: sha256sum or shasum is required." >&2; exit 1
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT INT TERM

echo "Downloading Gradle $GRADLE_VERSION ..."
curl -fsSL -o "$TMP/gradle.zip" "$BASE_URL/gradle-$GRADLE_VERSION-bin.zip"
curl -fsSL -o "$TMP/gradle.zip.sha256" "$BASE_URL/gradle-$GRADLE_VERSION-bin.zip.sha256"

EXPECTED=$(tr -d ' \n\r' < "$TMP/gradle.zip.sha256" | cut -c1-64)
ACTUAL=$(sha256 "$TMP/gradle.zip")
if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "ERROR: checksum mismatch for gradle-$GRADLE_VERSION-bin.zip" >&2
  echo "  expected: $EXPECTED" >&2
  echo "  actual:   $ACTUAL" >&2
  exit 1
fi
if [ -n "$PINNED_SHA256" ] && [ "$PINNED_SHA256" != "$ACTUAL" ]; then
  echo "ERROR: download does not match the pinned checksum for gradle-$GRADLE_VERSION-bin.zip" >&2
  echo "  pinned: $PINNED_SHA256" >&2
  echo "  actual: $ACTUAL" >&2
  exit 1
fi
echo "Checksum OK: $ACTUAL"

unzip -q "$TMP/gradle.zip" -d "$TMP/dist"
mkdir "$TMP/empty"
( cd "$TMP/empty" && "$TMP/dist/gradle-$GRADLE_VERSION/bin/gradle" wrapper \
    --gradle-version "$GRADLE_VERSION" \
    --distribution-type bin \
    --gradle-distribution-sha256-sum "$EXPECTED" )

for f in gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties; do
  [ -f "$TMP/empty/$f" ] || { echo "ERROR: generated wrapper is missing $f" >&2; exit 1; }
done

mkdir -p "$ROOT/gradle/wrapper"
cp "$TMP/empty/gradlew" "$TMP/empty/gradlew.bat" "$ROOT/"
cp "$TMP/empty/gradle/wrapper/gradle-wrapper.jar" "$TMP/empty/gradle/wrapper/gradle-wrapper.properties" "$ROOT/gradle/wrapper/"
chmod +x "$ROOT/gradlew"

echo "Wrapper installed. Verify with:  ./gradlew --version"
