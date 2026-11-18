# Feed sync state v1 — exact format proposal (#236)

**Status: S1–S4 explicitly approved by the owner on 2026-11-18** in the conversation,
against this document at commit `2caf8bb` (PR #238). The approval came from that
decision, not from the merge. A writer is a separate PR.**
This is decision U6 of [the approved automatic feed sync proposal](AUTOMATIC_FEED_SYNC_PROPOSAL.md).
Inbox format 1, local v2, stage v1, association registry v1 and the container do not change.

## Purpose

Hold the three things automatic sync needs that no existing file records:

1. which feed this client follows, so following resumes after restart (U2);
2. per device, the **base**: the remote version the local device last matched, and
   the latest remote version seen (U3);
3. an image adoption that was started but not yet confirmed, so a crash can be
   finished or undone on the next load (U5).

## Files

```text
se.tmm.sensorplanner/feed-sync-v1/
  sync.bin       # the frame below
  sync.lock      # permanent empty file, exclusive OS lock only
```

Host no-backup private storage, separate from drafts, inbox, stage, registry and
images. Same-directory temporary files are ignored on read. **Följ feed** creates
the file. An absent file means "not following", never an empty state, and a reader
never creates one. Writes follow the existing store rule: lock, validate the whole
file, write a temporary file, sync, read back and replace atomically.

## Exact frame v1

Conventions match [inbox format 1](INBOX_FORMAT.md): big-endian integers, UUIDs as
two longs, strict UTF-8 strings with an `int` byte length, already in Java
`String.trim()` form. A flag is one byte, 0 or 1; an absent optional body is omitted.

| Part | Bytes |
|---|---|
| Header | `int 0x544d4d53` (`TMMS`), `int` version `1`, UUID syncId, UUID inboxId, flag following, string contextId, string feedName, string streamConnect, `int` recordCount |
| Each record (36–116) | below, in strictly ascending order of deviceUid's 16 bytes compared unsigned |
| Trailer (32) | SHA-256 of every preceding byte; no bytes after it |

| Header field | Rule |
|---|---|
| syncId | Not all zero. Generated when the file is created. |
| inboxId | Not all zero. The inbox whose receipt IDs the records use. |
| following | 1 = fetch automatically. 0 = **Sluta följa**: records are kept, nothing is fetched. |
| contextId | 1–4,096 bytes, starts with `mission-api-v1|` and has more after it. The same opaque context the inbox already stores (service and mission GUID). |
| feedName | 1–1,024 bytes. Display label only, never identity. |
| streamConnect | 1–1,024 bytes. The selected profile's connect string, used only to find the SDK credential at fetch time. No credential, certificate or password is stored. |
| recordCount | 0–256. |

| Record field | Encoding and rule |
|---|---|
| deviceUid | UUID, not all zero. At most one record per device. |
| remoteReceiptId | UUID, not all zero. The inbox receipt holding the device's M in the latest fetch that contained it. |
| baseReceiptId | Flag, then UUID if present (not all zero). The inbox receipt holding base M. Absent = no base yet. |
| image base | One byte: `0` unknown, `1` the base has no image, `2` followed by the 32-byte SHA-256 of the base image PNG. |
| adoptNew | Flag, then UUID if present (not all zero). The owned image `<uuid>.png` prepared for adoption and not yet confirmed. |
| adoptOld | Flag, then UUID if present (not all zero). The image that was current when adoption started. Only allowed with adoptNew, and differs from it. |

Owned image references are always `<lowercase uuid>.png` (`LocalImages`), so the
16-byte UUID is the whole reference. An empty file with the fixtures' labels is 202
bytes. The largest file is 35,929 bytes.

## Why M is not stored here

As in the association registry, the inbox already keeps every exact variant M under
one receipt ID per (context, device, M). Referencing it avoids a second spelling of
every field. The remote image is not stored either: U4 derives it from the registry
records for the device, preferring one bound to the remote M.

## How the fields give the U3 decision

Conflict is not a stored flag. On each fetch, the writer compares remote R (the new
`remoteReceiptId`'s M and, through the registry, its image), base B (`baseReceiptId`
and the image base) and the current local draft L. Metadata and image are compared
separately. A device's local image is compared by the SHA-256 of its owned bytes.

- **Behåll min** sets the base to R, so the conflict is resolved and the device
  shows **Ej publicerad** until the user publishes.
- **Använd inkommande** or an automatic apply sets the base to R after the local
  commit has been read back.
- A device that is deleted locally keeps its record, which is how U3 knows not to
  add it back.

## Image adoption recovery (U5)

1. Prepare the owned copy with `LocalImages.prepareReceived` and read it back.
2. Write adoptNew = the new reference and adoptOld = the current one, if any.
3. Commit with `DraftAttachments.attach`, bound to the expected local state.
4. Set the image base, clear adoptNew and adoptOld, then delete the old image after
   a fresh unreferenced check.

On load, a record with adoptNew is resolved before any fetch. If the draft refers to
adoptNew, the commit happened, so step 4 runs. If it still refers to adoptOld (or no
image when adoptOld is absent), the commit did not happen: delete adoptNew if it is
unreferenced, clear both fields, and let the next fetch try again. If the draft
refers to something else, the user changed it meanwhile: delete adoptNew if it is
unreferenced, clear both fields and leave the rest alone.

## Preconditions the writer must prove

The file cannot show these, so each is checked under the locks before commit:

1. Every receipt ID exists in inbox `inboxId` with this file's contextId and the record's device.
2. contextId came from the approved source selection, never from a payload.
3. A new base equals what the local draft holds after a read-back commit.

## Honest limits

- A crash after step 1 and before step 2 leaves one unreferenced owned image that
  nothing will clean up. No data is lost. A general orphan sweep is not proposed here.
- Following another feed needs the file to be removed first, by a separately
  reviewed reset. Devices then have no base, so the first fetch reports any difference
  as a conflict rather than overwriting local work.
- At 256 records new devices are not followed and the user is told. Nothing is evicted.
- Unknown versions, bad digests and rule violations stop following and never rewrite
  or reset the file.
- **Not detected:** loss of the whole file, restore of an old backup, or tampering by
  another plugin under the host UID. The digest detects accidental corruption only.

## Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| S1 | Approve the exact frame, including 256 records and the field limits | Approve |
| S2 | Reference inbox receipts for base and remote M instead of copying M | Approve |
| S3 | Record adoptNew and adoptOld before the commit and resolve them on load as above | Approve |
| S4 | Accept the limits above, including the possible unreferenced image after a crash | Approve |

Approval should name this document's commit. A writer is then a separate PR.

## Independent fixtures and verification

[The fixtures](../test-fixtures/synthetic/feed-sync-state-proposal/README.md) share
their context, inbox, device, receipt and H1 digest with the association registry
fixtures. `scripts/test-feed-sync-state-proposal.py` is a stdlib-only oracle that
runs in public CI. It pins the three files by size and hash, cross-checks the shared
identities against the registry fixture, and covers framing errors, every header and
record rule, bad flag and image-kind bytes, ordering and duplicates, and the exact
maximum file of 256 widest records.

## Implementation — 2026-11-18

`FeedSyncState` in the Java core implements these bytes: a strict codec and a file
store (`sync.bin`, `sync.lock`) in a directory the caller supplies. It has no fetch,
conflict, adoption, UI or transport code, so nothing in the APK writes the file yet.

- `create` is the only way to start and refuses an existing file. `read` returns
  null for an absent file and creates nothing.
- `replace(expected, next)` locks, validates the whole file, refuses if the file no
  longer equals `expected`, and leaves an identical rewrite byte for byte untouched.
  Writes use a same-directory temporary file, sync, read-back and atomic replace.
- Records are written in canonical order. Wrong order, duplicates, unknown versions
  and bad digests fail closed and never rewrite the file.
- **The caller must prove** the preconditions above; the core cannot see the inbox.

`sh scripts/test.sh` covers the three goldens byte for byte, every rejection the
Python oracle covers, the exact 35,929-byte maximum and 257 records refused, create
and reopen, stale writes, identical rewrites, failed replacement and an unknown
future version left unrewritten. No SDK build, emulator, phone or server was involved.

## Executor — 2026-11-18

`se.tmm.sensorplanner.plugin.local.FeedSyncApply` (#242) is the first writer of records. It runs on
the planner's serialized worker inside each `FeedFollowUi` fetch, after the inbox and image intake,
so only one fetch and apply run at a time. Nothing in it sends anything (U8).

1. **Recovery first.** On load and before every plan, each record with adoptNew is resolved exactly
   as in "Image adoption recovery" above.
2. **Plan.** The local image state of every ACTIVE device is the SHA-256 of its owned bytes
   (`LocalImages.read`, cached per reference because owned files are never rewritten). An owned
   image that cannot be read gets a digest that matches nothing, so it is never replaced.
   `FeedSyncPlan.nextSync` is written with `FeedSyncState.replace(expected, next)`.
3. **Metadata ADD** reuses the import Add path: `ImportDecisionPreparation.addLocally` (no re-add
   confirmation, so a known local deletion is never re-added) and `LocalV2Store.commit`. The new
   device has no image. After read-back the base becomes the remote receipt and the image base
   "none", which is what the local draft then holds (precondition 3).
4. **Metadata APPLY** uses `LocalV2Store.save` bound to the exact expected state, with R's fields,
   the later of R's and the local `updatedAt`, the local `createdAt`, imageRef and revision + 1. The import `useSelected` path
   cannot express this: it requires a separate local edit time, so L would never equal R and the
   device would look unpublished after every update. A different `createdAt` cannot give L = R; the device is reported as a conflict instead.
   Sync equality ignores `updatedAt` (owner-approved 2026-11-18, #236), so an earlier R time is applied and the local time kept.
   After read-back the base becomes the remote receipt.
5. **Image APPLY** reads the staged entry, checks its PNG digest against the association,
   `prepareReceived` (which reads the copy back), writes adoptNew/adoptOld, calls
   `DraftAttachments.attach` with the later of the local and R's `updatedAt`, then sets the image base from the adopted bytes and
   clears the adoption. `attach` already deletes the old copy after a fresh unreferenced check;
   step 4 repeats that check for recovery. `LocalImages.Pending.reference()` was added so adoptNew can
   be written before the commit.
6. **Image ADOPT_BASE with no image** (no bound image, no local image, UNKNOWN base) is bookkeeping in
   `nextSync` only: the image base becomes NONE (kind 1), so the deployer's first photo applies.
7. **CONFLICT / AMBIGUOUS** change nothing locally. `FeedFollowUi.conflicts()` and `unpublished()`
   (L ≠ B against the stored base) are read-only, in-memory sets for the list UI.

A stale local state, or any failure, skips that device for the round; the next fetch retries. No
device is deleted and no image is cleared because something is missing. Robolectric tests are in
`FeedSyncApplyTest`: ADD, ADD with image, image replacement, metadata APPLY keeping the image,
conflict leaving local files byte-identical, duplicate fetch leaving every file byte-identical,
stale local state, a simulated process kill after each U5 step, and recovery after a user image change.

The photo alternation found during review (old and new photo both in the feed) is fixed by the
second U4 amendment: an image bound to the feed's current M is H, so the old photo no longer wins
after an adoption. `FeedSyncApplyTest` covers photo 2 published with M2 while photo 1 is still in the
feed, a byte-identical next fetch, and a later status-only M3 keeping photo 2.
