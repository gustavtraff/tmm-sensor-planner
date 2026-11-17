# Selected-feed image intake backend (#206 / #200 / #26)

The owner-approved I1–I5 trial now has an inactive selected-feed reader and local
intake composition. No public UI action or image export is activated by this slice.
All current container/descriptor/inbox/stage/registry/local-v2 bytes are unchanged.

## Read boundary

`MissionImageInput` parses one selected GUID/name overview, reusing the existing
strict UTF-8/JSON duplicate-key/depth checks. The separate 512 KiB / 256-resource
budget does not widen metadata’s 64 KiB / 64-member budget. Duplicate resource hashes,
malformed claims and over-limit input reject the whole list. Only `.tmmi` resources
with the observed `application/octet-stream` type are eligible. Resource names are
labels, never paths; no response-supplied URL is followed.

`MissionImageFetch` checks selected resource hash/length/type before and after
GET `/Marti/sync/content?hash=<validated hash>`. It verifies actual full-container
length and SHA-256 as well as the approved strict container/descriptor/PNG checks.
A readable hash alone is never feed source evidence. Other resources may change;
a missing/changed selected resource returns no accepted payload. Claims mean the
selected service/feed observation, not author identity or a continuous lease.

The existing `MissionSnapshotHttp` supplies the actual same-origin request through
ATAK's public configured TLS/provider APIs. It retains identity encoding, status 200,
redirect/authentication-fallback refusal, header/time limits and caller cleanup.
The image attempt has a 60-second ceiling; existing metadata attempts remain 12 seconds.
The eventual controller must schedule actual abort/unblocking, invalidate on source
loss, bound concurrency to one and reject stale lifecycle callbacks. The synchronous
backend alone is not that UI lifecycle or an actual SDK HTTP runtime result.

## Local persistence boundary

`ReceivedImageIntake` requires explicitly initialized existing stores and a payload
from that approved fresh read adapter. It reloads local state and exact inbox /
registry namespaces before recording the observed metadata, actual stage and
association. It writes no active local draft/image, host map item or feedback.

- An exact retained delivery reopens/verifies its actual original content and reuses
  the association; missing historical content is an error, never silently repaired.
- A prior interrupted association may leave a ready **unreferenced** stage. Fresh
  approved intake can reuse it only after exact descriptor/PNG equality and a new
  normal locked association proof. This does not reconstruct old source history.
- Other contexts retain separate receipt/association/stage identities. Pending
  recovery, namespace mismatches, conflicts and quotas refuse without eviction.
- The stores are separate existing commits, not a new atomic transaction. A failed
  or canceled operation may already have saved metadata or an unreferenced ready
  pair, or committed an association. Reload actual history; never claim rollback
  or automatically retry/delete. Existing registry-before-stage locking is retained.

## Verification — 2026-11-12

`sh scripts/test.sh` passed. The full standalone native Robolectric API 26/35
image/UI suite passed 546 cases, zero failures/errors and one existing skip; image
lint passed. New tests use independent real container/PNG bytes and actual stores
for exact readback, repeat/reopen/context separation, cancellation between stage
and association, orphan reuse, missing historical content and conflicting metadata.
Resource tests cover fixed paths, before/after membership, full-file hash, partial /
trailing bytes, JSON/identity/type/count/size limits, deadlines and unchanged
metadata limits. `sh scripts/check-sdk.sh` also passed external 5.6.0.24 SDK probes, CivDebug
assembly/lint/app tests and static APK checks.

No emulator, actual product HTTP call, UI lifecycle/native publication, signed
integrated acceptance, physical phone/server test or signing submission ran for
this backend. Those gates remain required before the owner can test image sharing.
