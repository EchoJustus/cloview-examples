# cloview-examples

Example applications and the community showcase for
**[cloview](https://github.com/echojustus/cloview-core)**: one declarative Clojure UI,
written once in `.cljc` and rendered natively by JavaFX (cljfx), WPF (ClojureCLR) and
Flutter (ClojureDart).

**The code in this repository is MIT-licensed: copy what you need into your own
project, commercial or not.** The framework itself, [cloview-core], has its own licence
(see "License" below).

> **Status: before the first public release**, like cloview-core. The apps track
> cloview-core's `main` branch.

## Gallery

| App | What it shows | Hosts | Evidence |
|---|---|---|---|
| **[Fitness](apps/fitness)** (flagship) | A workout tracker with five tabs (Catalog, Plan Builder, Execution Log, Anatomy & Analytics, Calendar) built as **one shared `.cljc` app** with thin host entry points; about 90% of its code is shared. Keyed lists, multi-select filters, plan editing, heart-rate import, a month calendar, and `:ui/native` renderers for the anatomy heatmap, the radar and the calorie chart. | JavaFX, WPF, Flutter | A frozen acceptance benchmark ([`benchmarks/fitness-baseline`](benchmarks/fitness-baseline)): its 37 business oracles pass on all three hosts (Windows run of 2026-10-25). Case-by-case status: [capability map](docs/FITNESS_CAPABILITY_MAP.md). |

More apps will join this table ("Adding an example app" below). Apps built and hosted
elsewhere are listed in the **[community showcase](showcase/README.md)**.

## Getting started

Clone both repositories into the same folder. The apps use cloview-core's modules
through the sibling path `../cloview-core`:

```bash
git clone https://github.com/echojustus/cloview-core
git clone https://github.com/echojustus/cloview-examples
cd cloview-examples
clj -M:fitness                 # fitness unit tests + the 37 business oracles (JVM)
clj -M:run-fitness-javafx      # the fitness app on JavaFX
```

With [babashka](https://babashka.org), `bb tasks` lists every task:

| Host | Tests + business oracles | Off-screen smoke | Interactive |
|---|---|---|---|
| JVM | `bb fitness:test` | `bb smoke:fitness:javafx` | `bb run:fitness:javafx` |
| CLR (Windows) | `bb fitness:test:clr` | `bb smoke:fitness:wpf` | `bb run:fitness:wpf` |
| Flutter (≥ 3.35) | `bb fitness:flutter` | (widget test inside) | `bb run:fitness:flutter` |

- `bb fitness:test` also runs the benchmark's own Python checkers (package integrity,
  fixtures, the `--observed` comparison), and `clj -M:static-check` checks every app
  under `apps/` with cloview-core's static checker.
- On Windows, `verify\run-windows-checks.ps1` runs everything on all three hosts and
  packages the results ([VERIFY_ON_WINDOWS.md](VERIFY_ON_WINDOWS.md)).
- Toolchains: those of cloview-core
  ([requirements](https://github.com/echojustus/cloview-core#requirements)), plus
  Python 3.7+ for the benchmark checkers.

## Repository layout

```
cloview-examples/
├── apps/
│   └── fitness/                   the flagship app: shared .cljc app + javafx / wpf / flutter entry points
├── benchmarks/
│   └── fitness-baseline/          the fitness app's frozen acceptance package (docs, oracles, fixtures, tools)
├── docs/
│   ├── FITNESS_CAPABILITY_MAP.md  every benchmark capability: cloview support, status, evidence
│   └── NATIVE_CONTRACTS.md        the fitness app's :ui/native views
├── showcase/                      the community gallery: apps built with cloview elsewhere
├── verify/
│   ├── run-windows-checks.ps1     Windows harness: steps 01–05 and a results zip
│   └── bb/cloview/examples/       babashka tasks, built on cloview-core's verify/bb helpers
├── deps.edn  bb.edn               JVM aliases and babashka tasks (bb tasks lists them)
├── .github/workflows/             CI; it checks out cloview-core next to this repository
├── CLAUDE.md  AGENTS.md           instructions for Claude Code (developer) and operator agents
├── VERIFY_ON_WINDOWS.md
└── LICENSE                        MIT
```

## Adding an example app

1. A folder under `apps/` with the shape of `apps/fitness`: `shared/` holds the app
   (state, events, effects, views, and their tests) in `.cljc`; `javafx/`, `wpf/` and
   `flutter/` hold only thin entry points and optional `:ui/native` renderers.
2. Its `deps.edn` files reach cloview-core with `:local/root` paths into
   `../cloview-core` (copy them from `apps/fitness`).
3. Its tests in `deps.edn`, `bb.edn` and CI. `clj -M:static-check` already covers every
   folder under `apps/`.
4. A `README.md`: what it demonstrates, how to run it, its host-specific code.
5. A row in the gallery above.

New apps are MIT like the rest of the repository, unless an app needs another licence
(see the override convention below).

## Working in this repository

Claude Code reads [`CLAUDE.md`](CLAUDE.md); operator agents that run job tickets read
[`AGENTS.md`](AGENTS.md). The development workflow, the job tickets and the decision
record are kept in cloview-core
([DEVELOPMENT_WORKFLOW.md](https://github.com/echojustus/cloview-core/blob/main/docs/DEVELOPMENT_WORKFLOW.md),
[decisions](https://github.com/echojustus/cloview-core/blob/main/docs/STAGE_2_2_DECISIONS.md)),
and so is how a Claude Code session's work becomes commits
([DELIVERY_DESIGN.md](https://github.com/echojustus/cloview-core/blob/main/docs/DELIVERY_DESIGN.md),
[COLLABORATION.md](https://github.com/echojustus/cloview-core/blob/main/docs/COLLABORATION.md)).

- **`benchmarks/fitness-baseline/` is frozen.** Its files are hash-manifested; the only
  edit since delivery is recorded in [`benchmarks/README.md`](benchmarks/README.md).
- **The licences are deliberate.** This repository is MIT; cloview-core is EPL-2.0 OR
  GPL-2.0-or-later. Do not "harmonise" them.

## License

© 2026 EchoJustus (Jiapeng Ji)

The code in this repository is licensed under the [MIT License](LICENSE). That covers
the whole repository, including the fitness app and its acceptance benchmark
(`benchmarks/fitness-baseline/`), except a directory listed under "Exceptions" below.

- **Scope.** The MIT licence covers only the example code in this repository, not
  [cloview-core], which keeps its own licence: EPL-2.0, with GPL-2.0-or-later as a
  Secondary License.
- **Compatible with both routes through cloview-core.** MIT is downward-compatible with
  either route: the examples may be combined with cloview-core under EPL-2.0 (the
  default) or under the GPL Secondary License, and MIT content is absorbed cleanly into
  either.
- **Per-directory overrides.** A single example directory may ship its own `LICENSE`
  (for example EPL-2.0 for a quasi-commercial flagship app), together with a one-line
  note under "Exceptions" below. This is how a risky example gets protected later,
  without ever licensing the whole gallery under a copyleft licence. An override covers
  what is released after it: code already published here under MIT stays available
  under MIT.

### Exceptions

None so far. Format of an entry: `` `apps/<name>/`: <licence>, see `apps/<name>/LICENSE` ``.

### Why MIT

Recorded here so the choice is not "corrected" later:

- People **copy-paste** from an examples repository; that is its only reason to exist.
  Any clause that raises the cost of copying defeats it.
- The patent-worthy assets live in cloview-core (the reconciler, the bridge's process
  management, the licensing module's tamper sealing) and are covered there by EPL-2.0's
  express patent grant and patent-retaliation clause. The examples carry little patent
  value.
- Pure GPL would be counterproductive here. It does **not** prevent someone from
  reselling a re-skinned copy (the GPL permits selling copies with source), it deters
  the enterprise developers who most need onboarding material, and GPL-2.0, unlike
  GPL-3.0, has no express patent grant.

The provenance of the fitness app's reference core is kept as forensic record, not as a
licence condition: the SHA-256 tables in [`apps/fitness/README.md`](apps/fitness/README.md)
and the benchmark's `SOURCE_MAP_PUBLIC.csv` and `MANIFEST_SHA256.csv`.

[cloview-core]: https://github.com/echojustus/cloview-core
