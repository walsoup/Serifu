# Serifu full-flow UX audit

Baseline: `main` @ 2714e74 (v0.7.0-alpha.3). Grounded in the app sources and the
`docs/` screenshots. This audit is the input to the intuitive-flow rebuild; it
describes problems, not solutions (solutions live in `PLAN.md`).

## The flow today

1. **Onboarding** (3-5 steps): Welcome -> Languages (+ offline pack downloads,
   Wi-Fi-only toggle) -> "Allow restricted settings" (sideloaded installs only)
   -> Enable accessibility service -> Try it / pick reader apps.
   `ui/onboarding/OnboardingScreen.kt`
2. **Home**: brand lockup + mode badge, "watching N apps in Auto" caption,
   accessibility-off banner, language-direction hero (tappable -> Languages),
   engine status card, script picker, Trigger pills (Auto/Manual/Off),
   configuration rows (Languages, Lettering, Reader apps, Behaviour, Engine,
   Debug, About), and a bottom CTA "Read a sample page". `ui/home/HomeScreen.kt`
3. **In a reader app**: a small red floating pill docked to a screen edge.
   Gestures (`coordinator/TranslationCoordinator.kt`):
   - tap = translate now (or, if mode is OFF, silently switches to AUTO)
   - double-tap OR long-press = open the HUD (two redundant gestures)
   - drag = re-dock to either edge
4. **HUD** (cream paper panel): source-language row, AUTO/MANUAL/OFF,
   Pause / Peek (hold) / Open. Auto-dismisses on a timer
   (`Tuning.HUD_DISMISS_MS`). `overlay/HudView.kt`
5. **Translation**: canvas crossfades the lettered page over the reader;
   hold Peek to see the original; pill ring shows a busy state.
   `overlay/TranslationCanvasView.kt`
6. **Settings**: Languages (pack downloads, pivot note, Wi-Fi-only), Reader
   apps, Lettering (bubble preview, font, size range, caps, SFX), Behaviour
   (settle timer, rules, haptics, pill edge/opacity), Engine (on-device
   ML Kit / Bergamot / API), Debug, About.
   `ui/settings/SettingsScreens.kt`
7. **Sample page**: bundled manga page to try lettering inside the app.
   `ui/testpage/TestPageActivity.kt`

## Friction, by stage

### First run / onboarding
- The two hardest steps (restricted settings, accessibility toggle) send the
  user into system screens with text-only instructions; nothing confirms they
  came back successful except the waiting label. (`OnboardingScreen.kt`,
  `enable_waiting`, `restricted_steps`)
- Nowhere does onboarding teach the pill gestures. The only place they are
  spelled out is the accessibility content description
  (`pill_content_description`), which sighted users never see.

### Home
- The thumb-zone CTA already resolves the top blocker and opens the reader
  when ready (good). But when everything is set, the eye-catching constant
  element is still "Read a sample page" (ghost) and the state machine of
  CTAs (setup / accessibility / turn on / download pack / pick readers /
  open reader) is implicit — nothing explains the flow or what happens after
  the reader opens.
- A first-time user has to infer the activation model (pill appears in reader
  apps) from a one-line caption (`home_active_hint`).
- "Behaviour  450 ms" and "Lettering  9-22 sp · SFX Compact" are summaries
  only a developer understands. (`SettingLine` rows)
- Mode OFF + tap pill => silently becomes AUTO. Surprising and invisible.

### Overlay (pill + HUD)
- Tap vs double-tap vs long-press vs drag is undiscoverable; double-tap and
  long-press do the same thing, which signals gesture uncertainty.
- The HUD speaks a different visual language (cream paper, hard shadows) than
  the app (dark, hairlines, red seal). Same product, two personalities.
- HUD auto-dismiss on a timer can vanish mid-decision; a language tap also
  closes it immediately, so switching language *and* mode takes two openings.
- Peek is hold-only with no affordance.

### Settings
- Engine screen repeats its explanation twice (`engine_api_desc` vs
  `engine_ai_body` are near-identical) and leads with raw endpoint plumbing
  (API key / model name / custom endpoint) — the most technical screen is the
  one a struggling user lands on when translation "doesn't work".
- "Behaviour" mixes four unrelated groups (timing, translation rules, haptics,
  pill). "Scroll settle timer / Debounce wait time" is jargon for "how long
  after you stop scrolling before Serifu letters the page".
- Languages mixes source, target, downloads, pivot caveat and network policy
  on one long scroll.
- Engine status on home (`ActiveModelStatusCard`) duplicates engine/pack
  state that Engine and Languages also own.

### Cross-cutting
- No visible per-page progress: during capture+OCR+translation the only
  feedback is the pill's ring phase. A slow page reads as frozen (the user has
  stated a general preference for visible progress on long jobs).
- Error tooltips exist (`tooltip_secure`, `tooltip_capture_failed`,
  `tooltip_model`) but are easy to miss and not reachable from the app.
- Light/dark: the app ships both palettes; overlay HUD is cream-only.
- Terminology drift: "letters/lettering" vs "translates", "Behaviour",
  "Script" vs "Source language".

## What already works (keep)

- Brand: dark warm charcoal + red seal + strong type; hairline-separated
  setting rows; speech-bubble lettering preview on Lettering.
- Onboarding feature trio (OCR / lettering / privacy) is good copy.
- Reader-picker scoping ("Serifu only letters the apps you choose") is a good
  mental model; keep it prominent.
- Peek, Pause, dock-to-edge are good mechanics; they need discoverability,
  not replacement.
- JVM screenshot harness (`.github/workflows/screenshots.yml`,
  `app/src/screenshot/`) renders every production screen light+dark in CI.
