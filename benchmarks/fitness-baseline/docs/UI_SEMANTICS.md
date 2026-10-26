# UI semantics and native rendering freedom

## Contract rather than a proposed DSL

The descriptions and event names here are neutral benchmark vocabulary. They are not a mandated EDN schema, macro grammar, framework API, or distribution architecture. A recipient may choose runtime view data, compiled forms, functions, macros, or a hybrid after research. It must show how one shared application declaration and business layer produce the same meaningful controls and outcomes on each required host.

Names such as `exercise/save` below denote logical events for recording acceptance traces. Map them to the chosen API and document the mapping. The raw native callback signatures may differ. The normalized event meaning and resulting business state may not.

## Minimum control meanings

| Meaning | Required semantics | Native freedom |
|---|---|---|
| Text/label | Correct content, association with data/control, visible wrapping or reachable overflow | Native fonts and text layout |
| Button | One activation delivers one logical command; disabled activation delivers none | Native button style and focus outline |
| Text input | Editable value and change/commit event; labels and validation are reachable | Native editor/IME and border |
| Numeric input | Validate finite values and domain ranges; preserve units | Text field or native numeric editor |
| Single choice | Exactly one selected value from supported options | Dropdown, radios, or segmented controls |
| Multiple choice | Stable set of chosen IDs, independent labels, explicit clear behavior | Checkboxes, chips, or a selection list |
| Switch/checkbox | Boolean state and boolean event, not inverted on host change | Native switch or checkbox |
| List/repeated rows | Stable item identity after insertion, deletion, filtering, and reorder | List, table, or card view |
| Row/column/grid | Predictable grouping, order, alignment, spacing, and resize behavior | Native layout primitives |
| Scroll | Hidden content remains reachable; nested scrolling does not make a page blank | Native scrollbars and gestures |
| Dialog/editor | Open, validate, save, cancel, and focus return have consistent state effects | Native modal or equivalent routed editor |
| Image/media | Defined intrinsic content, fitting, stable viewport and failure state | Native image/video backend |
| Feedback | Distinct busy, success, empty, unsaved, and failure states | Banner, status area, or inline feedback |

Native tabs are allowed for view or page choices but are not a requirement to replace every existing navigation element. A framework must separately document which primitive vocabulary it actually supports.

## Shared style subset

At minimum the benchmark uses semantic roles for primary action, normal text, secondary text, background, surface, selection, error, and disabled content. It uses font-size hierarchy, padding/gap, bounds, alignment, border/shape, and horizontal/vertical composition. Hosts may round dimensions and use platform themes. Exact shadow blur, typography metrics, shader effects, icon artwork, and identical animation timing are outside pixel parity.

A style must not change a button into an unlabelled decoration or hide a state change. Color-only distinction is insufficient for selection, errors, or a completed item; include text, checked state, an icon with a name, or an equivalent accessible indication. This explicit accessibility gate is a prospective framework acceptance requirement; the historical app tests do not certify a full screen-reader audit.

## Screens and logical controls

### Library and exercise editor

- Search input: `catalog/query-change`, string value.
- Muscle, intensity, equipment multiselect: `catalog/filter-change`, dimension and selected IDs/tags.
- Clear all: `catalog/clear`, clears visible text and all dimensions.
- Exercise rows/cards: stable exercise ID, name, primary/secondary muscles, equipment, intensity, notes/media access.
- Add/edit: `exercise/open`, optional existing ID; save: `exercise/save`, validated draft; cancel: `exercise/cancel`.
- Media chooser: `media/select`; cancellation is not a failed import and cannot erase the previous media.
- Add to plan: `plan/add-exercise`, chosen exercise ID and target plan ID.

### Plan and active training

- Plan date/name editor and ordered item list.
- Each row is keyed by plan/item identity, not exercise ID or list index.
- Item settings expose duration, repetitions, target HR, and optional scheduled time.
- Duplicate, move up/down, remove: `plan/item-copy`, `plan/item-move`, `plan/item-remove`.
- Start, pause, resume, complete: `training/start`, `training/pause`, `training/resume`, `training/complete`.
- Backfill: `training/backfill`; its visible duration source distinguishes planned from measured time.
- Active status survives page navigation. Busy/disabled states prevent impossible second starts or duplicate submissions.

### Records and import preview

- Completed records expose date, effective duration, duration source, repetitions, snapshot identity, and estimated calories.
- HR file selection opens a preview with valid/invalid/duplicate counts and timezone interpretation before `heart-rate/import-confirm`.
- UTC offset input is minutes. Explicit-offset inputs are unaffected by this field.
- Saving, saved, unsaved, and retry are distinct states. Failed persistence cannot be presented as a completed import.
- Export triggers a native effect; cancellation leaves records intact.

### Calendar

- Month title, previous/next month, today, 42 Monday-first date cells.
- Each date cell exposes date, completed sets, duration, kcal when known, and planned/completed item progress.
- `calendar/select-date` selects the exact represented local date, including adjacent-month cells where valid.
- Selected-day details provide plans and `analytics/open-date`.
- Boundary navigation outside the supported UI year range is disabled. Blank/invalid cells emit no date selection.

### Analytics

- Inclusive date range with validation and preset ranges.
- Actual/planned single choice, weighted-minutes/sets single choice, 2D/3D style choice.
- Current/previous equal-period summary and per-muscle comparison use the same payload.
- Body selection: `body/select-muscle`, stable muscle ID; view choice: `body/style-change`.
- 3D orientation is transient view state. Changing it must not change a training record or aggregate.
- 2D front/back muscle regions and 3D surfaces map to the same IDs.
- Radar and daily calories expose units and meaningful values; a table or accessible data summary may accompany a native chart.
- Empty data has an honest empty/zero/unknown representation. Zero prior totals never render infinite percent.

### Profile and optional integrations

- Profile editor shows weight units, resting/max HR, and an explicit HR-adjustment switch. Example defaults are labelled.
- Generic async operations expose busy/disabled state, result, error, and cancellation without moving domain logic into each renderer.
- Capability-gated pages explain unsupported functionality. A disabled unavailable Health Connect action is acceptable on non-Android hosts; a fake success is not.

## Layout acceptance

Use 1600 x 1100 logical pixels for a wide reference and 390 x 844 for a narrow reference where the host supports resizing. Test a 320-pixel-wide component host for calendar/comparison. These are benchmark test viewports, not minimum device specifications. Desktop hosts may declare a documented minimum window size and use an alternative native arrangement at that size; claiming narrow-screen support requires the narrow checks.

Grouping and reading order must be recognizable. All required controls must remain reachable without overlap or clipping that prevents their use. Scrolling a page must not detach an editor from the wrong item. A drawer, sidebar, menu, or tabs can all be valid navigation choices. Do not grade differences in window decorations, system dialogs, font rasterization, or pixel positions as failures.

## State, lifecycle, and native-child probes

Stable keys must prevent text, focus, selection, media, and event handlers from attaching to the wrong row after reorder. Re-rendering the same view must not accumulate event handlers. Removing a view must dispose its subscriptions, timers, media, and foreground service effects or transfer ownership explicitly to a longer-lived application scope.

These lifecycle and native-child probes extend the existing app evidence and are marked PROPOSED in the case catalog. A prospective split-view test embeds a native child next to a shared button/list. Moving its divider updates only presentation; activating either child sends the correct shared event once; removing it releases its resources. The fitness app does not already claim a split-view screen.

The extension renderer may be host-specific. The extension's declared inputs, output events, error reporting, and ownership must be inspectable. A screenshot alone cannot prove those contracts.
