# Private inbox format 1 (#15)

**Status: A–C and the concrete format from PR #92 explicitly approved by the
repository owner on 2026-10-25; D (application to local drafts) remains deferred.**
Approval was recorded in the conversation, not inferred from merging the proposal.
The SDK-independent [InboxStore implementation](INBOX_STORAGE.md) uses this frame.
No Android adapter, transport, import application or acknowledgment is included.
The historical proposal filename is retained for links.

## Isolation from existing contracts

Use a separate private directory with `inbox.bin`, a permanent `inbox.lock`
and ignored same-directory temporary files. Never reuse `drafts.bin`, its magic,
lock or schema counter. [DraftStore v1](LOCAL_STORAGE.md) remains unchanged; no
old-to-new draft migration is proposed. Metadata units/catalogue remain those in
[IMPORT_REVIEW.md](IMPORT_REVIEW.md); this is not a wire format for #14 or #25.

All receipts remain stored for review. No APPLIED, resolved, dismissed, Shared,
tombstone, remote revision, content hash, image handle or delivery-status field is
included. Arrival order and timestamps provide no causal/permission authority.
Current local images stay exclusively in the current local draft/image store.

## Primitives and frame

All integers are big-endian. `int` is signed 32-bit, `long` signed 64-bit and
`double` IEEE 754 binary64. UUIDs are two longs, most significant then least.
An instant is epoch seconds (long) followed by nanoseconds (int, 0–999,999,999),
and must fit java.time.Instant. Strings are an int UTF-8 byte length followed by
strict UTF-8; no BOM stripping, Java modified UTF, implicit normalization or trim.
Flags are exactly one byte, 0 or 1. Optional values omit their body when flag = 0.

| Frame order | Encoding |
|---|---|
| Header | int `0x544d4d49` (`TMMI`), int formatVersion `1`, UUID inboxId, int receiptCount |
| Receipt | UUID receiptId; string contextId; instant firstObservedAt; metadata M; int observationCount; that many local observations |
| Trailer | 32-byte SHA-256 of every preceding byte; no trailing bytes |

An explicitly initialized empty inbox is a 60-byte frame with receiptCount 0,
an inboxId and a valid digest. File size includes the digest and is at most
16,777,216 bytes. Strings are at most 65,536 bytes, with domain limits additionally
applied (including the comment limit). Unknown magic/version, invalid UTF-8/enums,
noncanonical required text, illegal flags, invalid domains/instants, inconsistent
counts, truncation or trailing payload bytes reject the whole file unchanged.
The digest detects accidental corruption; it is not a signature or encryption.

Receipt count is nonnegative and individually capped at 1,000 before allocation.
Every receipt has at least one local observation. The **sum of receipt count and
all observation counts is at most 1,000**, including absent observations; thus an
actual valid frame has at most 500 receipts. Check counts/budgets before allocating
or looping over declared data. A valid header cannot authorize an unbounded read.

### Metadata M, in exact order

1. UUID deviceUid.
2. Strings displayId, type, status, owner.
3. Instants createdAt, updatedAt.
4. Byte deployedAtPresent, followed by instant deployedAt if present.
5. String comment.
6. Doubles latitude, longitude.
7. Byte bearingPresent, followed by double bearingDeg if present.

Use existing enum **names** and domain validation, not ordinals or UI translations.
Display ID/owner must already be nonempty and equal to their Java `trim()` result;
comment may be empty. Finite coordinates/bearings, type/bearing compatibility,
creation/update ordering and optional presence follow TmmDraft. Neither imageRef
nor a local/remote revision is part of M. The incoming domain carrier uses local
revision 1 to invoke current domain validation; that value is not serialized.

Preserve finite double bits, including negative zero. Equality still treats +0/-0
as equal under ImportReview. Raw serialized bytes or their hashes therefore must
not be substituted for metadata equality. Instants retain nanoseconds exactly.

### Local observation, in exact order

- Byte localPresent = 0: no further body. It means the UUID was absent at an
  observation, not that it never existed, is new, or was remotely deleted.
- Byte localPresent = 1: long localRevision (> 0), then M for that local variant.
  Its deviceUid must equal the receipt's incoming deviceUid. No local image handle
  is serialized or pinned. A missing historical image must not be inferred from
  this metadata-only representation.

Within a receipt reject duplicate absent observations and duplicate present
observations with the same localRevision and equal M. Equal metadata at different
local revisions may remain as different observed local states, for example after
an image-only edit. No distributed ordering is inferred from those counters.

## Identity and source context

inboxId is a locally generated UUID for explicit initialization. Receipt IDs are
locally generated UUIDs, unique within the inbox, retained across replacements and
retries. They are never device UUID replacements or sender message IDs. The
reader accepts opaque UUID values, not only the particular IDs in the fixture.

contextId is a nonempty, already trimmed, case-sensitive opaque local identifier
for the validated source namespace/origin mapping. Do not use a mutable feed label,
editable owner/callsign, credential or provider URI as the mapping. The adapter's
mapping/authentication remains unimplemented and needs review with #14; this file
stores neither proof of identity nor permission to apply data. Core does not
validate that trust merely by decoding a string. Receipt time need not be later
than remote metadata times, because clocks may differ.

The logical duplicate key is **(contextId, deviceUid, compared M)**. A repeated
submission must return the original receipt ID/time without adding a receipt;
a different M or context preserves a separate receipt/provenance record. A stored
frame containing duplicate logical keys is invalid, even if receipt IDs/times or
signed-zero encodings differ. Different UUIDs never merge by visible label.
Receipt/local-observation order is insertion order, not winner selection.

InboxStore implements insertion under its own exclusive lock. The independent
fixture pins the format; separate storage tests exercise insertion, locking,
process-restart reopening and write failures. See [the API and evidence](INBOX_STORAGE.md).

## Approved initialization, retention and failure boundary

1. The committed inbox file itself carries initialization identity; no independent
   mutable marker or multi-file journal is used. Missing `inbox.bin` is
   NOT_INITIALIZED_OR_MISSING, never an implicitly empty successful load.
2. Only an explicit initialize-new operation, under the permanent inbox lock, may
   create a new empty frame with a new inboxId. It must refuse any existing file,
   including corrupt/unsupported/zero-length data. The caller must explicitly
   decide that starting without recoverable history is acceptable; absence alone
   cannot establish a fresh installation. Initialization is not a receive side effect.
3. Keep all committed pending receipts and observed metadata within the caps.
   No expiry/eviction, reset, selective deletion, export or restore operation is
   included in the first slice. At capacity report NOT_STORED/full; do not drop a
   conflict or acknowledge receipt. A later reset/retention design needs separate
   approval and must disclose loss of deduplication/review history.
4. Recover committed frames only; abandoned temporary files are not live receipts.
   Never auto-promote a temp file or reset malformed storage. A readback after an
   ambiguous commit must distinguish present, absent and unreadable, not blindly
   retry under a new receipt ID. InboxStore provides lookup only after a complete
   successful read and validation; unreadable storage is an error, never absence.
5. Preserve the earlier process-restart-only durability boundary. Parent-directory
   sync, power loss, app-data deletion and rollback of an old backup are not solved
   by this file header. An inboxId is not a monotonic rollback detector.

## Independent fixture and current checks

[The synthetic candidate](../test-fixtures/synthetic/inbox/candidate-v1.base64)
was encoded independently with Python standard-library `struct`, `uuid`, UTF-8,
`hashlib` and `base64`, without DraftStore or a Java encoder. Its
[field values and byte offsets](../test-fixtures/synthetic/inbox/README.md) allow
independent inspection. Base64 is a repository wrapper, not the disk format.

The decoded fixture is **964 bytes**; SHA-256:
`b836b42f3fc195046817fb4746faecbc9f9c130e486c4db80505bffdd1f4e513`.
It contains three receipts: two differing incoming variants for one UUID/context,
and the equal base metadata in another context. It retains two distinct local
metadata versions and explicit absent observations. Unicode, nanoseconds, optional
values, enum names and signed-zero encodings are pinned.

`InboxFormatFixtureTest` keeps its independent test-only inspector and compares
all fixture values with the production decoder. Production encoding of the decoded
fixture must reproduce the exact independent bytes. Both readers reject malformed
checksums, versions, counts, lengths, UTF-8, instants, domains, flags, local
revisions/UUIDs, duplicate receipt IDs/logical keys, truncated and extra payloads.
Negative cases re-seal altered payloads when needed rather than only failing a digest.

The fixture bytes and existing DraftStore v1 fixture are unchanged. Current test
results, storage failure checks and remaining platform gates are recorded in
[INBOX_STORAGE.md](INBOX_STORAGE.md). No draft migration or wire format is added.
