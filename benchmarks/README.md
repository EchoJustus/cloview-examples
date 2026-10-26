# Benchmarks

## `fitness-baseline/`: the frozen acceptance package

`fitness-baseline/` is the **Fitness Framework Acceptance Baseline** (English edition,
2026-10-24), stored as delivered except for one licence edit approved by the owner
(below). It defines what a cloview port of the fitness and workout application must
demonstrate on the JVM, Dart and CLR hosts:

- docs: `README_FIRST.md`, `TASK_SCOPE.md`, `CLAIM_BOUNDARIES.md`, `docs/*.md`;
- `acceptance/`: `CAPABILITY_MATRIX.csv`, `cases.json`, `ACCEPTANCE_GUIDE.md`,
  `result-template.json`;
- `fixtures/`: business inputs and oracles, the demo document, synthetic heart rate, the
  muscle dictionary;
- `media/`: the synthetic GIF and its spec;
- `reference-core/` and `reference-ui/`: provenance of the shared core and the Flutter UI
  components;
- `tools/*.py`: integrity and oracle checkers;
- `visuals/`: reference screenshots.

### Licence

The package ships under this repository's [MIT licence](../LICENSE), like the rest of
cloview-examples: © 2026 EchoJustus (Jiapeng Ji), the author and sole rights holder of
the source application, Clogem Fitness 0.3.0. Attribution and provenance are preserved by
`SOURCE_MAP_PUBLIC.csv`, `reference-core/SOURCE_MAP.json` and `MANIFEST_SHA256.csv`, and,
for the fitness app's copy of the reference core, by the SHA-256 tables in
[`apps/fitness/README.md`](../apps/fitness/README.md#provenance-of-the-reference-core).

### The one edit since delivery

On 2026-10-25 the owner relicensed the package. The last paragraph of `README_FIRST.md`,
which described its licence status at delivery, now states the licence above, and the
`README_FIRST.md` row of `MANIFEST_SHA256.csv` was updated to match. The paragraph's
closing sentence about auditing dependency licences was kept. No other file changed;
`CLAIM_BOUNDARIES.md` in particular is byte-identical to the delivered package.

| `README_FIRST.md` | Bytes | SHA-256 |
|---|---:|---|
| as delivered | 4,664 | `56D1A2C28346150F62B373A75080FA556DC223F9E5DD2C47BBD75E0B3C67E7D7` |
| now | 4,782 | `CBEB4378336678989CF9F1C195B7AE93D70C3E7ABC03D8E08B012138521EB0AF` |

### Rules

- **Do not edit anything under `fitness-baseline/`.** `MANIFEST_SHA256.csv` lists every
  file's hash, and `tools/check_package.py` fails on any change. A change the owner
  decides on is made together with its manifest row and recorded above.
  `.gitattributes` marks the folder `-text`, so Git never converts its line endings.
- **The cloview port lives elsewhere:**
  - [`apps/fitness/`](../apps/fitness): the app, with the reference core copied from
    `reference-core/` (provenance tables in its README);
  - [`docs/FITNESS_CAPABILITY_MAP.md`](../docs/FITNESS_CAPABILITY_MAP.md): case-by-case
    support and status;
  - [`docs/NATIVE_CONTRACTS.md`](../docs/NATIVE_CONTRACTS.md): the native views.
- **Results start `NOT_RUN`** (`CLAIM_BOUNDARIES.md`). Never copy the historical Flutter
  passes into another host's column. A case passes only with the evidence `cases.json`
  asks for, on that host.
- **Evidence must contain no personal health data, pairing secrets, machine account names
  or absolute developer paths.** The harness results (`verify/out/`) contain local paths
  and are git-ignored; review them before attaching any to an acceptance record.

### Checks

Run from the repository root:

```bash
python3 -I benchmarks/fitness-baseline/tools/check_package.py     # package integrity (58 files)
python3 -I benchmarks/fitness-baseline/tools/check_fixtures.py    # fixture consistency (37 cases)
clj -M:fitness --out-dir verify/out                               # DOM-001 on the JVM -> envelope
python3 -I benchmarks/fitness-baseline/tools/check_fixtures.py --observed verify/out/fitness-jvm-business-results.json
```

`bb fitness:test` runs all four. `bb fitness:test:clr` and `bb fitness:flutter` do the
same on ClojureCLR and Dart. On Windows, `verify\run-windows-checks.ps1` covers all three
hosts (steps 02–04) and runs the checkers on each host's envelope (step 05).
