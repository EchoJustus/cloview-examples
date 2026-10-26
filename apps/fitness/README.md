# Fitness example: the benchmark app on JavaFX, WPF and Flutter

This is the fitness and workout application of the acceptance benchmark
(`benchmarks/fitness-baseline`), built as **one shared cloview app** with thin host entry
points. It uses the modules of [cloview-core](https://github.com/echojustus/cloview-core),
cloned next to this repository (`../cloview-core`). It has five tabs:

| Tab | Contents |
|---|---|
| Catalog | search, multi-select filter chips, keyed exercise list, detail with media preview |
| Plan Builder | ordered items; copy, move, remove; start, pause, resume, complete, backfill |
| Execution Log | completed records; heart-rate CSV/JSON import with preview |
| Anatomy & Analytics | 2D body heatmap, six-region radar, set-volume comparison, daily calories |
| Calendar | 42-cell month grid, day details, *Day analysis* |

What is implemented, what is partial and what is planned, case by case:
[`docs/FITNESS_CAPABILITY_MAP.md`](../../docs/FITNESS_CAPABILITY_MAP.md). The native view
contracts: [`docs/NATIVE_CONTRACTS.md`](../../docs/NATIVE_CONTRACTS.md).

## Layout

```
apps/fitness/
├── shared/                         one copy of the app, used by every host
│   ├── deps.edn  deps-clr.edn
│   ├── src/clogem/fitness/         the reference core, byte-identical to the baseline (below)
│   │   domain.cljc catalog.cljc analytics.cljc
│   ├── src/fitness/
│   │   app.cljc        initial state, store, register!
│   │   events.cljc     all event handlers (pure; time and date from coeffects)
│   │   views.cljc      the root view: five tabs, canonical vocabulary + composites + natives
│   │   derive.cljc     memoized derivations and native props (ARCH-004 counters)
│   │   native.cljc     registry of host renderers; explicit placeholder when missing
│   │   body.cljc       2D anatomy geometry, colour scale and hit test (from muscle_view.dart)
│   │   hr_import.cljc  heart-rate import preview and commit (DOM-005)
│   │   dates.cljc      portable calendar arithmetic
│   │   demo.cljc       the demo document (equal to fixtures/demo-document.edn; a test checks it)
│   │   oracle.cljc     DOM-001 runner and FITNESS_BUSINESS_RESULTS_V1 writer
│   │   verify.cljc     unit tests + oracles entry point for every host
│   │   io.cljc         file helpers for verify and tests
│   └── test/
│       clogem/fitness/*_test.cljc  the reference tests, ported to .cljc
│       fitness/app_test.cljc       the shared app on the headless reconciler
├── javafx/  fitness.javafx.{main,native,smoke}  JavaFX entry, Canvas renderers, off-screen smoke
├── wpf/     fitness.wpf.{main,native}            CloViewHost entry points, WPF Canvas renderers
└── flutter/ fitness.flutter.{main,native} + test  Flutter entry, CustomPaint renderers, widget test
```

### Host overrides (ARCH-001)

Everything under `shared/` is consumed unchanged by all three hosts. The only
host-specific code is the following.

| Host | Files | Why |
|---|---|---|
| JavaFX | `javafx/src/fitness/javafx/main.clj` | start the JavaFX shell, install the effects, register the renderers, enable persistence |
| | `javafx/src/fitness/javafx/native.clj` | the three drawing surfaces as JavaFX `Canvas` |
| | `javafx/src/fitness/javafx/smoke.clj` | off-screen verification (not part of the app) |
| WPF | `wpf/src/fitness/wpf/main.cljr` | the CloViewHost entry points `start!`, `run-headless-smoke!` and `run-tests!` |
| | `wpf/src/fitness/wpf/native.cljr` | the drawing surfaces as a WPF `Canvas` of shapes |
| Flutter | `flutter/src/fitness/flutter/main.cljd` | Flutter entry point |
| | `flutter/src/fitness/flutter/native.cljd` | the drawing surfaces as `CustomPaint` |
| | `flutter/test/fitness/flutter/fitness_test.cljd` | widget test + DOM-001 on Dart (not part of the app) |

The renderers receive only data (`:props`) and report input by dispatching the event
vectors found in `(:events props)`. A host without a renderer for a kind shows an explicit
"not available on this host yet" text (ARCH-003).

### Provenance of the reference core

The core files are copies of `benchmarks/fitness-baseline/reference-core`. These six files
(the three sources and their three tests) are under the repository's MIT licence, like
everything else (README, "License"). Their SHA-256
values equal the entries of the baseline's `MANIFEST_SHA256.csv`:

| File | SHA-256 |
|---|---|
| `shared/src/clogem/fitness/domain.cljc` | `b7c688d37f9f04b4d89379733e85107e5fec01e5562ba6fdcb027285aa64d85d` |
| `shared/src/clogem/fitness/catalog.cljc` | `48fbd06c7889b3581d30157682d2752acd5e922ce3150a3899f6e0459b65bc3c` |
| `shared/src/clogem/fitness/analytics.cljc` | `24c70c27eab81648254b3e8707a2ae9a7c8b6be9ae4818849f6a17fd1dca837f` |

The tests were renamed `.clj` → `.cljc` so every host runs them. Three changes were
made, and nothing else:

- `clojure.lang.ExceptionInfo` became `#?(:cljd cljd.core/ExceptionInfo :default
  clojure.lang.ExceptionInfo)`;
- `Math/abs` became `abs`;
- *(Stage 2-2b)* the one `with-redefs` block in `domain_test` (it counts calls to
  `validate-hr-samples`) is wrapped in `#?(:cljd nil :default …)`. ClojureDart has no
  `with-redefs`: calls between namespaces are compiled statically. The first Windows run
  of the Dart tests stopped on it ("Unknown symbol: with-redefs"). That one call-count
  assertion now runs on the JVM and ClojureCLR only; every other assertion runs on Dart
  too.

| Test | Baseline SHA-256 | Port SHA-256 |
|---|---|---|
| `catalog_test` | `fff9022e4665174cb79eeec5f87d2bf38e0e8148df93f19e781bbec9841b02bc` | identical |
| `domain_test` | `5f63534c79753fd07a885372e3fcb1e012afd54dfb5cf1034a4268bc8b9c762e` | `eb8976d5a8daa51a7af363261d495c0e4170dff73ee2c7c235a80098567682d2` |
| `analytics_test` | `e81be295866233bd14c0b740ea53e0cd0121b81fa814d7700f92b6885a7f7f64` | `dba22da9fa80f437f77101a7471443b31b65e24bb634bf4141190296c7d07c25` |

The core is kept unchanged so the oracles test the reference itself. Its known
ClojureCLR hazards are documented, not patched:

- `sort` of equipment tags is culture-sensitive on ClojureCLR 1.12, so
  `fitness.derive/equipment-options` re-sorts ordinally;
- `sort-by` on ties is unstable above 16 items on the CLR and above 32 in Dart. Neither
  is reached by the fixtures.

## Running it

From the root of cloview-examples (with cloview-core cloned next to it):

| Host | Tests + DOM-001 oracles | Off-screen smoke | Interactive |
|---|---|---|---|
| JVM | `bb fitness:test` | `bb smoke:fitness:javafx` | `bb run:fitness:javafx` |
| CLR (Windows) | `bb fitness:test:clr` | `bb smoke:fitness:wpf` | `bb run:fitness:wpf` |
| Flutter (≥ 3.35) | `bb fitness:flutter` | (widget test inside) | `bb run:fitness:flutter` (`-d windows` by default) |

- `bb fitness:test` runs `clj -M:fitness`: the reference tests, `fitness.app-test` and all
  37 oracles. It writes `verify/out/fitness-jvm-{business-results,summary}.json`, then
  runs the baseline's own checkers:
  - `tools/check_package.py`;
  - `tools/check_fixtures.py`;
  - `tools/check_fixtures.py --observed <envelope>`.

  The checkers need Python 3 (`py -3`, `python` or `python3`); without it they are
  skipped with a note.
- On Windows, `verify\run-windows-checks.ps1` does all of the above on all three hosts
  (steps 02–04, then the checkers on each host's envelope in step 05) and packages the
  results (see [`VERIFY_ON_WINDOWS.md`](../../VERIFY_ON_WINDOWS.md)).

Without babashka:

```bash
clj -M:fitness --out-dir verify/out          # JVM tests + oracles
clj -M:smoke-fitness-javafx verify/out/fitness-jvm-smoke.json
clj -M:run-fitness-javafx
python3 -I benchmarks/fitness-baseline/tools/check_fixtures.py --observed verify/out/fitness-jvm-business-results.json
```

```powershell
# CLR, after the harness (or bb wpf:build) staged target\clr-src and built CloViewHost
# (from a copy of cloview-core's host template, in target\clr-host-project):
.\target\clr-host\CloViewHost.exe --src .\target\clr-src --ns fitness.verify --run-tests --out verify\out\fitness-clr-summary.json
.\target\clr-host\CloViewHost.exe --src .\target\clr-src --ns fitness.wpf.main --headless-smoke --out verify\out\fitness-clr-wpf-smoke.json
.\target\clr-host\CloViewHost.exe --src .\target\clr-src --ns fitness.wpf.main
```

**Persistence.** The interactive apps load and save the document through
`cloview.fx.storage`.

- Desktop: `%LOCALAPPDATA%\cloview\fitness.edn`, with a `.bak` backup (EFFECT-001/002).
- Flutter: shared_preferences.

Tests and smokes run with persistence off and the embedded demo document.

## Logical events

`benchmarks/fitness-baseline/docs/UI_SEMANTICS.md` names logical events for acceptance
traces. Their cloview event vectors:

| Benchmark logical event | cloview event (payload appended by the control) | Notes |
|---|---|---|
| `catalog/query-change` | `[:catalog/query-change s]` | |
| `catalog/filter-change` (dimension, selected ids) | `[:catalog/filter-toggle dim id checked?]` | One event per chip action (the option and its new state). The resulting id set is `[:catalog :filters dim]`. |
| (clear one dimension) | `[:catalog/clear-dimension dim]` | |
| `catalog/clear` | `[:catalog/clear]` | clears the search text and every dimension |
| (select a row) | `[:catalog/select exercise-id]` | |
| `exercise/open`, `exercise/save`, `exercise/cancel` | — | the editor is planned (capability map, UI-001/002) |
| `media/select` | `[:media/pick exercise-id]` → `:file/pick` → `[:media/picked id paths]` → `[:media/app-dir id src dir]` → `[:media/copied id dest]` | Cancel: `[:media/pick-cancelled]`. Failures: `[:media/failed id err]`, `[:pick/failed :media err]`. |
| `plan/add-exercise` | `[:plan/add-exercise exercise-id]` | target: the selected plan, or a new plan for today |
| `plan/item-copy` | `[:plan/item-copy plan-id item-id]` | the copy is placed right after its source |
| `plan/item-move` | `[:plan/item-move plan-id item-id ±1]` | |
| `plan/item-remove` | `[:plan/item-remove plan-id item-id]` | |
| `training/start` | `[:training/start plan-id item-id]` | refused while another item is active |
| `training/pause` / `resume` / `complete` | `[:training/pause]` `[:training/resume]` `[:training/complete]` | a second complete is a no-op |
| `training/backfill` | `[:training/backfill plan-id item-id]` | duration source `plan-estimate` |
| (heart-rate file) | `[:hr/pick]` → `[:hr/picked paths]` → `[:hr/read path text]` or `[:hr/read-failed err]` | offset field: `[:hr/offset-change s]` |
| `heart-rate/import-confirm` | `[:hr/confirm]` | cancel: `[:hr/cancel]` |
| `calendar/select-date` | `[:calendar/select-date "YYYY-MM-DD"]` | |
| (month navigation) | `[:calendar/change-month "YYYY-MM"]`, `[:calendar/go-today]` | |
| `analytics/open-date` | `[:analytics/open-date "YYYY-MM-DD"]` | |
| (range) | `[:analytics/preset :latest-week\|:last-7\|:last-30]`, `[:analytics/range from to]` | |
| (mode, metric) | `[:analytics/mode "actual"\|"planned"]`, `[:analytics/metric "sets"\|"weighted-minutes"]` | |
| `body/select-muscle` | `[:body/select-muscle id-or-nil]` | |
| `body/style-change` | `[:body/style-change :two-d\|:three-d]` | |
| (navigation) | `[:nav/tab tab]`, `[:nav/split ratio]` | |
| (persistence) | `[:doc/save]`, `[:doc/load]` | Results: `[:doc/saved k]`, `[:doc/save-failed err]`, `[:doc/loaded doc]`, `[:doc/load-failed err]`. |

To record a trace, pass `:on-event` to `cloview.core.store/create-store`, as the smokes
do. Each processed event arrives exactly once, in order.
