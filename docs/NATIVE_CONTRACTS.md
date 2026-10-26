# Native views and composites for the fitness benchmark

This document is the contract between the shared fitness views (`.cljc`) and the
host-specific drawing code (JavaFX, WPF, Flutter). It covers:

- the anatomy heatmap (2D now, 3D specified);
- the six-region radar;
- the set-volume comparison;
- the daily calorie chart;
- the training calendar;
- the media preview (GIF and MP4).

It was derived from the benchmark package (`benchmarks/fitness-baseline`):

- docs `UI_SEMANTICS.md`, `FUNCTIONAL_BASELINE.md` and `VISUAL_REFERENCE.md`;
- cases EXT-001..006, DOM-008/009, UI-003/004 and ARCH-004;
- `reference-ui/lib/muscle_view.dart` and `analytics_widgets.dart`;
- the reference core.

The EDN examples were produced by the shared implementation, `fitness.derive`, from
`fixtures/demo-document.edn`.

**Status legend.**

- **Implemented**: code exists and is exercised by the tests and smokes listed in §10.
- **Specified**: the contract is fixed here, but no host renders it yet (Stage 3 work).

| View | Form | JavaFX | WPF | Flutter |
|---|---|---|---|---|
| Body map, 2D | `:ui/native :fitness/body-canvas` + canonical controls | implemented (Canvas) | implemented (Canvas of Shapes) | implemented (CustomPaint)¹ |
| Body map, 3D | same native, `:style :three-d` | specified; the 2D map plus a note is shown | specified | specified |
| Radar | `:ui/native :fitness/radar-canvas` + canonical value table | implemented | implemented | implemented¹ |
| Set-volume comparison | canonical only (shared) | implemented | implemented | implemented¹ |
| Daily calories | `:ui/native :fitness/calorie-canvas` + canonical value line | implemented | implemented | implemented¹ |
| Training calendar | composite `cloview.core.components/month-grid` | implemented | implemented | implemented¹ |
| Media: GIF | canonical `:ui/image` | implemented (ImageView animates) | implemented (`cloview.wpf.gif`) | implemented (Image animates)¹ |
| Media: MP4 | `:ui/native :fitness/media-player` | specified; explicit "not available" placeholder | same | same |

¹ Written against the ClojureDart/Flutter APIs and checked statically (including the
ClojureDart coverage rule). In the Windows run of the Stage 2-2b tree (2026-10-25,
Flutter 3.47.8), the fitness app compiled and passed its 47 Dart tests, the widget test
of the shell among them, and the 37 oracles. No test compares the renderers' pixels;
that needs a visual check. The sandbox has no Flutter SDK.

## 1. Decisions

| # | Decision |
|---|---|
| D1 | Only drawing surfaces are `:ui/native`: body canvas, radar canvas, calorie canvas and media player. Everything around them is canonical: headers, selects, legends, value tables, the comparison and the calendar. The same declaration therefore gives the same controls and events on every host, and only pixels are host code. |
| D2 | Shared code computes everything a host draws: loads, levels in 0–1, scale maxima, formatted text, colours. Hosts map props to pixels and pointer input to logical events. |
| D3 | Native output events are data. `:props :events` maps a logical event to an app event vector. The renderer appends the payload and calls the `dispatch!` it captured when the host built its renderers. This needs no core change; a core-level alternative is open question O4. |
| D4 | App state arrives as controlled props: selection, style, metric, mode, range, month, media source. Transient host state stays in the host: 3D orientation, drag, hover, scroll, playback position, caches. |
| D5 | Shared `.cljc` (`fitness.body`) holds the 2D polygons, silhouette, palette, colour scale and the 2D hit test. The 3D mesh data will join it (Stage 3), with projection and picking done natively per host (§3.6). |
| D6 | Expensive derivations are memoized on their inputs, never on the db's identity (ARCH-004). `fitness.derive` wraps each one in `cloview.core.memo/memo-last` over the db slices it reads. |
| D7 | The calendar is a canonical composite of `:ui/button` cells whose multi-line labels carry every value. A dedicated `:ui/pressable` tag is deferred (O3). |

## 2. Common contract

### 2.1 Wiring

```clojure
;; shared (fitness.views): a native leaf inside canonical layout
(native/view :fitness/body-canvas {:key :body-canvas :test-id "body-map-canvas"
                                   :props (derive/body-props db)})

;; each host entry point, once at start-up (apps/fitness/<host>/…/main)
(native/register-renderers! (natives/renderers {:dispatch! #(st/dispatch! app/store %)}))
```

- **Registry.** `fitness.native` holds `{kind host-map}`. `register-renderers!` merges into
  it, and `renderer` and `available-kinds` read it.
- **Host maps.** These are the `:ui/native` host maps of cloview.
  - JavaFX and WPF: `{:create (fn [] node) :update (fn [node props]) :dispose (fn [node])}`.
  - Flutter: `{:build (fn [build-context props] widget)}`.
- **Missing renderer.** `native/view` renders a canonical box with the text "This view is
  not available on this host yet (`<kind>`)". It is never an empty box (ARCH-003).
- **Diffing.** The reconciler compares `:props` by value.
  - JavaFX and WPF: `:update` runs only when the props changed.
  - Flutter: the `:ui/native` adapter returns the identical widget for equal props.

### 2.2 Prop values

- **EDN only.** No functions, host objects or atoms.
- **No NaN or ±Infinity.** `(= ##NaN ##NaN)` is false, so a NaN would force an update on
  every render. Non-finite values become `nil` plus a status key, as in the calorie
  chart's `:status`.
- **Number types.** Quantities are doubles and counts are longs. `(= 2 2.0)` is false on
  the JVM and CLR but true in Dart, so mixing them would make diffs host-dependent.
- **Identifiers.**
  - Muscle ids: the 20 stable strings of the reference dictionary.
  - Dates `"YYYY-MM-DD"`, months `"YYYY-MM"`.
  - Body styles `:two-d` / `:three-d` (`:2d` is not valid EDN).
  - Metric and mode are the reference core's strings: `"sets"` / `"weighted-minutes"`
    and `"actual"` / `"planned"`.
- **Text.** Everything a renderer draws arrives pre-formatted (`*-text`, `:label`,
  `:labels`, `:a11y-label`). Shared formatting uses integer arithmetic only:
  - `fmt-quantity` writes whole numbers as integers, otherwise one decimal;
  - `fmt-whole` rounds half up.

  On ClojureCLR 1.12.6 `str` of a double drops a zero fraction, and .NET formatting
  follows the current culture.
- **Sizes.** `:size {:width :height}` in logical pixels.
- **Accessibility.** `:a11y-label` maps to JavaFX `setAccessibleText`, WPF
  `AutomationProperties.Name` and Flutter `Semantics(label:)`.
- **Events.** A key missing from `:events` disables that event.

### 2.3 Logical events

The event ids are the benchmark's `UI_SEMANTICS` names, so traces read in benchmark
vocabulary. Handlers in `fitness.events` validate every payload; an unknown id or a
malformed date changes nothing.

| Event vector (payload appended) | Emitted by | Effect |
|---|---|---|
| `[:body/select-muscle id-or-nil]` | body canvas tap, muscle select, Clear | `[:analytics :selected-muscle]`. Never touches records or aggregates; unknown ids clear the selection. |
| `[:body/style-change :two-d\|:three-d]` | style select | `[:analytics :body-style]` |
| `[:calendar/select-date "YYYY-MM-DD"]` | calendar cell | `[:calendar :selected]`; adjacent-month cells select their own date |
| `[:calendar/change-month "YYYY-MM"]` | previous / next | `[:calendar :month]`, only within 1900-01..2100-12 |
| `[:calendar/go-today]` | Today | month and date from the `:fitness/today` coeffect |
| `[:analytics/open-date "YYYY-MM-DD"]` | Day analysis | range d..d, then switch to the analytics tab |
| `[:media/error exercise-id {:path s :reason kw}]` | media player (specified) | status message only; the document is unchanged |

**Emission rules, on every host:**

1. **Only from user input.** Never emit from `:create`, `:update`, `:build`, layout or
   paint, and never as the echo of a programmatic change.
2. **Exactly once per activation, and a drag never selects.** A click is a press and
   release within the platform drag threshold:
   - JavaFX: `MouseEvent.isStillSincePress`;
   - WPF: `SystemParameters.MinimumHorizontal/VerticalDragDistance`;
   - Flutter: `onTapUp`.

   Taps on the silhouette or the background emit nothing.
3. **Read the event vector from the latest props.** `:update` stores them in the node
   (JavaFX: the node's properties map; WPF: the element state; Flutter: the closure of
   the current build). A changed handler then needs no node recreation.

### 2.4 Ownership and lifecycle

| From props (app db) | Transient (host instance) |
|---|---|
| selection, style, metric/mode, range, month, selected date, media source, playback flags | 3D yaw/pitch, drag, hover, chart scroll, play/pause and position, decoded frames, geometry, painters, players |

- **Transient state** lasts as long as the native element and resets when it is recreated
  (key change or unmount). The natives use stable `:key`s, so renders and style switches
  never recreate them.
- **`:update`** is idempotent and cheap when the render-relevant props are unchanged.
- **`:dispose`** releases listeners, timers, players, file handles and GPU resources. It
  never dispatches.
- **Split views (EXT-006).** Moving a `:ui/split-view` divider changes only the size. The
  host relayouts and repaints, with no event and no recreation.

### 2.5 Stutter-free updates

1. **Derivation** (`fitness.derive`). `memo-last` compares arguments with `=`, which is
   cheap for the `identical?` slices of an unchanged document.
   - The inputs are `(:records :plans :exercises :profile :heart-rate)` plus the options,
     never the whole db. A selection creates a new db, and `st/sub` memoizes only on db
     identity.
   - Separate cached functions: `analysis` (current, previous, comparison), `calendar`,
     the heart-rate index, range calories, calendar calories, equipment options and the
     catalog filter.
   - `fitness.derive/calls` counts real computations. Selecting a muscle, switching the
     style, changing tabs or tapping the canvas leaves every counter unchanged. The app
     test and both GUI smokes assert this as ARCH-004 evidence.
2. **Props.** Props are rebuilt from the memoized results. Values that did not change are
   `=`, so the reconciler skips the native.
3. **Host.** Repaint only on a props change, which is all a native sees. The current
   renderers redraw one canvas, which takes well under a millisecond at these sizes.
   - **JavaFX:** one `Canvas` per view, repainted in `:update`.
   - **WPF:** one `Canvas` of frozen-geometry shapes, rebuilt in `:update`.
   - **Flutter:** `CustomPaint`. Painters are rebuilt only when the props change, since
     the adapter memoizes on `:props`.
   - **Planned refinement:** for the 3D view and long ranges, mutate brushes and
     transforms instead of rebuilding, and cache the 3D face list per orientation and
     size (§3.6).

### 2.6 Palette

| Name | Colour |
|---|---|
| ink | `#243B36` |
| muted | `#74817A` |
| teal | `#258675` |
| orange | `#E98D4F` |
| paper | `#F6F5EF` |
| previous | `#BCCDC5` |
| grid lines | `#D9E1D7` |

Colour is never the only channel:

- selection: outline, a written selection line and the select's value;
- unknown kcal: `?` and the word "unknown" in the accessible text;
- plan status: written as `c/p done`;
- calendar selection: the day number in brackets.

cloview colours use CSS order `#RRGGBB` / `#RRGGBBAA`, with alpha last, on every host.

## 3. Body map: `:fitness/body-canvas`

### 3.1 Shared panel (`fitness.views/body-panel`)

| Part | Content |
|---|---|
| Controls | style `:ui/select` (`body-style`: 2D/3D, emits `:body/style-change`); muscle `:ui/select` (`muscle-select`: 20 muscles plus "No muscle selected", the keyboard and accessible path EXT-003 asks for); **Clear** (`muscle-clear`, emits `[:body/select-muscle nil]`) |
| Note | while `:three-d` is chosen: "The 3D body view is not implemented on this host yet; the 2D map is shown." |
| Canvas | the native (`body-map-canvas`) |
| Selection line | `muscle-selection`: `"{name} — {load} {unit} — relative load {round(level·100)}%"`, or a hint |

### 3.2 Props (`fitness.derive/body-props`)

| Key | Type / unit | Nil? | Derivation |
|---|---|---|---|
| `:size` | `{:width 440 :height 340}` | no | fixed |
| `:style` | `:two-d` \| `:three-d` | no | `[:analytics :body-style]`, default `:two-d` (O6) |
| `:selected` | muscle id | yes | `[:analytics :selected-muscle]`, kept only if it is one of the 20 ids. It survives range, metric and style changes (O5). |
| `:metric` `:mode` `:unit` | `"sets"`\|`"weighted-minutes"`, `"actual"`\|`"planned"`, `"sets"`\|`"min"` | no | analytics options |
| `:regions` | 20 × `{:id :name :load :level}` in dictionary order | no | `load` = the current period's `(:muscles summary)` value if it is a positive number, else 0.0. `level = load / scale-max`. |
| `:scale-max` | double ≥ 1 | no | `max(1.0, max load)`. This is the reference rule: values ≤ 1 keep their absolute scale, so a lone 0.5 set is never shown at full scale. |
| `:labels` | `{:front "Front" :back "Back"}` | no | captions |
| `:a11y-label` | string | no | names the selection with its load and relative load |
| `:events` | `{:body/select-muscle [:body/select-muscle]}` | no | §2.3 |

**Example.** Week 2026-10-07..13, sets, actual, pectoralis selected. Regions with zero load
are omitted here; the props always hold all 20.

```clojure
{:size {:width 440 :height 340} :style :two-d :selected "pectoralis-major"
 :metric "sets" :mode "actual" :unit "sets" :scale-max 3.0
 :regions [{:id "pectoralis-major" :name "Pectoralis major" :load 3.0 :level 1.0}
           ;; … 18 zero regions …
           {:id "triceps-brachii" :name "Triceps brachii" :load 1.5 :level 0.5}]
 :labels {:front "Front" :back "Back"}
 :a11y-label "Front and back muscle map. Selected: Pectoralis major, 3 sets, relative load 100%."
 :events {:body/select-muscle [:body/select-muscle]}}
```

### 3.3 2D geometry and drawing (shared `fitness.body`)

The source of truth is `apps/fitness/shared/src/fitness/body.cljc`. Its data was
extracted by script from `_FlatAnatomy` / `_FlatBodyPainter` in `muscle_view.dart`.

- **Body space.** 100 × 280 units, y down. The front and back figures share the
  silhouette, scale and camera.
- **Layout** (`body/layout w h`). `s = min((h−28)/280, w/245)`. The front origin is
  `(0.25w − 50s, 7)` and the back origin `(0.75w − 50s, 7)`. Captions sit at
  `(0.25w | 0.75w, h−9)`.
- **Silhouette** (`body/silhouette`), not selectable:
  - a head oval, a neck rounded rectangle, a quadratic-Bézier torso, one arm and one leg;
  - the arm and leg are mirrored by `x → 100−x`;
  - path commands `:M :L :Q :C :Z :oval :rrect`.
- **Regions** (`body/regions`, `body/polygons`):
  - simple polygons, each with a leader **anchor**;
  - authored on the left half and mirrored, anchor included;
  - front: 9 ids in 11 polygons per side (`rectus-abdominis` is 3 blocks); back: 11 ids;
  - 44 polygons in all, and every id appears on exactly one figure;
  - deep muscles are projected onto the surface: iliopsoas on the front, rhomboids and
    erector spinae on the back.
- **Paint order.** Silhouette first, then the list order (left, then mirror). Later
  regions sit on top, so `rectus-femoris` covers `quadriceps`.
- **Colour** (`body/level-color`). Level 0 is `#CAD2CC`. A level ℓ > 0 is
  `lerp(#B1D8C5, #217B67, ℓ)`.
  - silhouette fill `#DDE2DE`;
  - region outline `#F6F5EF`, 0.5 body units wide;
  - selected outline `#E98D4F`, 1.2 body units wide.
- **Hit test** (`body/pick w h [x y]` → id or nil):
  1. Test the front figure, then the back (they never overlap).
  2. Convert to body units: `p' = (p − origin)/s`.
  3. Scan the polygons in reverse paint order with the non-zero winding rule, as Flutter's
     `Path.contains`.

  Every host calls this same function, so a tap at an anchor selects the same id
  everywhere. The JavaFX and WPF smokes tap the pectoralis anchor `(40, 51)` and check
  `pectoralis-major`, and tap the background and check that nothing is emitted.

### 3.4 Hosts (2D, implemented)

| Host | Rendering | Input |
|---|---|---|
| JavaFX (`fitness.javafx.native`) | one `Canvas`. Silhouette paths, region polygons and captions are painted in `:update`. | `MOUSE_CLICKED` with `isStillSincePress` → `body/pick` → `(dispatch! (conj ev id))` |
| WPF (`fitness.wpf.native`) | `Canvas` (transparent background, so it is hit-testable). `Path` with `StreamGeometry` for the silhouette, `Polygon`s for the regions, `TextBlock` captions, all non-hit-testable children. | `MouseLeftButtonDown`/`Up` within the system drag distance → `body/pick`. The element state also holds `:tap!`, used by the headless smoke to drive the same path. |
| Flutter (`fitness.flutter.native`) | `GestureDetector` + `CustomPaint` with a `CustomPainter` built from the props | `onTapUp` → `body/pick` with the laid-out size (a stretching parent can make the box wider than `:size`; the painter also draws at the real size) |

### 3.5 3D model (specified, Stage 3)

From `_BodyGeometry` and `_BodyPainter` in `muscle_view.dart`:

- **Meshes.** 76 UV ellipsoids (10 × 18; 209 vertices and 324 triangles each), 24,624
  triangles in all.
  - 6 axial shell parts, plus per side 13 shell parts, 11 front and 11 back muscles.
  - Each entry is `{c r axis? id? colour?}`. They will be copied verbatim from
    `muscle_view.dart:712–871` into a shared `fitness.body.mesh`.
- **View.**
  - Offset the model by `(0, −3.23, 0)`, then apply yaw (about y) and pitch (about x).
  - Project with `8.5/(8.5−z)` at scale `min(H/6.8, W/3.35)`, centred. This equals a
    camera at z = 8.5.
  - Cull back faces and sort faces by centroid z.
- **Colours.**
  - Muscle base `#B3CBB7`, shell `#C4CEBD`, eyes `#667A6D`.
  - A loaded muscle is `lerp(teal, orange, ℓ)`.
  - Flat shading: `0.66 + 0.34·max(0, n·L)` with `L = norm(−0.45, 0.70, 1)`. The selected
    region gets +0.12.
- **Picking.** The foremost triangle wins, shell included, so the far side can never be
  picked through the body.
- **Interaction (transient).**
  - Drag: `yaw += dx/115` (mod 2π), `pitch = clamp(pitch + dy/220, ±0.38)`.
  - Front/Back buttons inside the native set (0, 0) and (π, 0).
  - Orientation survives 3D → 2D → 3D switches, because the native is not recreated.

### 3.6 3D host plan

| Host | Plan |
|---|---|
| JavaFX | `SubScene` with a depth buffer. One `MeshView` per mesh (`TriangleMesh` built once), `PhongMaterial` cached per colour, `PerspectiveCamera(true)`. Drag changes two `Rotate` angles. Hit: `PickResult.getIntersectedNode().getUserData()`. Without `ConditionalFeature.SCENE3D`, fall back to a Canvas port of the Flutter painter. |
| WPF | `Viewport3D`. One `GeometryModel3D` per mesh (frozen `MeshGeometry3D`, cached `DiffuseMaterial`), `AmbientLight` ≈ 0.66 plus a `DirectionalLight`, `PerspectiveCamera` at (0, 0, 8.5). Drag changes two `AxisAngleRotation3D.Angle`s. Hit: `VisualTreeHelper.HitTest`, smallest `DistanceToRayOrigin`. |
| Flutter | Port `_BodyPainter`: software projection and one `drawVertices` batch. The face list is cached per (yaw, pitch, size) and reused by picking. Yaw and pitch live in `State`. Use `Float32List`/`Int32List` in the hot loop, and `RepaintBoundary`. |

On the GPU paths, Phong shading replaces the reference's flat shading. Pixel parity is not
required (O8).

## 4. Radar: `:fitness/radar-canvas`

**Panel.** "Training distribution": the native radar plus an accessible value table
(`radar-table`: Region | Current | Previous) for EXT-005.

**Grouping.** The benchmark package does **not** contain the muscle → axis mapping: the
reference `TrainingRadar` receives finished values. It fixes only two things:

- the axis order: Chest, Back, Shoulders, Arms, Core, Lower body;
- aggregation by **sum** (the gallery's Arms 2.5 = triceps 1.5 + biceps 1).

`fitness.derive/radar-axes` uses this proposed partition (O1), which covers every id
exactly once:

| Axis (clockwise from top) | Muscles |
|---|---|
| Chest | pectoralis-major |
| Back | latissimus-dorsi, trapezius†, rhomboids, erector-spinae† |
| Shoulders | anterior-, lateral-, posterior-deltoid |
| Arms | biceps-brachii, triceps-brachii |
| Core | rectus-abdominis, obliques |
| Lower body | gluteus-maximus, gluteus-medius, quadriceps, rectus-femoris, hamstrings, iliopsoas†, gastrocnemius, soleus |

† Unconfirmed: trapezius (Back or Shoulders), erector-spinae (Back or Core), iliopsoas
(Core or Lower body).

**Props** (`fitness.derive/radar-props`):

- `:size {:width 300 :height 260}`;
- `:metric` and `:unit`;
- `:series [:current :previous]`;
- `:scale-max`: `max(1, all drawn values)`, shared by both series;
- `:empty?`: every current value is 0;
- `:axes`: 6 × `{:id :label :current :previous :current-level :previous-level}`, the group
  sums of the current and the adjacent previous period;
- `:a11y-label`;
- no events.

**Drawing** (`_RadarPainter`):

- `n = max(3, |axes|)`, `R = max(4, min(0.31W, 0.34H))`, axis i at angle
  `−π/2 + 2πi/n`.
- Four rings (25–100 %) and the spokes in `#D9E1D7`, 1 px.
- Previous series: a `#BCCDC5` outline, 1.5 px, dashed on JavaFX and WPF (Flutter's
  canvas has no dash style, so it is drawn solid there). The reference draws only the
  current series (O11).
- Current series: teal fill at about 15 % alpha (`#25867526`), a 2 px stroke and dots of
  radius 3.
- Labels at 1.34 R, clamped to `[35, W−35] × [9, H−9]`, size 11.

**Example.** Week 2026-10-07..13 compared with 09-30..10-06, sets, actual.

```clojure
{:size {:width 300 :height 260} :metric "sets" :unit "sets" :series [:current :previous]
 :scale-max 3.0 :empty? false
 :axes [{:id :chest :label "Chest" :current 3.0 :previous 0.0 :current-level 1.0 :previous-level 0.0}
        {:id :back :label "Back" :current 0.0 :previous 0.0 :current-level 0.0 :previous-level 0.0}
        {:id :shoulders :label "Shoulders" :current 0.0 :previous 0.0 :current-level 0.0 :previous-level 0.0}
        {:id :arms :label "Arms" :current 1.5 :previous 0.0 :current-level 0.5 :previous-level 0.0}
        {:id :core :label "Core" :current 0.0 :previous 0.0 :current-level 0.0 :previous-level 0.0}
        {:id :lower-body :label "Lower body" :current 0.0 :previous 0.0 :current-level 0.0 :previous-level 0.0}]
 :a11y-label "Training distribution, relative load in sets: Chest 3 (previous 0), Back 0 (previous 0), Shoulders 0 (previous 0), Arms 1.5 (previous 0), Core 0 (previous 0), Lower body 0 (previous 0)"}
```

## 5. Set-volume comparison: canonical (`fitness.views/volume-compare`)

The reference `AnalyticsComparison` is built from plain boxes, not a painter, so the
cloview version is canonical too and needs no host code. From top to bottom:

1. **Title.** "Completed training comparison", or "Planned training comparison" in
   planned mode.
2. **Ranges.** "Current {from} - {to} · previous {from} - {to}". The previous range is
   `analytics/adjacent-range`.
3. **Three stat cards**: Active minutes, Sets and Training days. Each shows
   `fmt-quantity(current)` and "{change} vs previous", where the change text follows the
   reference rule:
   - a finite percent gives a sign, the rounded value and `%`;
   - else, if delta > 0: "New";
   - else: "-".

   It is never "∞%" or an invented percentage (DOM-008).
4. **Per-muscle columns.** A horizontal `:ui/scroll` (`volume-compare`) of the 20
   muscles, in dictionary order, keyed by id. Each column holds a previous bar
   (`#BCCDC5`) and a current bar (teal), both bottom-aligned (`:align :end`). Bar height
   is `max(1, 90·v/scale)` px with `scale = max(1, every value)`. The current value is
   written below, then the muscle name.
5. **Note.** The reference text explaining the metric weights.

All 20 rows are shown, as the core returns them (O2).

## 6. Daily calories: `:fitness/calorie-canvas`

**Panel.** "Energy history (kcal)":

- in planned mode, the note "Energy shows completed training only.";
- when `:empty?`, the text "Complete training to view energy." replaces the canvas;
- otherwise the native, inside a horizontal `:ui/scroll`;
- a canonical value line (`MM-DD: value` for each day), with "estimated with the example
  profile" when the profile is the demo's.

**Derivation.** `(domain/daily-calories records profile heart-rate from to
{:heart-rate-index idx})`, with one entry per date of the range (`fitness.dates/date-seq`).
The value is always **actual completed** energy, whatever the mode (DOM-008, EXT-005).

| `:status` | When | `:kcal` | Drawn |
|---|---|---|---|
| `:none` | no completed record that day | 0.0 | a zero-height bar labelled "0" |
| `:known` | the day's kcal is a number | double | a bar |
| `:unknown` | the day's kcal is nil (weight missing or invalid; any unknown record makes the day unknown) | nil | **no bar**: an outlined 8 px placeholder (dashed on JavaFX and WPF) labelled "?", and "unknown" in the accessible text. Never 0. |

**Props** (`fitness.derive/calorie-props`):

- `:size {:width max(320, 40 + 46·n) :height 200}`;
- `:unit "kcal"`;
- `:scale-max`: `max(1, max known kcal)`;
- `:highlight`: the last date, drawn orange like the reference's last bar;
- `:empty?`: every day is `:none`;
- `:example-profile?`;
- `:days`: n × `{:date :label "MM-DD" :kcal :status :value-text}`, where `:value-text` is
  `fmt-whole(kcal)` or "?";
- `:a11y-label`;
- no events.

**Drawing** (`_BarsPainter`):

- `bottom = H−30`, `top = 21`, `cell = (W−28)/n`, five grid lines;
- bar centre at `14 + cell·(i+0.5)`, width `min(30, 0.52·cell)`, height
  `kcal/scale·(bottom−top)`;
- value label at `bottom − h − 11` and date label at `bottom + 17`, both size 10.

**Example.** Week 2026-10-07..13, demo profile (70 kg, marked as an example).

```clojure
{:size {:width 362 :height 200} :unit "kcal" :scale-max 12.25 :highlight "2026-10-13"
 :empty? false :example-profile? true
 :days [{:date "2026-10-07" :label "10-07" :kcal 0.0 :status :none :value-text "0"}
        ;; … 10-08 .. 10-11 the same …
        {:date "2026-10-12" :label "10-12" :kcal 4.8999999999999995 :status :known :value-text "5"}
        {:date "2026-10-13" :label "10-13" :kcal 12.25 :status :known :value-text "12"}]
 :a11y-label "Energy history, estimated kcal per day: 2026-10-07 0 kcal, …, 2026-10-12 5 kcal, 2026-10-13 12 kcal"}
;; profile without :weight-kg -> 10-12 and 10-13 become {:kcal nil :status :unknown :value-text "?"}
```

**Long ranges.** At about 120 days or more, paint only the visible window. The scroll
offset is transient. JavaFX canvases hit texture limits, commonly 8192–16384 px.

## 7. Training calendar: `cloview.core.components/month-grid`

**Decision: a canonical composite, not a native.** The reasons:

- 42 focusable, pressable cells, each with an exact date payload (DOM-009);
- standard disabled states;
- real text for accessibility;
- the existing event path on every host, with no host code.

About 170 nodes is trivial for the reconciler. The composite is generic, so it lives in
`cloview.core` rather than in the example, and it takes the shape returned by
`clogem.fitness.analytics/calendar`.

**Props:**

| Key | Meaning |
|---|---|
| `:calendar` | `{:month "YYYY-MM" :weekdays [7 names] :cells [42 × {:date :day :in-month? :duration-min :sets :planned-items :completed-items :plan-complete?}]}`, Monday first |
| `:selected` `:today` | dates or nil |
| `:kcal` | `{date kcal-or-nil}`. The fitness app joins `daily-calories` over the first..last cell date (`fitness.derive/calendar-kcal`, O10). A day with activity and no number shows "? kcal", never 0. |
| `:on-select` | event vector. A dated cell dispatches `(conj on-select date)`, adjacent-month cells included. Blank cells are disabled. |
| `:on-prev` `:on-next` `:on-today` | navigation event vectors. Previous and next are disabled outside `:min-month "1900-01"` .. `:max-month "2100-12"`. |
| `:title` | header text (the fitness app uses "2026 / 10") |
| `:labels` `:cell-label` | localization overrides; a custom cell text function |
| `:gap` `:cell-height` | 4 and 76 px; every cell has the same size |
| `:test-id` | prefix: `<id>/prev`, `<id>/next`, `<id>/today`, `<id>/<date>` |

**Cells.** Each cell is a `:ui/button`, whose label lists every value on its own line:

```
[13] (today)          day number; brackets mark the selection, "(today)" marks today
2 sets · 2.5 min      only when there was activity
12 kcal               or "? kcal" when the energy is unknown
2/3 done              only when items were planned
```

- Outside the month: opacity 0.55.
- Selected: a 2 px `#3366cc` border, in addition to the brackets.

**Day details** (`fitness.views/day-details`) sit below the grid: a long date title, then
the plans of the selected date with "done" / "open" written out per item, and a **Day
analysis** button that emits `[:analytics/open-date d]`.

Shading cells by active minutes (the reference's `lerp(#EDF4ED, #A6D5BE, sqrt(min/max))`)
needs a custom cell surface. Flutter's button chrome would hide a background. It is tracked
with `:ui/pressable` (O3).

## 8. Media preview

**Panel.** In the catalog detail, `fitness.views/media-preview`:

- a fixed 320 × 180 viewport (`media-preview`) that never resizes with the media
  (EXT-001/002), showing the file path below;
- **Import media** (`:media/pick` → `:file/pick` → `:file/app-dir` → `:file/copy`). The
  exercise then references a **managed copy** under `<app-dir>/media/`, never the
  original. A cancelled pick never erases the previous media.

**GIF and other images: canonical `:ui/image`** with `:src {:file path}` and
`:fit :contain` (implemented):

| Host | GIF animation |
|---|---|
| JavaFX | `ImageView` animates GIFs natively |
| Flutter | `Image.file` animates GIFs natively |
| WPF | `Image` does **not** animate GIFs. `cloview.wpf.gif` decodes local `.gif` files with `GifBitmapDecoder` (`OnLoad`, so the file is not locked) and composes each frame per the GIF disposal rules: `/imgdesc/Left` / `Top`, `/grctlext/Disposal` 2 and 3, the logical screen size. Frames are built with `DrawingVisual` and a clip, rendered to frozen `RenderTargetBitmap`s, and swapped on a `DispatcherTimer` using each frame's delay (0 or 1 cs means 100 ms, like browsers). A GIF whose composed frames would exceed 64 M pixels (width × height × frames, about 256 MB as Pbgra32) plays as a still first frame. The player stops when the image is destroyed or its source changes. |

The fixture `media/synthetic-motion.gif` is 320 × 180, with 32 full-canvas frames, a
90 ms delay, disposal 2 and looping forever. Imported GIFs may use partial frames and
disposal 1 or 3, which is why WPF composes frames.

**MP4: `:ui/native :fitness/media-player`** (specified). Shared code already routes video
files to it: `fitness.derive/media-kind` matches `mp4`/`m4v`/`mov`/`webm`, and
`media-props` builds the props. No host registers a renderer in Stage 2-2, so every host
shows the explicit "not available on this host yet" text inside the fixed viewport.

| Key | Type | Notes |
|---|---|---|
| `:source` | `{:kind :mp4 :path s}` | the managed copy |
| `:viewport` | `{:width 320 :height 180}` | fixed; letterbox inside it |
| `:fit` | `:contain` | never stretch or crop |
| `:autoplay? :loop? :muted? :controls?` | booleans | `:controls?` adds host-native play/pause and seek inside the box |
| `:labels` | `{:empty :loading :missing :unsupported :decode}` | state texts, drawn inside the box |
| `:a11y-label` | string | |
| `:events` | `{:media/error [:media/error exercise-id]}` | dispatched once per source value with `{:path :reason}`, where the reason is `:missing`, `:unsupported` or `:decode` |

**Rules.**

- The box never resizes: placeholder, loading and error states all draw inside it.
- A new `:source` disposes the old player before loading the new one.
- Position and play/pause are transient; guard the seek slider against echo.
- `:dispose` stops playback and releases the player and its file handle.

**Host plan.**

| Host | Video | Release / errors |
|---|---|---|
| JavaFX | `Media` + `MediaPlayer` + `MediaView` (javafx-media), `setCycleCount(INDEFINITE)` | `setOnError` → error state; `dispose()` |
| WPF | `MediaElement` (`LoadedBehavior=Manual`, `Stretch=Uniform`); loop on `MediaEnded` | `MediaFailed` → error state; `Stop`, `Close`, `Source=null`. Needs Media Foundation, so Windows N editions report it as unavailable. |
| Flutter | `video_player`, which has no first-party Windows implementation: use `video_player_win` or `media_kit` (O7) | dispose in `State.dispose` and when the source changes |

**Example.**

```clojure
{:source {:kind :mp4 :path "<app-dir>/media/press-1792000000000.mp4"}
 :viewport {:width 320 :height 180} :fit :contain
 :autoplay? true :loop? true :muted? true :controls? true
 :labels {:empty "No media" :loading "Loading media" :missing "Media file is unavailable"
          :unsupported "This format is not supported on this device"
          :decode "The media file could not be decoded"}
 :a11y-label "Band Chest Press demonstration video"
 :events {:media/error [:media/error "press"]}}
```

## 9. Open questions

| # | Question |
|---|---|
| O1 | Radar partition: it is not in the package. Confirm it against the original app, especially the three † muscles. |
| O2 | Volume comparison: hide all-zero rows? The core returns 20; the reference gallery showed 4. |
| O3 | Add `:ui/pressable` (a pressable surface with arbitrary content) as a canonical tag? It would allow shaded calendar cells and pressable comparison columns. The cost is a `hostops.cljr` branch, a `->desc` method and a Flutter adapter. |
| O4 | Native event transport: keep D3 (events as data in props), or add a core extension, where `:on-*` keys on `:ui/native` get a stable `emit` passed to `:create`/`:build`? The extension would reuse cloview's trampolines and the WPF re-entrancy guard. |
| O5 | The muscle selection persists across range, metric and mode changes. Should a range change clear it? |
| O6 | Default body style: 2D (chosen; implemented and more accessible) or 3D (the reference default)? |
| O7 | Flutter MP4 on Windows: `video_player_win` or `media_kit`? EXT-002 needs real decoding on every claimed host. |
| O8 | GPU 3D on JavaFX/WPF changes the shading and needs `SCENE3D` or hardware tiers. Keep the software painter as the fallback? |
| O9 | A day whose records were absorbed by overlap is `:known` with 0.0 kcal. Should it be `:none`? |
| O10 | Calendar kcal joins `daily-calories`, which the core `calendar` does not do. Does the original app do the same? |
| O11 | The radar's previous-period overlay is not in the reference. Keep it? |

## 10. Where this is tested

| Test | What it checks |
|---|---|
| `apps/fitness/shared/test/fitness/app_test.cljc` (JVM, CLR, Dart) | every screen renders without tree problems; an identical re-render costs zero host operations; natives without renderers are explicit placeholders; media routing by kind; ARCH-004 counters unchanged by selection, style and tab changes; energy statuses `:known` / `:unknown`; calendar navigation and bounds |
| cloview-core's `modules/core/test/cloview/core/components_test.cljc` | chip-group and month-grid structure; one event per toggle; the reconciler round trip (a user toggle writes nothing back; an external change is one patch; echoes are swallowed) |
| `apps/fitness/javafx/src/fitness/javafx/smoke.clj` (JavaFX, off-screen) | 21 checks: chips, natives mounted, body tap selects `pectoralis-major`, background tap emits nothing, no recomputation, calendar select and navigation, the exact logical event sequence |
| `apps/fitness/wpf/src/fitness/wpf/main.cljr` (WPF, off-screen) | the same scenario on WPF |
| `apps/fitness/flutter/test/fitness/flutter/fitness_test.cljd` | the fitness shell widget test and DOM-001 on Dart |
