# Architecture

Intended chain: domain model → local storage → local map representation →
explicit publishing adapter → verified ATAK/CoT route.

Implemented: core/ with plain Java for drafts, validation and distances.
app/ contains a loading probe whose lifecycle passed in the SDK developer emulator,
plus an offline synthetic feature-layer experiment compiled against SDK 5.6.0.24.
See [LOCAL_MAP_PROBE.md](LOCAL_MAP_PROBE.md) and [ADR 0003](decisions/0003-local-feature-layer-experiment.md).
Storage, production map adapters, attachment transport and networking code do not exist yet.

Accepted SDK target: ATAK-CIV API 5.6.0 ([ADR 0002](decisions/0002-atak-5.6-target.md), 2026-10-12).
The supplied SDK directory is labeled full build 5.6.0.24 and contains an app, guide and JARs;
the phone is reported as full build 5.6.0.12. Neither identical API versions nor these labels
prove build compatibility. Developer-host loading is proven; feature-layer experiments do not establish
production compatibility or transmission privacy.
The official HelloWorld on main is a reference example, not an established
5.6.0 contract: https://github.com/TAK-Product-Center/atak-civ/blob/main/plugin-examples/helloworld/app/build.gradle
SDK source: https://tak.gov/pages/sdks

Stage 0 must show that local map representations are not transmitted, that UUIDs and metadata
survive a round trip, that the sharing target is selected manually and how attachments are transported.
An ordinary CoT event is not assumed to become a Data Sync feed.

The TAK Server version is unknown and does not block the initial local plugin build.
Record it before transport experiments; the two-client sharing gates remain required.
