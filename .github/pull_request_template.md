Adding a row to the community showcase? Fill in "What changes" with the row and delete
the other sections.

## Topic

Branch: `<area>/<topic>` into `main`

Scope, in one sentence:

Needs cloview-core at: `main`, or the branch or commit this depends on

## What changes

For people who read or copy the examples:

Commits on this branch (short id and summary):

-

## Evidence

- Linux: CI on this pull request (fitness tests, the 37 oracles, static check, benchmark checkers).
- Windows (harness steps 01–05): result and where it ran, or "not affected".
- Flutter: result, or "not affected".

## Review notes

Where to start, risks, follow-up work:

## Checklist

- [ ] One topic only: nothing from another branch is mixed in.
- [ ] Title, description and commit messages carry no emoji, no AI attribution, no `Co-Authored-By` and nothing about how the change was produced.
- [ ] `benchmarks/fitness-baseline/` is untouched, or the owner's change is recorded in `benchmarks/README.md` with its `MANIFEST_SHA256.csv` row.
- [ ] The reference-core copy (`apps/fitness/shared/*/clogem/`) is untouched.
- [ ] New code is MIT like the rest of the repository, or its directory override is listed under "Exceptions" in `README.md`.
- [ ] Documentation is updated where behaviour changed.
