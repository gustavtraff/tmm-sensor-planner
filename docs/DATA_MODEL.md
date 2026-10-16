# Data model

The core's initial model is a local draft (schemaVersion 1), with no serialized
storage or CoT standard yet. The full proposed record is in project plan §5.

UUID is the stable identity. displayId and owner are required, trimmed and must
not be empty. Visible IDs are manually entered to match the device label (synthetic
example: `PIR 134`), without automatic numbering or an enforced naming format.
ID uniqueness across a working area is not implemented; UUID remains the record identity.
Position: finite degrees, latitude [-90,90], longitude [-180,180].
createdAt/updatedAt: UTC Instant; updatedAt must not precede createdAt.
Optional deployedAt: actual time, not automatically derived from status.
Bearing: optional for PIR/Scout, finite [0,360), clockwise from true north; Gateway and Mini
reject bearing. Comment: at most 2000 UTF-16 code units (Java String.length()).
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

Production serialization, migration, unknown schema versions and CoT mapping remain
to be implemented. The separate [synthetic CoT experiment](COT_ROUND_TRIP.md) uses
fixed test fields; it does not freeze or implement the production draft wire schema.
During the synchronization stage, conflicts must preserve the original for manual review.

Issue #8's [local MVP decision record](decisions/0006-local-mvp-product-rules.md)
covers status transitions, ID warnings, time behavior, candidate filtering and image
limits. Confirmed owner choices and PR-review defaults are identified separately.
The core now implements manual-ID validation, validated edits and candidate filtering.
Duplicate warnings, persistent storage, forms and image-file processing remain #11–#13.
No serialized storage exists; this change requires no migration and does not define
a production stored/wire format. Later schema changes still require migration tests.
