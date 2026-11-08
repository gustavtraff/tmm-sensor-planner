# Architecture

Current chain: private domain/local-v2 storage and image copies → local TMM feature
layer → separately reviewed host-copy handoff/native Data Sync publication; and
explicit configured Mission snapshot → durable metadata inbox → reviewed local-v2
application. The private layer is not replaced by ordinary shared MapItems.

The domain/store remain plain Java. The ATAK adapter owns forms, MGRS/map placement,
image inspection, lifecycle and source guards. SDK host copying/updating requires a
fresh saved selection and explicit review. Native Data Sync's own feed/confirmation
UI controls publication; an SDK persist/readback is not delivery acknowledgment.
[Signed-v28 evidence](SIGNED_V28_METADATA_ROUND_TRIP.md) records the bounded
standard-client metadata round trip, native publication prompt and No/retry behavior.
Host-copy publication remains isolated-loopback-only, with no operational fallback.

[ADR 0012](decisions/0012-explicit-mission-snapshot-intake.md) records the owner's
accepted explicit snapshot receive route. The supported SDK supplies strict TLS for
an already configured connected profile; the operator chooses the separate API port
and returned mission GUID. Complete bounded metadata validates before existing-format
inbox intake. Fetch never auto-applies, deletes missing records or uploads private
images. Separate preview/Save retains conflict/decision evidence and preserves local
image handles. Source means configured logical service plus mission membership,
not author identity, complete server history or a continuous permission lease.

No supported private Android Data Sync caller contract is claimed or invoked.
The Mission API adapter is GET-only; a direct REST publisher and actual image-feed
association/adoption remain separate work. [The native source investigation](INGRESS_SOURCE_VERIFICATION.md)
explains why importer/logger hints are not promoted to trusted inbox writes.

The historical [local map probe](LOCAL_MAP_PROBE.md), [synthetic CoT experiment](COT_ROUND_TRIP.md)
and [metadata-sharing lab](METADATA_SHARE_LAB.md) retain their experimental limits.
They are not substitutes for the current configured receive contract or operational
release approval. [NEXT_STEPS](NEXT_STEPS.md) separates implemented components from
remaining physical, server, destination and image-transport gates.

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
adapter. The dated investigation below predates the current receive decision; see ADR 0012 above. [ADR 0010](decisions/0010-investigate-data-sync-workflows.md)
is proposed for review; private storage/features and experimental wire contracts
remain unchanged. Data Sync auto-publication and queued offline sends need explicit
#4/#7 testing before any published MapItem representation is introduced.
