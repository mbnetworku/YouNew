# YouNew

A YouTube TV-style app for Android TV boxes: dark theme, large rounded 16:9 thumbnails,
a white focus ring, and a slim left icon menu that expands to labels.

## Build

Needs JDK 17 and the Android SDK (platform 34, build-tools 30.0.3).

```
./gradlew assembleStfdroidRelease
```

The universal APK is written to `smarttubetv/build/outputs/apk/stfdroid/release/`.
To sign it, create a `keystore.properties` file in the project root (never commit it):

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
