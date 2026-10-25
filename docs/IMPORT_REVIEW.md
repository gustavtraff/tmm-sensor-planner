# Metadata import review proposal

Status: proposed for human review with the SDK-independent part of
[Issue #15](https://github.com/gustavtraff/tmm-sensor-planner/issues/15), F10/F11.
Date: 2026-10-17. No synchronization policy or production receiver is approved by
this document or by passing tests. Human PR review may adopt this limited classifier;
source identity, causal versions and user resolution remain open. The separate
2026-10-25 inbox approval/implementation is recorded below.

## Scope and API

`ImportReview.assess(snapshot, incoming)` compares an existing local snapshot with
one already domain-validated, metadata-only candidate. It returns immutable references
to the original local and incoming objects and one of these results:

| Kind | Meaning |
|---|---|
| `NEW_UID` | No local record has that UUID; `local` is null. A matching visible label does not merge different UUIDs. |
| `SAME_METADATA` | The same UUID has equal compared metadata. No second record or local revision is created. |
| `REVIEW_REQUIRED` | The same UUID has a metadata difference. Keep both snapshots for explicit review; neither wins automatically. |

This function has no storage, file, network, parser, acknowledgment or UI operation.
All results are assessments, not authorization to import. `REVIEW_REQUIRED` does not
prove concurrency: a difference may be sequential, stale or caused by clock skew.
There is no automatic last-write-wins, timestamp ordering or field-by-field merge.

The complete supplied collection must contain nonnull drafts with unique UUIDs;
null arguments/entries and duplicate UUIDs are rejected, including duplicates unrelated
to the incoming UUID. No ambiguous snapshot produces a partial assessment. Inputs
are immutable TmmDraft instances; the collection itself is not modified. The caller
must supply a stable snapshot rather than a concurrently changing collection.

## Comparison contract

Compare visible ID, type, status, owner, createdAt, updatedAt, optional deployedAt,
comment, latitude, longitude and optional bearing. Text equality is case-sensitive
on domain-validated text, with no additional trimming or Unicode normalization.
Instants compare exactly, including nanoseconds and optional presence. Finite numeric
coordinates/bearings compare by numeric equality; positive and negative zero match.
No geographic tolerance, magnetic conversion or inferred deployment timestamp applies.
Changes in creation time, status, position or optional field removal require review
just as other differences do. Both earlier and later updatedAt values require review.

Two client-local values are deliberately excluded:

- `revision` is a positive counter of local writes. Equal metadata with different
  revisions remains SAME_METADATA; different metadata requires review at lower,
  equal or higher revisions, including Long.MAX_VALUE. No remote causal version,
  edit permission or trusted source is inferred from this counter.
- The existing local `imageRef` remains untouched and is not part of metadata
  comparison. An incoming nonnull imageRef is rejected, even for a new UUID;
  the classifier cannot resolve or copy another client's private handle. Incoming
  null does not clear or prove absence/delivery of the local image. Image identity,
  replacement/removal over transport and acknowledgment remain #26.

The comparison is between the snapshots supplied now. A local edit that changes
updatedAt can require review even when the visible fields look the same. The
function does not remember prior arrivals, rewrite timestamps or establish replay
history. Repeated assessment of the same metadata has the same result. The separate
inbox provides durable staging deduplication under its approved context/equality
contract, without accepting imports.

## Persistence and later adapter gates

No snapshot-v1 bytes, migration rules, TmmDraft edits or DraftStore writes change.
Existing v1 fixture and separate-process reopen checks continue to run. Incoming
TmmDraft is a convenient validated test/domain value, not a published wire schema
or a record that may be saved directly with the sender's revision/image handle.

A future receiver must validate/authenticate the actual source and full supported
schema before comparison. Persist both conflicting variants and their provenance
before reporting durable receipt; this classifier retains them only in memory.
Reload/reassess and enforce stale-write checks at the eventual user-confirmed commit:
the local draft or image may have changed since review. Define causal identity,
repeat/update/withdrawal/deletion semantics, permissions and conflict UI before
implementing acceptance. A UID absent after local deletion is merely NEW_UID here;
there are no tombstones to prevent resurrection. Do not connect automatic imports
or sharing using only this helper. The laboratory CoT fixture is not that contract.

Issue #15 remains open for application decisions, transport/UI integration,
and two-client/restart tests. No ATAK API or SDK/device behavior is verified by this
change. #35's local image adapter and the shared emulator remain separate.

## Verification

On 2026-10-17, `JAVA_HOME=<Temurin-17> sh scripts/test.sh` passed with Temurin
17.0.20.1+1 / `--release 8`. Import-review tests exercise distinct/same UUIDs,
repeated assessment, every compared field, null transitions, signed zero, lower/
equal/exhausted local revisions, local-image preservation, rejected incoming handles
and invalid collections. Assessment of an actual saved snapshot preserves its bytes
and reopened local record. Existing core/forms/storage, fixed v1 fixture and separate
writer/reader JVM tests also pass. No SDK build, emulator or two-client test was run.

## Durable receipt follow-up — 2026-10-25

The owner explicitly approved A–C and the concrete PR #92 format for the separate
[InboxStore](INBOX_STORAGE.md). It reuses `sameMetadata` for receipt deduplication,
retains different variants and observed local metadata across restart, and never
writes DraftStore or changes this pure classifier. Current local image references
remain in the live draft; historical observations intentionally omit image handles.
The image-only-edit regression still verifies SAME_METADATA and stale-save rejection.

[The contract rationale](IMPORT_PERSISTENCE_PROPOSAL.md) records the remaining gates.
D, live-draft application, remains deferred. There is no Android/transport caller,
source authentication or acknowledgment. Staging a candidate is not accepting it.

## Read-only inbox review (#15) — 2026-10-25

`InboxReview.assess(inboxSnapshot, localSnapshot)` assembles review data from an
already loaded, validated InboxStore snapshot and a stable collection of immutable
local drafts. It performs no I/O and calls the existing ImportReview classifier;
comparison rules and both storage formats are unchanged. It has no Android caller
or dependency on outgoing metadata encoding/preparation.

The immutable result contains the inbox identity and groups for UUIDs present in
the inbox. Groups follow first receipt occurrence, and variants within each group
follow receipt insertion order. Neither order selects a winner. Local drafts with
no receipt are not listed; equal visible IDs do not merge different UUIDs.

Each group exposes:

- `uid`: the device UUID shared by the group's receipts.
- `currentLocal`: the complete local draft supplied for that UUID, including its
  actual local revision and image handle; null means absent in that supplied
  snapshot. It does not mean the UUID never existed or that recreation is allowed.
- `variants`: one entry per original receipt, with its existing `NEW_UID`,
  `SAME_METADATA` or `REVIEW_REQUIRED` assessment against `currentLocal`. Each entry
  retains the original receipt ID, source context, first-observed time, incoming
  metadata and historical local observations through its immutable `receipt`.

Equal metadata from different contexts remains separate receipt evidence inside
one UUID group. Grouping asserts no common source authority or ownership. No
variant is hidden because it currently equals the local metadata. Historical
observations (including explicit absence and stripped image handles) are never
substituted for `currentLocal`, updated or appended by review.

The complete local collection is validated for null entries and duplicate UUIDs,
including unrelated duplicates and an empty inbox. Invalid arguments fail before
returning any result. A caller must load both stores successfully and supply stable
snapshots; it must not translate missing/corrupt/unreadable inbox data into an
empty successful review. There is no combined inbox/draft read transaction.

The result is a **point-in-time assessment of supplied data**, not a live view,
staleness detector, confirmation token or permission to apply. Reload and reassess
after local edits, image changes, deletion/recreation or inbox changes. Earlier
results remain immutable and can be stale immediately after creation. An image-only
edit may still yield SAME_METADATA while the full `currentLocal` has a different
image handle/revision; historical metadata cannot prove current image identity.
A deleted UUID yields NEW_UID without authorizing resurrection. Local revision
reuse after deletion cannot establish object generation. Source authentication
and application decisions/transactions (D) remain deferred.

### Verification

On 2026-10-25, `JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh` passed on macOS
with `--release 8` and assertions enabled. InboxReviewTest exercises the unchanged
independent format-1 fixture, preserved receipt/provenance/history, same-metadata
and differing variants, signed-zero equality, equal labels across different UUIDs,
interleaved grouping order, absent local drafts, immutable results and rejection
of ambiguous/null local collections even with an empty inbox.

Real separate DraftStore/InboxStore instances verify reassessment after metadata
edits, image-only replacement/removal, deletion and explicit local recreation.
Reviews preserve inbox bytes, draft bytes and synthetic image bytes; reopening
both stores keeps the pending receipt and compares against the latest supplied
local state. All existing core/store/fixture checks also pass. No emulator, Docker
container, Android build, SDK/device or transport test ran. There is no application,
resolution state, schema migration, historical-image pinning or UI in this change.
