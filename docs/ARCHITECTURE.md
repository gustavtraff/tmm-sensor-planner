# Architecture

Intended chain: domain model → local storage → local map representation →
explicit publishing adapter → verified ATAK/CoT route.

Implemented: core/ with plain Java for drafts, validation and distances.
app/ reserves space for an Android plugin verified against the SDK. Storage,
ATAK adapters, map objects, attachment transport and networking code do not exist yet.

SDK target: ATAK-CIV 5.5.0. No integration APIs have been proven.
The official HelloWorld on main is a reference example, not an established
5.5.0 contract: https://github.com/TAK-Product-Center/atak-civ/blob/main/plugin-examples/helloworld/app/build.gradle
SDK source: https://tak.gov/pages/sdks

Stage 0 must show that local map representations are not transmitted, that UUIDs and metadata
survive a round trip, that the sharing target is selected manually and how attachments are transported.
An ordinary CoT event is not assumed to become a Data Sync feed.
