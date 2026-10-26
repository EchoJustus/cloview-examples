# Fitness Application: Multi-Host Framework Acceptance Baseline

Package edition: 2026-10-24 (prepared October 23-24). Source application baseline: Clogem Fitness 0.3.0.

This is an English-language benchmark kit for evaluating a future shared Clojure UI and application framework. It is not that framework, a proposal for its final DSL, or a claim that the source application already runs on JVM or CLR.

The intended recipient is Claude during architecture research, repository initialization, and subsequent implementation acceptance. The goal is one shared application specification and business model with predictable behavior on JVM/JavaFX via cljfx, Dart/Flutter via ClojureDart, and a Windows GUI via ClojureCLR. Native-looking controls are welcome. Pixel-identical rendering is not required.

## Start here

1. Read `TASK_SCOPE.md` and `CLAIM_BOUNDARIES.md` before drawing conclusions from screenshots or historical test counts.
2. Read `docs/FUNCTIONAL_BASELINE.md`, `docs/UI_SEMANTICS.md`, and `acceptance/ACCEPTANCE_GUIDE.md` for the observable contract.
3. Inspect `fixtures/` and `docs/DATA_AND_ORACLES.md`. Expected numeric results come from an English-normalized derivative of the existing pure core, with explicit synthetic inputs.
4. Inspect `visuals/` and `docs/VISUAL_REFERENCE.md`. These are English-localized component references, not evidence of a released English application or any JVM/CLR implementation.
5. Read `docs/PORTING_AND_EVIDENCE.md` and use `acceptance/result-template.json` to record actual host results.
6. Give `STARTUP_PROMPT.txt` to the Claude or Claude Code session that will apply this benchmark. It supplements the architecture task; it does not select the framework design in advance.

## What is included

| Area | Purpose |
| --- | --- |
| `docs/` | English functional rules, host boundaries, visual provenance and verification history |
| `reference-core/` | Selected pure application core, normalized to English for this benchmark |
| `fixtures/` | Fictional data and deterministic expected results |
| `media/` | Neutral synthetic animation for media and fixed-viewport checks |
| `reference-ui/` | Reproducible localized component rendering material, if listed in the visual guide |
| `visuals/` | Rendered references using existing Flutter component geometry and layout |
| `acceptance/` | Capability matrix, test cases and an unexecuted result template |
| `tools/` | Local fixture, report and package-integrity checks |
| `evidence/` | Results actually obtained while preparing this package, separate from historical application verification |
| `SOURCE_MAP_PUBLIC.csv` | Source-relative provenance and transformation notes |

`FILES_INCLUDED.txt` is the exact archive inventory. `MANIFEST_SHA256.csv` records every other entry. The checksum of the ZIP is supplied beside the archive, outside it.

## Quick local checks

Run from the extracted package root with Python 3:

```text
python tools/check_fixtures.py
python tools/check_package.py
python tools/check_acceptance_report.py acceptance/result-template.json
```

The template check validates report structure only. Its NOT_RUN results cannot pass acceptance. The guide describes running the included Clojure oracle and collecting genuine results from each future host.

Use `--require-pass` to enforce the report's selected profiles. Use `--full` to require both CORE and FITNESS_NATIVE across the required hosts. These switches intentionally fail on the untouched template.

## Interpretation

- A successful Python consistency check is not a successful host port.
- A successful pure-core run is not evidence of rendered controls, callback delivery, native persistence, media playback or UI responsiveness.
- A screenshot is not evidence that a button works.
- A capability shown as unavailable must be declared unavailable. A required capability cannot silently become optional or be counted as a pass.
- Business tolerances and IDs are stable contracts; colors, exact typography, window chrome and native picker appearance may vary.

The original production app and personalized exercise media were not modified to create this package. No personal training history, Health Connect records, pairing credentials, original portrait images, platform SDKs, compiled executables or APKs are included. Synthetic records are benchmark data, never observed human activity.

This package is © 2026 EchoJustus (Jiapeng Ji) and ships under the MIT License of the repository that contains it (`LICENSE` at the repository root). Attribution and provenance are preserved by `SOURCE_MAP_PUBLIC.csv` and the SHA-256 tables (`MANIFEST_SHA256.csv`, `reference-core/SOURCE_MAP.json`). Audit dependency licenses separately before redistributing a future framework or sample application.
