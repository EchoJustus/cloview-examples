# Re-render the English component references

This optional Flutter-only gallery preserves baseline components as a visual
reference. It is not the proposed multi-host framework or a recommended DSL.
See `../docs/VISUAL_REFERENCE.md` for exact provenance and limits.

Validated toolchain: Flutter 3.29.3 / Dart 3.7.2. The copied components use
Flutter APIs from this version. Use a clean working copy of this directory so
build artifacts remain outside a frozen acceptance package.

1. Install or select a compatible Flutter SDK, then change into `reference-ui/`
   in the separate working copy.
2. Run `flutter pub get`. An already populated cache permits `--offline`.
   `pubspec.lock` records the dependencies used for the reference rendering;
   keep it when reproducing the original Flutter 3.29.3 run.
3. Set `BASELINE_FONT_PATH` to a locally installed Latin TTF font. The reference
   capture used Segoe UI. Fonts are not redistributed in this package.
4. Set `BASELINE_ICON_FONT_PATH` to the SDK's
   `bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf`.
5. Optionally set `BASELINE_OUTPUT_DIR` to a separate output directory. If
   omitted, captures are written to `../visuals` relative to the working copy.
6. Run `flutter test test/reference_gallery_test.dart --reporter expanded`.

The test loads both fonts explicitly because widget tests otherwise substitute
test fonts. Changing fonts can alter wrapping and rasterization; that is not a
semantic acceptance failure. The test supplies fixed synthetic October 2026
presentation values, with no I/O beyond reading fonts and writing screenshots.
It does not read personal application storage or reach a network service.

The shipped image files were rendered using the test in this directory. The
three files in `lib/` are English-localized derivatives, not untouched source.
The source project SHA-256 provenance is recorded in
`../visuals/source-provenance.json`.
