# Private unassociated image-pair storage (#146 / #26)

The owner explicitly approved [stage-v1's exact layout](IMAGE_STAGE_LAYOUT_PROPOSAL.md)
after PR #143. `ImageStageStore` implements that format in the app image adapter,
reusing `ReceivedImagePayload` and the approved descriptor codec. It has no UI,
ingress or transport caller. It never opens drafts, an inbox, active attachments,
gallery resources or a host map item. No persisted existing format changes.

## Caller contract

Use a trusted, dedicated app no-backup `Path`, proposed under
`se.tmm.sensorplanner/received-image-stage-v1`, with an existing trusted parent.
All calls are synchronous worker operations. The caller bounds worker concurrency
and live image memory; a successful bounded test is not a heap guarantee. Shared
ATAK/plugin UID access is not a security boundary against a malicious peer.

- `initializeNew()` explicitly creates only an absent root, empty OS lock file and
  exact synced/read-back format marker. It never initializes on construction/read,
  adopts an existing directory or resets damaged storage. Initialization failure
  can leave an incomplete root or an actually valid empty root; inspect actual state.
- `prepare(payload)` freezes canonical descriptor/exact verified PNG bytes and a
  fresh receiver-generated local UUID before writing. Incoming identities never
  select a path. Equal incoming offers can occupy separate unassociated stages.
- `stage(prepared)` permits one attempt on that handle. Under an exclusive OS lock,
  it validates all existing state and quota, creates the pending directory/files
  without replacement, syncs each file, reopens/verifies the complete pair, then
  atomically renames pending to ready and verifies actual ready bytes again.
- `inspect()` returns immutable sorted ready/pending UUID lists and actual logical
  file bytes. It validates every ready pair, caps enumeration and accounts partial
  files. Pending blocks admission, even if its contents are complete. Missing,
  corrupt, foreign, unsupported or ambiguous state fails without resetting it.
- `read(localId)` validates the stage before returning one verified ready pair.
  A pending or unknown ID cannot be read as an image. Read-only inspection/read of
  valid ready records is possible alongside retained pending records.
- `reconcile(prepared)` checks the original operation's actual outcome. The overload
  accepting the **original local ID and exact frozen payload** supports a caller
  that retained both across restart. A ready result requires byte equality of the
  entire canonical descriptor and PNG, not just UUID, pixels or digest. Pending
  remains pending; absent means absent now. No new identity/retry is generated.

Failures have bounded categories without filesystem paths/provider messages or
causes. Unsupported or busy locking never falls back to unlocked access. The lock
also covers reads, reservation, publication and readback. An already-attempted handle
cannot retry after any result; retain/reconcile it. A lock/capacity error before a
write can leave no candidate. Failure during a write leaves pending; failure after
rename may leave ready. A readback failure never reports known completion and
never deletes either candidate file. Synthetic allocation failures use the existing
safe low-memory category. There is no deletion, cleanup, auto-resume or eviction API.

## Exact limits and remaining guarantees

The approved marker/names, 16 total candidate directories, 256 MiB logical-byte
budget, 262256-byte descriptor and 16777216-byte PNG caps are enforced. Sparse
partial files count logical length. Filesystem overhead/free space are outside
that quota. Symlinks, nonregular files, unknown entries and duplicate local IDs
across pending/ready fail closed. Cooperative writers share the same exclusive OS
lock; arbitrary concurrent replacement by another host/plugin is outside scope.

File sync, atomic rename and byte readback do not prove directory durability under
power loss or backup rollback. No receipt/index history is stored: whole-directory
disappearance cannot be detected by enumeration. An empty valid stage does not
prove nothing was previously received/lost. Local UUIDs and matching bytes cannot
restore source/feed authority after restart. Any future association registry must
retain its own expected content IDs, source/metadata evidence and missing-data state.

No positive association acknowledgment, metadata application, active-image change,
Data Sync delivery or operational sharing follows from a READY result. This is only
known unassociated stage readback. A concrete cleanup/recovery operation and its
review still precede end-user ingress activation.

## Verification

Focused API 26/35 Robolectric tests use actual native PNG decoding and the independent
stage fixtures. They cover initialization and every injected write milestone,
unknown outcomes before/after rename, exact descriptor/content match, immutable
inspection, same incoming pair with separate local IDs, partial/complete pending,
bad/unknown/missing files, symlinks, canonical spelling, entry/actual-byte limits,
no eviction and synthetic allocation/readback faults. Recreated store instances
exercise reopened filesystem state. A separate plain JVM provides actual OS-lock
exclusion, while two concurrent in-process instances exercise blocked admission/read.

These tests use the development host filesystem under Robolectric, not an Android
device filesystem or a killed/restarted ATAK process. Native process/restart,
device storage denial/full-disk, unsupported-filesystem atomic move and power-loss
acceptance remain unverified. No emulator, Docker, server, phone, signing submission
or transport was used for this slice. The focused suite passed **30 API 26/35 cases**, with no failures/errors/skips.
Standalone image lint reported zero errors and six warnings. Local supplied-SDK
CivDebug/CivRelease assembly, CivDebug lint and all three existing app unit tests
passed (167 executed tasks). App lint had zero errors and 57 warnings. The debug
APK passed actual signature/discovery/API/permission/packaging checks. Local release
remains unsigned; no SDK method was introduced. These results do not establish
standard-host runtime compatibility or a native Android filesystem/restart result.
