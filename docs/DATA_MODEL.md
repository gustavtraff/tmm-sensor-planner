# Data model

The [connected local trial](CONNECTED_LOCAL_TRIAL.md) changes session access only.
It adds no persisted consent, transport fields or migration; snapshot v1 and opaque
local image handles are unchanged. Connected trial drafts remain local drafts.

The core's initial model is a local draft (schemaVersion 1), with versioned local snapshot
storage; no production CoT standard yet. The full proposed record is in project plan §5.

UUID is the stable identity. displayId and owner are required, trimmed and must
not be empty. Visible IDs are manually entered to match the device label (synthetic
example: `PIR 134`), without automatic numbering or an enforced naming format.
ID uniqueness across a working area is not implemented; UUID remains the record identity.
Position: finite degrees, latitude [-90,90], longitude [-180,180].
createdAt/updatedAt: UTC Instant; updatedAt must not precede createdAt.
Optional deployedAt: actual time, not automatically derived from status.
Bearing: optional for PIR/Scout, finite [0,360), clockwise from true north; Gateway and Mini
reject bearing. The local UI uses whole 6400-circle mils and retains untouched
exact degree values; [DEVICE_DETAILS.md](DEVICE_DETAILS.md) defines conversion,
rounding and the equivalent 0/6400 boundary. This is not a snapshot/wire-unit change.
Comment: at most 2000 UTF-16 code units (Java String.length()).
Image reference: optional local reference; no image blob, file handling or sharing.
It is not a wire field or recipient-readable path. Keep it out of CoT and package
manifests; a future attachment needs separate content identity and verified bytes.
The [attachment investigation](DATA_SYNC_AND_ATTACHMENTS.md) tests a standalone
synthetic PNG only; its package UID does not establish a TMM image association.
Revision: positive local revision number, with no synchronization/acknowledgment claims.

`TmmDraft.edit(...)` returns a validated new instance, preserving UUID and createdAt,
setting updatedAt from the supplied clock instant and advancing revision by one.
All editable fields are explicit inputs; no status change derives/clears deployedAt,
bearing or an image handle. All five statuses can be selected manually. A type change
with an incompatible bearing is rejected rather than silently dropping that bearing.
An edit time before the previous updatedAt or an exhausted revision is rejected.
An equal timestamp is permitted. Failure leaves the original immutable instance intact.
This is a domain edit, not a storage commit, concurrency policy or image-file operation.

Types are MINI, SCOUT, PIR and GATEWAY, with display names Mini, Scout, PIR and Gateway.
Status and type are separate enums. All objects are local drafts; a shared state
will not be created before transport has been verified. createdBy/updatedBy, precision/source,
sharedAt and conflict metadata will be introduced when identity/SDK and the synchronization stage are settled.

Distances use haversine, meters, and a mean radius of 6371008.8 m. The same UUID
is excluded. Both queries include only PLANNED/DEPLOYED/ACTIVE candidates; the source
may have any status. The gateway list additionally filters for gateways. Results sort
by ascending distance, then UUID for ties. Empty eligible sets return no nearest
neighbor/an empty gateway list. Re-querying an edited collection recalculates results. These are
map distances, not terrain, link or line-of-sight measures.

The local storage byte contract, limits, first-version fixture and fail-closed schema
handling are in [LOCAL_STORAGE.md](LOCAL_STORAGE.md). No older persisted format exists;
future schema changes require migration tests. Production CoT mapping remains pending.
The separate [synthetic CoT experiment](COT_ROUND_TRIP.md) uses
fixed test fields; it does not freeze or implement the production draft wire schema.
During the synchronization stage, conflicts must preserve the original for manual review.

Issue #8's [local MVP decision record](decisions/0006-local-mvp-product-rules.md)
covers status transitions, ID warnings, time behavior, candidate filtering and image
limits. Confirmed owner choices and PR-review defaults are identified separately.
The core now implements manual-ID validation, validated edits, form parsing and candidate filtering.
The [#52 coordinate editor](MAP_POSITION.md) displays/accepts MGRS while retaining
the canonical numeric position unchanged when the displayed reference is untouched.
Map selection changes only the form until explicit Save; no schema/wire migration.
The [offline planner](LOCAL_PLANNER.md) now connects duplicate warnings, validated
forms and Android persistence on API 26+. Physical acceptance, connected privacy
and image-file processing remain #11–#13. The core store persists all current fields, including an opaque image
handle; it does not persist or validate image bytes. Unknown schemas are not overwritten.

The independent [local image library](LOCAL_IMAGES.md) prepares normalized PNG
copies with opaque UUID-based handles. New copies use the explicitly disclosed
1,920-pixel longest-side cap (#66), with 10 MiB source / 16 MiB owned-PNG
limits; existing PNGs remain readable without rewriting them. See
[IMAGE_QUALITY.md](IMAGE_QUALITY.md). Snapshot v1
and existing handles are unchanged. Image save/detach ordering, ambiguous failures
and old/missing handle behavior are specified there; no image bytes enter the
snapshot or wire schema.

The proposed [metadata import review](IMPORT_REVIEW.md) compares validated snapshots
by UUID without writes. It ignores local revision counters and preserves local image
handles; different metadata requires explicit review. Incoming image handles are
rejected. This preparatory #15 helper changes neither snapshot v1 nor the production
wire contract and does not implement a receiver or durable conflict storage.

The proposed [metadata sharing review](SHARE_REVIEW.md) exposes an explicit metadata
projection without image handles or local revision counters. It retains the saved
record privately to detect stale reviews and binds the destination selection to
that review. This #14 preparation defines no production wire encoding, storage
migration, sharing state or transport; local images remain excluded.

The [manual sharing lab contract](METADATA_SHARE_LAB.md) uses a separate experimental
`tmmShareLab` tag with exact coordinates alongside the SDK-rounded waypoint. It
excludes image handles and local revision counters and imports nothing into the
local snapshot. This is not the accepted production CoT/synchronization schema.

The #49 primary-still JPEG amendment changes input normalization only: auxiliary
camera content is discarded before decoding the primary JPEG range. Snapshot v1,
opaque image handles, PNG storage limits and the wire model are unchanged. Existing
owned images are not rewritten; see [the image contract](LOCAL_IMAGES.md).
