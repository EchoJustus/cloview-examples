# Task scope

## User objective

Use the current fitness application as a practical acceptance baseline for a future library/framework that shares UI declarations, events, state and business logic across Clojure hosts. The shared representation may be `.cljc`, `.edn`, or a justified combination. That design decision remains open.

The baseline must make failures observable: one declared button must become one usable button; invoking it must deliver the intended logical event exactly once and produce the specified state transition. A native control may look different while satisfying the same contract.

## Target hosts

| Target | Required baseline environment | Design flexibility |
| --- | --- | --- |
| JVM | JavaFX via cljfx | Native controls, layout and CSS mapping |
| Dart | Flutter via ClojureDart | Widget composition, host effects and custom painters |
| CLR | Windows GUI via ClojureCLR | WPF, WinForms or another justified native adapter |

CLR GUI support on macOS/Linux is not a mandatory acceptance gate. Android is an additional Flutter deployment and integration profile, not a requirement that desktop JVM/CLR implement Android Health Connect.

## What this task supplies

1. An English functional benchmark grounded in the existing application.
2. Observable control/event/state contracts, deterministic data and numeric oracles.
3. Visual references for body visualization, calendar and comparisons.
4. Capability tiers and an evidence format for future host implementations.
5. An English handoff prompt for applying the benchmark after or during framework development.

## What this task does not decide

- Framework/repository name, view DSL, runtime interpretation versus compilation, macro design or reconciler architecture.
- Whether delivery uses source dependencies, a multi-module repository, or separate Maven/Dart/NuGet artifacts.
- The correctness of compiler/runtime claims made in the user's pasted research prompt. Those are research questions or hypotheses, not verified facts in this package.
- Whether a particular CLR GUI toolkit supports a platform. The implementer must establish that through current primary documentation and executable probes.

The pasted research material is context for the other AI's assignment. It does not instruct this package-building task to conduct that research, initialize the future framework, or run its proposed orchestration workflow.

## Selection and transformations

The controlling task selected the application's README, architecture/domain/import/Android/sync/verification documentation, three pure-core source files and their tests, selected UI tests, and the body/calendar/comparison/shell widget sources. Source-relative paths and transformations are recorded in `SOURCE_MAP_PUBLIC.csv` and the component/core guides.

Only derivatives needed for this benchmark are exported. Chinese prose and display labels are translated to English. Where a Chinese equipment string is also a stored value, the reference derivative explicitly normalizes that value and its fixture inputs together; it is not an untouched production dataset. Stable muscle identifiers, formulas and behavioral rules remain unchanged.

The original personal portrait and 25 bilingual exercise GIFs are outside the export selection. Neutral generated animation tests playback semantics without reproducing that likeness. This reduces media-specific coupling while preserving the fitness workflow and data model.
