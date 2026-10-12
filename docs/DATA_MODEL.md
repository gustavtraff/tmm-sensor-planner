# Data model

The core's initial model is a local draft (schemaVersion 1), with no serialized
storage or CoT standard yet. The full proposed record is in project plan §5.

UUID is the stable identity. displayId and owner are required, trimmed and must
not be empty. ID uniqueness across a working area is not implemented.
Position: finite degrees, latitude [-90,90], longitude [-180,180].
createdAt/updatedAt: UTC Instant; updatedAt must not precede createdAt.
Optional deployedAt: actual time, not automatically derived from status.
Bearing: optional for PIR/Scout, finite [0,360), provisionally true north; Gateway and Mini
reject bearing. Comment: provisionally at most 2000 UTF-16 code units (Java String.length()).
Image reference: optional local reference; no image blob, file handling or sharing.
Revision: positive local revision number, with no synchronization/acknowledgment claims.

Types are MINI, SCOUT, PIR and GATEWAY, with display names Mini, Scout, PIR and Gateway.
Status and type are separate enums. All objects are local drafts; a shared state
will not be created before transport has been verified. createdBy/updatedBy, precision/source,
sharedAt and conflict metadata will be introduced when identity/SDK and the synchronization stage are settled.

Distances use haversine, meters, and a mean radius of 6371008.8 m. The same UUID
is excluded. The gateway list filters for gateways and sorts in ascending order. All statuses
provisionally count. These are map distances, not terrain, link or line-of-sight measures.

Serialization, migration, unknown schema versions and CoT mapping remain to be implemented.
During the synchronization stage, conflicts must preserve the original for manual review.
