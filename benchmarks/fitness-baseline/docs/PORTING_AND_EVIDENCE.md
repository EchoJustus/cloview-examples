# Porting protocol and evidence expectations

## One application, multiple adapters

Supply a shared application layer that declares the exercise library/editor, plans, timing/completion, records, calendar and analytics. Sharing only colors or JSON fixtures while reimplementing all screens and business rules separately does not demonstrate the user's objective.

Do not force a particular view representation. A runtime map, function DSL, macro DSL or EDN-plus-bindings design is acceptable if the same shared source expresses the benchmark and is supported by executable host evidence. Generated host code is acceptable; document the generator and the shared source from which it is produced.

Publish a small provenance table for the port:

| Concern | Shared source | Host override | Reason and evidence |
| --- | --- | --- | --- |
| Domain operations | One shared implementation | Only documented compiler/runtime compatibility changes | Oracle output comparison |
| Screen structure | One declaration source | Native control mappings | Control inventory and screenshots |
| Events/state | Shared logical event contract | Native callback conversion | Event trace and state transition |
| Effects | Shared requests/results | Clock, IDs, storage, media, networking | Fault-injection and real integration evidence |
| Extended views | Shared inputs and callbacks | Body/chart/media/split-view implementation | Lifecycle and semantic checks |

There is no arbitrary shared-line percentage gate. Review actual ownership: business rules and the application declaration must not be duplicated by host. Reader-conditionals used to hide three complete applications fail that objective even if the file extension is `.cljc`.

## Recommended vertical slice

1. Load the synthetic library with known primary/secondary muscles, intensity and equipment.
2. Type a search and select filters; verify visible exercise IDs.
3. Add the same exercise twice to a plan; verify independent item IDs and ordering.
4. Start, pause, resume and complete one item with an injected clock. Verify active duration and exactly one record.
5. Save, restart and reload. Verify the snapshot, item identity and completion status.
6. Inspect actual muscle load, equal-length comparison and the selected calendar day.
7. Inject a failed save and a stale asynchronous result. Preserve data and expose a recoverable error.

This slice exercises more meaningful portability boundaries than an isolated counter while staying smaller than the full application.

## Evidence artifacts

For each host, record source revision, OS/architecture, compiler/runtime, framework adapter version, dependency lockfile, launch/build commands and required native dependencies. Store evidence with portable relative paths.

Each interaction case should identify the initial fixture, action sequence, relevant logical events, state before/after, and observed result. Record screenshots for layout/visibility; record event/state or integration output for behavior. A screenshot alone cannot establish persistence or callback correctness.

Numeric comparisons must use the fixture guide's tolerances. Compare dates, identifiers, set counts and event counts exactly. Do not hide timezone errors behind wide numeric tolerances. Test dates using an explicit zone/offset and a controllable clock; do not base acceptance on the evaluator's current date.

Fault cases need deliberate, reproducible failure injection: storage rejection, a permission denial, an unsupported capability, disposal during an asynchronous operation, and a concurrent document edit. Keep these distinct from normal success paths.

## Performance measurements

Use a fixed machine profile, release builds, fixed fixture scale and documented warmup. Report cold load time, filtering latency, analytics range-change latency and UI frame/render observations separately. Repeat runs and report sample count and median/p95 where meaningful. Record data scale and whether assets were cached.

The source baseline contains functional cache/painter checks, not a universal timing budget. Set explicit agreed budgets before comparing hosts; until then report measurements without inventing a performance PASS. A host may differ in frame scheduling and still satisfy semantic parity, but visibly blocked controls or repeated side effects are defects.

## Distribution acceptance

From a clean downstream sample, consume the framework in the manner its design promises. Build and launch each required host without copying private development paths or editing framework internals. Verify assets, native libraries, entry points and lifecycle teardown. Source/git dependencies, a monorepo and host-native packages are all candidates; the benchmark evaluates the documented promise rather than selecting one.

Optional integrations must expose availability and failure states. For example, a desktop adapter can report Health Connect unavailable and still pass the core profile. It cannot fabricate heart-rate records or claim that an Android integration was tested merely because a mock bridge returned data.
