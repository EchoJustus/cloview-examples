# Fitness benchmark capability map

This document reviews `benchmarks/fitness-baseline/acceptance/CAPABILITY_MATRIX.csv` and
the package's `docs/UI_SEMANTICS.md` against cloview's vocabulary, composites, native
contract and effects. For every acceptance case it records:

- what in cloview supports the case;
- how much of it the `apps/fitness` scaffold already implements;
- what evidence exists;
- what remains for Stage 3.

**No acceptance result is claimed here.** Per the package's
[`CLAIM_BOUNDARIES.md`](../benchmarks/fitness-baseline/CLAIM_BOUNDARIES.md), results
start `NOT_RUN`. A case passes only when it has been run on the host with the evidence
that `acceptance/cases.json` asks for: screenshots, traces, logs. The evidence below is
the sandbox's, meaning JVM tests, ClojureCLR built from source, JavaFX under Xvfb and WPF
stand-ins, plus what the Windows harness produces in steps 02–05
([`VERIFY_ON_WINDOWS.md`](../VERIFY_ON_WINDOWS.md)). The Windows run of the Stage 2-2b
tree (2026-10-25, before the repository split, when these checks were harness step 07)
gave 37/37 DOM-001 oracles on the JVM, on ClojureCLR and on Dart, the fitness smokes
passed on JavaFX (21/21) and real WPF (19/19), and the 47 Dart tests passed
(cloview-core's [`WINDOWS_VERIFICATION_REPORT.md`](https://github.com/echojustus/cloview-core/blob/main/docs/WINDOWS_VERIFICATION_REPORT.md) §8).

**Status words.**

- **Ready**: support and scaffold are in place, with automated evidence; formal acceptance
  is still to run.
- **Partial**: framework support exists, but the scaffold covers only part of the case.
- **Planned**: not built yet; the approach is noted.

For the Flutter column: the Dart code is written and statically checked, including the
ClojureDart coverage rule. On Windows (Flutter 3.47.8, the Stage 2-2b run of 2026-10-25)
the fitness app compiled, passed its 47 Dart tests and produced a Dart oracle envelope
that matches all 37 business results.

## 1. Control meanings (UI_SEMANTICS) → cloview vocabulary

| Benchmark meaning | cloview | Native result (JavaFX / WPF / Flutter) |
|---|---|---|
| Text/label | `:ui/text` (`:wrap?`) | Label / TextBlock / Text |
| Button (one activation = one command; disabled = none) | `:ui/button` `:on-press`, `:disabled?` | Button / Button / ElevatedButton |
| Text input | `:ui/text-input` (controlled `:value`, `:on-change`, `:on-submit`) | TextField / TextBox / TextField with a managed controller (caret and IME safe) |
| Numeric input | `:ui/text-input` plus shared validation (e.g. the HR offset field: integer minutes, -840..840) | same |
| Single choice | `:ui/select` (controlled `:value`), `:ui/tabs` | ComboBox / ComboBox / DropdownButton |
| Multiple choice (stable id set, labels, explicit clear) | `components/chip-group`: `:ui/toggle` chips in a `:ui/wrap`, plus a clear button | ToggleButton in a FlowPane / ToggleButton in a WrapPanel / FilterChip in a Wrap |
| Switch/checkbox | `:ui/checkbox`, `:ui/switch` (controlled `:checked`, boolean payload) | CheckBox (the switch is a styled CheckBox) / CheckBox / Checkbox, Switch |
| List / repeated rows | keyed children (`:key`): the reconciler and cljfx keep identity across insert, delete, filter and reorder | |
| Row / column / grid | `:ui/row` `:ui/column` `:ui/grid` (`:columns` `:auto`/`{:fr n}`/px), `:gap`, `:align`, `:grow` | HBox, VBox, GridPane / StackPanel, Grid / Row, Column, Table-like grid |
| Scroll | `:ui/scroll` (`:direction`) | ScrollPane / ScrollViewer / SingleChildScrollView |
| Dialog/editor | `:ui/modal` (`:open?`, `:on-dismiss`) | owned modal Stage / overlay layer in the window / overlay portal |
| Image/media | `:ui/image` (`:src {:file|:asset|:url}`, `:fit`); video via `:ui/native :fitness/media-player` | ImageView / Image (+ GIF animation) / Image |
| Feedback (busy/success/empty/unsaved/failure) | app state rendered as text: the status row (`Saving…`, `Saved`, `Save failed`, `Unsaved changes`), empty-state texts, disabled buttons while busy | |
| Navigation | `:ui/tabs` / `:ui/tab`, `:ui/split-view` | TabPane / TabControl / TabBar; SplitPane / Grid + GridSplitter / custom split |

**Shared style subset.** `:style` supports the roles the benchmark uses:

- padding, gap, width/height bounds, alignment;
- border (width, colour), radius, background, opacity;
- `:font-size`, `:font-weight`, colour (`#RRGGBB[AA]`), text alignment.

Colour is never the only cue (see cloview-core's
[`ARCHITECTURE.md`](https://github.com/echojustus/cloview-core/blob/main/docs/ARCHITECTURE.md) §4): chips are
checked toggles, the selected day is bracketed, and "done"/"open" and "unknown" are
written out.

## 2. Case by case

### Core UI

| Case | cloview support | Scaffold today | Evidence | Remaining |
|---|---|---|---|---|
| **UI-001** Library and form control semantics | all control meanings of §1 | Library: search, chip filters, keyed list, detail. **No exercise editor and no profile editor yet.** | screens render with no tree problems (`app_test`); kitchen-sink smokes cover every control | exercise editor (`exercise/open`, `exercise/save`, `exercise/cancel`) and profile editor (weight, resting/max HR, HR-adjustment switch) — **Partial** |
| **UI-002** Validation, mutually exclusive choices | editor controls; validation in the reference core | — | the domain rules (primary required, disjoint primary/secondary, *No equipment* exclusive) run in the DOM-001 oracles | editor UI that shows the failing field — **Partial** |
| **UI-003** Search and multiselect composition | `chip-group` (one event per toggle), `:ui/text-input` | **Ready**: OR within a dimension, AND across, secondary muscles count, trimmed case-insensitive search, Clear all, clear one dimension | `app_test/catalog-filters`; `components_test`; JavaFX smoke (21 checks) and WPF stand-in smoke: chip toggles, count, clear all, chip unchecked after clear | formal run with screenshots |
| **UI-004** Equipment options, independent intensity | derived options (`fitness.derive/equipment-options`) | **Partial**: assigned tags only, ordinal order, *Not set* for missing equipment, obsolete selections dropped (`effective-filters`) | `app_test` (option order and *Not set*) | creating custom tags and editing intensity without touching MET need the editor |
| **UI-005** Stable repeated-row identity | keyed reconciliation; `reorder-item`, `duplicate-item`, `remove-item` from the core | **Partial**: plan rows keyed by item id with Up/Down/Copy/Remove; copy lands after its source | `app_test/plan-items-and-training`; `reconcile_test` (keyed reorder, focus and handler stability) | editable item settings (duration, reps, target HR, scheduled time) for the focus-while-reordering check |
| **UI-006** Editor and file-picker cancellation | `:file/pick` with `:on-cancel` / `:on-failure` | **Partial**: cancelling the media chooser keeps the previous media and says "No file chosen"; cancelling the HR chooser re-enables import | `app_test` (cancel / unsupported) | editor cancel needs the editor. Flutter has no file chooser yet (§4.3). |
| **UI-007** Navigation and usable resizing | `:ui/tabs`, `:ui/split-view`, `:ui/scroll`, grow rules | **Partial**: five tabs, a split catalog, scrolling screens; active training lives in the document and survives navigation | smokes navigate every tab | document a minimum window size and check 1600×1100; narrow layouts are not claimed |
| **UI-008** Keyboard and named state cues | native focusable controls; text cues; `:a11y-label` on natives | **Partial**: by design — state is written out, the body map has a select and a Clear button as its keyboard path | — | audit with each host's accessibility inspector |
| **UI-009** Conditional views, standard layout vocabulary | V1 vocabulary; conditional children; `:style` | **Ready** | in cloview-core: the Greeter kitchen sink on JavaFX (27/27) and on the WPF stand-ins (29/29), and the Flutter widget test (its harness step 06); conditional removal dispatches nothing (no-echo checks) | screenshots |

### Domain

| Case | cloview support | Scaffold today | Evidence | Remaining |
|---|---|---|---|---|
| **DOM-001** Execute every frozen oracle | shared `.cljc` core; `fitness.oracle`; `FITNESS_BUSINESS_RESULTS_V1` writer | **Ready** | 37/37 exact on the JVM, on ClojureCLR 1.12.6 and on master, and through CloViewHost's `Program.cs`; on Windows also on Dart (the 2026-10-25 run; harness step 04 here); `check_fixtures.py --observed` PASS for each envelope | record results in `result-template.json` |
| **DOM-002** Plan snapshot, repeated items | core snapshot rules | **Partial**: start and backfill capture the exercise snapshot; scheduled time is shown as metadata and never starts anything | `app_test` (snapshot captured) | showing that history survives a catalog edit needs the editor; plan date/name editing |
| **DOM-003** Measured timing, idempotent completion | `:now` coeffect (injectable clock) | **Ready**: only one active item; pauses excluded; a second Complete is a no-op | `app_test/plan-items-and-training` with a controlled clock | — |
| **DOM-004** Backfill, interrupted-session recovery | — | **Ready**: backfill is marked `plan-estimate`; after a reload the open interval of unknown length is dropped and shown as interrupted, and Resume opens a fresh interval (`fitness.events/recover-active`) | `app_test` (1 s + 1 s counted, downtime not counted) | check with real persistence on each host |
| **DOM-005** HR import preview and explicit time | `:file/pick`, `:file/read`, `cloview.fx.codec` (CSV/JSON), `fitness.hr-import` | **Ready**: preview counts (valid, invalid, duplicates, rows), the zone rules, a minutes offset field, cancel/confirm | `app_test/heart-rate-import-preview-and-commit` (fixture, JSON, offset 480, bare time rejected, cancel unchanged) | Flutter file chooser (§4.3) |
| **DOM-006** HR coverage, honest energy | core `record-calories` | **Ready**: the log shows kcal or "kcal unknown (reason)" and HR coverage; charts distinguish unknown from 0 | DOM-001 oracles; `app_test` (energy statuses) | profile editor for the HR-adjustment switch |
| **DOM-007** Overlapping and overnight activity | core | oracle level | DOM-001 | — |
| **DOM-008** Inclusive periods, mode switching, set weights | core `summary`, `comparison`, `adjacent-range` | **Ready**: actual/planned and sets/weighted-minutes selects; daily kcal always actual; zero prior gives "New" or "-" | `app_test` (no percent for a zero prior); DOM-001 | a free date-range editor (presets exist) |
| **DOM-009** Calendar dates, progress, navigation | `components/month-grid` | **Ready**: 42 Monday-first cells; adjacent-month selection; navigation limited to 1900–2100; day details; *Day analysis* | `components_test`, `app_test/calendar-navigation`, JavaFX and WPF stand-in smokes | leap-February screenshot evidence |
| **DOM-010** Round-trip, idempotent migration | EDN storage; core `migrate-document` | **Partial**: storage round-trips EDN; migration runs on load | DOM-001 migration oracles; `fx_test` round-trip | a JSON export, which UI_SEMANTICS mentions ("Export triggers a native effect"), is not built |

### Effects and architecture

| Case | cloview support | Scaffold today | Evidence | Remaining |
|---|---|---|---|---|
| **EFFECT-001** Durable save, ordered writes, retry | `:storage/put` with `:on-success` / `:on-failure`, serial writes, atomic replace | **Ready**: the status row shows Saving…, Saved and Save failed; a failure keeps the in-memory edits; Save retries | `fx_test` (callbacks, atomic replace) | fault injection on each host (delayed storage) |
| **EFFECT-002** Corrupt store and backup | desktop storage keeps `<file>.bak`; an unreadable primary is preserved as `.corrupt-<ms>` and the app continues from the backup; both unreadable means the store refuses writes | **Ready** on desktop: `:doc/load-failed` reports and saves nothing | `fx_test/storage-backup-and-corruption` (JVM, CLR 1.12.6, CLR master) | Dart keeps no backup (shared_preferences); a backup scheme there is to be designed |
| **EFFECT-003** Stale async commits, active-session guard | handlers commit against the current db | **Ready** for HR import: the merge applies to the current document, and no commit happens while a training item is active (the preview is kept) | `app_test` | the same guard for LAN sync (§4.2) |
| **EFFECT-004** Cancellation, disposed owner safety | effects report to the app-scoped store, never to a view; natives' `:dispose` never dispatches | **Partial**: a late file read of an earlier pick is ignored; results land in app state, so leaving the view loses nothing | `app_test` | per-view scoped operations (none exist yet) |
| **EFFECT-005** Repeat render, no duplicate callbacks | stable event trampolines (WPF), cljfx event maps, Flutter adapters rebuilt from data | **Ready** | `reconcile_test` (handler changes cost no host ops); an identical re-render costs zero operations (`app_test`); no-echo smoke checks | the 20× mount/remove loop as an explicit test |
| **EFFECT-006** UI thread vs background effects | effect executor marshals off-thread dispatches; file I/O on background threads (JVM/CLR) | **Ready** | `store_test/executor-marshals-off-thread-dispatches`; `fx_test` file effects | — |
| **ARCH-001** Shared provenance, extension boundaries | one shared app (`apps/fitness/shared`); host code only in entry points and natives | **Ready**: the host-specific files are `fitness.{javafx,wpf,flutter}.{main,native}` and the smokes | `apps/fitness/README.md` lists every host override and the provenance hashes | — |
| **ARCH-002** Clean downstream consumption | `:local/root` deps on a sibling cloview-core checkout; cloview-core's CloViewHost template | **Partial**: runs from checkouts of both repositories, side by side, with documented commands | — | publishing (git deps or Clojars, NuGet-free host template) |
| **ARCH-003** Capabilities, explicit unsupported state | `fitness.native/available-kinds`; placeholders for missing renderers; `:file/pick` reports `:unsupported` on Flutter | **Ready** | `app_test` (native placeholder, MP4 placeholder, unsupported chooser) | a capability report per host (language host / OS / optional integrations) |
| **ARCH-004** Cache validity, reproducible observations | `cloview.core.memo/memo-last` on db slices; `fitness.derive/calls` counters | **Ready** (correctness half) | `app_test` and both GUI smokes: selection, style and tab changes recompute nothing | timing observations with hardware and scale (the case requires them when timing is claimed; none is claimed) |

### Native views

| Case | cloview support | Scaffold today | Evidence | Remaining |
|---|---|---|---|---|
| **EXT-001** GIF import, stable animated viewport | `:file/pick` → `:file/app-dir` → `:file/copy` (managed copy); `:ui/image` animates GIFs on every host (WPF via `cloview.wpf.gif`) | **Ready** on JVM and WPF: fixed 320×180 viewport, managed copy, cancel keeps the old media | GIF decoding and frame composition run on the WPF stand-ins (real GIF parsing of the fixture); `app_test` media routing | Flutter chooser (§4.3). A missing-file error state inside the viewport needs an `:on-error` event on `:ui/image`; today a missing file leaves the viewport empty on every host. |
| **EXT-002** MP4 playback and disposal | `:ui/native :fitness/media-player` contract ([`NATIVE_CONTRACTS.md`](NATIVE_CONTRACTS.md) §8) | **Planned**: shared code routes video to the contract; no renderer yet, so an explicit placeholder | `app_test` (placeholder, props) | MediaPlayer (JavaFX), MediaElement (WPF), video_player_win or media_kit (Flutter) |
| **EXT-003** 2D front/back muscle selection | `:fitness/body-canvas` + shared geometry and hit test (`fitness.body`) + select + Clear | **Ready** on JVM and WPF (Flutter code written): all 20 ids selectable by tap or by select | JavaFX smoke and WPF stand-in smoke (tap → `pectoralis-major`, background → nothing, no recomputation) | screenshots |
| **EXT-004** Real 3D body | contract and host plan (`NATIVE_CONTRACTS.md` §3.5–3.6) | **Planned**: the 3D choice shows the 2D map and a note | — | mesh data, SubScene / Viewport3D / software painter |
| **EXT-005** Radar, calorie chart, comparison | `:fitness/radar-canvas`, `:fitness/calorie-canvas`, canonical comparison, value tables | **Ready** on JVM and WPF (Flutter code written) | smokes mount the canvases; `app_test` (unknown vs zero; no full-scale empty data) | — |
| **EXT-006** Native split-view, raw child | `:ui/split-view` + `:ui/native` inside shared layout | **Partial**: the catalog uses a split view; the natives sit in shared layout | divider movement is presentation-only (`:on-resize` ratio) | the probe layout (a native child beside a shared list in one split) as a test |

### Optional integrations

| Case | Status |
|---|---|
| **INT-001..003** LAN pairing, sync conflicts, security | **Planned**; see §4.2. No LAN parity is claimed. |
| **INT-004..005** Android Health Connect | **Planned**: needs a ClojureDart plugin binding on Android. Not applicable to the desktop hosts. |

## 3. Logical events

The mapping from `UI_SEMANTICS` event names to the app's event vectors is in
[`apps/fitness/README.md`](../apps/fitness/README.md#logical-events). Traces
recorded through the store's `:on-event` hook (as the smokes do) read in benchmark
vocabulary.

## 4. Effects alignment

### 4.1 Heart-rate CSV/JSON import (`cloview.fx.file`, `cloview.fx.codec`, `fitness.hr-import`)

```
[Choose CSV/JSON file] → :file/pick {:extensions ["csv" "json"] :on-picked :on-cancel :on-failure}
  → :file/read {:path p}              background thread on JVM/CLR, dart:io on Dart
  → (hr/preview text {:offset-min m}) pure: codec/csv-maps or codec/read-json → rows → instants
  → preview shown: valid / invalid / duplicates / rows, zone interpretation, rejected rows
  → [Import] → hr/commit into the current document (EFFECT-003 guards) → :storage/put
  → [Cancel] → stored samples unchanged
```

- **CSV:** RFC 4180 with a BOM, CRLF and quoted fields, case-insensitive headers. **JSON:**
  arrays of flat objects. Column aliases cover common exports, including Samsung-Health-like
  `start_time` / `heart_rate`.
- **Instants:**
  - an explicit `Z` or offset is used as is;
  - zone-less date-times need the explicit offset field (-840..840 minutes);
  - epoch values are milliseconds, or seconds when |n| < 10¹¹;
  - a bare time or bare date is rejected.
- **BPM** must be 25..250, and the last valid duplicate wins (domain rules).

### 4.2 LAN sync (`cloview.fx.lan-sync`)

The benchmark makes LAN sync an **optional integration profile** (INT-001..003).
`FUNCTIONAL_BASELINE.md` warns that "merely providing an HTTP effect does not recreate
this application protocol".

**What cloview has today:**

- protocol v1 envelopes, an EDN codec, a 6-digit pairing code;
- a deterministic last-writer-wins merge with tombstones (`merge-records`,
  `merge-collections`, `changes-since`), using an ordinal device tie-break so every peer
  picks the same winner, even on ClojureCLR;
- final effect shapes (`:lan/advertise` `discover` `serve` `reply` `request` `stop`),
  whose runners report `:not-implemented` (ARCH-003).

**Entity mapping for the fitness document.** Each synchronised entity needs a stable
`:id`, `:updated-at` (epoch ms) and `:device`, plus `:deleted?` tombstones.

| Collection | Entity id | Notes |
|---|---|---|
| exercises | exercise `:id` | media travel separately (below); `:media-path` is local to each device |
| plans | plan `:id` | items keep their own ids; conflicts are per plan in v1 |
| records | record `:id` | completed records are effectively immutable; recovered/backfilled flags travel |
| heart-rate batches | batch `:id` | samples are deduplicated by timestamp on merge (domain `validate-hr-samples`) |
| profile | singleton | merged as one entity |
| media | content hash (SHA-256) | transferred by hash, size-bounded, verified before it is published as playable |

**Gap to the reference protocol (Stage 3):**

| Required (FUNCTIONAL_BASELINE / INT cases) | cloview today | Plan |
|---|---|---|
| Invitation-based pairing with authenticated encryption; replay, expiry, revocation (INT-003) | pairing code only, cleartext EDN | invitation with a key exchange; an AEAD per message; nonces and expiry |
| Per-entity versions with explicit conflict resolution (INT-002) | last-writer-wins | version vectors; concurrent variants kept for the user to resolve |
| Bounded, hash-checked media; no traversal (INT-003) | — | a content-addressed transfer with size limits; staging before publish |
| Stale-snapshot and active-session guard before commit (EFFECT-003) | the guard exists for HR import | the same check before applying a merge |
| Foreground only, cancelled on page disposal; private networks only | — | effects scoped to the sync page; address checks |
| Real cross-device transport evidence (INT-001) | — | two stores on separate endpoints; loopback alone does not count |

### 4.3 File chooser on Flutter

`:file/pick` is registered by each UI host: JavaFX `FileChooser`, WPF `OpenFileDialog`.
On Flutter it reports `{:error :unsupported}` until the `file_picker` plugin is wired.
The fitness app shows "Choosing files is not available on this host yet" rather than
pretending the user cancelled. This blocks EXT-001 and DOM-005 on the Dart host until it
lands. Planned: `flutter pub add file_picker`, then a `:file/pick` runner in
`cloview.flutter.effects`.
