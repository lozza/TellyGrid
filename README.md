# TellyGrid

**A UK home screen for Android TV: recommendations from your apps, a live TV guide, and one click straight to the show.**

[![Android APK](https://github.com/lozza/TellyGrid/actions/workflows/android.yml/badge.svg)](https://github.com/lozza/TellyGrid/actions/workflows/android.yml)
[![Live guide](https://github.com/lozza/TellyGrid/actions/workflows/guide.yml/badge.svg)](https://github.com/lozza/TellyGrid/actions/workflows/guide.yml)

![TellyGrid Home on Android TV](docs/screenshots/home.png)

| Home rows | Guide |
| --- | --- |
| ![Watch next and app rows](docs/screenshots/home-rows.png) | ![TV guide](docs/screenshots/guide.png) |
| **Apps** | **Settings** |
| ![Installed apps](docs/screenshots/apps.png) | ![Settings](docs/screenshots/settings.png) |

## Download

**[Get the latest APK from Releases](https://github.com/lozza/TellyGrid/releases/latest)** and sideload it onto your Android TV or Google TV.

This is an early test version, so far tested on a Philips Android 11 Freeview Play TV. Please report problems in [Issues](https://github.com/lozza/TellyGrid/issues).

## What it does

- **Home** shows a rotating banner and rows of recommendations published by your apps (Netflix, Disney+, Prime Video, Jellyfin, Stremio, Pluto TV and more), plus BBC iPlayer's most popular episodes. Picking one opens that exact title.
- **Watch next** lists what you were watching most recently, and you choose which apps can add to it.
- **Guide** is a time-grid guide for Freeview, NOW, discovery+, Pluto TV and TNT Sports channels. Selecting a channel opens it live, using the TV's tuner for Freeview.
- **Apps** lists everything installed, and lets you choose and order the apps on Home.

TellyGrid only hands off to apps you already have. It contains no streams, accounts or tokens.

### Make sure go into Accessibility settings to force the Launcher as the main app. It will stop the Android TV default from loading. Home and Back buttons will then work as expected.

## What's new in alpha 7

A new rotating Home banner, Watch next that stays up to date, faster artwork, direct links for Pluto TV and 12 discovery+ channels, a BBC iPlayer row, a new font and logo, and many remote-control fixes. Full notes are on the [release page](https://github.com/lozza/TellyGrid/releases/tag/v0.7.0-alpha.7).

## Build it yourself

Needs JDK 17 and Android SDK 35:

```bash
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## More detail

- [Technical notes](docs/TECHNICAL.md): architecture, provider links, the guide feed and the roadmap.
- [Handoff notes](docs/HANDOFF-2026-09-27.md): current state and what to do next.
