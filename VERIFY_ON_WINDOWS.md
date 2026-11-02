# Verify cloview-examples on Windows

The fitness app's checks that cannot run in Claude's Linux sandbox (real WPF,
Flutter/ClojureDart, the Windows Clojure CLI) run from `verify\run-windows-checks.ps1`,
which packs the results into one zip to upload for review. The framework has its own
harness in cloview-core (see its
[`VERIFY_ON_WINDOWS.md`](https://github.com/echojustus/cloview-core/blob/main/VERIFY_ON_WINDOWS.md)).
Before the repository split these checks ran as step 07 of the single harness and passed
on all three hosts on 2026-10-25 (cloview-core's
[`WINDOWS_VERIFICATION_REPORT.md`](https://github.com/echojustus/cloview-core/blob/main/docs/WINDOWS_VERIFICATION_REPORT.md)
§8). This harness runs them as steps 02–05; on a GitHub-hosted Windows runner, started by
hand (`.github/workflows/windows-harness.yml`), it first passed all five steps on
2026-10-26, and again with the Verdict step of section 5 on 2026-11-01.

## 1. Prerequisites

The same toolchains as cloview-core's harness, plus Python for the benchmark's checkers:

| Tool | Version | Used by step | Install | Check |
|---|---|---|---|---|
| Windows | 10 22H2 or 11, x64 | all | — | `winver` |
| PowerShell | 5.1 (built in) or 7.x | all | — | `$PSVersionTable.PSVersion` |
| Git for Windows | any recent | cloning; 02 fallback; 04 (ClojureDart is a git dep) | `winget install Git.Git` | `git --version` |
| JDK | **11 or newer** (21+ recommended) | 02, 04 | `winget install EclipseAdoptium.Temurin.21.JDK` | `java -version` |
| Clojure CLI | 1.12.x | 02, 04 | [clj-msi](https://github.com/casselc/clj-msi) | `clojure --version` |
| .NET SDK | **10** (9 or 8 accepted) | 03 | `winget install Microsoft.DotNet.SDK.10` | `dotnet --list-sdks` |
| Flutter SDK | **3.35 or newer** (3.47.x stable recommended) | 04 | https://docs.flutter.dev/get-started/install/windows | `flutter --version` |
| Windows Developer Mode | on | 04, `bb run:fitness:flutter` | `start ms-settings:developers` | step 01 reports `symlink_support` |
| Python 3 | 3.7+ (3.12 recommended) | 05 | `winget install Python.Python.3.12` | `py -3 --version` |
| Babashka *(optional)* | any recent | `bb` tasks | https://github.com/babashka/babashka#installation | `bb --version` |

**Both repositories, side by side.** The apps use cloview-core's modules through
`:local/root "../cloview-core/…"` paths, and step 03 builds the WPF host from a copy of
cloview-core's template in `target\clr-host-project`; the harness only reads the
cloview-core checkout. Clone both into one short folder without spaces, on a local drive
(Flutter's `.bat` tools cannot run on a `\\wsl.localhost\...` path):

```powershell
cd C:\src
git clone https://github.com/echojustus/cloview-core
git clone https://github.com/echojustus/cloview-examples
```

**Clojure CLI.** clj-msi puts `clojure.exe` on the PATH (open a **new** PowerShell
afterwards); Scoop's `clojure`, deps.clj's `clj.exe` and the older ClojureTools PowerShell
module, even one installed only for PowerShell 7, work too. **.NET.** The SDK brings the
*Microsoft.WindowsDesktop.App* runtime that WPF needs; the harness picks
`net10.0-windows` when it can, else `net9.0-windows` or `net8.0-windows`.

**Flutter.** The pinned ClojureDart 0.9.20261002 needs Flutter 3.35 or newer (it uses
`AlignmentGeometry.topLeft`): with an older one every compile fails, and step 04 stops
with instructions. Run `flutter upgrade`, or pass `-FlutterUpgrade`. Without Developer
Mode or an elevated shell, `flutter pub add` reports "Building with plugins requires
symlink support"; step 01 warns, and step 04 continues once the packages are added,
since compiling and `flutter test` need no plugin symlinks. Running the Flutter app
needs them, and Visual Studio 2022 with *Desktop development with C++*.

## 2. Run it

```powershell
cd C:\src\cloview-examples
powershell -ExecutionPolicy Bypass -File .\verify\run-windows-checks.ps1
```

`-ExecutionPolicy Bypass` applies to this one process only. With Babashka, `bb verify`
does the same and passes options through (`bb verify -SkipFlutter`). The first run
downloads Maven/Clojars and NuGet packages, ClojureDart and pub packages, so it is slower.

Every step runs even when an earlier one failed or threw (the step is then `failed`, with
the message in `00-summary.json`); a run stopped with Ctrl+C still writes the summary and
the zip, marked `aborted`. The console ends with one status per step: `ok`, `failed`,
`skipped` or `partial`.

| Option | Effect |
|---|---|
| `-SkipJvm` (alias `-SkipJavaFX`) | skip step 02 |
| `-SkipClr` (alias `-SkipWpf`) | skip step 03 |
| `-SkipFlutter` | skip step 04 |
| `-FlutterUpgrade` | run `flutter upgrade` first when the installed Flutter is older than 3.35 (it changes your SDK, so it is opt-in) |
| `-ClrTargetFramework net8.0-windows` | force the WPF host's target framework |
| `-NoZip` | leave the results in `verify\out\` only |

## 3. What each step does

| Step | What runs | Writes (in `verify\out\`) | `ok` when |
|---|---|---|---|
| 01 env | OS, PowerShell and toolchain diagnostics; .NET SDKs, Desktop runtimes and the chosen TFM; Flutter version and symlink support; Python; the cloview-core checkout (path, commit) | `01-env-check.json` | cloview-core is found (otherwise `failed`, and steps 02–04 cannot run) |
| 02 JVM | `clj -M:fitness` (reference and app unit tests, the 37 DOM-001 oracles); `clj -M:static-check` (cloview-core's static check on `apps\`); `clj -M:smoke-fitness-javafx` (off-screen JavaFX; retried with `:cljfx-git` if it fails without writing its JSON) | `02-jvm.log`, `02-fitness-jvm-summary.json`, `02-fitness-jvm-business-results.json`, `02-fitness-jvm-smoke.json` | all three exit 0 |
| 03 CLR | stage cloview-core's and the app's sources into `target\clr-src`; build `target\clr-host\CloViewHost.exe`; `CloViewHost.exe --ns fitness.verify --run-tests` (unit tests and oracles on ClojureCLR) and `--ns fitness.wpf.main --headless-smoke` (off-screen WPF) | `03-clr.log`, `03-fitness-clr-summary.json`, `03-fitness-clr-business-results.json`, `03-fitness-clr-wpf-smoke.json` | build, tests and smoke exit 0 |
| 04 Dart | check Flutter ≥ 3.35; prepare `target\flutter-fitness` (`clj -M:cljd init` once, `flutter pub add`); `clj -M:cljd compile` (on failure also `clj -M:cljd-diag compile`, the full cause chain); `clj -M:test:cljd test` (widget test, shared tests, oracles on Dart) | `04-dart.log` (`flutter test` counts the Dart tests here), `04-fitness-dart-summary.json` (oracle results only: it lists unit tests as skipped), `04-fitness-dart-business-results.json` | Flutter ≥ 3.35; init, `pub add`, compile and tests exit 0 |
| 05 checkers | in `benchmarks\fitness-baseline`: `tools\check_package.py`, `tools\check_fixtures.py`, and `tools\check_fixtures.py --observed` for each host's envelope | `05-baseline-checks.log` | every checker exits 0 and every `--observed` check passes, for all three hosts |
| 00 | step summary; `missing_files` lists the gaps | `00-summary.json` | — |

**`partial` (step 05).** Every check that ran passed, but at least one host left no
envelope to compare because its step never ran the oracles: skipped by an option or a
missing tool, stopped by a Flutter older than 3.35, or a failed CloViewHost build.
`hosts_not_run` in `00-summary.json` names those hosts. A failing checker, or a host whose
oracle run left no envelope, makes the step `failed`. Without Python 3.7+ it is
`skipped`; the oracle comparisons inside steps 02–04 still run.

## 4. What to upload

Upload **`verify\cloview-examples-windows-verification-results.zip`**, which holds every
file of `verify\out\`, even when some are missing: `00-summary.json` records what ran,
what was skipped and why. The files contain tool versions, the OS version, the commits of
both checkouts and local paths (your user folder in tool locations, the repository
paths). Review them before you share them; acceptance evidence must not contain absolute
developer paths ([`benchmarks/README.md`](benchmarks/README.md)).

## 5. On GitHub Actions

[`.github/workflows/windows-harness.yml`](.github/workflows/windows-harness.yml) runs the
same script on a GitHub-hosted Windows runner, with cloview-core checked out next to this
repository and JDK 21, the Clojure CLI, .NET 10, Flutter 3.47.8 and Python 3.12
installed. It runs only when started by hand: *Actions → windows-harness → Run workflow*,
or `gh workflow run windows-harness.yml`. The optional `core_ref` input selects a
cloview-core branch, tag or commit (`-f core_ref=<ref>`; empty means its default branch).
Results: the job summary, and the `windows-verification-results` artifact (`verify/out/`
and the zip, kept for 14 days).

The job passes only when every step is `ok`. The harness itself exits 0, so the job's
last step, Verdict, runs cloview-core's `ops/tools/ci_summary.py --require-ok`: a
`failed`, `skipped` or `partial` step, an `aborted` run or a missing `00-summary.json`
fails the job. Run on your PC, the harness keeps reporting skips without failing. The
option has been on cloview-core's `main` since 50a2565 (2026-10-26): a `core_ref` without
that commit, such as 09d4afe, fails the Verdict step with the tool's usage text.

## 6. Visual check (owner)

No smoke or test compares pixels ([`docs/NATIVE_CONTRACTS.md`](docs/NATIVE_CONTRACTS.md),
note ¹), so a person looks at each host once, and again after visual changes:

```powershell
bb run:fitness:javafx
bb run:fitness:wpf        # stages the sources and builds CloViewHost first
bb run:fitness:flutter    # Flutter for Windows: needs Developer Mode and the VS C++ workload
```

In each app, take a screenshot of the whole window on each of the five tabs: Catalog,
Plan Builder, Execution Log, Anatomy & Analytics, Calendar. Name them
`<host>-fitness-<screen>.png`, for example `wpf-fitness-calendar.png`. The apps open on
the document an earlier session saved (`%LOCALAPPDATA%\cloview\fitness.edn` on JavaFX and
WPF), else on the demo document; the reference renders are in
`benchmarks/fitness-baseline/visuals/`. The recorded ticket for this check, which also
covers the Greeter and says where the screenshots go, lives in cloview-core:
[`ops/jobs/20261026-visual-check.md`](https://github.com/echojustus/cloview-core/blob/main/ops/jobs/20261026-visual-check.md).

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| "running scripts is disabled on this system" | use the exact command of §2 (`-ExecutionPolicy Bypass`) |
| step 02 or 04 skipped with `clj not found` | install the Clojure CLI, then open a **new** PowerShell |
| step 03 `build_exit: no-tfm` or `no-dotnet` | install the .NET 10 SDK, or pass `-ClrTargetFramework net8.0-windows` |
| step 03 could not delete `target\clr-host` | close any running `CloViewHost.exe` (for example one started by `bb run:fitness:wpf`) |
| step 04 compile error | the log holds a second run, `clj -M:cljd-diag …`, with the full cause chain: that part is what the review needs |
| `check_package.py`: "File inventory differs" | something added files under `benchmarks\fitness-baseline` (for example `pub get` in `reference-ui`); remove them |
| you want a clean re-run | delete `target\` (`bb clean` also empties `verify\out\`) and run the script again |
