# Serifu intuitive-flow rebuild — swarm plan

Goal: rebuild Serifu's UI so every part of the flow is intuitive, not just
restyled. Work happens on this branch (`feat/ui-rebuild`); `main` stays
untouched until Wal approves a merge after visual review.

## Design direction

Keep the identity, remove the friction:

- Keep the dark warm palette, red seal, big native-script type, hairlines.
  Keep Linen (`linen/`) as the design system; extend it rather than forking.
- One visual language across app and overlay. The HUD is rebuilt from the
  same tokens as the app (dark surface, red accent) instead of cream paper.
- Home answers, in order: **is it working? what do I do next? what is it
  translating?** The primary action becomes opening the reader (with the
  sample page as a secondary "try it" path).
- Teach the pill once, in the product: a first-overlay coach mark
  (tap = translate, hold/double-tap = options, drag = move). Kill one of the
  two HUD-opening gestures (keep long-press; double-tap becomes translate).
- Plain-language settings. Every summary row must mean something to a
  non-developer ("Letters 450 ms after you stop scrolling", not "450 ms").
- Every long wait shows visible progress (capturing / recognising /
  translating states on the pill, HUD and canvas), never a frozen page.
- Mode OFF + pill tap = translate once (and say so), never a silent switch to
  AUTO.

## Hard constraints (every agent)

1. Do not re-add purged images or anything from Serifu-archive/backups. Only
   the bundled manga panels and current `docs/` images exist on purpose.
2. Preserve settings storage keys/schema (`settings/SettingsCodec.kt`) — no
   data loss or reset for installed users.
3. No new network calls in the default on-device path. Privacy copy in
   onboarding/About must stay true.
4. Use `Serifu.colors` / `Serifu.type` tokens and Linen components. No new
   ad-hoc colors, no cream+serif "AI template" look, no decorative chips,
   fake badges or pill-everything.
5. Keep copy tone: short, plain, confident. Preserve good existing strings;
   rewrite jargon. British "Licences" stays.
6. Every screen you touch must render in the JVM screenshot harness
   (light + dark) and be added to `.github/screenshots/expected.txt`.
7. Compose + existing architecture only (no new view system, no new major
   dependency without noting it in the PR description).
8. Overlay views are classic Views on a window, not Compose — keep that
   split, restyle within it.

## Workstreams (7 agents)

| # | Name | Owns | Key outcomes |
|---|------|------|--------------|
| 1 | Home + status | `ui/home/`, `ui/components/ModelUi.kt`, home strings | Primary "open reader" action, clear service/engine status, plain summaries, sample page demoted to secondary |
| 2 | Onboarding | `ui/onboarding/`, onboarding strings | Teaches pill gestures, confirms each system step succeeded, keeps feature trio copy |
| 3 | Pill + HUD | `overlay/FloatingControlPillView.kt`, `overlay/HudView.kt`, `overlay/OverlayManager.kt` | One visual language with app, sane gesture set, HUD stays open while in use, Peek affordance |
| 4 | Translation feedback | `overlay/TranslationCanvasView.kt`, `coordinator/`, `debug/` | Visible capture/OCR/translate progress, reachable error states, keep crossfade+peek |
| 5 | Settings: reading | LanguagesScreen, ReadersScreen, BehaviourScreen | Grouped, plain-language, meaningful row summaries |
| 6 | Settings: engine | EngineScreen, model-download UI in `ui/components/ModelUi.kt` | De-duplicated copy, progressive disclosure for API plumbing, clear download states |
| 7 | Consistency + harness | LetteringScreen, DebugScreen, AboutScreen, `ui/design/`, `ui/theme/`, `ui/testpage/`, `.github/screenshots/` | Token consistency, dark/light parity, expected.txt current, gallery green |

Agents 3 and 4 share `overlay/`; 3 owns pill+HUD, 4 owns canvas+events.
Agent 7 lands last and reconciles drift between the rest.

## Workstream acceptance criteria

1. **Home + status**: ready state leads with one obvious primary action
   (open the reader / pick one); the sample page is visibly secondary; each
   summary row reads as a sentence a non-developer understands; service-off,
   pack-missing and API-key-missing states each show exactly one fix action.
2. **Onboarding**: one step teaches tap/hold/drag on the pill with a visual;
   system-setting steps detect return and confirm success or say what is
   still missing; existing feature-trio copy kept.
3. **Pill + HUD**: HUD uses `DarkPalette`/`LightPalette` values (no
   `InkPaper` cream); long-press opens HUD, double-tap reserved or removed
   (not both); HUD does not auto-dismiss while the user is interacting;
   Peek shows a visible affordance.
4. **Translation feedback**: capture/OCR/translation phases surface as text
   or determinate progress on pill+HUD; errors (`tooltip_*`) are also
   reachable from the HUD; no silent OFF->AUTO switch on tap (tap in OFF
   translates once and says so).
5. **Reading settings**: Behaviour rows read in words ("Letters N ms after
   you stop scrolling"); Languages groups source/target/downloads/network;
   no row summary is a bare number.
6. **Engine settings**: one explanation of the API engine, not two;
   endpoint field under progressive disclosure; Gemini note with the
   endpoint it belongs to; pack download states (not downloaded /
   downloading x% / ready) unambiguous.
7. **Consistency + harness**: every touched screen in `expected.txt` and
   rendering light+dark; no `InkPaper` references left for HUD; token usage
   only; gallery job green.

## Execution

- Runner: parallel coding agents against the Vultr Serverless Inference
  OpenAI-compatible endpoint (`https://api.vultrinference.com/v1`).
  GLM 5.3 for overlay/coordinator work (1M context, tool calling,
  $0.75/1M in, $3.00/1M out), GLM 5.3 Flash ($0.10/$0.35) or DeepSeek V4.1
  Flash ($0.15/$0.60) for the lighter screens. Runner tooling is chosen
  after budget approval.
- Verification loop: each agent runs the JVM screenshot harness for its
  screens, reads the PNGs, and iterates until they look right. CI re-renders
  the full gallery on every push to this branch.
- Budget: capped spend approved by Wal before any agent runs.

### Spend cap enforcement (required before any paid call)

Vultr inference has no platform-side spend limit, and post-response usage
accounting alone cannot hold a cap (concurrent calls and one large final
response can overshoot). The runner must:

1. Reserve before every request: estimated worst-case cost =
   (actual input tokens + `max_completion_tokens`) x reconciled per-model
   price (input, output, cache, tool and reasoning rates confirmed against
   the live docs before the run, not assumed from another workload).
2. Count reservations centrally across all agents (one metering point);
   reject a request when reserved + in-flight >= cap minus headroom.
   Headroom covers one max-size response per in-flight agent.
3. Fail closed: a missing `usage` field, a rate/price lookup failure, or a
   retry storm aborts the run, not retries blind.
4. Reconcile reserved vs actual `usage` on every response and release the
   difference; log both so the cap decision is auditable.
5. If a centrally enforced pre-request cap cannot be established for the
   chosen runner, that uncertainty goes back to Wal before any paid call.

- Done means: gallery green, Instinct visual review of every screen, Wal
  approves the preview, then — and only then — a PR to `main`.
