# cloview-examples: notes for Claude Code

Example apps for cloview: one declarative `.cljc` UI rendered natively by cljfx/JavaFX
(JVM), WPF (ClojureCLR) and Flutter (ClojureDart). The framework is **cloview-core**,
cloned next to this repository (`../cloview-core`); its `CLAUDE.md`, `README.md`
("Working in this repository") and `docs/ARCHITECTURE.md` hold the framework's rules,
and its `docs/STAGE_2_2_DECISIONS.md` the decisions of both repositories.

## Collaboration contract

Settled rules. They bind every Claude Code session in this repository, whatever a tool or
a default of the product suggests.

1. **Never mutate git history or touch the remote.** No `git commit`, `git push`,
   `gh pr create`, `gh pr merge`, `git merge`, `git rebase`, `git reset`,
   `git cherry-pick`. Read-only git is fine. Deliverables are files on disk.
2. **Dropbox is the transport, with one root per repository.** Prefer reading
   `<DROPBOX>/cloview-core/_inbox/…`, and `<DROPBOX>/cloview-examples/_inbox/…` when
   working on cloview-examples, before asking the owner for anything. Deliver artifacts
   under `<DROPBOX>/<repository>/_outbox/`, where `<repository>` is the one they belong to
   (`cloview-core` or `cloview-examples`), or cloview-core when they belong to neither.
   Never open a turn with "please upload …"; if a read fails, name the path you tried. If a
   write fails, present the artifact in chat so the owner can download it by hand.
3. **Naming red lines.** Branch names are lowercase ASCII, hyphen-separated, descriptive
   and reasonably short; they never start with `claude/`, `tmp/`, `temp/`, `wip/`, `dev/`
   or `my-`, and are never a bare `patch-N` or `test-N`. Commit messages carry no emoji, no
   AI attribution, no `Co-Authored-By` and no statement about how the change was produced.
4. **Nothing agreed in chat travels forward.** A new session starts empty and reads only
   committed files: whatever is settled must land in a repository file.

How work travels from a session to commits (its format, branches, pull requests) is
designed in cloview-core's `docs/DELIVERY_DESIGN.md` (`../cloview-core/docs/`, or
[on GitHub](https://github.com/echojustus/cloview-core/blob/main/docs/DELIVERY_DESIGN.md)).
Re-read it before your first delivery in a session, follow the options its decision table
marks CHOSEN, and ask about any still marked PROPOSED.

## Checks that must stay green

```bash
clojure -M:fitness         # fitness unit tests + the 37 business oracles (JVM)
clojure -M:static-check    # cloview-core's static check on apps/: calls, arities, portability, ClojureDart coverage
python3 -I benchmarks/fitness-baseline/tools/check_package.py   # the frozen package is intact
```

On Windows, these also apply: `verify/run-windows-checks.ps1`, steps 01–05. CI runs the
Linux checks (`.github/workflows/ci.yml`, with cloview-core checked out next to this
repository); the harness runs on `windows-harness.yml`.

## Rules

- **`benchmarks/fitness-baseline/` is frozen.** Never edit it. A change the owner decides
  on goes in together with its `MANIFEST_SHA256.csv` row and is recorded in
  `benchmarks/README.md`. `CLAIM_BOUNDARIES.md` is evidence scope, not a licence: never
  "clean it up".
- **The reference core is a tracked copy:** never edit `apps/fitness/shared/src/clogem/`
  (byte-identical to the benchmark's) or `apps/fitness/shared/test/clogem/` (the ported
  tests: exactly the three documented edits). SHA-256 provenance is in
  `apps/fitness/README.md`.
- **The licences are deliberate.** This repository is MIT; cloview-core is EPL-2.0 with
  GPL-2.0-or-later as a Secondary License. Never "harmonise" them, never add a
  rights-reserved line or a note that excludes files from the licence. One app folder may
  get its own `LICENSE` only with a one-line entry under "Exceptions" in `README.md`.
- **The framework's portability rules apply to the apps** (cloview-core's ARCHITECTURE.md
  §6): `:cljd` first in reader conditionals; only ClojureDart-defined symbols in
  Dart-compiled files; ordinal string order; no `str` of doubles in UI or JSON; no
  top-level side effects; no type hints on struct locals in `.cljr`.
- **Framework changes belong in cloview-core.** Tasks and scripts here only read the
  sibling checkout; never write into it.

## Saving tokens and context

The same rules as cloview-core's `CLAUDE.md`: prefer CI to asking anyone to run
something; read summaries (`verify/out/00-summary.json`, the CI job summary) before logs;
give long logs to a subagent. Job tickets for this repository are written in
cloview-core's `ops/jobs/`; their steps run here with `--cwd ../cloview-examples`.
