# Data contracts

This is the index of canonical contracts, not an implementation timeline.
[Current implementation and open gates](NEXT_STEPS.md) are maintained separately.
The [previous accumulated model notes](archive/2026-11-16/DATA_MODEL.md) remain
available unchanged. This consolidation changes no bytes, fields or validation.

## Domain and local storage

`TmmDraft` uses stable UUID identity and manually entered, trimmed `displayId`.
Types are Mini, Scout, PIR and Gateway; statuses are Planned, Deployed, Active,
Retrieved and Cancelled with manual transitions. Owner is required; comment is
bounded to 2000 Java UTF-16 code units. Coordinates must be finite within latitude
[-90,90] and longitude [-180,180]. Timestamps are UTC; deployment time is optional
and manually supplied. Updates preserve UUID/createdAt and advance local revision.

Bearing is finite [0,360) clockwise from true north, optional only for PIR/Scout.
The UI uses [6400-mil conversion](DEVICE_DETAILS.md) and [MGRS position entry](MAP_POSITION.md),
without changing canonical degree/coordinate storage. Distance uses haversine,
mean radius 6371008.8 m, excludes the same UUID and considers only
Planned/Deployed/Active candidates; gateway results additionally require Gateway.

- [Original snapshot v1](LOCAL_STORAGE.md): compatibility and malformed/future-schema refusal.
- [Local v2/fingerprint bytes](LOCAL_V2_FORMAT.md), [codec](LOCAL_V2_CODEC.md),
  [storage](LOCAL_V2_STORAGE.md), [migration](LOCAL_V2_MIGRATION.md) and
  [Android activation](VERSIONED_PLANNER_STORAGE.md).
- [Import/application and recovery](IMPORT_APPLY_CONTRACT.md),
  [decision preparation](IMPORT_DECISION_PREPARATION.md),
  [confirmation](IMPORT_CONFIRMATION_UI.md) and [cleanup principles](LOCAL_MISSION_CLEANUP.md).
- [Inbox format](INBOX_FORMAT.md) and [persistence](IMPORT_PERSISTENCE.md):
  receipt/source/UUID/metadata variants; [store](INBOX_STORAGE.md) and [review](INBOX_REVIEW_UI.md).

Local revisions and persistent mutation versions are separate from incoming wire
identity. Missing response members do not delete drafts; metadata differences
require review. Local image handles never become remote paths or inbox image data.

## Publication and source meaning

[Publication XML](PUBLICATION_FORMAT.md), [codec](PUBLICATION_CODEC.md) and
[envelope](PUBLICATION_ENVELOPE.md) define the reviewed metadata projection.
[Configured snapshots](CONFIGURED_SNAPSHOT_SOURCE.md) bind explicit Fetch to a
configured logical service and mission GUID, not author identity, complete history
or independent per-feed historical versions. [Reviewed host updates](REVIEWED_HOST_UPDATES.md)
and [configured publication](CONFIGURED_PUBLICATION_TRIAL_CONTRACT.md) define
separate handoff guards. Native publication is not a receipt or global Shared state.

## Images

[Local image ownership](LOCAL_IMAGES.md), [quality](IMAGE_QUALITY.md) and
[viewer](IMAGE_VIEWER.md) govern normalized private PNG copies and opaque handles.
New copies have a 1920-pixel longest-edge cap without upscaling, 10 MiB original
input and 16 MiB owned-PNG bounds. Existing copies are not silently rewritten.

[Logical sharing rules](IMAGE_SHARING_CONTRACT.md), [descriptor v1](IMAGE_OFFER_FORMAT.md),
[stage v1](IMAGE_STAGE_LAYOUT.md), [association registry](IMAGE_ASSOCIATION_REGISTRY.md)
and [container v1](IMAGE_CONTAINER_FORMAT.md) retain their original approval scope.
[Selected-feed intake](SELECTED_FEED_IMAGE_INTAKE.md) and [received review](RECEIVED_IMAGE_REVIEW.md)
check actual bytes against retained metadata; a hash alone is not source authority.
[The first viewing-trial contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md) keeps viewing
separate from active local attachment adoption and outbound acknowledgments.
[Automatic feed sync](AUTOMATIC_FEED_SYNC_PROPOSAL.md) proposes automatic fetching
and base-based conflict detection (#236). U1–U8 are approved; the sync-state format is not.

Changing a contract requires its documentation and relevant migration/compatibility
checks together; a renamed document does not approve a new writer or transport.
