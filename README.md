<p align="center"><img src="docs/banner.png" alt="YouTv Plus" width="360"></p>

<h1 align="center">YouTv Plus</h1>

<p align="center">
  A clean, rounded, YouTube TV-style video app for Android TV boxes, smart TVs, phones and tablets.<br>
  <b>Works on Android 5.0 and newer.</b>
</p>

<p align="center">
  <a href="https://github.com/mbnetworku/YouNew/releases/latest/download/YouTvPlus.apk"><b>⬇ Download YouTvPlus.apk</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/mbnetworku/YouNew/releases/latest">All files</a>
  &nbsp;·&nbsp;
  <a href="#install">How to install</a>
  &nbsp;·&nbsp;
  <a href="#questions-and-answers">Questions</a>
</p>

---

## Why YouTv Plus

| | |
| --- | --- |
| 📺 **Made for the TV** | Dark theme, big rounded 16:9 thumbnails, a clear focus ring and a slim sidebar that opens to show names. |
| 🖱 **Remote, mouse, touch** | Everything can be used with a remote, a USB mouse, a keyboard or a finger. Works on a phone upright and sideways. |
| ⚙️ **One Settings screen** | Opens inside the Home screen next to the sidebar. Pages on the left (Accounts, General, Player, Language, Subtitles, Appearance, About), their options on the right. Choices unfold in place, with no pop-ups. Round, coloured icons and smooth animations. |
| 🔄 **Updates itself** | When a new version is on GitHub the app offers "Update now / Later" (at most every 12 hours). Settings › About has the switch and a "Check for updates now" button. |
| 🔑 **Three ways to sign in** | Google's page inside the app, the same page in a pop-up window, or scan the QR code with your phone. Signing in is optional. |
| 🕘 **History** | The videos you watched, newest first, with how far you got. Works without an account. |
| 🧹 **Clean feeds** | One "Clean up my feeds" page instead of many switches. |
| 🎬 **More** | Shorts player, topic buttons on Home (Music, Gaming, News, Sports), English and Arabic layout. |

## Download

Newest version: **[YouTvPlus.apk](https://github.com/mbnetworku/YouNew/releases/latest/download/YouTvPlus.apk)**

| File | For |
| --- | --- |
| **`YouTvPlus.apk`** | Any device. Take this one if you are not sure (bigger file). |
| `YouTvPlus-armeabi-v7a.apk` | Most Android TV boxes and TVs (32-bit ARM). Smaller. |
| `YouTvPlus-arm64-v8a.apk` | Newer 64-bit boxes, phones and tablets. |
| `YouTvPlus-x86.apk` | PCs and emulators. |

Older versions and notes: [Releases page](https://github.com/mbnetworku/YouNew/releases).

## Install

**On the device itself**
1. Download the APK (or copy it with a USB stick).
2. Open it with the file manager and allow *Unknown sources* when Android asks.
3. Press *Install*, then *Open*.

**From a computer with ADB** (TV and computer on the same network, network debugging on):

```bash
adb connect <TV_IP>:5555
```

```bash
adb install -r YouTvPlus.apk
```

> **First time only:** if an older build of the app is installed and Android says the update conflicts
> (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), uninstall the old one once, then install this one.

## Questions and answers

**Do I need a Google account?** No. Subscriptions are kept on the device. Sign in only if you want your own feed.

**Why must I uninstall once?** Android installs an update only when it is signed with the same key as the installed app.
Early builds used a temporary key that changed every time. Builds made with the project's own key (see
[Signing key](#signing-key-for-the-maintainer)) update over each other, and the in-app updater then works.

**Google Play Protect shows a warning.** The app is not on the Play Store, so Play Protect may ask you to confirm.
Choose *Install anyway* if you trust the source. This project does not try to get around Google's checks.

**Google sign-in asks for more.** Google decides how its sign-in page behaves (extra checks, 2-step verification).
If one method fails, try another of the three, or use the app without signing in.

**It does not look right on my screen.** Open an [issue](https://github.com/mbnetworku/YouNew/issues) with a
screenshot, your device and the version from Settings › About.

## How it is checked

Every push is built by GitHub Actions. The APK is checked (minimum Android 5.0, signed, aligned) and started on
emulators with Android 5.0, 5.1, 8.0 and 11. The build fails if the app crashes or the Settings panel is blank or
cut off, and a release is published only when all of those pass. Real-device behaviour (mouse on a TV box, Google
sign-in with a real account) is tested by hand, not by the emulators.

## For developers

- [docs/TECHNICAL.md](docs/TECHNICAL.md): architecture (Settings panel, sidebar, updater), build, tests, release, known traps.
- [docs/CLAUDE.md](docs/CLAUDE.md): rules and working notes for AI assistants and contributors.
- [docs/AI_MEMORY.md](docs/AI_MEMORY.md): version timeline, decisions, lessons and open items.

**Build** (JDK 17, Android SDK platform 34, build-tools 30.0.3):

```bash
./gradlew assembleStfdroidRelease
```

Output: `smarttubetv/build/outputs/apk/stfdroid/release/YouTvPlus-<version>-<abi>.apk`. Without a keystore the
debug key is used, so the file still installs. On Windows, turn on long paths and clone into a short folder:

```bash
git config --global core.longpaths true
```

```bash
git clone https://github.com/mbnetworku/YouNew.git C:\src\yn
```

### Signing key (for the maintainer)

On Windows double-click `tools\make-signing-key.bat`. It asks for administrator rights, creates the key in
*Documents\YouTvPlus-key*, copies it to the clipboard and opens the GitHub secrets page. Add the four secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` there. Never put the key or passwords in
the repository or in chat.

### New release

1. Raise `versionCode` and `versionName` in `smarttubetv/build.gradle`.
2. Push, then run the *Build and check APK* workflow with `release_tag` set to `v<versionName>`, or publish a
   release with that tag on GitHub.
3. The APKs are attached automatically after the emulator checks pass.

## Licence and credits

Based on [SmartTube](https://github.com/yuliskov/SmartTube) by Yurii Liskov and contributors, and on the
MediaServiceCore and SharedModules libraries by the same author. Released under the **GNU GPL v3** (see `LICENSE`),
the same licence as the original. Not affiliated with or endorsed by Google or YouTube.
