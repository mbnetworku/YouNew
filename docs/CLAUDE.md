# YouTv Plus: notes for Claude and other AI assistants

Read this first, then [TECHNICAL.md](TECHNICAL.md) (how the project is built) and [AI_MEMORY.md](AI_MEMORY.md)
(what has happened, what was decided, what is still open). Update `AI_MEMORY.md` at the end of every piece of work.

(Claude Code loads a `CLAUDE.md` from the repository root automatically; the root file only points here.)

## The project in four lines
- Android TV style YouTube client, fork of SmartTube, app id `com.younew.tv`, name **YouTv Plus**, minSdk 21, target 34.
- Used on an Android 5.1.1 TV box (KII PRO, 32-bit ARM, driven by a USB mouse, often no remote) and on the owner's phone.
- Code: `smarttubetv/` (UI), `common/` (presenters, data), `leanback-1.0.0/` (modified sidebar), `MediaServiceCore/` (YouTube API).
- Releases are built and published only by GitHub Actions (`.github/workflows/build-apk.yml`).

## Rules
1. **No secrets in the repository, ever.** No keystore, password, token, `keystore.properties`, Wi-Fi name, IP address,
   MAC address, e-mail address or personal path. If the owner pastes a password or key in the chat, do not copy it into
   any file, message or commit; tell them to type it only into GitHub's secrets page. Do not echo strings they paste.
2. Push only to the branch you were given (here `claude/practical-wozniak-ou260z`). Do not open a pull request unless
   asked. Never rewrite history, never force-push.
3. Do not put model names or IDs into commit messages, pull requests, code comments or documents (the required
   attribution trailer on commits is added as the session instructions say).
4. Be honest about testing. Say what was verified (and how), what was only built, and what could not be seen. Never
   write "works" for something no test or screenshot showed. Read the `CHECK` lines of the emulator test, not only the
   green tick.
5. Do not delete features to simplify: hide them in code (`YouNew: hidden`), so they can come back.
6. Keep English and Arabic strings in step (`common/src/main/res/values/` and `values-ar/`). New strings are named
   `younew_*`. Use `\n` for line breaks in strings, never real line breaks.
7. Do not change the app's icons unless asked (the owner said so for the sidebar).
8. Everything on screen should be rounded and readable on a dark background; test with animations ON.
9. Do not claim to bypass Google Play Protect or Google's sign-in protection. State the legitimate options
   (own signing key, "Install anyway", Play Store or F-Droid) and leave the decision to the owner.

## How to work here
- **No local build.** The usual sandbox cannot download the Android SDK (`dl.google.com` is blocked). Edit, push,
  run the workflow, read the logs. Check the GitHub tools you have (`actions_list`, `get_job_logs`,
  `actions_run_trigger`, `get_latest_release`). Large logs: save to a file and search it.
- **Test only:** run the workflow with `release_tag` empty. **Release:** run it with `release_tag` = `v<versionName>`
  after raising `versionCode` and `versionName` in `smarttubetv/build.gradle`. The release is published only if every
  emulator test passes, so a broken version cannot go out.
- If a run fails in 30 seconds with HTTP 5xx while downloading Gradle, it is GitHub, not the code: re-run it.
- When something cannot be seen (a screenshot you cannot open), make the test **look**: `uiautomator dump` for texts
  and bounds, a pixel check, `check_inside_screen`, `check_crash`. Add the check before the fix.
- Prefer small commits with a message that says what changed and why. Raise the version for every release.
- Wait for results with a scheduled reminder, do not poll in a loop.

## Where things are (details in TECHNICAL.md)
| Topic | Files |
| --- | --- |
| Settings panel | `smarttubetv/.../tv/ui/settings/SettingsPanelView.java`, `SettingsPanelFragment.java`, `SettingsScreenActivity.java`; `AppDialogPresenter.setCapture` |
| Hidden settings pages and options | `AppDataSourceManager`, `*SettingsPresenter` (search `YouNew: hidden`) |
| Sidebar | `leanback-1.0.0/.../HeadersSupportFragment.java`, `res/layout/icon_header_item.xml`, `drawable/header_item_*.xml` |
| Auto update | `common/.../misc/AppUpdater.java`, `UpdateFileProvider.java`, manifest `<provider>`, `BootDialogPresenter` |
| Sign-in | `tv/ui/signin/SignInFragment.java`, `YTSignInPresenter`, `WebBrowserActivity` |
| History | `VideoStateService`, `BrowsePresenter`, `SidebarService`, `NoHistoryError` |
| Icons | `smarttubetv/src/main/res/drawable-nodpi/younew_ic_*.png`, `mipmap-nodpi/app_*.png` |
| CI and tests | `.github/workflows/build-apk.yml`, `.github/scripts/smoke-test.sh` |

## Things that went wrong before (do not repeat)
- Telling the owner to "install over the current version" while builds were signed with changing debug keys.
- Declaring a feature done because the app did not crash. A blank panel passed every early test.
- Testing only with animations off, then shipping animations that start at alpha 0.
- Adding a second `FileProvider` with the same class name (manifest merge failure).
- Calling an API newer than 21 without a guard (lint stops the release build).
- Editing a layout text that code overwrites at runtime (the sidebar brand).
- Writing a large UI blind and publishing it before it was tested. Test first, release second.

## Talking to the owner
- Plain, short language. The owner is not a developer; say what changed and what they will see, then what is still
  open. Explain a limit clearly instead of hiding it (for example Google's own rules for passwords and sign-in).
- They test on a phone (portrait and landscape) and on the TV box. Layouts must work in both.
- When a script for Windows is needed, make it a `.bat` that asks for administrator rights itself and then runs the
  PowerShell part (see `tools/make-signing-key.bat`).
- Give the download link and the install steps with every release, and remind them of what they still have to do
  (uninstall once, the four GitHub secrets, merge the pull request).
