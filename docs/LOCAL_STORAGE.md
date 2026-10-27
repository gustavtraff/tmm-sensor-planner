# Local draft storage

Issue #11 adds `core/DraftStore`, a dependency-free local snapshot store. It has
no ATAK, network, image-decoding or publishing operations. The [offline planner
adapter](LOCAL_PLANNER.md) now calls it on Android API 26+ using host-private
no-backup storage. Physical forms/map acceptance remains #12, image ownership #13.
This is not a phone A02/A09 acceptance result.

## API and failure behavior

- Construct with a private, plugin-owned directory. `load()` returns an immutable
  list; an absent snapshot means a new empty store. An existing empty, unreadable,
  corrupt or unsupported snapshot is an error, never an empty-list fallback.
- `save(draft)` inserts revision 1 or updates the same UUID at exactly the saved
  revision plus one, with unchanged creation time and a nondecreasing update time.
  Duplicate visible labels remain separate records. Stale edits fail explicitly.
- `delete(uid, expectedRevision)` requires the saved revision and deletes metadata
  only. Neither save nor delete resolves, opens or removes `imageRef` files.
- Every operation obtains a nonblocking exclusive lock on `drafts.lock`, including
  reads. Another instance/process holding the lock gives an explicit retry error.
  Keep that lock file; deleting/replacing it defeats coordination.
- Save validates the complete existing snapshot before changing anything. It writes
  a same-directory temporary file, flushes and syncs its bytes, then requests atomic
  replacement of `drafts.bin`. Unsupported/failed atomic replacement has **no**
  copy/delete fallback. Failure before replacement preserves the previous snapshot;
  temporary-file cleanup is attempted on normal exit. If an I/O error occurs after
  replacement, reload to determine the committed state before retrying. A killed
  process may leave an ignored temporary file.
- Limits: 1,000 records, 16 MiB total file size including digest, 65,536 UTF-8 bytes
  per string. Domain validation also applies (including the smaller comment limit).
  Oversized input/output fails without replacing saved data. These are bounded MVP
  storage limits, not performance claims for a trial phone.

The revision check protects ordinary local edits; it is not a distributed conflict
policy. Deleting then explicitly recreating a UUID starts a new local record. The
UI must generate fresh UUIDs for new devices and must not silently retry failed
writes, reset the store, or display success before `save` returns. Map previews
must reflect the saved result. On failure keep the user's edit and show an error.

The snapshot is plaintext sensitive local data, **not** encryption or a backup.
Use private app storage, never shared Downloads or a feed. Do not log snapshots,
field values, raw filesystem exceptions/paths or attachment references. Android
backup/export policy must be checked before this store is wired into the APK.
A missing file is indistinguishable from a fresh installation; external deletion,
storage failure and sudden power-loss durability are not solved by this format.
The parent directory is not fsynced; atomic replacement is not a claim of surviving
power loss. On process restart the committed snapshot is reopened, not a temporary
file. Keep unsupported data in place for a compatible app/recovery decision.

## Version 1 byte contract

All numbers are big-endian. `int` = signed 32-bit; `long` = signed 64-bit;
`double` = IEEE 754 binary64. Strings use an `int` byte length followed by strict
UTF-8 (no Java modified UTF). Flags are exactly one byte, 0 or 1. Required trimmed
text must already be canonical; the reader rejects padding rather than changing it.

| Order | Bytes |
|---|---|
| Header | `int 0x544d4d44` (`TMMD`), `int schemaVersion=1`, `int recordCount` |
| Each record, in saved insertion order | UUID most/least significant `long`s; strings displayId, type, status, owner; createdAt and updatedAt; deployedAt flag and optional instant; comment string; latitude and longitude doubles; bearing flag and optional double; imageRef flag and optional string; revision long |
| Trailer | 32-byte SHA-256 of all preceding bytes |

An instant is epoch seconds (`long`) and nanoseconds (`int`, 0–999,999,999).
Type/status strings are enum **names**, never ordinals: `MINI`, `SCOUT`, `PIR`,
`GATEWAY`; `PLANNED`, `DEPLOYED`, `ACTIVE`, `RETRIEVED`, `CANCELLED`.
No trailing payload bytes or duplicate UUIDs are allowed. Every decoded record
passes the domain constructor's validation. The digest detects accidental
corruption, not deliberate modification/authenticity. No local path is a wire field.

This is the **first persisted format**. There is no earlier production version to
migrate. The fixed independently encoded [v1 fixture](../test-fixtures/synthetic/storage/v1.base64)
pins compatibility, including nanoseconds, Unicode, zero bearing and optional data.
Future-version, malformed and truncated files are rejected without rewriting them.
Every future schema change must add a real old-to-new fixture migration test before
changing the writer; never reinterpret an unknown schema as empty storage. This
format does not define a production CoT wire schema.

## Verification and Android handoff

On 2026-10-17, `sh scripts/test.sh` passed with Temurin 17.0.20.1+1 and `--release 8`:
all fields/UUID/revision survive reopen; a separate JVM writer exits before a new
reader process opens its data. Tests also cover updates, deletion, duplicate visible
labels, stale saves/deletes, failed atomic replacement, lock contention, v1 fixture
compatibility, digest/UTF-8/enum/domain errors, duplicate UUIDs, padding, future
versions, truncation, trailing bytes, string/file/record limits and a broken symlink.
Rejected files remain byte-identical after attempted reads, saves and deletes.
These tests use synthetic coordinates only. No emulator/server/phone was started
for this change; a local no-network API is not observed ATAK offline behavior.

The implementation uses NIO file APIs available on Android API 26+, while the APK
currently declares minSdk 21; the Android references list
[Files](https://developer.android.com/reference/java/nio/file/Files) and
[Instant](https://developer.android.com/reference/java/time/Instant) at API 26.
`Instant` in the existing model also needs Android
compatibility handling. The #12 prototype uses a runtime API 26 guard before
constructing its adapter; older clients get an unavailable message without raising
APK minSdk. It uses private host no-backup storage, translated errors, background
I/O and map rebuilding after load. See LOCAL_PLANNER.md for bounded runtime results.
This is not API 21–25 planner support. #13 must separately prove actual image bytes
survive restart. Future schema migration and physical-phone A02/A09 remain open.

## Separate import inbox — 2026-10-25

The owner explicitly approved A–C and the PR #92 byte format for a separate
[durable review inbox](INBOX_STORAGE.md). Its own file, lock, codec and tests leave
this DraftStore v1 format, snapshot behavior and current local image handles
unchanged; no draft migration is involved. The [inbox format](INBOX_FORMAT_PROPOSAL.md)
has an independent golden fixture plus restart, concurrency and write-failure checks.

D, application to local drafts, remains deferred. Independent inbox and DraftStore
saves are not an import transaction. Later apply work needs explicit review of
revision/deletion reuse, fresh-image preservation, timestamp invariants and recovery;
see [the contract rationale](IMPORT_PERSISTENCE_PROPOSAL.md).

## D application storage follow-up — proposed 2026-10-25

[The D contract proposal](IMPORT_APPLY_CONTRACT_PROPOSAL.md) recommends a local v2
snapshot with persistent per-UUID mutation versions/deleted slots and atomic
application/decision evidence. This is not an implemented format or adopted
migration. A new v1 characterization in DraftStoreTest proves that exact
byte-identical delete/recreate can reuse a revision and admit an old save/delete;
ordinary v1 checks therefore cannot serve as D's confirmation token. Current v1
reader/writer/fixture and image behavior are unchanged. Review the logical change,
then exact encoding/independent migration fixtures, before any v2 writer.

## Exact local v2 format follow-up — proposed 2026-10-26

[The exact local v2 proposal](LOCAL_V2_FORMAT_PROPOSAL.md) separates file version 2
from the unchanged TmmDraft domain schema. It specifies active/deleted slots,
persistent versions, committed decisions and canonical TMMR fingerprints, with
[independent golden/expected-migration fixtures](../test-fixtures/synthetic/storage-v2/README.md)
and a test-only inspector. Current v1 production code/fixtures remain unchanged.
Migration fixtures preserve existing fields/revisions/images and start fresh
version/history identity; they cannot invent pre-v2 deletion/application history.
No production v2 writer/migrator or mission-cleanup activation is added.

## Approved local v2 in-memory implementation — 2026-10-27

The owner explicitly approved [the exact local v2/TMMR formats](LOCAL_V2_FORMAT_PROPOSAL.md)
and PR #102 fixtures. [LocalV2Codec and LocalStateFingerprint](LOCAL_V2_CODEC.md)
now implement bounded encoding/decoding and hashing entirely in memory. This
DraftStore v1 implementation/fixture, missing-file behavior and image ownership
remain unchanged. No existing writer is activated against v2, and no migrator,
file writer, apply transaction or reset is included. Passing fixture/codec tests
does not establish migration/durability or Android compatibility.


## Isolated v2 transaction implementation — 2026-10-27

[LocalV2Store](LOCAL_V2_STORAGE.md) now persists the approved v2 frame in an explicitly
selected private test directory and commits prepared metadata plus decision evidence
atomically. It has expected-state save/delete and local-only operation readback.
This live DraftStore v1 implementation, fixture and Android callers remain unchanged.
No migration/activation/reset exists; do not mix writers or infer versions from v1.
