#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
BUILD_TARGET="${1:-debug}"
DEFAULT_JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
DEFAULT_ANDROID_SDK_ROOT="/opt/android-sdk"

case "$BUILD_TARGET" in
  debug)
    GRADLE_TASK="assembleDebug"
    ;;
  release)
    GRADLE_TASK="assembleRelease"
    ;;
  *)
    printf 'usage: %s [debug|release]\n' "$0" >&2
    exit 2
    ;;
esac

"$ROOT_DIR/fetch-deps.sh"

if [ -z "${JAVA_HOME:-}" ] && [ -x "$DEFAULT_JAVA_HOME/bin/javac" ]; then
  export JAVA_HOME="$DEFAULT_JAVA_HOME"
fi

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -d "$DEFAULT_ANDROID_SDK_ROOT" ]; then
  export ANDROID_HOME="$DEFAULT_ANDROID_SDK_ROOT"
fi

if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

exec gradle "$GRADLE_TASK"
