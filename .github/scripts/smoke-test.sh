#!/usr/bin/env bash
# Installs the debug APK on the running emulator, starts it, and fails if the app crashes.
set -u
PKG=com.younew.tv
# Universal APK: it holds every ABI, so it runs on both the x86 and the x86_64 emulators
APK=$(ls apk/debug/*universal*.apk | head -1)
adb shell getprop ro.build.version.release
echo "Installing $APK"
adb install -r "$APK" || exit 1
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 30
adb shell dumpsys window windows | grep -E "mCurrentFocus|mFocusedApp" || true
adb logcat -d > logcat.txt
# /data/local/tmp: the Android 5 emulators have no writable SD card
adb shell screencap -p /data/local/tmp/screen.png && adb pull /data/local/tmp/screen.png screen.png || true
# Sign-in screen: must open without crashing and show the QR code (screenshot in the artifacts).
# It is not exported, so it is started as root (works on the "default" emulator images).
if adb root > /dev/null 2>&1; then
  sleep 3
  adb wait-for-device
  adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity" || true
  sleep 15
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  adb shell screencap -p /data/local/tmp/signin.png && adb pull /data/local/tmp/signin.png signin.png || true
  # Press OK on the first button ("Sign in on this TV"): opens the sign-in page on the TV
  adb shell input keyevent KEYCODE_DPAD_CENTER || true
  sleep 15
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  adb shell screencap -p /data/local/tmp/signin-tv.png && adb pull /data/local/tmp/signin-tv.png signin-tv.png || true
  # Open the sign-in screen again, then the second button: Google's page in a pop-up window inside the app
  # (started again instead of Back: on a TV without WebView the first button leaves the screen)
  adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity" || true
  sleep 6
  adb shell input keyevent KEYCODE_DPAD_DOWN || true
  sleep 1
  adb shell input keyevent KEYCODE_DPAD_CENTER || true
  sleep 15
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  adb shell screencap -p /data/local/tmp/signin-popup.png && adb pull /data/local/tmp/signin-popup.png signin-popup.png || true
  # The new Settings screen: open every page (0 Accounts ... 6 About), press Right and OK on the first option
  for page in 0 1 2 3 4 5 6; do
    adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.settings.SettingsScreenActivity" --ei page $page || true
    sleep 7
    adb shell input keyevent KEYCODE_DPAD_RIGHT || true
    sleep 1
    adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
    adb shell screencap -p /data/local/tmp/settings-$page.png && adb pull /data/local/tmp/settings-$page.png settings-$page.png || true
    adb shell input keyevent KEYCODE_BACK || true
    sleep 2
  done
  # Inline choice: open General, press OK on the first row (Boot to section) to unfold its list in the page,
  # then move down, pick a choice and check that nothing crashes
  adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.settings.SettingsScreenActivity" --ei page 1 || true
  sleep 7
  adb shell input keyevent KEYCODE_DPAD_CENTER || true
  sleep 2
  adb shell screencap -p /data/local/tmp/settings-choice-open.png && adb pull /data/local/tmp/settings-choice-open.png settings-choice-open.png || true
  adb shell input keyevent KEYCODE_DPAD_DOWN || true
  adb shell input keyevent KEYCODE_DPAD_CENTER || true
  sleep 3
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  adb shell screencap -p /data/local/tmp/settings-choice-done.png && adb pull /data/local/tmp/settings-choice-done.png settings-choice-done.png || true
  adb shell input keyevent KEYCODE_BACK || true
  adb logcat -d > logcat.txt
else
  echo "adb root not available: sign-in screen not checked"
fi

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
