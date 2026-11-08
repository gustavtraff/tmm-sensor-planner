# Private image association registry v1 — exact format proposal (#26)

**Status: exact bytes approved by the owner on 2026-11-08 (R1–R4), satisfying
decision H in [the image contract](IMAGE_SHARING_CONTRACT_PROPOSAL.md) for a scoped
writer.** This approval covers this registry only, not receipts, a container,
adoption, source authority or transport.

**Owner approval recorded 2026-11-08:** the owner explicitly approved all decisions R1–R4 in the conversation, for this document as merged at `afae71b`. Approval came from that decision, not from the merge.

## Purpose

Record, durably and privately, one fact: **association verified for review**. The
approved contract requires exact bytes, a verified source context and a retained
metadata receipt to be bound before that fact can be claimed. Stage v1 deliberately
cannot record it: it has no history and no source. Decision C keeps adoption a
separate explicit step, so a record here changes nothing about any local image.

## Why this format first

| Remaining format | Why it can or cannot be specified now |
|---|---|
| Association registry | Depends only on approved inputs: descriptor v1, stage v1 and inbox receipts. This document. |
| Outbound receipt | Feedback is off by default (D) and needs an authenticated return route that does not exist yet. Deferred. |
| Transport container | Must match the actual route. [The native feed trial](DATA_SYNC_IMAGE_FEED_TRIAL.md) has not settled it. Deferred. |
| Adoption record | Belongs to the local-v2 transaction as a separate operation. Deferred. |

## Files

```text
se.tmm.sensorplanner/received-image-association-v1/
  associations.bin       # the frame below
  associations.lock      # permanent empty file, exclusive OS lock only
```

The directory is host no-backup private storage, separate from drafts, inbox, stage
and active images. Same-directory temporary files are ignored on read.

Initialization is an explicit caller action. An absent file means *not initialized*,
never an empty registry, and a reader must not create one. Writes follow the existing
store rule: lock, validate the whole file, write a temporary file, sync, read back
and atomically replace. There is no copy or delete fallback.

## Exact frame v1

Conventions match [inbox format 1](INBOX_FORMAT_PROPOSAL.md): big-endian integers,
UUIDs as two longs, instants as `long` seconds plus `int` nanoseconds, strict UTF-8.

| Part | Bytes |
|---|---|
| Header (44) | `int 0x544d4d41` (`TMMA`), `int` version `1`, UUID registryId, UUID inboxId, `int` recordCount |
| Each record (140 + n) | below, in insertion order; order carries no meaning |
| Trailer (32) | SHA-256 of every preceding byte; no bytes after it |

| Record field | Encoding and constraint |
|---|---|
| associationId | UUID, not all zero; equals the descriptor's value |
| deliveryId | UUID, not all zero; equals the descriptor's value |
| deviceUid | UUID; equals the descriptor's and the receipt's device |
| receiptId | UUID of the inbox receipt that retains the exact metadata M |
| stageLocalId | UUID, not all zero; the stage-v1 entry holding the verified pair |
| contextId | `int` n (1–4,096) then n strict UTF-8 bytes, already in `String.trim()` form; equals the receipt's contextId |
| pngLength | `int`, 1–16,777,216 |
| width, height | `int`, each 1–1,920 |
| pngSha256 | 32 raw bytes |
| verifiedAt | instant from the local clock, at the moment of commit |

An empty frame is 76 bytes. Records are limited to **256**, which is decision F's
history budget, so the maximum file is 1,084,492 bytes. A contextId over 4,096 bytes
is a capacity error and is never truncated. registryId is generated at initialization.
inboxId names the one inbox whose receipt IDs these records use, because receipt IDs
are unique only within an inbox.

## Why M is not stored here

The inbox already retains M, and its duplicate key (contextId, deviceUid, compared M)
gives each exact variant one receipt ID. Referencing it avoids a second spelling of
every field. Within one context, equal receiptId means equal M under domain equality.

## Identity rules

A reader rejects the **whole file** when any of these is broken:

- (contextId, associationId, deliveryId) occurs at most once.
- stageLocalId occurs at most once. One stage entry backs one record.
- Every record sharing (contextId, associationId) has the same deviceUid, receiptId,
  pngLength, width, height and pngSha256. A second deliveryId of an unchanged
  association is allowed. A changed binding under the same associationId is the
  identity conflict the approved descriptor anticipates.

The writer treats a repeat of an existing record as an idempotent no-op, keeping the
original record and verifiedAt. It refuses a conflicting binding and preserves what
is stored. The same associationId in another context is separate provenance.

A replacement image has a new associationId and gets its own record. Nothing here
selects a winner: not order, not verifiedAt, not UUID value.

## Preconditions the writer must prove

File structure cannot show these, so each must be checked against current state
under the appropriate locks before commit:

1. The stage entry is ready and passes the existing descriptor and PNG verification.
2. The inbox receipt exists in inbox `inboxId`, and its contextId and device match.
3. Compared M from the receipt equals M decoded from the stage descriptor.
4. The contextId came from the approved adapter, never from a payload.

Decision F's stage discard must refuse an entry referenced by a record, unless a
later reviewed operation removes the record first. This is the eligibility proof
that [the discard backend](IMAGE_STAGE_MAINTENANCE.md) already says is required.

## Failure and honest limits

- A crash before replacement leaves the stage entry unassociated, and the attempt
  can be made again. An ambiguous outcome is resolved by reloading and comparing.
- A record whose stage entry has gone is reported as **missing content**. The record
  is kept and never silently dropped.
- Unknown versions, bad digests and rule violations fail closed without rewriting.
  There is no reset. At 256 records new writes refuse; nothing is evicted.
- **Not detected:** loss of the whole file, restoration of an old backup, or tampering
  by another plugin under the host UID. The digest detects accidental corruption only.
- A record states that this device verified this binding at that time. It is not
  delivery, acceptance, adoption, authentication of an author, or proof that the
  image still exists. Scene content is never verified.

## Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| R1 | Approve the exact frame, including 256 records and a 4,096-byte contextId | Approve |
| R2 | Reference the inbox receipt rather than copying M | Approve |
| R3 | Block stage discard for referenced entries | Approve |
| R4 | Keep records when content goes missing, with no automatic cleanup | Approve |

Approval should name this document's commit. A writer is then a separate PR.

## Independent fixtures and verification

[The fixtures](../test-fixtures/synthetic/image-association-proposal/README.md) reuse
the approved offer and stage goldens. `scripts/test-image-association-proposal.py` is
a stdlib-only oracle and runs in public CI. It pins all three files by size and hash,
cross-checks their fields against the real offer and stage fixtures, and covers
framing errors, every field bound at and past its limit, a full 256-record maximum
file, and the identity rules. It ran locally with seven passing test groups.

No Java implementation, SDK or image suite, emulator, phone, server or signing step
was run for this proposal.

## Implementation — 2026-11-08

`ImageAssociationRegistry` in the Java core implements these bytes. It has no UI,
ingress, transport or adoption caller, so nothing in the APK writes a record yet.

- `associate` proves preconditions 2 and 3 itself, by requiring a retained inbox
  receipt for the exact context and M, and a matching `inboxId`.
- Precondition 1 runs under the registry lock through a stage proof, normally
  `ImageStageStore.requireReady`. An exact repeat needs no live entry.
- **The caller must prove precondition 4**: contextId came from the approved
  adapter. The core cannot authenticate a source.
- **R3 is enforced.** The public `ImageStageStore.discard` needs the registry and
  runs under its lock, refusing a referenced entry. An absent or invalid registry
  also refuses. Both operations take the registry lock first, then the stage lock.

`sh scripts/test.sh` covers the three goldens byte for byte, malformed frames,
idempotent repeats, a second delivery, identity conflicts, stage reuse, missing
receipts, wrong inbox, failed atomic replacement, future versions left unrewritten,
reopen through a new instance, and the 256-record and context limits. No SDK build,
emulator, phone or server was involved.
