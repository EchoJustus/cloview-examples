# English-normalized reference core

These three `.cljc` files are isolated derivatives of the existing fitness
application's pure business core. They are reference evidence, not a proposed UI
framework implementation. The original application remains unchanged.

## Changes from the application source

- Chinese muscle names, equipment names, intensity labels, and weekday headings
  have English values. Compatibility keys named `zh` remain in data and code,
  but their values in this package are English. They are not a recommendation
  for a new localization API.
- Equipment labels also function as domain tag values in the original app.
  This derivative consistently translates both data values and comparisons.
  A future framework should prefer stable equipment IDs plus localized labels.
- The copied tests use matching English fixture strings. A fake absolute media
  path in a negative test is replaced by a relative `external/` path; it remains
  a non-demo path. No business formula, numeric constant, branch, or function
  signature was changed to achieve an English package.
- `SOURCE_MAP.json` records original and derivative byte hashes and relative
  source locations. The retained `clogem.fitness.*` namespaces identify the
  reference implementation; they do not prescribe the framework's package name.

## Reproduce from the package root

Use an existing Babashka executable; no downloaded dependencies are required.
On Windows:

```powershell
bb --classpath 'reference-core/src;reference-core/test' -m clogem.fitness.test-runner
bb --classpath 'reference-core/src' reference-core/runner.clj --check
python tools/check_fixtures.py
```

On a Unix shell, use `:` instead of `;` in the multi-directory classpath.
The checked-in results were produced on Windows with Babashka, not by executing
the candidate framework on JavaFX, Flutter, or CLR. The `.cljc` files contain no
GUI adapter, storage driver, timer, HTTP client, or health-device API.

`runner.clj --write` intentionally regenerates expected fixture files. Do not
use that mode during candidate acceptance: changing expected values to match a
candidate is not a successful test. Use `--check` against the frozen package.

The runner uses host-specific exception handling for this local tooling. It is
not itself a shared `.cljc` runner. A candidate must implement its own host
bindings and report observed outputs against the same frozen JSON cases.

On 2026-10-24, this derivative passed 37 tests with 213 assertions and all 37
frozen operation cases under Babashka 1.13.225. The submitted-output checker
passed 23 positive/negative self-checks. Exact scope and results are recorded
in `evidence/core-oracle-results.json`; no candidate host pass is implied.
