# Briefing for Android

Native Kotlin/Jetpack Compose reader for the daily briefing, built for the OnePlus Open (folded and unfolded).
It only reads the published data: `feed.json` and `editions/<date>.json` from the site. Never a second source.

**Install:** download the newest `briefing-*.apk` from
[Releases](https://github.com/KazimeirzOdnowiciel/briefings/releases/latest) and allow installs from your browser.
After that the app updates itself: an **UPDATE** pill appears when a newer release exists.

## Features

- Edition list with photos, one accent colour per section, scoreboards for NBA and football, and tonight's fixtures
- In-app reader for extracted article text (serif, adjustable size), with prev/next, Save and Share
- Two-pane layout when unfolded (section rail plus the section), predictive back and edge-to-edge
- Offline: every edition is cached as raw JSON. A date strip opens older editions, and search covers all of them
- Saved stories, a light/dark theme, and pull-to-refresh
- Home-screen widget (Glance) with the day's headlines
- 08:45 notification when the edition is live (exact alarm), and a separate sport channel for upsets and overtime
- Deep links `briefing://edition/YYYY-MM-DD`

## Build

Toolchain on the build PC: JDK 21, Android SDK 35 (build-tools 35.0.0) and Gradle 8.10.2. There is no wrapper in the project.

```bash
cd android
export JAVA_HOME="C:/Program Files/Android/openjdk/jdk-21.0.8"
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
"$LOCALAPPDATA/hermes/tools/gradle-8.10.2/bin/gradle.bat" --no-daemon assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

**Signing:** the release key is `briefing-release.jks` (alias `briefing`) with its password in `.briefing_keystore_pass`,
both in `~/.briefing/`. They are never committed, and `*.jks` is git-ignored. Without them the release build is
unsigned, so a fresh checkout still compiles. **Back the keystore up**: every release must be signed with
the same key, or Android refuses the update and the app has to be uninstalled first.

## Release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then build as above.
2. `python pipeline/release_app.py "what changed"` checks that the APK's version matches and that it is signed
   with the release key, then creates the `app-v<version>` release with `briefing-<version>.apk` attached.
3. Phones on an older version see the UPDATE pill on next launch.

## Data contract

See the main [README](../README.md#data-contract). The app shows the audio chip only when `audio` is set; since
2026-11-17 it is always `null`.

Source history before 0.38 lived outside git (built by the Hermes agent). Hermes's notes on the
toolchain traps, Glance and fold layout are in `%LOCALAPPDATA%\hermes\skills\software-development\briefing-android-app\`.
