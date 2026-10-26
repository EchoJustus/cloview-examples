# Instructions for operator agents

This file is for agents that **execute job tickets**, such as Codex CLI with a local model
or with ChatGPT. Claude Code, the developer, follows `CLAUDE.md` instead.

Tickets and the result recorder live in **cloview-core**, cloned next to this repository
(`../cloview-core`): follow its `AGENTS.md`. Record every step with cloview-core's
`ops/tools/result.py`, and give the steps that run here `--cwd ../cloview-examples`.

Rules specific to this repository:

- **Never edit tracked files.** In particular never touch `benchmarks/fitness-baseline/`
  (every file is hash-manifested) or `apps/fitness/shared/*/clogem/` (the reference
  core's tracked copy, with SHA-256 provenance in `apps/fitness/README.md`).
- **Harness results contain local paths.** `verify/out/` and
  `verify/cloview-examples-windows-verification-results.zip` are git-ignored: never
  commit them; hand over the zip only the way the ticket says.
- **Never "fix" a failure.** A failing step is a result: record it and stop or continue
  as the ticket says.
