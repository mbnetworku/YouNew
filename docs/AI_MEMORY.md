# YouTv Plus: AI memory

A running record for the next assistant (or person). Facts, decisions, lessons, open items. Keep it short and true;
add to it at the end of each piece of work. Never write passwords, keys, tokens or personal data here.
Last updated: 2026-11-05, after version 32.86.

## Where things stand
- Repository: `mbnetworku/YouNew` (public). Work branch: `claude/practical-wozniak-ou260z`. Pull request #2 into `main`
  is open: the owner has not merged it, so `main` is behind the branch.
- Latest published release: **32.86** (all four emulators passed; Settings panel fits the visible space, page list scrolls,
  sidebar redesign). README was fully redesigned afterwards (user-first layout, Q&A, developer section).
- Download: `https://github.com/mbnetworku/YouNew/releases/latest/download/YouTvPlus.apk`.
- Users: the owner tests on a phone (portrait and landscape) and an Android 5.1.1 TV box (KII PRO, USB mouse).

## Version timeline (what each added)
| Version | Added |
| --- | --- |
| before 32.69 | Fork of SmartTube; YouTube TV look; Home without account; Shorts; topic buttons; History not yet in the sidebar |
| 32.69 | History section in the sidebar (also without an account, with an explanatory empty screen) |
| 32.70 | Google sign-in QR code drawn on the device (ZXing), clear steps |
| 32.71 to 32.74 | "Sign in on this TV" (Google's page inside the app) and a pop-up window variant; not published because the Android 8.0 emulator has no WebView |
| 32.75 | WebView check with a message and fallback; sign-in screen with both buttons plus QR |
| 32.76 | One "Clean up my feeds" page (4 switches instead of about 20); no proxy/Conscrypt options, no Drive backup, no DeArrow |
| 32.77 | Fixed player buttons (11), 3 playback modes, hidden Audio delay/Presets/Seek interval, Language top button removed |
| 32.78 | New Settings screen: pages left, options right (one screen, mouse/touch/remote) |
| 32.79 to 32.80 | Settings fits phones (one pane upright, two sideways), animations, back arrow for mouse-only use |
| 32.81 | Choices unfold inside the page (no pop-up), icons on pages and settings |
| 32.82 | More animation (screen in/out, staggered pages, pop on switch, smooth unfold) |
| 32.83 | Settings inside the Home screen next to the sidebar; 26 round coloured icons; rounded card |
| 32.84 | Automatic update (build failed: duplicate FileProvider, fixed by `UpdateFileProvider`; not published) |
| 32.85 | Auto update, readable sidebar icons (90 % opacity, 32 dp), sidebar brand "YouTv Plus" with new mark, panel safety net against invisibility |
| 32.86 | Panel measures itself to the visible space (sidebar open / phone sideways), scrolling page list, sidebar redesign (rounded pills, taller rows), cut-off checks in CI |
| 32.87 | First release signed with the owner's own key (secrets set by `tools/make-signing-key.ps1`) |
| 32.88 | Sign-in screen redesigned: one rounded screen, big QR + code, Google page inside the same screen (`SignInFragment`) |
| 32.89 | Sidebar in touch/mouse mode: no focus-driven open/close (`BrowseSupportFragment.onRequestChildFocus`), tap on current icon toggles the bar, touch on content closes it |
| 32.90 | New account with empty personal Home falls back to the public Home (`BrowsePresenter.signedHomeWithFallback`); loading-arrow watchdog (25 s); player: swipe down hides bar/info, mouse hover no longer restarts the hide timer |
| 32.91 | Player look like modern YouTube: glass round buttons (focus ring, mouse hover), see-through track, red progress with always-visible red knob (`SeekBar`, `lb_control_button_*.xml`, dimens) |
| 32.92 | Player redesigned like YouTube: `PlayerOverlayView` (back arrow + title on top, big round Previous / Play-Pause / Next in the middle, feedback pill) and `PlayerGestures` (swipe left half = brightness, right half = volume, press and hold = 2x speed, tap on video hides controls, swipe down hides); double-tap 10 s is the older `DoubleTapPlayerAdapter` |

## Decisions and why
- **Settings panel is one custom view** drawn from the existing presenters' option lists (`setCapture`), so every kept
  setting still works and keeps its saved value. The old option dialog was a second transparent screen that
  swallowed touches.
- **Hide, do not delete.** Removed options are commented out with `YouNew: hidden`, saved values stay.
- **Releases only through CI**, gated by emulator tests, because the sandbox cannot build and the owner cannot verify.
- **Updater needs a public repo** and the same signing key for every build.
- **Name:** the app is "YouTv Plus" (not "YouTube TV Plus": "YouTube TV" is Google's trademark; the owner chose the safe name).
- **Icons:** the sidebar icons stay as they are (owner's wish); the settings icons are new round coloured ones.

## Lessons learned (mistakes made, fixed or still relevant)
1. Early advice "install over the current version, no uninstall needed" was wrong: CI builds without the owner's key
   get a new debug key each time, so Android refuses the update. The owner stayed on an old build for a while.
2. A green emulator test only meant "did not crash". A blank Settings panel passed. Tests now read the screen,
   look at pixels, tap, run with animations on, and fail on error dialogs and cut-off text.
3. The first emulator-passing version had an `Application Error` dialog on the About page that no log check caught;
   it did not reproduce in the next run. Watch for it (`check_crash` prints `APP ERROR DIALOG at:`).
4. The Home screen moves a main fragment right when the sidebar opens without narrowing it: custom views must clamp
   to the visible area.
5. Texts set from code override layout texts (sidebar brand).
6. `getContentLengthLong` and other API 24+ calls fail lint on minSdk 21.
7. A transient `HTTP 504` while downloading Gradle is GitHub; re-run.

## Open items
**Needs the owner**
- Add the four GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` = `youtvplus`, `KEY_PASSWORD`) using
  `tools\make-signing-key.bat`; then one more uninstall + install; after that in-app updates install over each other.
  The assistant cannot set secrets and must not store the password.
- Merge pull request #2 so `main` has the work.
- Tell what Google Play Protect said (if it warned): the assistant cannot bypass it; a real key, "Install anyway" or
  Play Store/F-Droid are the options.
- Send screenshots after each release (the assistant cannot open the CI screenshots).

**Backlog ideas**
- "Recommended" section ("Because you watched ..." rows from local history). Started earlier, paused; nothing committed.
- Redraw the other sidebar/top-bar icons as round (owner asked only to keep the sidebar icons unchanged for now).
- Animate other screens (Home cards, player controls) if the owner wants.
- Investigate whether the About-page error dialog (see lesson 3) is real.

## Environment facts
- Sandbox network: `dl.google.com` and `jitpack.io` blocked (no local Android SDK build); GitHub API reachable.
  GitHub blob storage (artifact download) is blocked, so CI screenshots cannot be opened from the sandbox.
- Only the working branch can be pushed; tags cannot be pushed from the sandbox: releases go through the workflow's
  `release_tag` input.
- GitHub tools available: list runs/jobs, read logs, trigger/cancel runs, latest release. No secrets API.
- CI emulators: API 21, 22, 26 (x86), 30 (x86_64). API 26 image has no WebView. `uiautomator dump` works on them.

## Owner preferences
- Wants things to work on the TV box with a mouse, and on the phone in portrait and landscape.
- Wants a modern look: rounded everything, readable icons, useful animations, fewer settings, one place for each
  function, no pop-ups and no extra screens.
- Prefers plain language; wants the download link and install steps with each release.
- For Windows helpers: a `.bat` that elevates itself and runs PowerShell.
