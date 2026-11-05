#!/usr/bin/env bash
# Installs the debug APK on the running emulator, starts it, and fails if the app crashes.
set -u
PKG=com.younew.tv
APK=$(ls apk/debug/*_x86.apk 2>/dev/null | head -1)
[ -z "$APK" ] && APK=$(ls apk/debug/*universal*.apk | head -1)
echo "Installing $APK"
adb install -r "$APK" || exit 1
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 30
adb shell dumpsys window windows | grep -E "mCurrentFocus|mFocusedApp" || true
adb logcat -d > logcat.txt
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png screen.png || true
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "The app crashed:"
  grep -A40 "FATAL EXCEPTION" logcat.txt
  exit 1
fi
if adb shell ps | grep -q "$PKG"; then
  echo "App started and is still running"
else
  echo "App process is not running"
  grep -iE "younew|AndroidRuntime" logcat.txt | tail -60
  exit 1
fi
