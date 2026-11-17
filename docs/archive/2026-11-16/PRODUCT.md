# Product

Planners create a local TMM device, review it and share it manually. Deployers
update the same stable identity. The accepted target is ATAK-CIV API 5.6.0; the phone full build is reported as 5.6.0.12.
The original API 5.5.0 target is superseded by [ADR 0002](decisions/0002-atak-5.6-target.md),
accepted by the user on 2026-10-12. HV Reports 1.1.9 [5.5.0] coexistence remains unverified.

Requirements F01–F12 and N01–N08 are defined in the [project plan](PROJECT_PLAN.md#3-requirements).
The MVP includes drafts, storage, a map view, forms, bearing, distances, a local image and
review. Sharing may only be added after an approved technical investigation.
Hardware control, telemetry, optimization, radio/coverage modeling and a dedicated server
are outside the first version. Distance says nothing about radio links or terrain.

The initial core implements parts of F01/F02/F03/F07/F08, without a user interface.
Accepted display names: Mini, Scout, PIR and Gateway, selected on 2026-10-12.
See [the device catalogue](DEVICE_CATALOGUE.md). On 2026-10-16 the owner confirmed
the five statuses (planned, deployed, active, retrieved, cancelled), manual status
changes, and distance candidates restricted to planned/deployed/active records.
The visible device ID is entered manually to match its real label, for example
synthetic `PIR 134`; no automatic numbering or forced naming format is intended.
The owner also confirmed one local JPEG/PNG, at most 10 MiB input, stored as a
separate copy without EXIF/embedded metadata; sharing remains deferred.

MVP defaults for owner text, duplicate warnings, manual deployment time, true-north
bearing, original symbols and bounded image decoding are recorded in
[ADR 0006](decisions/0006-local-mvp-product-rules.md), adopted by human merge of PR #28. Exact Mini
manual-symbol identification, sharing targets and editing received objects remain
outside those local defaults. Existing core implementation limits are recorded in
[the data model](DATA_MODEL.md).

The core now implements validated local edits and accepted status-filtered distances.
A [local snapshot store](LOCAL_STORAGE.md) now persists the core model and passes
separate-process reopen tests. The [guarded offline prototype](LOCAL_PLANNER.md) connects Android storage, forms,
review and local map selection on API 26+, including normalized local images.
The [manual metadata-sharing lab](METADATA_SHARE_LAB.md) adds explicitly reviewed
saved metadata to one isolated SDK-lab recipient; images are excluded. Production
sharing and physical-phone restart/field acceptance remain open.

## Form entry (#51)

Keyboard Done completes editing without advancing focus or saving. Deployment time
is optional and manually selected with local date/time controls; choosing a status
or saving a Planned draft never supplies a timestamp. An empty selector starts at
now when opened, while existing values are preserved until explicitly replaced or
cleared. Both selector confirmations are required; cancellation changes nothing.
The timezone and selected UTC offset are displayed. Storage remains a UTC Instant
with no schema change. [FORM_ENTRY.md](FORM_ENTRY.md) records minute precision for
new selections, daylight-saving handling and actual-client verification limits.

## Local physical evidence — 2026-10-22

The owner reports the signed version 15 offline phone workflow working, including camera images, zoom, MGRS/map movement and 6400-mil bearing. See [PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md) for scope and limitations. Earlier statements about pending local physical acceptance are historical; production connected privacy, sharing, import/conflicts, image delivery and full coexistence remain open.
