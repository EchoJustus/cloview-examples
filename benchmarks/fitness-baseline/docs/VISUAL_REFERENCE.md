# Visual reference and provenance

These four PNG files are **English-localized component references derived from
the existing Clogem Fitness 0.3.0 Flutter UI**. They are not screenshots of a
released English application, a new cross-host implementation, or real user data.
Every image contains a visible derivative-reference banner.

## What is reused and what is new

The files under `reference-ui/lib/` copy the real baseline's `MuscleBody`,
`TrainingRadar`, `CalorieBars`, `TrainingCalendar`, `AnalyticsComparison`, and
`FitnessShell` implementation. English labels, ASCII punctuation and a test font
replace the original localized text. Painting geometry, state, callback wiring,
layout constants and numeric logic are retained. Source-relative locations and
source SHA-256 values are in `visuals/source-provenance.json`.

The gallery composition, page headings, selected-day card and presentation data
in `reference-ui/test/reference_gallery_test.dart` were created for this package.
The harness composes the real components, supplies callbacks, and captures their
rendered output. It does not run the complete application, persistent state,
ClojureDart compiler, media player, Health Connect or network synchronization.
Navigation and date/month callbacks are observed as events; they do not load a
new application page or calculate a new month inside this small harness.

## Image index

| File | Dimensions in pixels | Reference content |
|---|---:|---|
| `visuals/desktop-analysis-2d.png` | 1600 x 1100 | Fixed front/back muscle regions, radar, daily energy and period/set comparisons |
| `visuals/desktop-analysis-3d.png` | 1600 x 1100 | Same data and layout with the original procedural 3D body |
| `visuals/desktop-calendar.png` | 1440 x 1000 | Monday-first 42-cell month, selected date, completion markers and day details |
| `visuals/mobile-calendar.png` | 390 x 1180 | Same calendar component inside the narrow shell, drawer entry and vertically stacked day details |

The mobile image uses an extended capture viewport so the full calendar and day
details can be inspected. It does not claim that every phone has this height.
The pixel ratio is 1.0 for every capture. Different hosts may use their native
font metrics, widgets, iconography, window chrome and responsive navigation.

## Synthetic presentation values

The current range is October 10-16, 2026; the equal-length previous range is
October 3-9. Current values are 18 active minutes, 5 completed sets and 2 training
days. Previous values are 12 minutes, 3 sets and 1 day. Current weighted muscle
sets are chest 3, back 2, triceps 1.5 and biceps 1. These weighted muscle values
must not be summed to obtain the physical set count.

October 14 has two completed sets and 6 minutes; October 16 has three completed
sets and 12 minutes. The latter date is selected. October 19 illustrates an
uncompleted scheduled plan. The baseline components format numbers for display;
the caloric bars round to whole values and calendar cells use one decimal when
needed. In the synthetic data, 12 * 4.2875 has a floating-point representation
just below 51.45 and is displayed as 51.4 by the calendar. A framework acceptance
implementation should compare numeric values with the tolerances specified by
the business fixtures, not demand identical textual rounding at midpoint cases.

These values exist to make layouts inspectable. They are **not** the business
oracle suite. Use the package's `fixtures/` inputs and expected outputs for
cross-host business-rule acceptance. No personal portrait, original exercise
GIF, private health data or embedded system font is included here.

The exact gallery inputs are in `visuals/presentation-data.json`, including
the analytics/calendar payload, body loads, radar values, selected date and
daily energy values. This is deliberately a separate presentation fixture from
`fixtures/demo-document.json`; do not use its screenshot values as expected
results for that business document.

## How to use the references for acceptance

Compare information hierarchy and behavior: visible labels, legible values,
reachable actions, selected/disabled states, grouped controls, scroll access,
month/date callbacks, muscle selection and preserved state when switching views.
Do not compare raw pixels across JavaFX, Flutter and CLR. The screenshots do not
require another host to recreate Flutter Material widgets or the exact mesh.
A native drawing component or a documented equivalent visualization is valid
when it meets the corresponding capability and event contract.

The sidebar-to-drawer threshold shown here is 760 logical pixels, a baseline
implementation detail. Other hosts can use suitable adaptive navigation while
retaining all required routes and logical events. The body image is a training
region schematic, not medical anatomy or a recovery measurement.

## New rendering verification performed for this package

One Flutter widget test passed on October 23, 2026. It rendered all four images,
asserted that no widget exceptions occurred, checked 3D-to-2D switching, selected
the pectoralis-major region through the actual 2D hit test, checked 42 calendar
cells, asserted the selected-date callback emitted exactly one value, verified
the next-month value, and opened the narrow drawer and asserted one navigation
event. These are local Flutter component results, not JVM/CLR results or a new
performance benchmark. The images were also visually inspected for readable
English, loaded icons, major clipping and consistency of displayed data.

See `visuals/render-verification.json` for the machine-readable capture record.

An independent package audit on October 24, 2026 rechecked all four images,
their dimensions and English labels, the three original source hashes, the
declared text/font substitutions, and every calendar/body/radar/energy input
against the gallery construction. PNG inspection found no EXIF and only the
standard sRGB rendering intent. The source provenance now includes derivative
hashes, and the rendering dependency lockfile is included. This audit did not
rerun the rendering test or establish new host interoperability.
