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
The configured-publication trial adds an explicitly reviewed session boundary;
see [its approved contract](CONFIGURED_PUBLICATION_TRIAL_CONTRACT.md).

[ADR 0012](decisions/0012-explicit-mission-snapshot-intake.md) records the owner's
accepted explicit snapshot receive route. The supported SDK supplies strict TLS for
an already configured connected profile; the operator chooses the separate API port
and returned mission GUID. Complete bounded metadata validates before existing-format
inbox intake. Fetch never auto-applies, deletes missing records or uploads private
images. Separate preview/Save retains conflict/decision evidence and preserves local
image handles. Source means configured logical service plus mission membership,
not author identity, complete server history or a continuous permission lease.

No supported private Android Data Sync caller contract is claimed or invoked.
The Mission API adapter is GET-only; a direct REST publisher and active local-image adoption remain separate work.
[Selected-feed image intake](SELECTED_FEED_IMAGE_INTAKE.md) uses the approved
container/stage/association contracts; the received viewer does not replace active images. [The native source investigation](INGRESS_SOURCE_VERIFICATION.md)
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

The isolated lab uses TAK Server 5.8-RELEASE-92. The target server was reported as a TAK Server 5.6 release on 2026-11-08;
its exact full build and physical-client compatibility still require confirmation.
