# In-memory import-decision preparation (#15/D)

`ImportDecisionPreparation` implements the memory-only review/preparation part of
[the adopted D contract](IMPORT_APPLY_CONTRACT_PROPOSAL.md). It uses existing TMMD
v2 state values, TMMR fingerprints, selected-field semantics and ImportReview
metadata equality. It introduces no persisted fields, schema or product policy.
This class performs no file operations. The separate [isolated v2 store](LOCAL_V2_STORAGE.md)
now commits Prepared choices under both owning locks. [The versioned planner candidate](VERSIONED_PLANNER_STORAGE.md) now adds migration/
explicit activation and versioned writers; this import preparation UI remains
unwired to a live transaction. Reset and trusted receive transport are not implemented. InboxReview and its existing read model
remain source-compatible and unchanged for the parallel read-only review view.

## Inputs and operations

Every factory takes a successfully loaded `InboxStore.Snapshot`, the explicitly
selected receipt UUID, and a supplied `LocalV2.Snapshot` with real store identity
and persistent state versions. It validates both complete bounded snapshots using
the existing in-memory codecs. It does not load files or fabricate empty stores
on load errors. Keep the supplied collections stable during calls. Trust/source
mapping remains the caller's precondition; an opaque context string is not proof.

| Factory | Adopted rule |
|---|---|
| `addLocally(inbox, receiptId, local, confirmReAdd)` | Requires an absent local UUID. Never-seen absence previews stateVersion 1; known deletion requires explicit `confirmReAdd=true` and advances the retained version. Local revision starts at 1, imageRef is null and incoming metadata/times are preserved exactly. No automatic resurrection or copy under a different UUID. |
| `keepLocal(inbox, receiptId, local)` | Requires an active local UUID. Different incoming metadata previews a keep-local decision while preserving the full draft, image association, revision and version. |
| `useSelected(inbox, receiptId, local, fields, localEditTime)` | Requires active local state and a nonempty set of the eight approved fields. Only selected values come from this one receipt; preserve UUID/createdAt/imageRef and unselected values. A real edit advances revision/stateVersion once and uses the exact previewed local Instant. |

Field values map to the existing mask: DISPLAY_ID 1, TYPE 2, STATUS 4, OWNER 8,
COMMENT 16, DEPLOYED_AT 32, POSITION 64, BEARING 128. Position selects both latitude
and longitude; selected null deployedAt/bearing is explicit removal. The caller's
field set is reduced to an immutable mask, never retained by reference. Existing
LocalV2Codec metadata projection is reused, without changing its byte contract.

USE_SELECTED blocks a creation-time mismatch. Other incoming times are retained as
receipt evidence and are not substituted for the local edit time. A real edit needs
a nonnull time >= the reviewed local updatedAt. Validate the combined draft: selecting
Gateway while retaining a local bearing fails until bearing removal is also selected.
No automatic field coercion, winner selection, timestamp max or silent rebase exists.
Counter exhaustion rejects mutations rather than wrapping; unchanged/keep choices
need not increment exhausted counters.

Complete SAME_METADATA for Keep/Use yields ALREADY_EQUAL, no decision-history work
and no timestamp/revision/version change, including with a local image. If incoming
metadata differs but the selected editable values do not, preview KEEP_LOCAL with
the requested nonzero mask, no edit time and no draft change. This is the existing
USE_SELECTED/KEPT format rule. An unused supplied edit time is normalized to null
because no edit is proposed. Add always requires absence, even if an active record
happens to compare equal. Receipt variants/provenance remain available unchanged.

## Exact preview, one-use preparation and Cancel

Factories return a pending review with immutable `preview` (`Plan`). It contains:

- A newly generated operation UUID, local store ID and inbox ID.
- The original selected immutable receipt, preserving its ID/context/arrival time,
  incoming metadata and historical observations without treating them as current.
- The complete reviewed local state, including persistent presence/version and full
  draft revision/image association, plus a defensively copied TMMR fingerprint.
- Original action, fixed selection mask, effective edit time, an exact hypothetical
  resulting full local state and transient Effect (ADD_LOCAL, UPDATE_LOCAL,
  KEEP_LOCAL, ALREADY_EQUAL). Effects are previews, not stored success statuses.

`isReAdd()` exposes the known-deletion case; `needsDecisionRecord()` is false only
for ALREADY_EQUAL. The full image association stays in this private in-memory
binding/result; future history encoding must strip raw historical handles and keep
the approved fingerprint/counters. The plan is not a committed `LocalV2.Decision`.
No current snapshot is modified, no receipt observation is appended and no ID is
claimed committed merely because a preview exists.

After the user reviews the exact preview, `prepare(freshInbox, freshLocal)` consumes
the pending review once, validates the supplied fresh snapshots and returns a
separate `Prepared` type with the same `plan`. Preview and prepared types stay
distinct. Any failed attempt is also consumed; unavailable/stale data needs a new
review. `close()` is Cancel, is idempotent and prevents pending preparation. It
performs no I/O. It does not erase preview memory, undo a result or revoke an already
returned Prepared object. No pending review is serialized or restored after restart.

Checks bind to the exact selected receipt ID/context/time/incoming metadata, inbox
ID, local store ID, target presence/version and full reviewed local metadata,
revision/imageRef plus fingerprint. A different source/store, edit, image change,
delete/re-add or absent → create → delete invalidates the old review. Numeric-zero
comparison follows existing rules; a real committed zero-sign mutation must still
advance the persistent version. Unrelated UUID writes/new receipts or additional
historical observations do not invalidate the unchanged selected variant/target.
The review never switches to another variant or rebases onto a newer local revision.

`Prepared.requireCurrent(freshInbox, freshLocal)` repeats the same pure check for
later integration. It is not an atomic commit or durable consent token: changes
can occur immediately afterward. A future owner of the transaction must repeat
validation and enforce its lifecycle, source authority, duplicate-operation readback,
capacity and actual application under the agreed lock boundary. This PR neither
reserves capacity nor inserts history nor implements that transaction. It validates
the supplied current snapshots, not an automatically applied/serialized new store.

Invalid action/state/selection/time/combined fields are argument errors with fixed
messages; stale binding or consumed/cancelled review is IllegalStateException.
Codec failures remain IOException. Null arguments/field entries are programmer
errors. No raw metadata, image references, credentials or file paths are logged.
Callers must not log plans or exception input objects.

## Later connection to the read-only review view

Use the existing `InboxReview.Variant.receipt.id` for selection. Do not require any
of these factories to view the shared picture. No method was added to InboxReview,
and its classification/grouping order is unchanged. The future adapter must obtain
a genuine versioned local snapshot independently: **do not synthesize v2 store IDs
or state versions from v1 draft revisions to enable the UI**. There is no activated
v2 store yet. That availability gate keeps the parallel view read-only.

A future integration can inspect `review.preview.result.draft`, full current image
and `effect`, allow Cancel, then invoke `prepare` only after the explicit action.
Only a later reviewed transaction component may accept Prepared and apply anything.
Keep receipt/stored-decision coverage, source authentication, storage initialization
and all file/Android/lifecycle behavior outside this preparation interface.

## Verification — 2026-10-27

`JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh` passed on macOS using
`--release 8`, assertions and synthetic data only. ImportDecisionPreparationTest
covers each selected field and all fields, optional null/type-bearing combinations,
Add/re-add/keep/equal/unchanged previews, exact counter/time/image binding, old
foreign times, creation mismatch, backward/missing edit time and exhaustion.

It rejects stale metadata/images even if supplied counters are reused, changed
store/source/receipt identities and absent/deleted/recreated states, consumes stale
attempts, and allows unrelated changes/appended evidence. Prepared checks remain
stale-sensitive after return; concurrent preparation attempts have one winner.
Memory-only hypothetical decision frames fit the unchanged codec. Existing v1 draft,
inbox and synthetic image bytes remain unchanged after review/prepare/Cancel.
All existing core/inbox/v2/old-contract/publication/envelope/100-device tests pass.

No emulator, Docker, Android/SDK build, image-library suite, server or transport
trial ran. This proves supplied-memory behavior and preservation only; it does not
prove v2 durability, migration, atomic stale-review protection or ATAK compatibility.

## Android preview component follow-up — 2026-10-27

[The memory-only decision UI](IMPORT_DECISION_UI.md) now consumes this unchanged
API for explicit choices, exact result/time/image preview and fresh one-use
preparation. It has an optional transition from the read-only browser, with no
commit callback, production loader, activation or persistence. The caller must
provide genuine versioned state and source/access policy; no v1-derived identity
or state-version fallback is introduced.
