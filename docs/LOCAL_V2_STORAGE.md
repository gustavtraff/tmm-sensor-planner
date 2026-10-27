# Isolated local v2 storage and atomic import — #15/D

The owner authorized this core-only implementation on 2026-10-27, after approving
[the logical D contract](IMPORT_APPLY_CONTRACT_PROPOSAL.md) and the exact
[TMMD v2 / TMMR formats](LOCAL_V2_FORMAT_PROPOSAL.md). `LocalV2Store` implements
file persistence and the prepared import transaction using those existing bytes.
No format, fixture, domain schema, inbox encoding or outgoing protocol changes.

**Not activated in the app.** Use an explicitly selected private test directory.
The live planner and image adapters still use DraftStore v1. An explicit
[core migrator](LOCAL_V2_MIGRATION.md) now exists, but no activation record,
cleanup/reset, production import entry or transport is implemented.
Do not point this store at a live installation, infer versions from revisions,
or initialize it as a fallback for missing/corrupt/v1 data. No APK version bump or
new signing archive is needed for this unwired core slice.

## Store API and ordinary edits

All operations use the permanent `drafts.lock`, with nonblocking exclusive OS file
locking. There is no cached snapshot, automatic retry or per-instance monitor wait.
Another thread/instance/process may receive BUSY. Keep the lock file in place.
Directory ancestors and exclusive ownership of the directory are trusted
preconditions; symbolic links at the owned directory/data/lock and nonregular
data/lock paths are refused. This is not protection against hostile path replacement.

- `initializeNew()` is an explicit start without history. It refuses every existing
  `drafts.bin`, including invalid/unsupported data. Successful initialization writes
  the approved 64-byte empty frame with a new random store UUID. After an uncertain
  result, load and inspect; do not initialize again as recovery.
- `load()` validates the entire bounded committed frame. Missing/corrupt/future/v1
  files are errors, never empty collections. Abandoned temp files are ignored and
  never promoted, repaired or cleaned on startup.
- `save(storeId, expectedState, draft)` checks the actual store identity and the
  target's presence, persistent version, full metadata, imageRef and revision under
  lock. New/re-added drafts require revision 1; active updates require exactly the
  next revision, unchanged createdAt and nondecreasing updatedAt. Every save advances
  stateVersion once, including image-only or byte-identical edits. Deleted re-adds
  retain/advance the deleted slot; callers must obtain explicit re-add consent.
- `delete(storeId, expectedState)` requires the exact active state and retains a
  deleted slot at the next version. It does not free capacity or delete an image.
  Long.MAX_VALUE versions/revisions never wrap. A save/delete's uncertain outcome
  needs inspection of the current state; these ordinary edits have no import ID.

Slots retain first-create order. Different UUIDs may share visible labels. Neither
save nor import opens, decodes, copies, cleans or verifies image bytes. Metadata
history strips raw historical handles; current active slots retain them unchanged.

## Prepared import transaction

`commit(inboxStore, prepared)` accepts only `ImportDecisionPreparation.Prepared`,
not an arbitrary caller-constructed command. The explicit choice must remain in
its original live session; do not serialize it or execute it from arrival callbacks.
The caller independently enforces current trusted-source/access policy. A context
string, fingerprint, readable file or Prepared object does not authenticate a source.

1. Acquire **inbox lock, then drafts lock**, and validate both committed frames.
   InboxStore exposes only a package-private callback for this coordinator; public
   independently locking load/receive methods are not nested. All locks are released
   on busy/failure. No UI/network work occurs under these locks.
2. Check store identity and operation ID before stale-state validation. An exact
   prior binding returns ALREADY_COMMITTED without rewriting anything; another
   binding under the same ID fails INVALID. Compare typed receipt/action/before/
   result/time/counter/mask values and fingerprint, not arbitrary serialized bytes.
3. For a new ID, repeat Prepared's complete current-inbox/receipt/state/fingerprint
   checks while both locks are held. Appended observations, other receipts and
   unrelated UUID edits do not stale the selected immutable receipt/target. Target
   metadata/image changes, delete/re-add, source receipt or dataset identity changes
   fail STALE. Never reload/rebase or create a new command automatically.
4. Follow the approved preview exactly: Add copies incoming metadata/times into
   revision 1 with no image; selected real edits keep current image/createdAt and
   use the exact previewed local edit time; Keep records a choice without changing
   the draft/version/revision. Unchanged selected values retain their requested
   nonzero mask as a Keep decision. Fully equal metadata is ALREADY_EQUAL with no
   history row or write. No images or inbox records are changed.
5. Append metadata-only incoming/displaced/result evidence and update the slot in
   **one** complete v2 frame. Encode/validate its full size and independent count
   limits before temp creation. File-sync a same-directory temp and request atomic
   replacement of `drafts.bin`. No copy/delete fallback, cross-file commit, pending
   intent, inbox resolved bit or separate ledger sidecar exists.

`Result` distinguishes COMMITTED, ALREADY_COMMITTED and ALREADY_EQUAL. Its decision
is historical metadata-only evidence (null for equal); its `current` is the full
current slot. After a later edit/delete, replay returns that newer current slot,
never restores the historical result. No result implies sharing, feed publication,
transport acknowledgment or a globally resolved conflict.

## Errors, capacity and readback

`Failure` is an IOException with a typed `Reason` and fixed safe text, with no
raw paths, input values or causes. BUSY means lock contention, STALE requires fresh
review, INVALID covers invalid projected/domain/text/encoded-size input or reused
operation binding, and CAPACITY covers projected slot/history count overflow.
The codec deliberately exposes one safe validation error, so an encoded-byte-limit
failure currently reports INVALID rather than a distinct size diagnosis. UNKNOWN
covers unavailable/unreadable evidence or filesystem errors that may occur after
commit. Do not infer rollback or show an unconditional “not saved” on UNKNOWN.

The independent 1,000-slot (including deleted), 1,000-operation, 16 MiB frame and
65,536-byte text bounds are unchanged. No evidence eviction is allowed. Full valid
stores remain readable and permit exact operation replay/no-write equality; existing
slot updates can proceed if their projected frame fits. A future app adapter must
add the adopted capacity usage/warning UI (including the 80% threshold), preserve
unsaved edits and explain slot/history exhaustion. This backend does not implement
that UI or the separately gated mission-cleanup operation.

`readback(expectedStoreId, operationId)` takes **only drafts.lock**, never the inbox
lock. It never repairs or reapplies anything:

| Status | Meaning |
|---|---|
| COMMITTED | Valid same-store history contains this operation. Return its historical decision **and current slot separately**, including later deletion. |
| NOT_COMMITTED | A complete valid same-store read has no such operation. This is not permission to rebase or retry under a new ID. |
| UNKNOWN | Missing/corrupt/unsupported/unreadable/different-identity or busy local evidence. Do not initialize/reset or infer absence. |

Inbox loss cannot erase embedded local commit evidence. An earlier commit can be
read back without the inbox; `commit` still requires readable locked inbox evidence.
A same-session retry after readback uses the original Prepared/ID/binding; new
commands must pass every original freshness check. Restart loses uncommitted
in-memory consent and requires fresh review. Parent-directory fsync, sudden power
loss and old-backup rollback remain outside the process-restart guarantee.

## Verification — 2026-10-27

`sh scripts/test.sh` passed with Temurin 17.0.20.1+1 / Java 8 target and assertions.
`LocalV2StoreTest` exercises real temporary files and permanent locks with synthetic
metadata/images, including:

- Add, explicit deleted re-add, selected edit, Keep, unchanged selection and equal
  no-write; stable identity, ordered slots/history and restart/reopen.
- Exact local image association/byte preservation and stripped historical handles;
  additional receipt observations/unrelated edits accepted; image-only changes,
  absent-create-delete and delete/recreate reject old prepared/ordinary writes.
- Exact-ID replay and ID-binding reuse rejection; historical readback after newer
  edit/delete, full history and missing inbox; no reapplication of historical results.
- Thread races (one different variant commits), same-process locks, a separate JVM
  holding the drafts lock, and lock release when the second lock is busy.
- Injected partial-write/sync, unsupported atomic move, post-replacement and cleanup
  failures, plus child JVM halts before/after atomic rename. Reopened frames contain
  either both result/evidence or neither; abandoned temps are not promoted.
- Missing/corrupt/future/v1 files unchanged, initialization refusal, symlink refusal,
  count caps, counter exhaustion, and an exact 16 MiB valid file whose oversized
  projected edit is rejected before any temporary file/replacement.

The existing core/inbox/v1/format/preparation/publication/restart/100-device suites
also pass. Fixed independent fixtures are unchanged. No Docker, emulator, Android/
SDK build, phone, image-library suite, remote transmission or CI wait was run.
These are core transaction/process tests, not migration or ATAK acceptance.

## Next integration boundary

Before activation, inventory and migrate every writer together: LocalPlanner's
creation/form/map-save path and DraftAttachments' attach/replace/remove/delete
path currently call v1 revision-only writes. The isolated [v1-to-v2 migrator](LOCAL_V2_MIGRATION.md)
and interruption tests now exist. Add explicit dataset activation and expected-state
propagation through all callers. Then connect the optional
review/choice UI to a background transaction with source/lifecycle checks, capacity
feedback and unambiguous current-versus-historical result handling. Genuine source
context/transport and signed two-client/phone verification remain separate gates.
