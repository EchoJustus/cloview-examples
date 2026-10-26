# Functional baseline: Fitness application 0.3.0

## Purpose and evidence boundary

This is an English description of the existing ClojureDart/Flutter fitness application. It is an application-sized benchmark for a future shared Clojure UI and business-logic framework. The framework must preserve user-visible meaning, event behavior, state transitions, and calculation results. It need not reproduce Flutter styling, exact pixels, fonts, animation curves, or window chrome.

The source implementation runs on Windows and Android. This package does not establish that its source already compiles on ClojureCLR or that JavaFX renders it. The framework recipient must implement and test those adapters. Source-relative references below identify the baseline repository; they are provenance, not a claim that every referenced file is distributed in this package.

The source interface and some source domain tags are Chinese. English fixture derivatives normalize display labels and equipment tags. Preserve the derivative's documented mapping rather than treating translated strings as byte-identical source data. All benchmark health and training samples are synthetic. The source's 25 demonstration exercise assets are not a required benchmark count; synthetic exercises must include multiple intensities and equipment combinations to exercise behavior that the original all-medium sample catalog does not.

## Application structure

| Area | Baseline behavior | Portable requirement |
|---|---|---|
| Today | Show dated plans and current training state | A reachable workflow for selecting and completing today's items |
| Exercise library | Search, filter, edit metadata, import GIF/MP4 | Equivalent library and editor semantics; native media/file effects may be adapters |
| Training plans | Date, name, ordered items, repeated exercises | Repeated exercise IDs allowed; each item has a unique identity |
| Training records | Measured or backfilled completion, HR import, export | Correct completed records and explicit duration provenance |
| Calendar | Monthly grid and day details | The same dates, counts, progress, and selected-day actions |
| Analytics | Period comparison, actual/planned, minutes/sets, body, charts | Shared data values and selectable representation |
| Device sync | Manual foreground pairing and transfer | Optional integration profile; generic async and commit semantics remain mandatory |
| Phone heart rate | Android Health Connect permission/query/import | Android-only capability; unavailable platforms must describe availability honestly |
| Profile and data | Weight, HR settings, exports, save status | Explicit defaults, validation, persistence, and useful failures |

The current UI uses a desktop sidebar and a narrow-screen drawer. Other native navigation systems are acceptable if all applicable pages and actions remain discoverable and reachable.

## Exercises and catalog

An exercise has identity, display name, primary muscles, secondary muscles, notes, MET, reference repetitions per minute, optional media, equipment tags, and a low/medium/high intensity label. An editor requires at least one primary muscle and one equipment choice; a bodyweight exercise explicitly chooses "No equipment". A muscle cannot be both primary and secondary. Primary wins when normalizing legacy duplicate muscle assignments.

Equipment is multiselect. "No equipment" is mutually exclusive with actual equipment. Missing equipment means "Not set", not "No equipment". Filter choices are derived from equipment currently assigned in the library. The editor also offers common equipment and custom input. Custom tags are trimmed and deduplicated. Removing the last use of a tag also removes obsolete filter selection.

Each filter dimension uses OR across its selected values. Muscle, intensity, equipment, and search dimensions combine with AND. An empty dimension imposes no restriction. Muscles match either primary or secondary membership. Search is a trimmed, case-insensitive substring of supported names, equipment, and muscle labels. Clear-all removes search text as well as filter selections. Filtering must not mutate the catalog or historical snapshots.

Intensity classification and MET are separate fields. Explicit low/medium/high edits do not alter calories. Legacy missing/invalid intensity initializes from MET: below 3 is low, below 6 is medium, otherwise high; invalid MET initializes medium. Catalog migration is idempotent and leaves plans, records, active timing, and historical exercise snapshots unchanged.

## Planning and training transitions

A plan contains a local calendar date, a name, and ordered items. Each item records its own ID, exercise ID, planned duration in seconds, repetitions, target HR, and optional scheduled local time. Scheduled time is planning metadata; the source does not automatically start a session when the time arrives. Multiple items may refer to the same exercise.

Copying an item gives the copy a fresh ID, places it after the source, and preserves its settings. Reordering changes sequence, not identity. Starting or backfilling an item captures the exercise snapshot so later catalog changes cannot silently rewrite the historical muscles, MET, or name.

Only one active item is allowed at a time. Start creates an open time interval; Pause closes it; Resume opens another; Complete closes the final interval and records completion. Completed effective time is the union of valid closed intervals clipped to the session boundaries. Pauses contribute no time. Completion is idempotent: a repeated completion cannot create a second physical group or charge a second calorie total.

Backfill uses the click time as completion and subtracts the planned duration. Its duration source must identify it as estimated from the plan. It is not measured time. After an interrupted process, an unclosed interval with unknown length must not be counted as completed work. The saved closed intervals can support recovery and subsequent continuation.

Repetitions currently come from the plan item's recorded settings; they are not counted from video, a camera, or a wearable. Target HR is never treated as measured HR.

## Dates, groups, and aggregation

Absolute instants use integer epoch milliseconds. Time intervals are half-open `[start,end)`. Calendar dates are explicit local `YYYY-MM-DD` values. The caller supplies date attribution; the domain core does not guess the user's time zone. A host adapter must split completed intervals at local midnight, retain a shared physical group identity, and proportion repetitions by active time.

Date filters include both endpoints. Actual aggregates exclude incomplete and zero-active-duration records. For sets, a physical group is counted once on its first active date, identified by `group-id` or, when absent, record ID. Overnight split rows must not become additional groups. Planned set identity is the plan/item pair, so intentional repeated exercises remain distinct.

Muscle loads are primary 1.0 and secondary 0.5 per effective minute or per set, depending on the selected metric. Duplicate assignments contribute once and primary wins. These are product-defined comparison weights, not measurements of muscle growth, fatigue, electrical activity, or recovery.

The previous comparison period immediately precedes the inclusive selected period and has the same number of calendar days. It is not a fixed number of elapsed milliseconds across daylight-saving transitions. A zero previous value yields an absent percentage rather than infinity or a fabricated percentage. The calendar has 42 Monday-first cells including adjacent-month days. The application UI date range is 1900 through 2100; unavailable boundary navigation is disabled.

Daily calories always represent completed actual activity, even while the body/radar view is showing planned activity. Changing view style or selecting a muscle must not change stored records or calculated load.

## Heart-rate import and energy

The importer accepts a flat CSV or one-sample-per-object JSON input. It reports valid, invalid, and duplicate counts and a preview before committing. It supports explicit ISO offsets, UTC, and epoch instants. Timezone-free date/time values require an explicit fixed UTC offset in minutes, from -840 through 840; the importer does not infer an offset from an unrelated metadata column. Bare time-of-day without a date is insufficient.

Valid HR is a finite single BPM value from 25 through 250. At a duplicate timestamp the last valid input wins. Explicit sample ends must be later than starts and are clipped by the next sample. Implicit samples extend to the next sample only when the gap is at most 30 seconds; otherwise they cover 5 seconds. Matching intersects these intervals with effective session intervals. Average HR is duration-weighted; coverage is covered effective time divided by all effective time. Missing coverage is not invented.

The baseline energy rule is:

```text
baseline_kcal = MET * 3.5 * weight_kg / 200 * active_minutes
```

Invalid/missing MET falls back to 3.5 with an explicit estimate flag. Missing/invalid weight produces unknown calories with a reason, not an invented total. Incomplete or zero-duration records contribute zero. This estimates gross energy over the activity interval; it does not subtract resting energy.

When HR adjustment is explicitly enabled, resting/max HR are valid, and matching HR exists:

```text
reserve = (average_actual_hr - resting_hr) / (max_hr - resting_hr)
hr_factor = clamp(0.75, 1.25, 1 + 0.5 * (reserve - 0.6))
overall_factor = 1 + coverage * (hr_factor - 1)
kcal = baseline_kcal * overall_factor
```

With no matching HR, a valid repetition frequency can use `clamp(0.75, 1.25, (reps / active_minutes) / reference_rpm)`. Any matched HR disables that frequency adjustment, even if the HR-adjustment switch is off. The rules are bounded application heuristics, not validated personal metabolic or medical measurements.

Daily aggregation prevents overlapping records from charging the same clock interval twice. A skipped overlap proportionally reduces repetitions as well as effective duration. Source snapshots remain intact.

## Persistence and effects

State is serialized locally, with ordered writes, temporary-file publication, and a previous valid backup. A failed save keeps edits in memory, exposes a persistent unsaved state, and offers retry. An ordinary action message must not replace a save failure with a false success. A damaged primary and backup stop automatic overwrite of the originals.

The framework need not adopt the original file layout, but must provide the same observable transaction, recovery, failure, and cancellation semantics through host effects. File selection cancellation leaves state unchanged; selecting media imports a managed copy, leaving the original intact.

An asynchronous merge must recheck its starting snapshot before committing and reject stale results or an active training session. A successful import/sync message follows durable save. Slashes in metadata keys survive JSON round-trip. Health batches are deduplicated and their sample count/provenance are retained separately from the sample collection.

## Native extensions and optional integrations

The existing body view is a rotatable procedural 3D schematic plus a selectable 2D front/back view with 20 muscle IDs. A host may implement its own renderer with the same IDs, loads, selection event, legend, and view-switch meaning. The baseline also has a six-region radar, daily calorie chart, GIF animation, and MP4 playback in a stable aspect-preserving viewport. Native renderers and libraries are allowed; business calculations must remain shared.

The future framework's split-view/native-child probe is an additional requirement, not a feature claimed by this fitness baseline. A JavaFX split pane, Flutter composition, or Windows-native splitter may implement it. The probe must demonstrate that the extension participates in shared event/state/lifecycle handling.

Android Health Connect is read-only and user initiated. Its query includes a source and explicit time range; confirmation imports a batch only after successful validation and local save. The source uses a separate native plugin. It is not a requirement for JVM or Windows CLR to emulate Health Connect.

LAN sync is manually initiated, foreground only, and private-network constrained. The source includes invitation-based pairing, authenticated encryption, per-entity version/conflict merge, tombstones, media validation, and cancellation on page disposal. Merely providing an HTTP effect does not recreate this application protocol. If claiming LAN integration parity, test the actual protocol with two independent stores and actual cross-device transport.

## Previously recorded verification, not new host certification

Source verification for version 0.3.0 was recorded on 2026-10-16 through 2026-10-18: 37 core tests/213 assertions; 11 state tests; 6 body tests, 4 calendar/comparison tests and 1 visual render; original app flow plus 4 catalog tests and 1 v3 app flow; 14 Health Connect page tests; 12 storage/import tests; 18 native plugin tests; 17 real loopback-socket sync tests and 1 actual sync-page bridge test. The Windows release rendered its first native frame; the final Android APK installed and launched on an Android 14 emulator.

The emulator completed native HR permission authorization and a real empty query. It did not verify a nonempty real Health Connect query, real Samsung Health history, or a physical phone. The final Android-to-Windows pairing attempt did not complete because automated text entry changed the invitation. There were zero paired devices and zero sync commits. Loopback tests do not establish real cross-device success. JVM and CLR results are entirely pending.

## Provenance

- `README.md`: user workflows, filters, samples, data persistence, and limitations.
- `core/API.md`: domain, catalog, analytics, interval, identity, and calendar contracts.
- `docs/architecture.md`: layer boundaries, caching, snapshots, state/effect behavior.
- `docs/energy-and-import.md`: import semantics and energy formulas.
- `docs/lan-sync.md` and `docs/android.md`: native integration scope and boundaries.
- `docs/verification.md`: historical checks and known unverified boundaries.
- `gui/test/app_flow_test.dart`, `catalog_flow_test.dart`, `app_v3_flow_test.dart`, `analytics_widgets_test.dart`, `health_connect_page_test.dart`, `sync_page_test.dart`, `lan_sync_test.dart`: representative executable source evidence.
