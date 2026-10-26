# Recorded application evidence

Source: the application's `docs/verification.md`, Android final acceptance record, and release metadata. These are historical records, not new executions of the original release during package preparation. Verification dates for 0.3.0 were 2026-10-16 through 2026-10-18.

| Area | Recorded result | Boundary |
| --- | --- | --- |
| Pure business core | 37 tests, 213 assertions passed | Does not prove every target Clojure host |
| ClojureDart state | 11 tests passed | Original generated Dart/state integration |
| Body widgets | 6 tests passed | Flutter selection, 2D/3D and drawing behavior |
| Calendar/comparison | 4 tests plus 1 visual render passed | Flutter components and small-width layout |
| Full application | Existing flow 1, catalog flows 4, new v3 flow 1 passed | Original application; synthetic test data |
| Health Connect page | 14 tests passed | Includes native-shell layout via test bridge |
| Storage and HR parser | 12 tests passed | Original local persistence and import parser |
| Android plugin | 18 Kotlin tests passed | Plugin behavior, not real Samsung data |
| LAN protocol | 17 real loopback-socket tests passed | Two isolated host directories; not two physical devices |
| Sync page/bridge | 1 real-socket bridge test passed | Concurrent local edit retry and persistence |
| Static checks | No errors, 4 generated redundant-null warnings | Warnings were retained, not edited away |
| Windows release | Version 0.3.0+3 startup and first frame passed | Clean data: 25 sample exercises, zero completed records |
| Android release | Version 0.3.0/3, package/signature/ABI checks passed | Local development signing key; not store release |
| Native Android emulator | Installed, launched, HR read permission granted, empty query succeeded | No nonempty native HR dataset verified |
| Emulator to host LAN | NOT COMPLETED, zero pairs and zero commits | Invitation text was altered by automated entry |

## Frozen artifact identifiers

The original binary artifacts are not included in this English benchmark. These digests identify the source release independently of a developer machine path.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Windows 0.3.0 portable ZIP, 2026-10-18 build | 38648633 | e3accb067cbd3377bbafea792ca619fa2e3dbc2ce8aa7f25fbeb837077e19db6 |
| Android 0.3.0 final APK, 2026-10-16 build | 102400811 | 2b4136c97e02b982aab1104e434a7db475bec88e3165a08fe9829aa28a69cdad |

Android supported API 28+, with ARM64, ARMv7 and x86_64 in the recorded artifact. This describes that build, not a recommendation about the future framework's minimum SDK.

## Package-specific evidence

`evidence/` records checks actually run on this English derivative package. Read each result's scope. New fixture-oracle checks establish expected reference outputs; localized Flutter gallery checks establish reference rendering. Neither establishes a completed JVM or CLR application.

All future host acceptance results remain NOT_RUN until separately executed and documented. The original source has implemented caches and reusable body painters, but no numeric performance improvement claim is inferred from those changes alone.
