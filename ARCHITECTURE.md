# Xray Phone Architecture

## Goal

Provide a phone-wide VPN on unrooted Android, controlled from Termux through `xrayctl`, while the actual VPN runtime lives inside an Android app.

## Constraints

- Plain Xray in Termux is not enough for a full-device VPN on unrooted Android.
- Android requires an app-owned `VpnService` for device-wide routing.
- The host machine cannot directly read `~/cfg.json` inside Termux private storage.

## Current Shape

1. Android app owns:
   - `VpnService`
   - boot receiver
   - foreground notification
   - direct-boot-aware app storage
   - embedded `libv2ray` runtime

2. Termux `xrayctl` controls the app:
   - `pair`
   - `load [path]`
   - `on`
   - `off`
   - `status`
   - `autostart 1|0`
   - diagnostics helpers such as `info`, `diag`, `logs`, `doctor`

3. Config handoff:
   - `xrayctl load` reads `~/cfg.json` locally on the phone by default
   - it base64-encodes the file
   - it sends the config bytes to the app through an explicit broadcast
   - the app stores the config in app-private storage

This keeps the config flow local to the phone. The host machine does not need to inspect the config.

## VPN Data Path

The current implementation does not use a local SOCKS bridge as the traffic path.

Flow:

1. Android creates `tun0` through `VpnService`
2. the app passes the TUN file descriptor directly into the mobile Xray core
3. Android routes default traffic into `tun0`
4. Xray forwards traffic out over the underlying network

The app uses the direct `tunFd -> Xray core` path via `libv2ray`.

## Local Tunnel Parameters

The Android app currently configures the local VPN interface with app-side values such as:

- tunnel IPv4 address
- tunnel IPv6 address
- DNS servers
- MTU

These are local `VpnService.Builder` parameters for the phone's virtual interface, not your remote server settings.

## Boot Flow

1. User grants VPN permission once
2. User enables `xrayctl autostart 1`
3. App stores config, token, and autostart flag in device-protected storage
4. App receives `LOCKED_BOOT_COMPLETED` or `BOOT_COMPLETED`
5. App checks:
   - autostart enabled
   - VPN permission already granted
   - stored config exists
6. App starts foreground `VpnService`
7. Service restores the Xray runtime and starts the VPN

The app is `directBootAware`, so it can start much earlier in the boot process on supported Android builds.

## Security Model

The control plane is explicit-broadcast based and currently hardened with:

- sender filtering
- per-device control token

Normal control flow is:

1. app generates a token on `pair`
2. token is stored in Termux at `~/.config/xrayctl/token`
3. later control commands must include that token

Notes:

- `root` cannot be meaningfully blocked
- Android sender identity for `am broadcast` varies by device/OEM build
- on some builds, `adb`/Termux `am` commands arrive with stripped sender identity, so the token is the main trust boundary there

## Reusable Setup

Host-side installer:

- `setup-phone.sh`

Termux-side bootstrap:

- `termux-bootstrap.sh`

The intended setup flow is:

1. install APK with `adb`
2. push `xrayctl` and bootstrap helper to shared storage
3. grant VPN permission once
4. pair token
5. run bootstrap inside Termux
6. bootstrap installs `xrayctl`, writes the token, loads config, and optionally enables autostart and starts the VPN
