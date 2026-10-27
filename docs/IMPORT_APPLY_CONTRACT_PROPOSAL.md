# Private import application and recovery — D proposal (#15)

**Status: owner-approved logical principles, including capacity feedback and safe
local mission cleanup, recorded explicitly in the conversation on 2026-10-25.**
Exact local v2 byte/fingerprint encoding and #102 fixture material were subsequently
approved explicitly on 2026-10-27; in-memory implementation is linked below.
The separate [isolated core store and prepared transaction](LOCAL_V2_STORAGE.md)
now implement file persistence. An [isolated core migrator](LOCAL_V2_MIGRATION.md)
is also implemented. The [versionCode 20 integration candidate](VERSIONED_PLANNER_STORAGE.md)
adds explicit app activation and versioned writers; signed runtime acceptance and
cleanup/reset remain separate work.
Reviewed baseline: main `b69eabd` (#98). A–C, InboxStore format 1 and read-only
InboxReview remain implemented and unchanged. No Android adapter, outgoing
metadata/preparation, SDK or transport changes are included.

## Exact-format follow-up — approved 2026-10-27

[The local v2 byte/fingerprint format](LOCAL_V2_FORMAT_PROPOSAL.md) and independent
#102 fixtures have explicit owner approval. [Production in-memory code](LOCAL_V2_CODEC.md)
now encodes/decodes the frame and computes fingerprints against those fixed bytes.
There is no file writer/migrator, activation, apply/reset or Android/transport
integration. The documented v1 history gap remains. Cleanup activation/ownership
encoding remains a separate review gate; inbox format 1 is unchanged.

## Preparation follow-up — 2026-10-27

[The SDK-independent import preparation interface](IMPORT_DECISION_PREPARATION.md)
now provides exact Add/re-add/keep/selected-value previews and one-use preparation
against supplied snapshots. It binds receipt/store/state/image evidence and
revalidates fresh inputs. It adds no file writer, migration, activation, application,
Android or transport, and leaves InboxReview compatible. Preview/Prepared objects
are not committed decisions; the later transaction must recheck atomically.

## Adopted user flow and scope

The [owner-approved receiving flow](https://github.com/gustavtraff/tmm-sensor-planner/issues/15)
was recorded on #15 on 2026-10-25: display the shared picture from a selected,
trusted feed without per-device acceptance; require explicit **Add locally**
(`Lägg till lokalt`) to enter the private editable collection; explicitly compare
and choose when received metadata differs from an existing local draft. Identical
metadata needs no repeated operator work. Preserve current images and incoming
variants; no automatic overwrite, winner selection or resurrection.

The issue comment records the owner decision; this proposal does not infer new
permission from feed membership, owner text, timestamps, a UUID or a merged PR.
Visibility is separate from local ownership/application. A missing local UUID is
not inherently a conflict. Source authentication/mapping and shared-map display
still need their own adapter evidence. This proposal changes none of those APIs.

## What existing code proves, and what it cannot prove

- [ImportReview](../core/src/main/java/se/tmm/sensorplanner/core/ImportReview.java)
  and [InboxReview](IMPORT_REVIEW.md#read-only-inbox-review-15--2026-10-25) compare
  supplied immutable snapshots only. SAME_METADATA excludes image handles and
  local revisions; no result is a commit/freshness token.
- [DraftStore](LOCAL_STORAGE.md) validates revision +1, unchanged createdAt and
  nondecreasing updatedAt under `drafts.lock`, then atomically replaces one file.
  Its `load`/`save` pair is not an expected-state transaction. Delete removes all
  evidence of the UUID, and explicit recreation starts at revision 1.
- The new **legacy characterization** in DraftStoreTest deletes/recreates the
  exact same record and obtains byte-identical snapshots. A previously prepared
  save and an old revision-based delete can then succeed. This is not approved D
  behavior: revision, metadata, image handle and even a full snapshot hash cannot
  detect this intervening history in v1. InboxReviewTest already demonstrates
  recreation/reassessment, while ImportReviewTest covers image-only stale saves.
- [InboxStore tests](INBOX_STORAGE.md#verification--2026-10-25) cover durable
  variants, locking, pre/post-replace failures and process halts. They do not make
  a draft write and a receipt-resolution write atomic. [DraftAttachments](../app/src/main/java/se/tmm/sensorplanner/plugin/images/DraftAttachments.java)
  cleans images against live drafts only; historical handles would not pin bytes.

Consequently D needs persisted local mutation identity and an atomic home for the
local result **and** decision/conflict evidence. A sidecar marker written after a
v1 save, or calling two public store methods in succession, does not supply either.

## Proposed operations and exact reviewed input

Names below describe a contract, not new public APIs. Prepare from successfully
loaded stores and an explicitly selected immutable receipt. A prepared command
binds a fresh operation UUID to: local store identity; target UUID and expected
local state version (or never-seen absence); exact reviewed full local draft when
present (including revision/imageRef); inbox identity, receipt ID, context and exact
incoming M; action, selected fields, complete result preview and chosen local edit
time when applicable. It contains no credentials. Existing M equality remains
ImportReview's; local freshness additionally requires the persisted state version.

Prepare/Cancel writes nothing. A prepared command is in memory only, expires on
process/session loss and is never automatically executed after restart. A caller
must independently revalidate trusted source/availability at confirmation; storing
context does not authenticate it. No arrival callback may invoke these operations.

| Action | Required local state and proposed result |
|---|---|
| Add locally | Explicit confirmation while the UUID is absent at exactly the reviewed absence version. Copy incoming M exactly into revision 1, with imageRef null; retain incoming creation/update times and show them as received values, without claiming authorship or local creation time. |
| Add after known local deletion | Same operation, but show prior local deletion and require explicit re-add confirmation. Keep the UUID; advance its persistent state version rather than restart it. A routine arrival/retry never re-adds. |
| Use selected received values | Active UUID at exactly the reviewed state. One selected receipt and an explicit field selection produce one validated local result. Keep current UUID, createdAt and imageRef; increment local revision once for a real metadata edit. Never combine different receipts implicitly. |
| Keep local | Explicit choice for this exact receipt and local state. Record durable review evidence without changing draft fields/revision/state version. It is neither deletion nor rejection of every future variant. |
| Already equal | Fresh ImportReview SAME_METADATA requires no prompt, write or revision increase. Keep the receipt visible and preserve its separate provenance. |

For an existing UUID, propose editable selections for displayId, type, status,
owner, comment, deployedAt, position (latitude/longitude together), and bearing.
Null removal is an explicit selected value. Validate the entire proposed TmmDraft:
for example changing PIR to Gateway cannot silently discard an unselected bearing.
No field-level automatic merge or source-ranking rule is introduced.

**Owner-approved time/identity principles:** incoming createdAt differing from
an existing local createdAt blocks received-value application in this first slice;
keep both variants for separate identity review. Do not silently change creation
time. For other manual field edits, use an explicitly previewed local edit Instant
as updatedAt, at least the current updatedAt, rather than adopting a foreign edit
clock or silently applying `max(local, remote)`. Earlier incoming updatedAt does
not forbid choosing a useful field, nor prove it stale. Both original timestamps
remain in receipt/decision evidence. A backward local clock fails with a clock
error and requires a new preview; never rewrite the confirmed time during commit.
If no selected editable value changes, record a keep-local decision without a
timestamp/revision bump. This manual-edit policy does not change
ImportReview equality, TmmDraft invariants or agent 1's outgoing format.

## Necessary storage change proposed before any writer

Recommend **a version 2 local draft snapshot**, replacing `drafts.bin` atomically
under the existing permanent `drafts.lock`. Keep `inbox.bin`/TMMI format 1 separate,
append-only for this slice and unchanged. Put the D decision and losing local
metadata in the same local snapshot as the changed draft. No APPLIED/resolved flag
is written back to the inbox, so there is no required two-file commit or recovery
journal. One successful rename is the application commit point.

This logical design is now concretized by the explicitly approved
[exact v2 bytes and independent fixtures](LOCAL_V2_FORMAT_PROPOSAL.md).
The in-memory codec does not introduce its file writer or migrator. Do not increment the domain
TmmDraft schema or change any production wire version merely to version this file.

| Persisted component | Minimum contract |
|---|---|
| Local store identity | Random UUID assigned on explicit new-v2 initialization or successful v1 migration; stable across writes/restarts. Different identity invalidates every old command. |
| UUID slots | At most one slot per UUID: positive long stateVersion plus either an active full TmmDraft or a deleted marker. Never-seen absence means no slot. Every create/edit/image association change/delete/re-add increments stateVersion under lock; first create starts at 1. Deleted slots remain. No wraparound at Long.MAX_VALUE; reject mutation. |
| Active draft | Existing validated fields/revision/imageRef. Revision still follows existing local write rules and may restart at 1 on explicit re-add; it is not the persistent stateVersion. |
| Committed operation record | Operation UUID; action; full immutable command binding needed to reject ID reuse; selected receipt identity/context/time and incoming M; before-local M or explicit absence and its local revision/stateVersion; selected fields; chosen edit time; resulting M/revision/stateVersion or unchanged-state outcome. No historical image handles or bytes. |

The operation binding must compare the exact full reviewed image association
without retaining a historical raw handle. Propose a SHA-256 fingerprint of a
separately specified canonical full-local-state encoding in the command/record,
with stateVersion as the primary freshness guard. This fingerprint is private
integrity/binding data, not authentication, a metadata deduplication key or an image
content hash. Its encoding needs independent fixture review with the v2 format;
a hash alone cannot replace the version/deleted slot. All new fields stay local.

Both the selected incoming and displaced local M survive in the operation record;
the resulting M makes a partial-field choice auditable. Existing inbox variants
and observations remain untouched. This is bounded metadata history, not image
pinning or a journal of all ordinary local edits. A choice concerns one receipt;
other source contexts/variants remain available for review with no inferred winner.

Propose limits: 1,000 UUID slots **including deleted slots**, 1,000 committed
operation records, 16 MiB total frame including checksum, 65,536 UTF-8 bytes per
string plus existing domain limits. Each operation has only the fixed before,
incoming and result metadata projections above, not unbounded nested lists. Full
storage rejects new changes explicitly without pruning history or tombstones;
reads and committed-operation readback still work. No reset/retention/export/restore
API is implemented by this PR. Filling deleted slots can block new UUIDs even
with few active drafts; capacity feedback and the separately specified explicit
local mission cleanup are required below. Existing active-slot updates can continue when
the slot-count cap is reached if all other bounds permit them.

### Capacity feedback — approved requirement

These are **per-device private storage** limits, not server quotas or a count of
all devices on a feed. Show active plus deleted UUID slot usage, operation-history
usage and committed frame bytes as separate bounded dimensions. Inbox format 1
has its own existing combined receipt/observation budget (1,000 entries, not 1,000
receipts), string and file limits; do not label it as the proposed UUID-slot limit.
A string/domain/storage error may prevent one input even with spare total capacity.

Provide a persistent, nonmodal local-storage warning when any applicable count or
file-size dimension reaches 80% (proposed initial UI threshold). Show which limit
is near/full with counts/bytes and what is still possible; avoid repeated arrival
popups or per-device acceptance prompts. Unreadable usage is unknown/error, not 0%.
Check the projected serialized size/counts before each commit. At a limit, report
that the attempted change **was not saved**, retain the user's edit/selection and
all previously committed data, and do not show success or silently evict evidence.
An ambiguous commit still uses UNKNOWN/readback, never a false NOT_SAVED message.

Reads, review and committed-operation readback remain available on valid full
storage. Existing updates/no-ops may proceed if the particular operation fits all
limits. Warn that reregistering after deletion does not free a slot and old choices
consume history. Explain the safe local mission-cleanup option and its data loss;
never launch cleanup automatically or as a generic one-click error recovery.

[Safe local mission cleanup](LOCAL_MISSION_CLEANUP_PROPOSAL.md) is a separate future
operation with explicit multi-step confirmation. It intentionally discards local
receipt/deduplication/decision/deletion history and creates fresh identities. It
does not increase server capacity or delete published objects. Cleanup's physical
layout/activation record and interruption fixtures join the exact-format review
before any reset writer; the inbox's existing frame itself remains format 1.

Every local writer must participate: ordinary creation/edit/delete, map/form edits,
image attach/replace/detach and later imports. Future expected-state save/delete
must bind store identity + slot version; allowing a revision-only path to update
v2 would reintroduce stale writes. Inventory those callers before rollout. An
unrelated UUID edit does not invalidate a target's confirmation. Keep-local history
writes do not advance the target version because they do not change its local state.

### Migration, initialization and rollout gates

- Under `drafts.lock`, read/validate the complete v1 file; preserve every active
  field, revision, insertion order and imageRef. Assign store identity and version
  1 to each existing active slot, with empty operation history. Commit v2 in one
  synced-temp/atomic replacement. Do not infer older deletion history: it is lost
  in v1; warn/document that migration establishes protection only from that point.
- Before replacement retain v1 unchanged; after replacement v2 is authoritative.
  No independent migration-complete sidecar. Legacy v1 binaries already reject
  version 2 and must not be allowed to recreate/fall back to an old live copy.
  Image ownership is unchanged and migration performs no cleanup.
- Missing local storage after v2 adoption is an error, never an implicit empty
  collection. Explicit initialization without history must be separately confirmed;
  never auto-initialize during Add. Corrupt/future/symlinked/ambiguous stores are
  kept in place. Losing app data or restoring an old backup is outside guarantees.
- No mixed v1/v2 writers or activation while current adapters still lack expected
  slot versions. A core-only prototype can test the new format in private temp
  directories after approval; Android rollout and all writer migration are later
  reviewed work. Migration must also fail without rewriting v1 when additional
  v2 identity/history overhead would exceed the total file limit. No such
  writer/migrator is implemented by this proposal.

## Proposed confirmation transaction and outcomes

1. Acquire nonblocking **inbox lock, then local draft lock**; if either is busy,
   release any acquired lock and report busy. No code may acquire them in reverse.
   Existing normal writes need only their own lock. The future coordinator must
   use internal locked primitives, not nest today's public, independently locking
   methods. No UI/network work runs while holding these locks.
2. Validate both full committed frames. First check the local operation ID: an
   identical already-committed command returns its historical result without a
   write; ID reuse with a different binding fails. If required store evidence is
   unavailable, report unknown instead of constructing a successful empty history.
3. For a new command, require exact inbox identity/receipt/context/incoming match
   and local store identity/slot stateVersion/full reviewed local match. The selected
   receipt's historical observations need not be unchanged; appended observations
   and other receipts do not invalidate the immutable selected variant. Recheck
   all domain/time/revision/version/capacity constraints. A new local creation,
   edit, image change, delete/re-add or store identity change rejects as STALE;
   never reload a newer revision and silently rebase the old confirmation.
4. Encode the complete proposed local snapshot with result, version/deleted-slot
   evidence and committed operation record. Validate sizes before temp creation,
   write a same-directory temp, flush/file-sync, then atomic replace; no fallback.
   No image write/deletion or inbox mutation is part of this operation.
5. Return a confirmed local result only after known commit. UI must distinguish
   **added/updated locally**, **local decision recorded**, **already equal**, busy,
   stale, invalid/clock/capacity failure and **outcome unknown**. None means Shared,
   feed published, sender acknowledged, globally resolved or source authenticated.
   Errors must use fixed safe messages, without raw metadata, image handles, source
   credentials or filesystem paths in logs/UI reports.

After a later local edit/delete, an operation record proves only what committed
then. Readback must also report the current slot separately, e.g. “added earlier;
now deleted”, never imply that a historical successful Add is still present.
Identical metadata remains prompt-free. A keep-local/partial-use decision can avoid
re-asking for that exact receipt while its resulting local stateVersion remains
current; other variants or later local mutations require fresh assessment. The
record never hides the shared picture, suppresses source evidence or auto-applies
new receipts. No durable intent exists to resume an uncommitted action on startup.

## Interrupted writes and recovery matrix

| Interruption / failure | Required committed state and caller behavior |
|---|---|
| Cancel, invalid/stale/clock/capacity or busy before writing | No draft/history/image/inbox change. Fresh review after stale; no automatic retry/rebase. |
| Encode, temp write or sync fails; process dies before rename | Old complete local snapshot/ledger remains authoritative. Ignore abandoned temps; never promote them. A missing operation is NOT_COMMITTED only after a valid same-store read. |
| Atomic replacement unsupported/fails before commit | Preserve old file, no copy/delete fallback. Reopen to establish state if outcome is uncertain. |
| Process dies after rename, or replacement/cleanup reports an error after commit | New local draft and operation record reopen together. Outcome is initially UNKNOWN; query the same store identity + operation ID. An identical committed ID is not applied twice. |
| Later edit/delete occurs before readback | Historical operation still COMMITTED; return the current slot separately. Never restore the earlier result automatically. |
| Corrupt, missing, future or different-identity local store | UNKNOWN/history unavailable, not NOT_COMMITTED. Do not initialize/reset, blindly retry under a new ID, or undo local state. |
| Inbox unreadable or identity/receipt replaced | No new apply. Local ledger readback can still establish an earlier committed operation from its embedded evidence; do not depend on the inbox to prove that local commit. |
| Migration interruption | Valid committed v1 before rename, or complete valid v2 after; keep unsupported/corrupt originals and ignored temp files for a human recovery decision. |

Expose committed-operation readback as a **local-store-only** operation taking
only `drafts.lock`, so inbox loss cannot erase proof already committed there. This
readback never reapplies or repairs a draft. A process restart loses an uncommitted
in-memory command; re-open review for a new explicit choice. Retrying the same
command is allowed only after readback and with the same operation ID/binding;
if absent, all original stale-state checks still apply. Do not persist or reuse a
prepared command across sessions as standing consent.

Metadata application preserves the exact reviewed current imageRef and never
cleans images. Concurrent image association changes advance slot version and fail
the old review. Physical missing/damaged image bytes remain an image error; do not
clear the handle or claim metadata acceptance delivered an image. Ordinary later
image cleanup must continue to consult current live drafts, preserve copies on
ambiguous saves/read failures, and ignore metadata history as attachment ownership.
Parent-directory fsync/power loss, rollback of old backups and operational source
permissions remain outside the process-restart boundary; atomic rename/file sync
is not advertised as protection against those failures.

## Adopted principles and remaining implementation gate

1. Owner-approved: a single local v2 commit for draft + durable choice/conflict evidence,
   permanent per-UUID versions/deleted slots, and migration of **all** local writers;
   keep inbox format 1 unchanged instead of a cross-file journal.
2. Owner-approved: the field/time/identity policy, including creation-time mismatch
   blocking, explicit local edit time and no automatic recreation/field coercion.
3. Owner-approved: per-receipt/state-version decision coverage and bounded retained metadata,
   including slot/history limits, no eviction and the legacy deletion-history gap.
   Required capacity feedback and separately confirmed local mission cleanup are
   included; implementation details/physical format remain unimplemented.
4. Exact v2 bytes/fingerprints and independent #102 fixtures are now explicitly
   adopted. In-memory codec verification is documented separately; file writing,
   migration, expected-state operations and cleanup activation remain later work. No approval of a remote protocol is implied.

## Required verification versus evidence actually run

Future acceptance (not implemented or claimed passing):

- Add absent/new versus known-deleted UUID; confirmation before another process
  creates it; different UUID/same visible label; same command duplicate/restart.
- Two simultaneous chosen variants: one committed local mutation, other STALE;
  unrelated UUID writes allowed. All received/displaced metadata retained, no winner
  inferred. No-op and keep-local choices do not bump local revision or alter images.
- Local metadata/image edit, delete/recreate at the same revision **and exact old
  bytes**, and absent → create → delete between review and confirmation all reject
  the old version. Exercise revision/stateVersion exhaustion and store replacement.
- Earlier/equal/later foreign times, creation mismatch, backward local clock,
  explicit nulls and incompatible type/bearing selections; exact preview/time
  binding. Keep current image reference/bytes on update; new Add has no image.
- Inject failures during encoding/temp write/sync/replace/cleanup, halt separate
  processes before/after rename, test lock contention/order, committed readback
  after inbox loss and after later local mutation, and reject operation-ID reuse.
- Exercise capacity warnings/full/unknown usage, retention of unsaved edits, no
  false NOT_SAVED after an ambiguous commit, and the separate cleanup interruption
  and stale-worker/confirmation matrix. These are not implemented tests.
- Independent v1→v2 migration fixture preserves every old field/image handle;
  migration interruption, downgrade rejection, missing/corrupt/future files, bounds
  and no promotion/reset. Verify every normal writer rotates slot state atomically.

Actual verification for this proposal is recorded in [TESTING.md](TESTING.md).
The existing suite/legacy characterization supplies a baseline, not evidence that
D's future transaction, format, migration or recovery implementation works.
