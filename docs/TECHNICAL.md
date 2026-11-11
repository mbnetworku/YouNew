# YouTv Plus: technical guide

For people (and AI assistants) who build, change or release the app. The user-facing text is in the
[README](../README.md); instructions for AI assistants are in [CLAUDE.md](CLAUDE.md); the project's running
memory (decisions, lessons, open items) is in [AI_MEMORY.md](AI_MEMORY.md).

## What it is

An Android TV style YouTube client for TV boxes, TVs, phones and tablets. It is a fork of
[SmartTube](https://github.com/yuliskov/SmartTube) (GNU GPL v3) with its own look and many simplifications.

| Item | Value |
| --- | --- |
| App id | `com.younew.tv` (flavor `stfdroid`) |
| App name | YouTv Plus |
| Android | minSdk 21 (Android 5.0), compile and target 34 |
| Languages | Java (UI, presenters), Kotlin (YouTube API module), XML |
| Version | `versionCode` / `versionName` in `smarttubetv/build.gradle` (raise both on every release) |
| Build tool | Gradle wrapper 7.5, Android Gradle Plugin 7.4.2, JDK 17 |
| Reference device | Android 5.1.1 TV box (KII PRO, 32-bit ARM, USB mouse), also an Android phone |

## Repository layout

| Folder | What is in it |
| --- | --- |
| `smarttubetv/` | The app module: screens, fragments, layouts, resources, manifest, flavors |
| `common/` | Presenters, data classes, preferences, helpers shared by the UI (`com.liskovsoft.smartyoutubetv2.common`) |
| `MediaServiceCore/` | YouTube API access (`youtubeapi`, `mediaserviceinterfaces`) |
| `SharedModules/` | Shared helpers (`sharedutils`) and the old updater library `appupdatechecker2` |
| `leanback-1.0.0/`, `fragment-1.1.0/` | Modified copies of AndroidX Leanback and Fragment (the sidebar lives here) |
| `exoplayer-amzn-2.10.6/` | The video player |
| `chatkit/`, `doubletapplayerview/`, `slidableactivity/`, `filepicker-lib/`, `leanbackassistant/` | Smaller libraries |
| `tools/` | `make-signing-key.bat` and `.ps1`: create the signing key (Windows, asks for administrator rights) |
| `.github/` | `workflows/build-apk.yml` (build, test, release) and `scripts/smoke-test.sh` (emulator test) |
| `docs/` | This guide, `CLAUDE.md`, `AI_MEMORY.md`, the README banner |

## Main parts of the YouTv Plus changes

### Settings panel (`smarttubetv/.../tv/ui/settings/`)
- `SettingsPanelView`: one plain Android view. The pages (Accounts, General, Player, Language, Subtitles,
  Appearance, About) are listed on the left, the options of the chosen page on the right.
  - **Narrow places** (a phone held upright, or the Home screen with the sidebar open): one pane at a time; a
    click slides the options in, the arrow at the top slides back. **Wide places:** both panes together.
    The decision is made from the width the panel really gets (`onSizeChanged`, threshold 600 dp).
  - It measures itself to the part of the screen that is visible (`onMeasure`, `onLayout`): the Home screen shifts
    it to the right when the sidebar opens without narrowing it.
  - The page list sits in a `ScrollView` (a phone held sideways is shorter than the list).
  - Choices unfold inside the page (`buildChoiceList`, `unfold`), no pop-up. Everything is rounded; icons are round
    coloured pictures `drawable-nodpi/younew_ic_*.png` (26 icons, chosen by page index and by words in the
    option name, see `iconFor`).
  - Animations are on `ViewPropertyAnimator`/`ValueAnimator`. `settle()` is a safety net: 1.5 s after anything is
    drawn, every item is forced to alpha 1 and no offset, so an interrupted animation can never leave it invisible.
- `SettingsPanelFragment`: puts the panel into the Home screen as the main fragment of the Settings section
  (`BrowseSectionFragmentFactory`). A settings password, if set, is asked first.
- `SettingsScreenActivity`: the same panel on a screen of its own (opened with an `EXTRA_PAGE` number; fallback).
- How the options get there: the page presenters (`GeneralSettingsPresenter`, ...) build option lists for
  `AppDialogPresenter`. `AppDialogPresenter.setCapture(...)` makes the next `showDialog()` hand those lists to the
  panel instead of opening the old dialog screen. The old dialog screens (`AppDialogActivity`) are still used by
  everything else (video menu, sign-in confirmations, update question).

### Sidebar (`leanback-1.0.0/.../HeadersSupportFragment.java`, `smarttubetv/.../layout/icon_header_item.xml`)
Brand at the top (`lb_headers_fragment.xml`, text set in `HeadersSupportFragment.setHeadersEnabled`), rail mode
(icons only) and expanded mode. Entries: rounded ring when focused (`header_item_selected.xml`), soft pill for the
open section (`header_item_current.xml`), unselected entries at 90 % opacity (`younew_fractions.xml`). Icons are the
app's own `icon_*.png`.

### Automatic update (`common/.../misc/AppUpdater.java`)
Reads `https://api.github.com/repos/mbnetworku/YouNew/releases/latest`, compares the tag with the installed
`versionName`, asks "Update now / Later" (through `AppDialogPresenter`), downloads the asset for the device
(first of `Build.SUPPORTED_ABIS`, else `YouTvPlus.apk`), checks that the file is signed with the same key as the
installed app, and starts Android's installer through `UpdateFileProvider` (`<provider>` in the manifest, paths in
`res/xml/younew_update_paths.xml`). Started at boot from `BootDialogPresenter.start()` (at most every 12 hours);
Settings > About has the switch and a "Check now" button. The permission `REQUEST_INSTALL_PACKAGES` is declared.
Requires a public repository.

### Other additions
- History section (local, works without an account), Subscriptions without an account, topic buttons on Home.
- Google sign-in: `SignInFragment` (QR code drawn with ZXing 3.3.3, "Sign in on this TV" through
  `WebBrowserActivity`, "pop-up window" with a `Dialog` + `WebView`; the app never sees the password).
- Fewer settings: many pages and options are hidden in code (search for `YouNew: hidden`), not deleted.
- Strings for new features: `younew_*` in `common/src/main/res/values/strings.xml` and `values-ar/strings.xml`
  (English and Arabic). Keep both in step.

## Build, test, release

The machine used for development often cannot reach `dl.google.com` (no Android SDK download), so the real build
and tests run on GitHub Actions. Workflow `.github/workflows/build-apk.yml`:

1. **build**: SDK packages, optional signing key from secrets, `assembleStfdroidDebug` + `assembleStfdroidRelease`,
   APK checks (`aapt` badging: minSdk must be ≤ 21; `zipalign`; `apksigner verify`), upload artifact `YouNew-apk`.
2. **smoke-test** on emulators API 21, 22, 26 (x86) and 30 (x86_64): installs the debug APK and runs
   `.github/scripts/smoke-test.sh`.
3. **attach-to-release** (only when a release tag is given): creates the GitHub Release and uploads
   `YouTvPlus.apk` (universal) and `YouTvPlus-<abi>.apk`. It runs only if every smoke test passed.

Triggers: every push to `main` or `claude/**`, pull requests, and **Run workflow** with the input `release_tag`
(for example `v32.86`; empty = test only). Pushing a tag `v*` also works where tags can be pushed.

### What the smoke test checks (`smoke-test.sh`)
- The app starts and is still running; no `FATAL EXCEPTION`.
- `check_crash`: fails on Android's "Application Error / Not Responding" window and prints the crash log.
- Sign-in screen and its buttons open without a crash.
- Every Settings page opens; a choice unfolds and one is picked.
- **Settings inside the Home screen, with animations switched ON:** the pages are on the screen (text dump with
  `uiautomator`), the content area is not blank (pixel check), tapping "General" shows "Boot to section".
- `check_inside_screen`: no text ends outside the screen with the options open, with the sidebar expanded, and with
  the phone turned sideways.
- Output lines starting with `CHECK` say what was verified. A `CHECK ... skipped` line means the emulator could not
  provide the information: read those, a green build can hide a skipped check.

What it cannot prove: how things look, real mouse behaviour on a TV, Google sign-in with a real account,
Play Protect behaviour. Screenshots are saved as artifacts, but they are only visible to someone who can open
GitHub.

### Make a release
1. Raise `versionCode` and `versionName` in `smarttubetv/build.gradle`.
2. Run the workflow with `release_tag` = `v<versionName>` on the branch.
3. When it is green, the release `v<versionName>` exists and `releases/latest/download/YouTvPlus.apk` points to it.

### Signing
- Without secrets the build uses a throw-away debug key that **changes on every CI run**. Android then refuses an
  update over an older build (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and so does the in-app updater.
- With the four repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`youtvplus`),
  `KEY_PASSWORD` the workflow writes `keystore.properties` and signs with the owner's key; every build then has the
  same signature. Create the key with `tools\make-signing-key.bat`.
- Never commit a keystore, a password or `keystore.properties` (they are in `.gitignore`).
- Without the Play Store, Google Play Protect may warn about the install: that is Google's decision (a real release
  key helps; "Install anyway" or publishing through Play or F-Droid are the ways around it).

## Known traps (found the hard way)
- **minSdk 21:** `HttpURLConnection.getContentLengthLong()` and similar API 24+ calls fail lint. Guard or avoid.
- **Manifest merge:** another library (`appupdatechecker2`) already declares `androidx.core.content.FileProvider`.
  A second one with the same class name breaks the build; the app uses its own subclass `UpdateFileProvider`.
- **Leanback main fragment:** a main fragment must implement `MainFragmentAdapterProvider` and call
  `notifyViewCreated`/`notifyDataReady`; the Home screen moves it right when the sidebar opens but does not narrow
  it. Views in it must measure themselves to the visible area.
- **Entrance animations that start at alpha 0** can stay invisible if cut short on a real device; the emulator runner
  disables animations, so tests must switch them on (`settings put global animator_duration_scale 1.0`).
- **Texts set from code override layout texts** (the sidebar brand is set in `HeadersSupportFragment`).
- **`ViewPropertyAnimator` settings persist** on a view (`setStartDelay`, `setDuration`): set them every time.
- **Android 5 emulators have no WebView** on some images; check `AppUpdater`-style `Utils.isWebViewAvailable`.
- `lintOptions.abortOnError true` is on: lint errors fail release builds.

## Licence
GNU GPL v3 (see `LICENSE`). Based on SmartTube by Yurii Liskov and contributors.
Player button icons (quality, channel, like, dislike, subtitles, add to playlist, subscribe, speed, search, share) are
Google's Material Symbols (rounded, weight 500), Apache License 2.0, rendered to PNG at the sizes of the old icons. Not affiliated with or endorsed by
Google or YouTube.
