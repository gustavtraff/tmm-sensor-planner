# Read-only inbox decision coverage (#15)

`InboxDecisionCoverage.assess(inboxSnapshot, localV2Snapshot)` associates current
received variants with existing committed decisions and the supplied current local
state. It is SDK-independent, performs no file I/O and changes no decision, receipt,
local state or format. It does not call LocalV2Store, an import writer, Android or
transport. InboxReview and the parallel confirmation/save flow remain unchanged.

## Model and matching

The result carries `inboxId`, local `storeId`, immutable `entries` and immutable
`allDecisions`. There is one entry per receipt in inbox insertion order, including
unreviewed variants. `allDecisions` preserves the complete supplied local decision
history in order, including decisions from another inbox or receipts no longer
present in this inbox. Nothing is deleted, hidden, deduplicated or selected as a
winner. Decision objects, receipts and draft values are existing immutable values.

Each entry exposes the original `receipt`, full supplied `currentLocal` state,
all matching `decisions` as Review records, and `covered`. A Review exposes its
original `decision` plus `current`. Receipt association requires **all** of:

- Exact inbox ID and receipt ID.
- Exact case-sensitive source context and first-observed Instant.
- Exact device UUID/target and incoming metadata under existing ImportReview
  equality (including nanoseconds/optionals; numeric +0/-0 match).

Equal labels or incoming metadata under another context/receipt/inbox never inherit
a choice. Receipt arrival time participates only in binding the selected receipt,
not causal ordering or author authority. Local observations appended to a receipt
do not change that identity. Source authentication is still a caller precondition.

A matching decision is `current` only when the target is ACTIVE at exactly that
decision's `afterVersion`, with matching `afterRevision` and result metadata.
`covered` is true if at least one exact-receipt Review is current. Later mutations,
including image association edits, must advance the persistent local version under
the existing contract. Delete/re-add or restoring old field values cannot revive an
older choice; a restarted local revision alone is insufficient. A new decision for
one receipt never covers the other retained variants for that UUID.

All earlier matches remain present with `current=false`. Multiple decisions can
remain current at one version (for example keep-local choices do not advance the
version); every match is retained. A historical Add can coexist with a now-deleted
current slot without claiming that the object is still local. `currentLocal` is
separate from metadata-only historical results and retains the actual supplied
local image association.

## Validation and limits

Both complete supplied snapshots are validated with the existing bounded in-memory
codecs before producing any coverage. This also checks current result/image
consistency under TMMD v2 rules: a hand-built same-version image/metadata mismatch
fails closed rather than being marked covered. No historical image handle is
invented. Older fingerprints and physical image bytes are subject to the existing
contract limits; coverage does not authenticate history or inspect image files.

Pass successfully loaded, stable snapshots. Null arguments are programmer errors;
invalid/ambiguous frames give existing safe codec failures. Do not turn a missing,
unreadable or unsupported store into fabricated empty history. Empty valid inboxes
are allowed and still preserve `allDecisions`. No arbitrary input values are logged.
The existing bounded receipt/decision counts cap the simple matching scan.

Coverage is a **point-in-time read model**, not durable consent, a confirmation
or write token, a history-repair mechanism or an atomic read across stores. It can
become stale immediately. Later preparation/commit must still reload and revalidate
under their owning lifecycle/lock rules. It does not alter current field selection,
prompt policy or comparison: SAME_METADATA may need no operator action even when
there is no stored matching decision and `covered` is false.

## Later read-only integration

Join an InboxReview variant to its coverage entry by its exact receipt identity
within this result's inbox ID. The entry list follows the inbox order rather than
InboxReview's UUID grouping; do not join by label or assume grouped row indexes.
The model does not require or modify the existing read view, LocalV2Store, import
preparation/writer, Android UI or transport. No new schema/product decisions exist.

## Verification — 2026-10-27

`JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh` passed on macOS with
`--release 8`, assertions and synthetic data. InboxDecisionCoverageTest uses the
unchanged independent v2 history fixture, checks current/historical Add/re-add/
selected/keep/no-op results, multiple old/current choices for one receipt, exact
receipt/provenance/metadata matching, labels/UUID separation, numeric zero,
additional observations, unrelated targets and complete unmatched-history retention.

It rejects continued coverage after local/image changes, deletion/re-add and old
values restored at a newer version; malformed same-version results/images and
duplicate snapshots fail validation. It checks empty/no-history/equal-metadata
cases, immutable output, exact byte preservation of supplied snapshots and unchanged
golden inputs. Existing core/store/migration/preparation/publication/100-device and
offline envelope checks also pass. No Docker, emulator, Android/SDK build, device,
server, transport or image-library suite was run. No live UI, operational-network
privacy or new write-path compatibility is claimed by these read-model tests.
