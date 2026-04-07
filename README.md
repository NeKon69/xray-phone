# xray-phone

## Warning

**BEWARE: THIS PROJECT IS EXPERIMENTAL AND WAS PUT TOGETHER WITH HEAVY AI ASSISTANCE. THE AUTHOR TAKES NO RESPONSIBILITY FOR WHAT HAPPENS TO YOUR PHONE, NETWORK, DATA, OR CONFIGURATION. USE IT ONLY IF YOU UNDERSTAND THE RISKS.**

`xray-phone` is a small Android companion app plus a Termux CLI for running Xray as a phone-wide VPN on unrooted Android.

It is designed around this flow:

- Android app owns the actual VPN through `VpnService`
- Termux `xrayctl` manages it
- your Xray config stays in Termux and is pushed into the app only when you run `load`

## What It Does

- provides a phone-wide VPN on unrooted Android
- lets you manage it from Termux with `xrayctl`
- supports `on`, `off`, `status`, `load`, `autostart 1|0`
- supports boot autostart
- uses the direct `tunFd -> Xray core` path through `libv2ray`

## What It Does Not Do

- it does not run as plain Xray inside Termux only
- it does not use Android root-only tricks
- it does not require a separate GUI VPN client app
- it does not rely on a local SOCKS bridge as the main VPN data path

## Why There Is An Android App At All

Unrooted Android requires an app-owned `VpnService` for full-device routing. Termux alone cannot claim that system VPN interface.

So the split is:

- Termux: controller and config source
- Android app: actual VPN owner and Xray runtime host

## Components

- `app/`: Android companion app
- `xrayctl`: Termux CLI
- `setup-phone.sh`: host-side one-shot installer over `adb`
- `termux-bootstrap.sh`: phone-side helper invoked inside Termux
- `ARCHITECTURE.md`: implementation notes

## Current Commands

Inside Termux:

```bash
xrayctl pair
xrayctl load [path]
xrayctl on
xrayctl off
xrayctl status
xrayctl autostart 1
xrayctl autostart 0
xrayctl info
xrayctl diag
xrayctl logs 100
```

Default config path is:

```bash
~/cfg.json
```

To use another config:

```bash
xrayctl load ~/other-config.json
xrayctl on
```

## Quick Start

Assumptions:

- `adb` works
- the phone already has Termux installed
- Xray already exists in Termux
- your config is in `~/cfg.json`

Build and install the debug APK:

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk" \
ANDROID_HOME="/opt/android-sdk" \
ANDROID_SDK_ROOT="/opt/android-sdk" \
gradle assembleDebug

adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
```

Then either set up manually or use the one-shot setup script.

## One-Shot Setup

Run from the host machine:

```bash
bash ./setup-phone.sh
```

By default it uses:

- APK: first signed release APK under `app/build/outputs/apk/release/`, otherwise `app/build/outputs/apk/debug/app-debug.apk`
- phone config path: `~/cfg.json`
- start immediately: yes
- enable autostart: yes

You can override those with environment variables:

```bash
DEVICE_CONFIG_PATH='~/other-config.json' START_NOW=0 ENABLE_AUTOSTART=1 bash ./setup-phone.sh
```

Important:

- the host cannot directly read `~/cfg.json` from Termux private storage
- `setup-phone.sh` solves this by launching a command inside Termux
- the actual config read still happens locally on the phone

## Manual Setup

Push the helper files:

```bash
adb push ./xrayctl /sdcard/Download/xrayctl
adb push ./termux-bootstrap.sh /sdcard/Download/termux-bootstrap.sh
```

Install `xrayctl` inside Termux:

```bash
mkdir -p ~/bin
cp /sdcard/Download/xrayctl ~/bin/xrayctl
chmod 700 ~/bin/xrayctl
```

Grant VPN permission once:

```bash
xrayctl prepare
```

Pair, load config, and start:

```bash
xrayctl pair
xrayctl load
xrayctl on
```

Enable boot autostart:

```bash
xrayctl autostart 1
```

## Security Model

Control commands are protected with a per-device token.

Flow:

1. `xrayctl pair` asks the app to generate a token
2. the token is stored in:

```bash
~/.config/xrayctl/token
```

3. later commands send that token back to the app

Notes:

- this is meant to block random third-party apps from controlling the VPN
- it is not meant to block `root`
- Android sender attribution for `am broadcast` differs across OEM builds, so token auth is the main protection layer

## Boot Behavior

The app is direct-boot aware and stores its runtime state in device-protected storage.

If these are all true:

- VPN permission was already granted
- a config has been loaded
- `autostart=1`

then the app should start the VPN on boot, often very early.

## Build Notes

Debug build:

```bash
gradle assembleDebug
```

Release build:

```bash
gradle assembleRelease
```

Current release output is:

```bash
app/build/outputs/apk/release/app-release-unsigned.apk
```

That file is unsigned, so it is not a normal installable release APK yet.

`setup-phone.sh` prefers a signed release APK automatically if one exists. If there is no signed release build, it falls back to the installable debug APK.

## Reuse On Another Phone

Yes, the same APK can be reused on another phone if:

- Android version is compatible
- CPU architecture is compatible with the bundled native libraries

Per-phone setup is still required:

- install APK
- grant VPN permission once
- pair token
- load config
- optional autostart enable

## Diagnostics

From Termux:

```bash
xrayctl status
xrayctl info
xrayctl diag
xrayctl doctor
xrayctl logs 100
```

From `adb`:

```bash
adb shell ip addr show tun0
adb shell ip route
adb shell dumpsys connectivity | rg 'VPN CONNECTED|tun0|dev.xrayphone'
```

## Known Limitations

- the host-side setup script still relies on Termux UI automation for the final bootstrap step
- some Android/OEM builds attribute `am broadcast` strangely
- release APK signing is not set up yet
- the local VPN interface parameters are currently app-side values, not derived from your config

## Repo Status

This repo is currently more of an experimental working prototype than a polished product.
