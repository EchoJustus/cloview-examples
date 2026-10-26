# Acceptance procedure

## What is being accepted

Accept the future framework by implementing the same small fitness application on each declared host using the same application declaration, domain rules, fixture inputs, and logical event vocabulary. Native rendering is allowed. Three independently rewritten applications with similar screenshots do not demonstrate the intended shared-code framework.

This package is a benchmark, not a preselected framework design. It does not settle EDN versus CLJC, runtime interpretation versus compilation, CLR toolkit choice, or distribution format. Record those decisions and test their consequences.

## Profiles and host scope

1. **CORE**: mandatory shared UI semantics, domain rules, state/effect boundaries, persistence, and framework lifecycle. Required targets are JVM/JavaFX through cljfx, Flutter through ClojureDart, and Windows CLR through the chosen native GUI toolkit. CORE is not passed until every mandatory case passes on all three. A missing CLR adapter is BLOCKED or UNSUPPORTED, not an overall pass.
2. **FITNESS_NATIVE**: media playback, body rendering, and charts from the application plus a clearly prospective split-view/native-child probe. This profile demonstrates extension architecture and is required to claim full fitness visual/interaction parity. CORE alone is a smaller milestone, not the full application result.
3. **OPTIONAL_INTEGRATIONS**: real LAN pairing/synchronization and Android Health Connect. Select them explicitly. An unselected optional case remains NOT_RUN/UNSUPPORTED with an explanation; it never becomes a pass. Android Health Connect is evaluated only on an Android Flutter build, not as a JVM/CLR desktop obligation.

CLR macOS/Linux support is optional. Publish its actual status separately if attempted. Operating-system availability must not be conflated with language-host implementation status. A supported native fallback may differ visually, but must retain the selected profile's functional contract.

## Evidence vocabulary

`cases.json` distinguishes `BASELINE_DERIVED` from `PROPOSED_FRAMEWORK_PROBE`. The former derives its semantics from the existing app; it does not imply that the new adapter passes. The latter adds a framework-specific acceptance obligation not previously certified by the app.

Source verification counts in the functional baseline are historical, recorded for app 0.3.0. Do not copy them into the framework's result report. The included fixture oracles establish expected values; running a package-integrity checker is not running the framework or its GUI.

Copy `result-template.json` into a separate results directory as one aggregate report. Fill the candidate revisions, shared source manifest, host override inventory, host OS/runtime/toolkit versions, build and launch commands, fixture digest, and selected profiles. Update it incrementally as each host is tested; retain NOT_RUN entries for all other applicable host/case pairs. Then record each case with one of:

- `PASS`: the stated expectation was observed, with evidence and no unexplained mismatches.
- `FAIL`: the expectation was executed and violated.
- `NOT_RUN`: no relevant execution evidence yet.
- `BLOCKED`: a known environment or implementation issue prevented the check.
- `UNSUPPORTED`: the target deliberately lacks the capability, with reason and fallback where applicable.

For required profile cases, only PASS satisfies the gate. Do not remove failing cases, rename their IDs, reduce selected profiles after failure, or count disabled controls as implementation of required behavior. Record a justified proposal to change a requirement separately for owner review.

## Run sequence

1. Verify package hashes and read the claim boundaries. Use only synthetic fixtures in an isolated store.
2. Record architecture decisions, dependency provenance, licenses, and the native adapter mapping. Prove that the shared view/application sources are actually consumed by each host build.
3. Run the included domain fixture vectors through the candidate's shared business layer on every required host. Compare structure, IDs, dates, null values and numeric results using the documented tolerance. Never round before comparison merely to hide divergence.
4. Replay each UI workflow with the same fixture and normalized event trace. Capture before/after state and actual host controls. Verify clicks and keyboard activation, not only screenshots.
5. Inject delayed, failed, cancelled, and out-of-order effects. Confirm the application remains correct and failure is visible. Inspect listener/resource lifetime across repeated mounting and removal.
6. Run the native extension profile; retain the semantic contract even when native geometry, dialogs, and styling differ.
7. Run any selected optional integration with actual endpoints/devices, separating mock, loopback, emulator and physical-device evidence.
8. Record cold start, repeat interactions, responsive layout, known defects, and unsupported capabilities. Publish the aggregate results file with separate per-host evidence folders and business-output files as needed.
9. Verify that a clean consumer project can consume the chosen distribution without copying the framework's internal sources by hand. The packaging decision itself is open; its documented installation must work.

## Evidence required for a pass

Use relative evidence paths and capture enough context to reproduce the run. A screenshot proves appearance, not event binding or persistence. A state diff proves data, not that a real button produced it. A headless test proves only its scope. Combine the following as required per case:

- A screenshot or short recording of the actual host UI.
- A normalized logical-event trace with stable control/item IDs and counts.
- Serialized before/after state and a fresh-process reload where relevant.
- Calculation input/output vectors and the candidate's assertion result.
- Failure/cancellation logs, capability reports, and resource counts where relevant.
- A clean build/run log with versions and the source/revision/fixture digests.

Do not include personal health data, pairing secrets, machine account names, or absolute developer paths in shared evidence.

## Machine-readable catalog and result contract

`cases.json` uses `FITNESS_ACCEPTANCE_CASES_V1`. It contains 40 cases: 29 CORE, 6 FITNESS_NATIVE and 5 OPTIONAL_INTEGRATIONS. Every case includes its profile, origin, applicable hosts, initial setup, ordered actions, observable expectations, evidence requirements, source-relative provenance and linked business-oracle IDs where available. A `source_refs` entry identifies the original source repository; it does not assert that the source file is included in this package. An initial setup may require evaluator-supplied media or controlled fault injection in addition to packaged fixtures.

Host IDs are `jvm`, `dart`, `clr` and `dart-android`. The first three are required for CORE and FITNESS_NATIVE. The Android entry covers additional native integration tests; it is not a fourth mandatory desktop framework host. Each case states exactly where it applies. There are 119 applicable host/case pairs, all initialized as NOT_RUN.

`result-template.json` uses `FITNESS_ACCEPTANCE_RESULTS_V1`. Keep its complete `hosts` and `results` inventories. Each result contains `host`, `case_id`, `status`, `evidence`, `observed`, `reason` and `duration_ms`. Evidence entries are relative file paths from the report directory. A PASS must have actual evidence, a nonempty observation, candidate revisions and completed host build/runtime metadata. A blocked/unsupported result must state why. `duration_ms` records test execution duration when measured; it is not a performance pass threshold.

`candidate.shared_source_manifest` contains entries shaped as `{"path":"shared/example.cljc","sha256":"<digest>"}`. `candidate.host_overrides` contains `{"host":"jvm","path":"adapters/example.clj","reason":"Native control and thread bridge"}` entries as needed. Use real relative paths and content hashes in a completed report. The untouched template intentionally has empty metadata and no PASS claims.

`selected_profiles` defaults to `["CORE"]`, a partial milestone. For full fitness visual and interaction parity, include both CORE and FITNESS_NATIVE. OPTIONAL_INTEGRATIONS is a separate bundle; selecting it requires its applicable LAN and Android cases. To report only some exploratory optional work, record the observations without selecting that bundle and name the narrower tested scope in prose. Do not claim the whole bundle passed from a subset.

Run these commands from the extracted package root, passing a path to the separate report:

```text
python tools/check_acceptance_report.py acceptance/result-template.json
python tools/check_acceptance_report.py results/candidate.json --require-pass
python tools/check_acceptance_report.py results/candidate.json --full
```

The first command checks only structure; it must not call the untouched template an accepted framework. `--require-pass` requires PASS for every applicable case in selected profiles. `--full` additionally requires CORE and FITNESS_NATIVE. The tool validates reporting consistency, not the truth of an attached screenshot or log; independent review of evidence remains necessary.

DOM-001 requires the separate observed business-output envelope described in `docs/DATA_AND_ORACLES.md`. Its `host` values use `jvm`, `dart` or `clr`. Run the entire frozen corpus on each required host and use `tools/check_fixtures.py --observed` to compare real outputs. Reading the expected JSON or validating its schema alone cannot pass DOM-001.

`CAPABILITY_MATRIX.csv` summarizes applicability. `REQUIRED_IF_SELECTED` means that the case is mandatory when that profile is selected. `NOT_APPLICABLE` means the case does not create a result slot on that host; it is not an execution status and must not be entered as a PASS.

## Performance method

No universal frame-time or speedup claim is certified by this baseline. Specify hardware, OS, power mode, build mode, viewport, record/sample counts, and run count before comparing hosts. Measure cold startup, time from filter/range selection to displayed result, and UI-frame/event latency during body rotation and long-list scrolling. Report median and p95 with the raw observations.

Instrument shared aggregate calls and HR-index construction. Repeated muscle selection/style changes with identical document and analysis options should not rescan the entire history. Changing samples or relevant options must invalidate the correct caches. The cache check is behavioral; it does not prescribe one cache implementation.

Set any release performance budget explicitly before grading. Until an agreed budget exists, report measured results and observed stalls rather than declaring a numeric performance pass. The lifecycle/cache correctness case still has a testable expectation independent of a speed target.

## Known boundaries inherited from the source

The existing app's real Android-to-Windows pairing attempt never completed: zero devices paired, zero commits. Its native Health Connect query returned an empty data set after permission was granted. Real Samsung Health history, nonempty native query, physical phone interaction, and the user's network were not certified. Reusing that source does not waive integration testing in the new framework.

Calorie formulas and muscle weights are deliberately approximate application rules. They are useful portability checks, not a medical validation program. A framework must preserve the explicit unknown/estimated states rather than presenting stronger health claims.

## Reporting completion

Use names such as "CORE passed on JVM, Flutter and Windows CLR", "FITNESS_NATIVE pending on CLR", or "Android Health Connect unsupported on this desktop host". Do not write simply "cross-platform passed" when a required host or profile is missing. Include all failures, blockers, deviations, and prospective probes still awaiting execution.
