<p align="center"><img src="docs/banner.png" alt="YouTv Plus" width="320"></p>

# YouTv Plus

A YouTube TV-style video app for Android TV boxes: dark theme, large rounded 16:9 thumbnails,
a white focus ring, and a slim left icon menu that expands to labels.

- **Works on Android 5.0 and newer:** TV boxes, smart TVs, phones and tablets.
- Remote, mouse and keyboard all work.
- No Google account needed: subscriptions are kept on the device. You can still sign in for your own feed.
- **Google sign-in, three ways:** *Sign in on this TV* (Google's page inside the app), *Sign in in a pop-up window* (the same page in a window over the sign-in screen), or scan the large QR code with your phone. Type your email and password, press Allow.
- **History** in the sidebar: the videos you watched, newest first, with how far you got. Works without an account too.
- Shorts player, topic buttons on Home (Music, Gaming, News, Sports), English and Arabic layout.

## Download

Get the newest version from the **[Releases page](https://github.com/mbnetworku/YouNew/releases/latest)**:

| File | For |
| --- | --- |
| **`YouTvPlus.apk`** | Any device. Pick this one if you are not sure (bigger file). |
| `YouTvPlus-armeabi-v7a.apk` | Most Android TV boxes and TVs (32-bit ARM). |
| `YouTvPlus-arm64-v8a.apk` | Newer 64-bit boxes, phones and tablets. |
| `YouTvPlus-x86.apk` | PCs and emulators. |

Direct link to the newest APK: `https://github.com/mbnetworku/YouNew/releases/latest/download/YouTvPlus.apk`

### Install on a TV box

- **USB stick:** copy the APK to the stick, open it with the box's file manager, allow "Unknown sources" when asked.
- **ADB** (TV and computer on the same network, network debugging on in the TV's developer options):

```bash
adb connect <TV_IP>:5555
```

```bash
adb install -r YouTvPlus.apk
```

If an older version signed with a different key is installed, Android refuses the update
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstall the old one first.

## Automatic checks

Every push is built by GitHub Actions. The APK is checked (minimum Android 5.0, signed, aligned) and
started on Android 5.0, 5.1, 8.0 and 11 emulators; the build fails if the app crashes. The APKs of
every build are under the run's *Artifacts* on the
[Actions page](https://github.com/mbnetworku/YouNew/actions).

**Signing key (do this once):** on Windows, double-click `tools\make-signing-key.bat`. It asks for
administrator rights, creates the key in *Documents\YouTvPlus-key*, copies it to the clipboard and opens
the GitHub secrets page, where you add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`. From then on every build has the same signature, so new versions install over old
ones. Without it each build gets a temporary key and the old version has to be uninstalled first.

**New release:** on GitHub, open *Releases*, then *Draft a new release*, make a tag such as
`v32.74`, and publish it. The APKs are built and attached to the release by themselves a few minutes
later. Raise `versionCode` and `versionName` in `smarttubetv/build.gradle` first, or devices will not
take the update.

## Build it yourself

Needs JDK 17 and the Android SDK (platform 34, build-tools 30.0.3).

On Windows, some file paths in this repo are longer than 260 characters once cloned into a deep
folder, and git then skips those files without stopping. Turn on long paths and clone into a short
folder:

```bash
git config --global core.longpaths true
```

```bash
git clone https://github.com/mbnetworku/YouNew.git C:\src\yn
```

Then build:

```bash
./gradlew assembleStfdroidRelease
```

The APKs (`YouTvPlus-<version>-<abi>.apk`) are written to `smarttubetv/build/outputs/apk/stfdroid/release/`.
Without a keystore they are signed with the debug key, so they still install. To sign with your own key,
create a `keystore.properties` file in the project root (never commit it):

```
storeFile=path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Licence and credits

This project is based on [SmartTube](https://github.com/yuliskov/SmartTube) by Yurii Liskov
and contributors, and on the MediaServiceCore and SharedModules libraries by the same author.
It is released under the **GNU GPL v3** (see `LICENSE`), the same licence as the original.
Modified files are marked by this project's history. Not affiliated with or endorsed by Google or YouTube.
