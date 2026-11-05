<p align="center">
  <img src="docs/icon.png" width="96" alt="Serifu icon, a vermilion rounded square with a white セ">
</p>

# Serifu (セリフ)

Serifu translates manga, manhua, and manhwa on an Android phone. It screenshots a reader you choose, finds the bubbles, and draws English over them.

- Japanese, Chinese, and Korean, plus Latin-script sources (English, French, Spanish, German, Italian, Portuguese)
- On-device translation with ML Kit, or an optional OpenAI-compatible cloud backend
- One-time language pack download of about 30 MB

This tree is version 0.6.6-beta, with `applicationId` `app.walsoup.serifu` and `minSdk` 30.

## Download

Get the latest APK from [Releases](https://github.com/walsoup/Serifu/releases/latest). Needs Android 11 or newer on an arm64 phone; first launch walks you through the restricted settings and accessibility service setup.

## Pages

Home picks the source script and the trigger:

- **Auto** translates after the page stops moving.
- **Manual** waits for a tap on the pill.
- **Off** draws nothing.

<img src="docs/home.png" width="360" alt="Home with Japanese selected">

The sample page bundles the full Volume 1 of *Give My Regards to Black Jack* (ブラックジャックによろしく) by SHUHO SATO (佐藤秀峰) - 209 pages, shared by the author under free secondary-use terms (二次利用フリー) - plus a multi-language stick-figure comic test page.

**Translate** runs OCR, clustering, translation, and lettering. **Boxes** draws the regions the clusterer found. Tap the page to hide the toolbar.

<img src="docs/sample.png" width="360" alt="Japanese sample page, with the toolbar under the status bar">

The rest of the settings sit on the home screen, under the trigger.

<img src="docs/settings.png" width="360" alt="Languages, lettering, reader apps, behaviour, engine, debug, about">

| Row | What it sets |
| --- | --- |
| Languages | Source script, the language you read, and the offline packs |
| Lettering | Font, size range, sound effects (compact, cover, or off). A fresh install uses 9-22 sp. |
| Reader apps | Packages Serifu is allowed to screenshot |
| Behaviour | How long Auto waits after the last scroll (default 450 ms), plus honorifics and the pill |
| Engine | On-device ML Kit, or an OpenAI-compatible API endpoint |
| Debug | Region outlines and the reasoning level sent to the API engine |
| About | Version and the OFL font licences |

<img src="docs/engine.png" width="360" alt="Engine settings, on-device or API">

<img src="docs/about.png" width="360" alt="About, version 0.6.6-beta, with the app icon beside the name">

## First launch

1. Pick the source script.
2. Grant the restricted settings Android requires for a sideloaded accessibility app.
3. Enable the accessibility service.
4. Choose the reader apps.

The service is what takes the screenshot. While it is off, the bottom button opens accessibility settings. With it on, the button reads a sample page instead.

Over a chosen reader, a pill sits on the screen edge. A tap translates the current page; a double-tap opens a floating HUD to swap languages, change trigger modes, or pause.

<p align="center">
  <img src="docs/hud.png" width="340" alt="Floating HUD overlay with language strip and controls">
  &nbsp;&nbsp;
  <img src="docs/translated.png" width="340" alt="Translated comic page with knockout SFX and lettering">
</p>

## How it works

Three Gradle modules: `:core` (plain Kotlin, no Android imports), `:app` (the phone), and `:linen` (the design system the screens are built on). Serifu paints with its own palette: paper `#F3EEE3`, ink `#16140F`, hanko `#C8322B`.

- **Translation** lives behind `TranslationEngine` in `:core`. On-device uses ML Kit; the optional cloud backend posts the page's bubbles to your endpoint and keeps those replies in an in-memory cache for the session.
- **Page flow** is one pipeline: split the page, cluster the bubbles, OCR, translate, letter. The accessibility service and the sample page both run it.
- **The reading loop** is a reducer. Accessibility callbacks become events; the reducer returns the next state plus effects like capture, process, show page, or move the pill.

## Tests

```
./gradlew :core:test :app:testDebugUnitTest   # JVM, no device
./gradlew :app:connectedDebugAndroidTest      # on a device
```

The JVM tests cover bubble detection and cleanup, lettering fit, the translation cache and prompt handling, and the reading loop. The device test opens the sample page, translates it, and checks that lettering lands inside the bubbles.

## Build

The debug build needs JDK 17. `./gradlew :app:assembleDebug` writes the debug APK.

## Licenses

Serifu's own code is source-available under the PolyForm Noncommercial License 1.0.0 (see LICENSE): reading, modifying and sharing for non-commercial purposes is fine, while commercial use needs permission. It is not an open-source license.

- `linen/` is the Linen design system, all rights reserved (see `linen/LICENSE`).
- The lettering fonts are under the SIL Open Font License (see `app/src/main/assets/licenses/`).
- The bundled sample manga is *Give My Regards to Black Jack* (ブラックジャックによろしく) by SHUHO SATO (佐藤秀峰), used under the author's secondary-use terms (<https://densho810.com/free/>), not under the app's license.

## Status

Serifu is still in early development, tested on real devices. OCR and lettering may still change between releases. If you find a bug or have feedback, please [open an issue](https://github.com/walsoup/Serifu/issues).
