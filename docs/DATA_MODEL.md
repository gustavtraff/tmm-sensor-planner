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
wire contract and does not implement a receiver. The separate durable inbox
implementation described below retains metadata conflict evidence only.

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

The [#83 sensor-point experiment](DATA_SYNC_SENSOR_TRIAL.md) adds only explicit
offline fixture/checker cases for a different host envelope and synthetic UUID
namespace. It reuses ShareLabFields without changing snapshot identity, canonical
degrees/coordinates, private image handles or a production wire contract. A live
MapItem edit cannot be assumed to update exact embedded metadata consistently.

The [#25 same-UUID runtime trial](DATA_SYNC_UPDATE_TRIAL.md) preserved the coherent
PIR update's exact metadata through server replacement and B's export after online
restart, without duplicate feed membership. Reconnect/list refresh still returned
the base metadata in the observed interval. Fresh export envelope timestamps are
not evidence of updated embedded fields. A later [controlled reconnect](DATA_SYNC_RECONNECT_TRIAL.md)
returned the exact update without restart; locally downloading an older history
version changed B's exported fields without changing the server, and selecting
Latest restored them. Feed version, host/exported content and durable TmmDraft
state must remain distinct. No snapshot, wire schema, revision,
conflict policy, durable TmmDraft import or acknowledgment contract changes here.

The [#25 permission/conflict/removal trial](DATA_SYNC_ACCEPTANCE_TRIAL.md) adds a
third same-UUID offline fixture, not a production version field or schema change.
Native conflict selection preserved the chosen exact fields, but does not persist
both candidates in TmmDraft storage. Local deletion left remote content available
for download; server removal deleted the current feed association, not every
local/history copy. A later receiver still needs explicit conflict storage and
withdrawal/tombstone rules under #15; native feed state must not substitute for them.

## Durable import inbox — 2026-10-25

The owner explicitly approved A–C and the PR #92 format for a separate
[InboxStore](INBOX_STORAGE.md). [Inbox format 1](INBOX_FORMAT_PROPOSAL.md) persists
local receipt identity, source context, incoming metadata variants and historical
local metadata observations. It uses its own TMMI file/lock and independent fixture;
TMMD snapshot v1, live draft revisions/timestamps and local image ownership remain
unchanged. No old draft format is migrated or reinterpreted.

Incoming/local image handles are not serialized into the inbox. Current handles
remain exclusively in live DraftStore records; historical images are not pinned.
D, application to local drafts, remains deferred. This is neither a production
wire schema nor a source-authentication, transport or acknowledgment contract.


## Private application state — D proposal, 2026-10-25

[The proposed D contract](IMPORT_APPLY_CONTRACT_PROPOSAL.md) would add local store
identity, per-UUID mutation versions/deleted slots and atomic decision/conflict
metadata in a local snapshot v2. These are proposed private persistence fields,
not TmmDraft domain/wire versions or changes to inbox format 1. No v2 parser,
writer or migration exists yet. The owner has now adopted the logical principles,
including capacity feedback and separately confirmed local mission cleanup; exact
byte-format/fixture and cleanup-activation review still precede their writers. Current domain/time/image rules remain unchanged.


## Exact local v2 byte proposal — 2026-10-26

[LOCAL_V2_FORMAT_PROPOSAL.md](LOCAL_V2_FORMAT_PROPOSAL.md) now gives the proposed
private snapshot and fingerprint bytes plus independent fixtures. Snapshot version
2 and fingerprint version 1 are local persistence formats, not a domain or outgoing
wire schema update. The production model/store/inbox and metadata comparison are
unchanged; old deletion/application history is unavailable on migration. Exact
format adoption still precedes any production v2 parser/writer/migrator.
