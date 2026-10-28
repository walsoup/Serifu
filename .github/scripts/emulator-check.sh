#!/bin/bash
# Emulator verification for CI: instrumented pipeline test + home screenshot.
# Fails loudly if the app never reaches the foreground (no false-green).
set -u

install_apk() {
  for i in 1 2 3; do
    if adb install -r "$1"; then return 0; fi
    echo "install attempt $i failed, retrying"
    sleep 10
  done
  return 1
}

install_apk app/build/outputs/apk/debug/app-debug.apk
install_apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm list packages | grep app.serifu || { echo "INSTALL VERIFY FAILED"; exit 1; }
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell am instrument -w -e class app.serifu.PipelineOnTestPageTest app.serifu.test/androidx.test.runner.AndroidJUnitRunner

for attempt in 1 2 3; do
  adb shell am start -W -n app.serifu/.ui.MainActivity || true
  sleep 12
  FOCUS=$(adb shell dumpsys window | grep -m1 mCurrentFocus || true)
  echo "attempt $attempt focus: $FOCUS"
  case "$FOCUS" in
    *app.serifu*) break ;;
  esac
  # Slow emulator: dismiss a system ANR dialog via its Wait button if present.
  adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window_dump.xml /tmp/dump.xml >/dev/null 2>&1 || true
  B=$(grep -o 'text="Wait"[^>]*bounds="\[[0-9,]*\]\[[0-9,]*\]"' /tmp/dump.xml 2>/dev/null | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | head -1 || true)
  if [ -n "$B" ]; then
    # shellcheck disable=SC2086
    set -- $(echo "$B" | tr -d '[]' | tr ',' ' ')
    adb shell input tap $((( $1 + $3 ) / 2)) $((( $2 + $4 ) / 2))
    sleep 5
  fi
done
adb exec-out screencap -p > screenshot-home.png
ls -la screenshot-home.png
FINAL=$(adb shell dumpsys window | grep -m1 mCurrentFocus || true)
echo "final focus: $FINAL"
case "$FINAL" in
  *app.serifu*) ;;
  *) echo "APP NOT FOREGROUND"; exit 1 ;;
esac
