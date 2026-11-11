#!/usr/bin/env bash
# Installs the debug APK on the running emulator, starts it, and fails if the app crashes.
set -u
PKG=com.younew.tv
FAILED=0

# Reads what is on the screen (uiautomator) into ui-<label>.xml and prints every text with its position.
ui_dump() {
  adb shell uiautomator dump /data/local/tmp/ui.xml > /dev/null 2>&1
  adb pull /data/local/tmp/ui.xml "ui-$1.xml" > /dev/null 2>&1
  echo "=== SCREEN $1"
  if [ ! -s "ui-$1.xml" ]; then echo "(no screen dump available)"; return 1; fi
  python3 - "ui-$1.xml" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', s):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    b = re.search(r'bounds="([^"]*)"', n)
    if t and t.group(1):
        print("  '%s' %s" % (t.group(1), b.group(1) if b else ''))
PY
}

# Looks for Android's "Application Error / Not Responding" window; if there is one, prints the crash log and fails.
check_crash() {
  if adb shell dumpsys window windows | grep -E "mCurrentFocus|mFocusedWindow" | grep -qE "Application Error|Not Responding"; then
    echo "APP ERROR DIALOG at: $1"
    adb logcat -d -b all -v threadtime 2>/dev/null | grep -B2 -A30 "FATAL EXCEPTION" | head -90
    adb logcat -d -b crash -v threadtime 2>/dev/null | tail -60
    FAILED=1
    adb shell input keyevent KEYCODE_ENTER || true
    adb shell input keyevent KEYCODE_BACK || true
  fi
}

# Fails when any text on the screen starts or ends outside the screen (a cut-off panel).
# The first bounds in the dump are the whole screen.
check_inside_screen() {
  local result
  result=$(python3 - "$1" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
nodes = re.findall(r'<node[^>]*>', s)
screen = None
bad = []
for n in nodes:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if screen is None:
        screen = (x2, y2)
        continue
    t = re.search(r' text="([^"]*)"', n)
    if t and t.group(1) and x2 > screen[0] + 1:
        bad.append("'%s' ends at x=%d (screen %d)" % (t.group(1), x2, screen[0]))
print("OK" if not bad else "CUT OFF: " + "; ".join(bad[:6]))
PY
)
  echo "CHECK nothing cut off ($2): $result"
  case "$result" in OK*) ;; *) FAILED=1 ;; esac
}

# The search keyboard: its corner keys must all be in the dump, with their whole box inside the screen
# (a key that touches the screen edge is taken as cut off: the dump clips boxes to the screen).
check_keyboard() {
  local result
  result=$(python3 - "$1" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
screen = None
found = {}
keys = ('A', 'G', 'V', 'DELETE', 'SPACE', 'SEARCH')
for n in re.findall(r'<node[^>]*>', s):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    box = tuple(map(int, b.groups()))
    if screen is None:
        screen = (box[2], box[3])
        continue
    t = re.search(r' text="([^"]*)"', n)
    if t and t.group(1) in keys and t.group(1) not in found:
        found[t.group(1)] = box
bad = []
for key in keys:
    if key not in found:
        bad.append("%s missing" % key)
        continue
    x1, y1, x2, y2 = found[key]
    if x1 < 0 or y1 < 0 or x2 >= screen[0] or y2 >= screen[1] or x2 - x1 < 8 or y2 - y1 < 8:
        bad.append("%s at [%d,%d][%d,%d] (screen %dx%d)" % (key, x1, y1, x2, y2, screen[0], screen[1]))
print("OK" if not bad else "NOT ALL ON SCREEN: " + "; ".join(bad))
PY
)
  echo "CHECK search keyboard on the screen ($2): $result"
  case "$result" in OK*) ;; *) FAILED=1 ;; esac
}

# Prints what the search box of the results screen shows.
box_text() {
  python3 - "$1" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
for n in re.findall(r'<node[^>]*>', s):
    if 'lb_search_text_editor"' in n:
        t = re.search(r' text="([^"]*)"', n)
        print(t.group(1) if t else '')
        break
PY
}

# Taps the Nth icon of the sidebar (1 = Search, the first; "last" = Settings), like a finger does.
# The collapsed sidebar shows icons without text, so they cannot be found by their name.
tap_sidebar_icon() {
  local coords
  coords=$(python3 - "$1" "$2" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
icons = []
for n in re.findall(r'<node[^>]*>', s):
    if 'id/header_icon"' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            icons.append(tuple(map(int, b.groups())))
if icons:
    x1, y1, x2, y2 = icons[-1] if sys.argv[2] == 'last' else icons[min(int(sys.argv[2]), len(icons)) - 1]
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
  if [ -n "$coords" ]; then
    adb shell input tap $coords
    echo "tapped sidebar icon $2 at $coords"
    return 0
  fi
  echo "no sidebar icons found on the screen, cannot tap"
  return 1
}

# The Home screen as a finger uses it: fresh start, tap Search (first sidebar icon), check its panel and keyboard;
# then tap Settings (last icon) and check that its page names are not cut off. $1 is a label for files and lines.
check_home_panels() {
  adb shell am force-stop "$PKG" || true
  sleep 2
  adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity" || true
  sleep 12
  check_crash "home ($1)"
  adb shell screencap -p /data/local/tmp/s.png && adb pull /data/local/tmp/s.png "home-$1.png" || true
  ui_dump "home-$1" || return 0
  tap_sidebar_icon "ui-home-$1.xml" 1 || true
  sleep 6
  check_crash "search panel ($1)"
  adb shell screencap -p /data/local/tmp/s.png && adb pull /data/local/tmp/s.png "search-home-$1.png" || true
  if ui_dump "search-home-$1"; then
    if has_text "ui-search-home-$1.xml" "SEARCH" && grep -q "Type or speak to search" "ui-search-home-$1.xml"; then
      echo "CHECK search panel in the Home screen ($1): SHOWN"
      check_keyboard "ui-search-home-$1.xml" "Home search panel, $1"
      tap_text "ui-search-home-$1.xml" "A" || true
      sleep 1
      tap_text "ui-search-home-$1.xml" "B" || true
      sleep 2
      ui_dump "search-home-$1-typed" || true
      if has_text "ui-search-home-$1-typed.xml" "ab"; then
        echo "CHECK Home search panel keys type ($1): WORKS"
      else
        echo "CHECK Home search panel keys type ($1): FAILED (no 'ab' on the screen)"
        FAILED=1
      fi
    else
      echo "CHECK search panel in the Home screen ($1): MISSING"
      FAILED=1
    fi
    tap_sidebar_icon "ui-search-home-$1.xml" last || true
    sleep 6
    check_crash "settings panel ($1)"
    adb shell screencap -p /data/local/tmp/s.png && adb pull /data/local/tmp/s.png "settings-home-$1.png" || true
    if ui_dump "settings-home-$1"; then
      check_not_clipped "ui-settings-home-$1.xml" "settings pages, $1"
    fi
  fi
}

# Fails when one of the Settings page names is clipped at its right side: its text box then ends exactly where
# the box of the panel ends (the dump clips boxes), instead of ending before it.
check_not_clipped() {
  local result
  result=$(python3 - "$1" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
names = ('Accounts', 'General', 'Player', 'Language and country', 'Language/Country', 'Subtitles', 'Appearance', 'About')
boxes = []
for n in re.findall(r'<node[^>]*>', s):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    t = re.search(r' text="([^"]*)"', n)
    if b and t and t.group(1):
        boxes.append((t.group(1), tuple(map(int, b.groups()))))
found = [(t, b) for t, b in boxes if t in ('Accounts', 'General', 'Player', 'Subtitles', 'About')]
if len(found) < 5:
    print("NOT SHOWN (page list not found)")
else:
    # every label of the list starts at the same x; a clipped one ends at the common right edge
    left = found[0][1][0]
    labels = [(t, b) for t, b in boxes if b[0] == left and abs(b[3] - b[1] - (found[0][1][3] - found[0][1][1])) <= 2]
    right = max(b[2] for t, b in labels)
    clipped = [t for t, b in labels if b[2] == right]
    print("OK" if len(clipped) <= 1 else "CLIPPED: " + ", ".join(clipped))
PY
)
  echo "CHECK names not cut off ($2): $result"
  case "$result" in OK*) ;; *) FAILED=1 ;; esac
}

has_text() { [ -s "$1" ] && grep -q "text=\"$2\"" "$1"; }

# Taps the middle of the first thing with this text, like a finger or a mouse click would.
tap_text() {
  local coords
  coords=$(python3 - "$1" "$2" <<'PY'
import sys, re
s = open(sys.argv[1], errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', s):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and b and t.group(1) == sys.argv[2]:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$coords" ]; then
    adb shell input tap $coords
    echo "tapped '$2' at $coords"
    return 0
  fi
  echo "'$2' not found on the screen, cannot tap"
  return 1
}
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
  # The redesigned sign-in screen: one screen, QR + code on the left, buttons on the right
  ui_dump signin || true
  if grep -q 'Sign in here' ui-signin.xml 2>/dev/null; then echo "CHECK sign-in screen buttons: SHOWN"; else echo "CHECK sign-in screen buttons: MISSING"; FAILED=1; fi
  check_inside_screen ui-signin.xml "sign-in screen"
  check_crash "sign-in screen"
  # OK on the first button ("Sign in here"): Google's page must open INSIDE this screen (a layer with a Back button).
  # A box without a WebView (API 26 emulator) shows a message instead and stays on the screen.
  adb shell input keyevent KEYCODE_DPAD_CENTER || true
  sleep 12
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  ui_dump signin-page || true
  if grep -q 'Google sign-in' ui-signin-page.xml 2>/dev/null; then echo "CHECK sign-in page inside the screen: SHOWN"; else echo "CHECK sign-in page inside the screen: not shown (no WebView on this emulator?)"; fi
  check_crash "sign-in page"
  adb shell screencap -p /data/local/tmp/signin-tv.png && adb pull /data/local/tmp/signin-tv.png signin-tv.png || true
  # Back closes the page inside the screen, not the screen
  adb shell input keyevent KEYCODE_BACK || true
  sleep 2
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  check_crash "sign-in back"
  # The new Settings screen: open every page (0 Accounts ... 6 About), press Right and OK on the first option
  for page in 0 1 2 3 4 5 6; do
    adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.settings.SettingsScreenActivity" --ei page $page || true
    sleep 7
    adb shell input keyevent KEYCODE_DPAD_RIGHT || true
    sleep 1
    adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
    check_crash "settings page $page"
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
  # Settings inside the Home screen: open the sidebar, go down to Settings (the last entry), press OK.
  # Then CHECK what is on the screen: the pages must be there, and a tap on one must show its options.
  # Animations ON, like on a real device (the emulator runner switches them off): an animation that leaves the
  # panel invisible must show up here
  adb shell settings put global window_animation_scale 1.0 || true
  adb shell settings put global transition_animation_scale 1.0 || true
  adb shell settings put global animator_duration_scale 1.0 || true
  adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity" || true
  sleep 6
  adb shell input keyevent KEYCODE_DPAD_LEFT || true
  sleep 1
  for i in 1 2 3 4 5 6 7; do adb shell input keyevent KEYCODE_DPAD_DOWN || true; done
  adb shell input keyevent KEYCODE_DPAD_CENTER || true
  sleep 6
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  adb shell screencap -p /data/local/tmp/settings-home.png && adb pull /data/local/tmp/settings-home.png settings-home.png || true
  # Pixel check: the area next to the sidebar must not be empty (looks at the picture, not at the view tree)
  python3 -m pip install -q pillow > /dev/null 2>&1 || true
  PIXELS=$(python3 - settings-home.png <<'PY' 2>/dev/null
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('L')
w, h = im.size
box = im.crop((int(w * 0.30), int(h * 0.12), w, int(h * 0.90)))
data = list(box.getdata())
print("%.2f" % (sum(1 for p in data if p > 90) * 100.0 / len(data)))
PY
)
  if [ -n "$PIXELS" ]; then
    echo "CHECK content area pixels lit: $PIXELS%"
    if python3 -c "import sys; sys.exit(0 if float('$PIXELS') >= 0.3 else 1)"; then
      echo "CHECK content area is not empty: OK"
    else
      echo "CHECK content area is EMPTY (blank screen)"
      FAILED=1
    fi
  else
    echo "CHECK pixel check skipped (no image library)"
  fi
  check_crash "settings in the Home screen"
  if ui_dump home-settings; then
    if has_text ui-home-settings.xml "General"; then
      echo "CHECK settings panel in the Home screen: SHOWN"
      tap_text ui-home-settings.xml "General" || true
      sleep 3
      ui_dump home-settings-tapped || true
      if has_text ui-home-settings-tapped.xml "Boot to section"; then
        echo "CHECK tap on a settings page: WORKS"
      else
        echo "CHECK tap on a settings page: FAILED (options not shown)"
        FAILED=1
      fi
      check_inside_screen ui-home-settings-tapped.xml "options open"

      # The sidebar opens (Left) and pushes the panel to the right: nothing may be cut off by the screen edge
      adb shell input keyevent KEYCODE_DPAD_LEFT || true
      adb shell input keyevent KEYCODE_DPAD_LEFT || true
      sleep 3
      if ui_dump home-settings-sidebar; then
        check_inside_screen ui-home-settings-sidebar.xml "sidebar expanded"
      fi

      # The phone held sideways (the screen is wider and shorter)
      adb shell settings put system accelerometer_rotation 0 || true
      adb shell settings put system user_rotation 1 || true
      sleep 5
      check_crash "phone turned sideways"
      if ui_dump home-settings-landscape; then
        check_inside_screen ui-home-settings-landscape.xml "turned sideways"
      fi
      adb shell settings put system user_rotation 0 || true
    else
      echo "CHECK settings panel in the Home screen: MISSING (no page list on the screen)"
      FAILED=1
    fi
  else
    echo "CHECK skipped: no screen dump on this emulator"
  fi
  # Search and Settings inside the Home screen, opened by taps on the sidebar icons (like a finger or a mouse).
  # First on this emulator as it is, then with the screen as wide as a usual phone held upright (360 dp): the
  # emulator's own screen is wider than that and hid a keyboard that did not fit a real phone.
  check_home_panels "emulator"
  if grep -q "Search for videos" ui-home-emulator.xml 2>/dev/null; then
    echo "CHECK no search pill at the top: WARNING (the old pill is still there)"
  else
    echo "CHECK no search pill at the top: OK"
  fi
  WIDTH=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x.*/\1/p' | tail -1)
  if [ -n "$WIDTH" ]; then
    adb shell wm density $((WIDTH * 160 / 360)) || true
    sleep 5
    check_home_panels "phone"
    adb shell wm density reset || true
    sleep 5
  fi
  # Search results screen (it has the search box and the same keyboard): the whole keyboard must be on the screen,
  # upright and sideways, and a key must add to what the box shows, also to text that did not come from the keys.
  adb shell am start -n "$PKG/com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity" || true
  sleep 10
  adb shell dumpsys window windows | grep -E "mCurrentFocus" || true
  check_crash "search screen"
  adb shell screencap -p /data/local/tmp/search.png && adb pull /data/local/tmp/search.png search.png || true
  if ui_dump search; then
    check_keyboard ui-search.xml "upright"
    tap_text ui-search.xml "A" || true
    sleep 1
    tap_text ui-search.xml "B" || true
    sleep 2
    ui_dump search-typed || true
    if [ "$(box_text ui-search-typed.xml)" = "ab" ]; then
      echo "CHECK search keyboard types into the search box: WORKS"
    else
      echo "CHECK search keyboard types into the search box: FAILED (the box shows '$(box_text ui-search-typed.xml)', not 'ab')"
      FAILED=1
    fi
    # Text from somewhere else (here: typed with a real keyboard, if the box takes it), then one more key
    adb shell input text xy || true
    sleep 2
    ui_dump search-other || true
    BEFORE=$(box_text ui-search-other.xml)
    tap_text ui-search-other.xml "A" || true
    sleep 2
    ui_dump search-added || true
    if [ "$(box_text ui-search-added.xml)" = "${BEFORE}a" ]; then
      echo "CHECK a key adds to what the box shows ('$BEFORE' + a): WORKS"
    else
      echo "CHECK a key adds to what the box shows: FAILED ('$BEFORE' became '$(box_text ui-search-added.xml)')"
      FAILED=1
    fi
    tap_text ui-search-added.xml "DELETE" || true
    sleep 2
    ui_dump search-deleted || true
    if [ "$(box_text ui-search-deleted.xml)" = "$BEFORE" ]; then
      echo "CHECK search keyboard DELETE: WORKS"
    else
      echo "CHECK search keyboard DELETE: FAILED (the box shows '$(box_text ui-search-deleted.xml)', not '$BEFORE')"
      FAILED=1
    fi
    check_crash "search typing"
    adb shell screencap -p /data/local/tmp/search-typed.png && adb pull /data/local/tmp/search-typed.png search-typed.png || true

    adb shell settings put system accelerometer_rotation 0 || true
    adb shell settings put system user_rotation 1 || true
    sleep 5
    check_crash "search screen turned sideways"
    adb shell screencap -p /data/local/tmp/search-landscape.png && adb pull /data/local/tmp/search-landscape.png search-landscape.png || true
    if ui_dump search-landscape; then
      check_keyboard ui-search-landscape.xml "turned sideways"
    fi
    adb shell settings put system user_rotation 0 || true
    sleep 3
  else
    echo "CHECK skipped: no screen dump of the search screen on this emulator"
  fi
  adb shell input keyevent KEYCODE_BACK || true
  adb logcat -d > logcat.txt
else
  echo "adb root not available: sign-in screen not checked"
fi

if [ "$FAILED" != "0" ]; then
  echo "A screen check failed (see the CHECK lines above)"
  adb logcat -d > logcat.txt
  grep -iE "SettingsPanel|AndroidRuntime|BrowseSupportFragment|ClassCast|NullPointer" logcat.txt | tail -40
  exit 1
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
