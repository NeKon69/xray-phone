#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
LIB_DIR="$ROOT_DIR/app/libs"
LIBV2RAY_VERSION="${LIBV2RAY_VERSION:-v26.3.27}"
LIBV2RAY_AAR_URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${LIBV2RAY_VERSION}/libv2ray.aar"
LIBV2RAY_AAR_PATH="$LIB_DIR/libv2ray.aar"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'error: missing command: %s\n' "$1" >&2
    exit 1
  }
}

need_cmd mkdir

mkdir -p "$LIB_DIR"

if [ -f "$LIBV2RAY_AAR_PATH" ]; then
  printf 'libv2ray already present: %s\n' "$LIBV2RAY_AAR_PATH"
  exit 0
fi

printf 'downloading libv2ray %s\n' "$LIBV2RAY_VERSION"

if command -v curl >/dev/null 2>&1; then
  if curl -fL "$LIBV2RAY_AAR_URL" -o "$LIBV2RAY_AAR_PATH"; then
    printf 'saved: %s\n' "$LIBV2RAY_AAR_PATH"
    exit 0
  fi
fi

if command -v gh >/dev/null 2>&1; then
  rm -f "$LIBV2RAY_AAR_PATH"
  gh release download "$LIBV2RAY_VERSION" -R 2dust/AndroidLibXrayLite -p libv2ray.aar -D "$LIB_DIR"
  printf 'saved: %s\n' "$LIBV2RAY_AAR_PATH"
  exit 0
fi

printf 'error: failed to download libv2ray. install curl or gh, and check network access to GitHub\n' >&2
exit 1
