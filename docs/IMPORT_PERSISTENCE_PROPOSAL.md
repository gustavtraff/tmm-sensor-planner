# Durable metadata inbox and conflict preservation (#15)

**Status: A–C and the format in PR #92 explicitly approved by the repository owner
on 2026-10-25. D (application to local drafts) remains deferred.** Approval was
recorded in the conversation, not inferred from a PR merge.
The SDK-independent [InboxStore](INBOX_STORAGE.md) implements the separate review
inbox. No Android adapter, transport, draft application or image lifecycle changes
are included. The design rationale below originated on main
`6ae582d7e83aee3330e7cc79553582d50f7ca183`; current API/evidence are linked above.

## D follow-up — proposed 2026-10-25

The owner subsequently adopted the receiving UX: trusted-feed visibility without
per-device acceptance, explicit Add locally, manual comparison/choice for different
local metadata and no repeated work for identical metadata. This does not approve
a writer or migration. [The D application/recovery proposal](IMPORT_APPLY_CONTRACT_PROPOSAL.md)
now concretizes expected-state confirmation, deleted/recreated UUIDs, preserved
images/variants and interrupted commits. Its local v2 format/migration and detailed
time/decision policies await explicit review; A–C and inbox format 1 are unchanged.

## Reviewed behavior and gaps

[ImportReview](../core/src/main/java/se/tmm/sensorplanner/core/ImportReview.java)
is a pure classifier. Equal UUID plus equal metadata gives SAME_METADATA; any
compared metadata difference gives REVIEW_REQUIRED, not proof of concurrency.
`revision` and local `imageRef` are excluded from comparison, and incoming image
handles are rejected. InboxReview now invokes `assess` for read-only grouping; there is no Android
receiver/apply caller. `ShareReview` also reuses `sameMetadata`, so changing that equality
would affect outgoing-review freshness as well as imports.

[DraftStore](../core/src/main/java/se/tmm/sensorplanner/core/DraftStore.java)
locks each operation, validates the complete snapshot and atomically replaces one
checksummed file. It rejects ordinary stale revisions, but `load` → assess → `save`
is not one transaction. A future caller that reloads, increments the latest revision
and blindly applies an old review would defeat the intended stale-review guard.
DraftStore stores no receipt, source context, decision or deletion history; the
separate InboxStore now retains receipts and metadata variants only.

Creation time must remain unchanged on updates; updatedAt cannot decrease and the
local revision must advance exactly once. A foreign revision must not be passed to
`save`. Replacing a UUID after deletion can restart its revision, so revision alone
is not a general generation/anti-resurrection token. Unknown/corrupt storage fails
closed; the checksum provides neither authentication nor power-loss durability.
See [the current comparison](IMPORT_REVIEW.md) and [v1 storage](LOCAL_STORAGE.md).

The [#25 native conflict/removal trial](DATA_SYNC_ACCEPTANCE_TRIAL.md) now shows a
Data Sync operator conflict choice and feed removal. It explicitly does not prove
persistent conflicting TmmDraft storage or recovery of the losing edit. A native
conflict dialog, feed download or server removal is not this plugin's import policy.

## Smallest recommended first slice

The approved first slice adds a **separate private, versioned inbox**. Persist validated
incoming metadata and an observed local metadata variant for later review. Leave
`drafts.bin` v1 and all local draft/image writes unchanged in that slice. No automatic
new-draft creation, update, merge, deletion, accepted/resolved status or remote
acknowledgment is included. This avoids a premature two-file commit protocol or a
migration of every existing local writer just to retain received candidates.

“Stored for review” means the receipt can be reopened after a process restart.
It does not mean “applied”, “Shared”, authenticated authorship, image delivered or
acknowledged to the sender. A later applying/resolution slice must meet the commit
gates below; it must not turn two separate saves into a claimed transaction.

### Logical receipt (the separate disk frame is specified in INBOX_FORMAT_PROPOSAL.md)

| Value | Minimum meaning |
|---|---|
| Local receipt ID | Stable locally generated identifier for readback/retry; never a new device UUID or inferred sender message ID |
| Source context | Stable caller-supplied validated namespace/feed/origin context, with provenance/trust evidence kept distinct from editable owner text |
| First observed time | Local receipt time, for presentation only; no ordering or conflict authority |
| Incoming metadata M | UUID and exactly the comparison fields in IMPORT_REVIEW.md; no foreign imageRef, local revision, path, provider URI or image bytes |
| Observed local metadata | Retained metadata-only variants for that UUID with their observed local revisions; record an absent observation explicitly, not as proof of never having existed |

The metadata projection includes createdAt/updatedAt as exact domain values; it
must not be rounded to transport-envelope times. A caller must validate the actual
source boundary and supported metadata schema before submission; core classes do
not authenticate it. Missing/unverifiable required context must not be replaced
with owner/callsign text or a guessed identity. An approved adapter mapping is a
separate gate before wiring real ingress. No new remote field is mandated here. Reject a nonnull incoming local imageRef
before staging; absent/null image metadata never clears a local attachment.

Historical local metadata intentionally excludes image handles. The current local
record retains its own handle in DraftStore. Keeping an old handle in a conflict
record would not retain its file: current attachment cleanup checks live drafts,
not an inbox. Historical image pinning/retention is outside this metadata proposal.

### Identity, repeat and conflict rules

| Input / state | Result / caller responsibility |
|---|---|
| Same source context, UUID and equal M | Return the existing receipt ID; do not add another row, conflict copy or local revision, including after restart |
| Same UUID, different M | Retain a separate immutable candidate; never replace the earlier incoming variant or choose by timestamp/arrival order |
| Same UUID, different validated source context | Preserve separate provenance/receipt evidence; this never permits a second live draft with that UUID |
| Different UUID, same display label | Separate identity; no merge by label |
| Current local metadata equals M | Report the fresh comparison as SAME_METADATA; do not rewrite a draft merely to record receipt |
| Current local metadata differs | Keep the received/observed variants and present REVIEW_REQUIRED; do not call it proven concurrency or select a winner |
| Current draft changes after receipt/review | Keep the receipt; reload and reassess the current local variant, without treating the earlier observed local snapshot as current |
| UUID locally absent or deleted | Retain for review only; NEW_UID is not authorization to recreate it or infer a remote deletion |

Equality reuses ImportReview's validated field rules, including optional values,
exact instants, case-sensitive text and numeric equality of positive/negative zero.
Changing only a remote local-write counter does not create a variant; changing
updatedAt does. If a digest is later used for indexing, its canonical encoding must
be approved/tested and full equality must still decide duplicates. No new content
hash or message/version field is required by this logical contract.

An observed local snapshot may become stale while the separate inbox is committed.
That is safe only because this first slice does not modify DraftStore or present
its observed snapshot as a live-state guarantee. Reassessment may retain a newly
reviewed local metadata variant; it must not overwrite earlier retained variants.
This is bounded conflict evidence, not a journal of every intermediate local edit.

### Commit, restart and failure boundary

- Receipt insertion and deduplication run under the inbox's own exclusive lock,
  with complete read/validate/compare/write in that operation. A repeat cannot race
  into a second receipt. Concurrent different variants must both survive.
- The inbox uses a bounded checksummed snapshot and same-directory synced
  temporary-file/atomic-replacement pattern. It enforces limits of
  1,000 stored receipt/retained-local-variant entries in total, 16 MiB total and
  65,536 UTF-8 bytes per string, plus domain limits. At capacity reject explicitly;
  do not silently evict a conflict or forget replay history.
- Return “stored for review” only after a known successful commit. After an
  ambiguous replacement/cleanup error, reopen by the same logical receipt key;
  report found/not found/unreadable before retrying. Corrupt/future/unsupported
  data must remain in place, not become an empty inbox.
- Reopen the exact stored variants, first-observed context/time and stable receipt
  IDs after a process restart. Retain pending history; do not silently expire it
  using CoT stale time, wall-clock age or a full-sync callback.
- Claim process-restart reopening only. As with DraftStore, file sync plus rename
  without proven parent-directory sync is not a sudden-power-loss guarantee.
- Keep inbox data private/no-backup under the eventual platform policy. No raw
  fields, source credentials, exceptions/paths or snapshots in public logs.

[Inbox format 1](INBOX_FORMAT_PROPOSAL.md) specifies the approved magic/version,
encoding, independent fixture, explicit initialization and retain-until-full rules.
There is no reset, export, restore or eviction API. Missing storage cannot establish
whether earlier receipt history was erased. Permanent replay suppression across
app-data deletion or restoring an old backup is not promised.

## Gates for later explicit application and resolution

1. Display a fresh local candidate and incoming variant before a user chooses.
   Never derive permission, causal order or edit authority from timestamps, local
   counters, feed membership, editable owner text or UUID alone.
2. Bind confirmation to the exact reviewed local state, including imageRef and
   revision, and the incoming variant. Revalidate atomically with the draft write;
   a local edit, image replacement/removal, deletion or recreation invalidates the
   earlier choice. Reloading and simply rebasing a candidate onto a newer revision
   is not revalidation. Ordinary revision checks alone do not solve deletion/reuse.
3. Preserve the **current reviewed local imageRef**, even when incoming metadata
   has no image. Metadata acceptance must neither delete nor replace its bytes.
   Image-only local changes may leave SAME_METADATA true and still invalidate a
   pending commit. New locally accepted records would start at local revision 1
   with no image, never the sender's revision/handle.
4. Decide createdAt mismatch and older updatedAt behavior before any apply API.
   Current `save` rejects those overwrites. Do not silently change local time to
   now or max(local, remote), change creation time, relax the store, or call remote
   clocks causal. Retaining the incoming variant without applying is the safe
   initial behavior; different clock/provenance semantics require explicit review.
5. Preserve both reviewed metadata variants before replacing a live draft. An
   APPLIED/resolved decision and its draft mutation need one recoverable transaction
   (for example a reviewed unified format) or an approved idempotent recovery
   protocol. Two independent file saves are insufficient. Neither alternative is
   implemented or selected by this first-slice recommendation.
6. Keep “keep local”, rejection, deletion and historical replay decisions durable
   once such operations exist. Define tombstones/re-import consent and retention
   before automatic recreation or incoming removals. Local metadata deletion is
   not server withdrawal, feed removal or secure erasure. No such operation is
   added to the inbox-only slice.

## Decision record — 2026-10-25

| Decision | Recommendation / boundary |
|---|---|
| A. First scope — approved | Separate durable review inbox; no live-draft writes or automatic application |
| B. Identity/equality — approved | Context + UUID + exact compared metadata; stable local receipt ID; retain different variants and provenance |
| C. Storage/history — approved | Independent bounded atomic format 1 with golden fixture, explicit initialization and retain-until-full; no reset/export/eviction API |
| D. Application — deferred | Separate later decision on transaction/recovery, review freshness/deletion reuse, clock/creation identity, permissions and losing-variant retention |

The owner explicitly approved A–C and the PR #92 format in the conversation,
including implementation with duplicate/conflict/restart/concurrency/write-failure
tests. D remains outside this implementation. No production transport or remote
protocol is approved by this document.

## SDK-independent checks

- Same logical receipt repeated before/after a separate-JVM reopen returns one ID;
  same-label/different-UUID and different-context cases keep their intended identity.
- Two differing same-UUID variants survive restart; each retained local/incoming
  value is intact. A repeat does not discard either variant or rewrite live drafts.
- Same-context simultaneous inserts deduplicate under lock; different candidates
  survive interleaving. Local edits and image-only changes require fresh review.
- Failed pre-replace writes preserve old bytes; post-replace failures are resolved
  by readback without duplicate receipts or false “applied” status. Lock contention,
  truncation/digest/version/UTF-8 errors and limits preserve original files.
- Existing v1 draft fixture remains byte-identical. A first inbox format needs an
  independently encoded fixture; any later format/migration needs old-to-new tests.
- Future apply tests must cover stale confirmation, delete/recreate, creation-time
  mismatch, earlier/equal/later remote times, exhausted revisions, current image
  retention and crash points between receipt/decision/draft persistence. Until the
  transaction design is approved, those are gates, not passing behavior.

## Historical proposal verification (before InboxStore)

`JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh` passed on 2026-10-25 using
`--release 8`. A focused new ImportReviewTest regression performs an assessment,
changes only the local image with unchanged metadata/time, verifies SAME_METADATA
still holds, and proves a stale save fails without changing the replacement image
or snapshot on reopen. This verifies existing behavior, not the proposed inbox.
All existing core/form/review/codec/storage/separate-process/100-device checks
passed. No emulator, Docker container, Android build, SDK or transport test ran.

## Approved implementation follow-up — 2026-10-25

[Inbox format 1](INBOX_FORMAT_PROPOSAL.md) now has a production codec/store,
independent golden-byte compatibility checks and storage/failure tests.
[INBOX_STORAGE.md](INBOX_STORAGE.md) records the current API, actual verification
and remaining gates. Application, migration of drafts and transport remain deferred.
