#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
ADB_BIN="${ADB:-adb}"
APP_ID="dev.xrayphone"
TERMUX_PKG="com.termux"
DEFAULT_DEBUG_APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
XRAYCTL_PATH="${XRAYCTL_PATH:-$ROOT_DIR/xrayctl}"
BOOTSTRAP_PATH="${BOOTSTRAP_PATH:-$ROOT_DIR/termux-bootstrap.sh}"
DEVICE_CONFIG_PATH="${DEVICE_CONFIG_PATH:-~/cfg.json}"
ENABLE_AUTOSTART="${ENABLE_AUTOSTART:-1}"
START_NOW="${START_NOW:-1}"
SKIP_PERMISSION="${SKIP_PERMISSION:-0}"
PHONE_DOWNLOAD_DIR="/sdcard/Download"

pick_default_apk() {
  local candidate
  for candidate in "$ROOT_DIR"/app/build/outputs/apk/release/*.apk; do
    [ -f "$candidate" ] || continue
    case "$(basename -- "$candidate")" in
      *unsigned*.apk)
        continue
        ;;
    esac
    printf '%s\n' "$candidate"
    return 0
  done

  printf '%s\n' "$DEFAULT_DEBUG_APK"
}

APK_PATH="${APK_PATH:-$(pick_default_apk)}"

die() {
  printf 'error: %s\n' "$1" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

adb_shell() {
  "$ADB_BIN" shell "$@"
}

ensure_connected() {
  local serial
  serial="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
  [ -n "$serial" ] || die "no adb device connected"
}

ensure_pkg() {
  local pkg="$1"
  adb_shell pm list packages | grep -q "package:${pkg}" || die "required package not installed: ${pkg}"
}

ensure_file() {
  [ -f "$1" ] || die "missing file: $1"
}

ensure_installable_apk() {
  case "$(basename -- "$APK_PATH")" in
    *unsigned*.apk)
      die "APK is unsigned and not installable via adb: $APK_PATH"
      ;;
  esac
}

encode_for_input() {
  printf '%s' "$1" | sed 's/ /%s/g'
}

launch_termux() {
  adb_shell am start -n com.termux/.app.TermuxActivity >/dev/null
  sleep 1
}

termux_run() {
  local command="$1"
  local encoded
  encoded="$(encode_for_input "$command")"
  launch_termux
  adb_shell input text "$encoded"
  adb_shell input keyevent 66
}

extract_token() {
  local output="$1"
  local token
  token="${output#*data=\"token=}"
  [ "$token" != "$output" ] || return 1
  token="${token%%\"*}"
  printf '%s\n' "$token"
}

pair_token() {
  local output
  output="$($ADB_BIN shell am broadcast --user 0 -n "$APP_ID/.control.ControlReceiver" -a "$APP_ID.action.PAIR")"
  extract_token "$output" || die "failed to pair app: $output"
}

set_autostart() {
  local token="$1"
  if [ "$ENABLE_AUTOSTART" != "1" ]; then
    return
  fi

  $ADB_BIN shell am broadcast --user 0 -n "$APP_ID/.control.ControlReceiver" -a "$APP_ID.action.SET_AUTOSTART" --ez enabled true --es token "$token" >/dev/null
}

print_status() {
  local token="$1"
  $ADB_BIN shell am broadcast --user 0 -n "$APP_ID/.control.ControlReceiver" -a "$APP_ID.action.STATUS" --es token "$token"
}

main() {
  need_cmd "$ADB_BIN"
  need_cmd awk
  need_cmd grep
  need_cmd sed
  ensure_connected
  ensure_pkg "$TERMUX_PKG"
  ensure_file "$APK_PATH"
  ensure_file "$XRAYCTL_PATH"
  ensure_file "$BOOTSTRAP_PATH"
  ensure_installable_apk

  printf 'Installing APK...\n'
  "$ADB_BIN" install --no-streaming -r "$APK_PATH"

  printf 'Pushing controller files...\n'
  "$ADB_BIN" push "$XRAYCTL_PATH" "$PHONE_DOWNLOAD_DIR/xrayctl" >/dev/null
  "$ADB_BIN" push "$BOOTSTRAP_PATH" "$PHONE_DOWNLOAD_DIR/termux-bootstrap.sh" >/dev/null

  if [ "$SKIP_PERMISSION" != "1" ]; then
    printf 'Opening VPN permission screen...\n'
    adb_shell am start --user 0 -n "$APP_ID/.ui.PrepareVpnActivity" >/dev/null
    printf 'Tap Allow on the phone if prompted, then press Enter here to continue... '
    read -r _
  fi

  printf 'Pairing control token...\n'
  token="$(pair_token)"

  printf 'Installing xrayctl into Termux and loading config...\n'
  termux_run "bash $PHONE_DOWNLOAD_DIR/termux-bootstrap.sh $token $DEVICE_CONFIG_PATH $START_NOW $ENABLE_AUTOSTART"
  sleep 3

  printf 'Setting app autostart...\n'
  set_autostart "$token"

  printf '\nCurrent status:\n'
  print_status "$token"

  printf '\nDone.\n'
  printf 'If Termux automation missed focus on this phone, run inside Termux:\n'
  printf '  bash %s/termux-bootstrap.sh %s %s %s %s\n' "$PHONE_DOWNLOAD_DIR" "$token" "$DEVICE_CONFIG_PATH" "$START_NOW" "$ENABLE_AUTOSTART"
}

main "$@"
