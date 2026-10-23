# Architecture

Intended chain: domain model → local storage → local map representation →
explicit publishing adapter → verified ATAK/CoT route.

Implemented: core/ with plain Java for drafts, validation and distances.
app/ contains a guarded offline local planner prototype with list/forms/review and
a private feature layer, plus the separate fixed-fixture CoT lab tool. The earlier
disposable map experiment established candidate SDK signatures.
See [LOCAL_MAP_PROBE.md](LOCAL_MAP_PROBE.md) and [ADR 0003](decisions/0003-local-feature-layer-experiment.md).
A separate synthetic CoT experiment exercises explicit single-recipient transport and
host marker detail import/re-export; see [COT_ROUND_TRIP.md](COT_ROUND_TRIP.md).
The [offline planner adapter](LOCAL_PLANNER.md) calls the versioned Java-core
[local snapshot store](LOCAL_STORAGE.md) in the host's private no-backup directory.
Normalized [local images](LOCAL_IMAGES.md) are integrated. The guarded
[metadata-sharing lab](METADATA_SHARE_LAB.md) serializes fresh saved-record reads,
review freshness checks and explicit one-recipient dispatch on the planner worker.
Its strict experimental wire codec is separate from snapshot-v1. Recipient inspection
uses actual SDK marker import/export without writing received data to DraftStore.
Production connected privacy and publishing remain pending.
Direct Data Sync integration is deferred because no applicable plugin API contract
is available in the inspected inputs. Mission Package APIs are documented SDK
candidates; the separate [attachment investigation](DATA_SYNC_AND_ATTACHMENTS.md)
does not implement a TMM image adapter or feed publication.

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

The isolated lab uses TAK Server 5.8-RELEASE-92. The operational server version remains
unknown; lab transport evidence does not establish operational compatibility.

## Data Sync investigation — 2026-10-23

[The integration investigation](DATA_SYNC_INTEGRATION.md) distinguishes a candidate
user-mediated export into Data Sync's chosen feed from a separate Mission API
adapter. Neither is implemented. [ADR 0010](decisions/0010-investigate-data-sync-workflows.md)
is proposed for review; private storage/features and experimental wire contracts
remain unchanged. Data Sync auto-publication and queued offline sends need explicit
#4/#7 testing before any published MapItem representation is introduced.
