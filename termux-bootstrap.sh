#!/data/data/com.termux/files/usr/bin/bash

set -eu

TOKEN="${1:-}"
CONFIG_PATH="${2:-$HOME/cfg.json}"
START_NOW="${3:-1}"
ENABLE_AUTOSTART="${4:-1}"

if [ -z "$TOKEN" ]; then
  printf 'usage: termux-bootstrap.sh <token> [config] [start_now] [autostart]\n' >&2
  exit 2
fi

XRAYCTL_SHARED="/sdcard/Download/xrayctl"
if [ ! -f "$XRAYCTL_SHARED" ]; then
  printf 'shared xrayctl not found in /sdcard/Download/\n' >&2
  exit 4
fi

mkdir -p "$HOME/bin" "$HOME/.config/xrayctl"
cp "$XRAYCTL_SHARED" "$HOME/bin/xrayctl"
chmod 700 "$HOME/bin/xrayctl"

umask 077
printf '%s\n' "$TOKEN" > "$HOME/.config/xrayctl/token"
chmod 600 "$HOME/.config/xrayctl/token"

if [ ! -f "$CONFIG_PATH" ]; then
  printf 'config not found: %s\n' "$CONFIG_PATH" >&2
  exit 3
fi

"$HOME/bin/xrayctl" load "$CONFIG_PATH"

if [ "$ENABLE_AUTOSTART" = "1" ]; then
  "$HOME/bin/xrayctl" autostart 1
fi

if [ "$START_NOW" = "1" ]; then
  "$HOME/bin/xrayctl" on
fi

printf 'termux bootstrap complete\n'
