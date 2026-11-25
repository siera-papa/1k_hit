#!/usr/bin/env bash
# Single-command Gradle launcher for the Linux project.
# Uses a compatible cached Gradle installation or downloads the official 8.13 distribution.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION=8.13

if [[ -n "${GRADLE_HOME:-}" && -x "${GRADLE_HOME}/bin/gradle" ]]; then
  cd "$ROOT"
  exec "${GRADLE_HOME}/bin/gradle" "$@"
fi

# Reuse the normal Gradle wrapper cache, when a previous app has downloaded it.
for existing in "$HOME"/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle; do
  if [[ -x "$existing" ]]; then
    cd "$ROOT"
    exec "$existing" "$@"
  fi
done

BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/distributions"
GRADLE_BIN="$BASE/gradle-$VERSION/bin/gradle"
if [[ ! -x "$GRADLE_BIN" ]]; then
  command -v unzip >/dev/null || { echo 'Nedostaje unzip: sudo apt install unzip' >&2; exit 1; }
  mkdir -p "$BASE"
  TEMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TEMP_DIR"' EXIT
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null; then
    curl -fL --retry 3 --connect-timeout 20 "$URL" -o "$TEMP_DIR/gradle.zip"
  elif command -v wget >/dev/null; then
    wget -O "$TEMP_DIR/gradle.zip" "$URL"
  else
    echo 'Nedostaje curl ili wget.' >&2
    exit 1
  fi
  unzip -q "$TEMP_DIR/gradle.zip" -d "$TEMP_DIR"
  mv "$TEMP_DIR/gradle-$VERSION" "$BASE/gradle-$VERSION"
fi
cd "$ROOT"
exec "$GRADLE_BIN" "$@"
