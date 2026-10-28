#!/usr/bin/env bash
# Captures every showcase screen in light and dark on a running emulator.
set -euo pipefail
cd "$(dirname "$0")"

PKG=com.walsoup.linen.showcase
OUT=../../screenshots
mkdir -p "$OUT"

adb install -r showcase/build/outputs/apk/debug/showcase-debug.apk
adb shell wm size 720x1560
adb shell wm density 320

demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
freeze_status_bar() {
  demo enter
  demo clock -e hhmm 0900
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4 -e mobile hide
  demo notifications -e visible false
}
adb shell settings put global sysui_demo_allowed 1

for mode in light dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2
  for screen in catalog list detail settings search editor onboarding live review dialog sheet snackbar controls colors type; do
    adb shell am force-stop "$PKG"
    adb shell am start -W -n "$PKG/.MainActivity" -e screen "$screen" > /dev/null
    freeze_status_bar
    sleep 3
    adb exec-out screencap -p > "$OUT/$screen-$mode.png"
    echo "captured $screen-$mode"
  done
done
