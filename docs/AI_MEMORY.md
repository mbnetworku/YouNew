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
| 32.93 | Player compact after the owner's screenshot: big description card hidden (title + channel line now in the top bar of `PlayerOverlayView`), smaller round buttons, Play/Prev/Next no longer repeated in the bottom row, Share button on by default, swipe up shows the Recommended/playlist row |
| 32.94 | Suggestions as a pop-up (`SuggestionsPanelView`): the owner did not like it, going back to the rows under the controls |
| 32.95 | Suggestions are the Leanback rows under the controls again (as in 32.93); the big middle buttons hide while a suggestion card is in front (`PlayerOverlayView.setMiddleVisible`). The pop-up commit was reverted |
| 32.96 | The big middle buttons hide as soon as the Suggestions row is in front (checked every 150 ms through `isOverlaySuggestionsRow`, also when scrolled by touch), return with the controls |
| 32.97 | Liquid glass look (published, the owner disliked it: plain YouTube TV look is wanted) |
| 32.98 | Liquid glass removed again (plain YouTube TV look, as the owner's reference pictures). Endless Home: `BrowsePresenter.onRowsEnd` + `ContentService.getHomeMoreRowsObserve` add 4 topic rows each time the last rows are reached. Search screen compact: `OnScreenKeyboard` keys 28-36 dp (were 44), keyboard on the right (narrow phone: centred, results below), search bar capped to the screen width |
| 32.99 | Search is the first entry of the sidebar and opens inside the Home screen (`SearchPanelFragment` / `SearchPanelView`: query field, mic, suggestion pills, compact keyboard; SEARCH opens the normal results screen, `SidebarService.addSearchOnce` adds it once for old installs, `BrowseSection.TYPE_SEARCH_PANEL`, icon `icon_search.xml`); the search button at the top of Home is gone; CI checks the panel | (first run failed: the CI test did not reach the sidebar after the Settings test, the old 'Search for videos' pill was still at the top; fixed in 33.00: pill hidden in `lb_title_view.xml`, test restarts the app first)
| 33.00 | Same as 32.99 plus the old search pill at the top really gone, CI test restarts the app and checks for it |
| 33.01-beta | Settings list with section headers (Language & Location, About) like YouTube TV; Search panel CI check is a warning (the tap on the sidebar entry is not reliable in CI); beta label |
| 33.03-beta | Two channels: production = normal releases (the download link and the updater's default); beta = tags with 'beta', marked as GitHub pre-releases by the workflow (`gh release edit --prerelease`). About > 'Include beta versions' lets the updater use the newest release of either kind (`AppUpdater.fetchRelease`, `releases?per_page=10`). Also the Search design pass (33.02) |
| 33.04 | First production release of the two-channel setup (production = normal tag, no beta label) |
| 33.05 | Includes the bug-check fixes (PR #4). Top bar: the topic buttons (Music, Gaming, News, Sports) show on the Home section only (`NavigateTitleView.setTopicButtonsVisible`, called from `BrowseFragment.updateTopButtons`); Settings, Search, History, channels etc. show only the logo. Player starts faster: `ExoPlayerInitializer` waits for 1 s of video before playing (was 2.5 s) and 2.5 s after a stall (was 5 s). Player overlay in Arabic: back arrow mirrored, Prev/Play/Next keep their order. Default video quality on Android 5-6 is 720p AVC 30 (was 1080p60 VP9; `PlayerData.getDefaultVideoFormat`), only for installs that never chose a quality. **Landscape only** (owner's wish: TV design, no portrait): `MotherActivity.lockLandscape` sets sensor-landscape for every screen (skipped on API 26 for see-through windows, which Android refuses); CI checks `landscape lock` with the device upright. Account chooser (`AccountChooserActivity`): logo, accounts and guest button in one scrolling column (they overlapped on a phone held sideways), accounts row scrolls sideways (4+ accounts were cut off), mouse hover lights an account. Player buttons (`PLAYER_BUTTON_YOUNEW`): Seek interval hidden, Open channel and Dislike added (no way to reach the channel from the player before). Like/Dislike pressed before the video data loaded no longer stay lit. CI now opens a video by link (`jNQXAC9IVRw`) and reports player screen, buttons (content descriptions), playing time and recommended rows (warnings, only a crash fails). New player icons: one filled rounded style (owner wanted rounded; Material Symbols Rounded 500 `-fill`, rendered white with Playwright from the npm package `@material-symbols/svg-500`), same file names and sizes (`lb_ic_hq/thumb_up/thumb_down/cc` in leanback xhdpi, `action_*` in common nodpi). Share button hidden on TVs (`VideoPlayerGlue.isTvDevice`: TV mode, leanback/TV feature, or no touch screen). Top bar account and return-to-video icons redrawn in the same style. Video menu (long press) is a fixed set `MainUIData.MENU_ITEM_YOUNEW` (checklist hidden): Watch later added (with a message; without an account it said nothing), Share only on phones, Pin to sidebar / Move / Rename section removed (sidebar is fixed) |

## Bug check after 32.92 (2026-11-11, not released, no version change)
Found by reading the custom code and building locally; fixed on branch `claude/bug-check-fixes`:
- Settings > Accounts builds its options later (avatars are fetched). The panel's one-time capture could be used by
  another page, or the accounts list opened as an old pop-up after the panel had moved on or closed. Now the page takes
  the capture when asked (`AppDialogPresenter.takeCapture`) and hands it back with `showDialog(title, onFinish, capture)`.
- Player: press-and-hold 2x stayed on if the screen was left (or the "finger up" was lost) during the hold.
  `PlayerGestures.release()` is now also called in `PlaybackFragment.onPause`, and a new touch restores the speed.
- Updater on Android 8+: the "allow installing apps" question came after the download, and the file was thrown away.
  It is asked before the download now (`AppUpdater.askForInstallPermission`).
- Shorts on a phone: a swipe up or down now goes to the next or previous Short (`PlaybackFragment.onShortsMouse`);
  before, only the remote's Up/Down or the mouse wheel could. Held upright, the video takes the whole width, so the
  title and buttons were placed off the screen: `ShortsOverlayView.updateHole` now puts them over the lower part of
  the video (with text shadows) when there is no room beside it. Wide screens are laid out as before.
- Settings icons in Arabic: `SettingsPanelView.iconFor` matched English words only, so in Arabic every setting got
  its page's icon. `englishName` now looks the name up in the app's English texts (read once, by reflection on
  `common.R.string`; the release is not minified).
- Local lint (`lintStfdroidRelease`, not part of CI) had 77 errors, all `MissingTranslation` in
  `smarttubetv/.../values/strings.xml`: the unused Leanback sample texts and internal keys are now marked
  `translatable="false"` (not deleted), the 9 texts that can appear got Arabic. Lint: 0 errors. The `younew_*`
  strings match in English and Arabic. The remaining warnings are in the original SmartTube code and were left alone.
- CI does not open Shorts and has no upright-phone run, so the Shorts changes are built but unseen: ask the owner
  for a screenshot of a Short on the phone, upright and sideways.
- Not checked by a person or an emulator in the sandbox (no KVM): only the CI emulators can show the screens.

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
- Sandbox network: on 2026-11-11 `dl.google.com` and `jitpack.io` were reachable, so a local build worked: Android
  command-line tools + `platforms;android-34` + `build-tools;34.0.0`, JDK 17 (Gradle 7.5 fails on JDK 21),
  `bash ./gradlew assembleStfdroidDebug`. Maven Central sometimes answers HTTP 429: wait and retry. No KVM, so no
  emulator. Earlier sandboxes had these hosts blocked. GitHub API reachable.
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
