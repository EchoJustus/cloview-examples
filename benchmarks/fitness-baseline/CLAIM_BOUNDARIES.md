# Claim boundaries

## Existing baseline versus future framework

The existing source application uses ClojureDart/Flutter on Windows and Android. Its business core is separated into `.cljc` files. No JVM/cljfx or ClojureCLR fitness application has been built or verified by this package. Reader conditionals and familiar Clojure APIs are not proof of support by every host/compiler.

The complete original application is not exported here. The selected English core and UI components are reference derivatives, with provenance and focused checks. The future framework must supply host adapters, state/effect wiring, packaging and runnable sample applications.

## Evidence levels

| Label | Meaning |
| --- | --- |
| HISTORICAL_BASELINE | Recorded source-application checks from the 0.3.0 verification record, mostly 2026-10-16 through 2026-10-18 |
| PACKAGE_CHECK | A check performed while assembling this English benchmark; use its recorded result and scope |
| DERIVED_REFERENCE | English-localized or synthetic material derived for evaluation; not a production release screenshot or a real user's data |
| PROPOSED | An acceptance requirement for the future framework; no existing pass is implied |

Future acceptance results start NOT_RUN. Never copy historical Flutter passes into another host's result column. Never describe a reference render as a completed port.

## Preserved limitations

- The final Android APK previously installed and launched on an Android 14 emulator. Native Health Connect read permission and an empty query succeeded. Zero samples were returned; import/export remained disabled.
- The final emulator-to-Windows pairing attempt did not complete because automated text entry altered the invitation. There were zero paired devices and zero sync commits in that attempt. The service and emulator were stopped afterward.
- Seventeen real loopback-socket tests and one UI-bridge/socket test were recorded as passing. These do not establish real Android-to-Windows interoperability on a user's phone, router or firewall.
- Actual Samsung Health records, nonempty native Health Connect queries, older-history access, real-phone document export and full physical-device synchronization remain unverified in the source baseline.
- Windows release startup and package checks are historical evidence of that artifact, not a portability guarantee for a new adapter.
- Cache and painter reuse were implemented and functionally checked. There is no published before/after latency or frame-time benchmark establishing a quantified performance improvement. Measure the future hosts separately.
- The original app's UI is primarily Chinese. English screenshots in this package are newly rendered localized component derivatives. They do not assert that the original release includes an English language setting.

## Domain limitations

The body model has 20 selectable training regions and is a schematic, not a medical anatomy model. Primary muscle load has weight 1.0 and secondary load 0.5. Colors and radar values represent those application-defined loads, not measured muscle growth, fatigue or recovery.

Energy is a coarse estimate based on MET, body weight and active time, with bounded optional heart-rate/frequency adjustments. It is not a clinical or physiological measurement. A planned target heart rate is never an observed heart-rate sample. The fixtures preserve these rules for software testing and make no health recommendation.

Completion time, active interval duration, planned duration and scheduled time are distinct. Manual backfill is explicitly labeled. Incomplete/open intervals are not silently treated as measured completed activity.

## Export limits

No personal records or original portrait-based media are included. All exercise names, plans, timestamps and heart-rate samples created for the benchmark are fictional. The package contains no live pairing invitations or trusted-device keys. Source attribution is preserved using project-relative paths and hashes, without local usernames or workstation paths.

Mechanical English/metadata/hash checks cannot prove anonymity, software correctness, or permission to redistribute dependencies. This is a locally reviewed benchmark package for the user's stated handoff.
