# Isolated v1-to-v2 migration — #15/D

The owner authorized this next implementation after #110 on 2026-10-27.
`LocalV2Store.migrateV1()` now implements the migration specified by the adopted
[D contract](IMPORT_APPLY_CONTRACT_PROPOSAL.md) and [exact local format](LOCAL_V2_FORMAT_PROPOSAL.md).
It reuses the [v2 store's](LOCAL_V2_STORAGE.md) permanent lock, bounded reader,
projected-frame validation, file-sync and atomic replacement. No new format,
domain schema, fixture bytes, dependency or activation sidecar is introduced.

**Core only; not called by the app.** Exercise this only on explicitly selected
synthetic private test directories until every planner/image writer and the app's
activation/lifecycle handling are converted together. There is no APK version bump,
automatic upgrade, production activation, reset/restore or image migration here.

## Explicit operation and preservation

`migrateV1()` holds the existing nonblocking exclusive `drafts.lock` throughout:

1. Read the existing bounded `drafts.bin` without a missing-file fallback. Reuse
   DraftStore's strict v1 decoder for the full checksum/framing/domain validation;
   do not nest its public independently locking methods. The decoder is extracted
   as package-private `DraftStore.decodeV1`; legacy load/save/delete behavior and
   its version 1 writer are unchanged.
2. For valid v1, assign one new random local store UUID. Copy every active record
   in its existing insertion order into an active slot at stateVersion **1**, with
   its exact original local revision. Preserve all UUIDs/text/times/nanoseconds,
   optional values, signed-zero double bits and raw current imageRef strings.
   Initialize empty decision history and no deleted slots.
3. Encode/validate the complete v2 result **before temp creation**. A valid v1 file
   can exceed the same 16 MiB limit after v2 adds identity/version overhead; fail
   without changing v1, deleting rows or truncating data.
4. File-sync the same-directory temp and atomically replace `drafts.bin`. This
   single replacement makes v2 authoritative. No separate completion flag, backup
   fallback, image/inbox write or temporary-file promotion is used.

If a **complete valid v2** is already present, the method returns that current
snapshot without writing, regenerating identity, resetting versions or clearing
history/deleted slots. This supports inspection/retry after an uncertain outcome;
it does not prove that this invocation performed the migration. Unknown versions,
corrupt v1/v2, missing files and symbolic/nonregular owned paths remain errors.
A header hint never bypasses full v2 checksum/model/history validation.

Nothing reads or changes image bytes or inbox data. A missing/damaged image file
remains an image error; migration preserves its handle. Removed v1 UUIDs and older
metadata/image/decision/incarnation history cannot be reconstructed. Saved revision
2 (or Long.MAX_VALUE) remains that revision while stateVersion starts at 1. The
future UI must explain the pre-v2 history gap and discard old reviews; protection
against delete/re-add starts with v2, not retrospectively.

## Failures and recovery

The existing typed safe `LocalV2Store.Failure` contract applies. BUSY means the
permanent lock is held. Invalid projected size reports INVALID before any write.
Read/validation or filesystem failures report UNKNOWN; after an I/O error, inspect
committed storage rather than assuming rollback or retrying an old v1 write.

| State after interruption | Required behavior |
|---|---|
| Complete v1 remains | A later explicit migration can validate and migrate it afresh. Ignore any abandoned temp and its uncommitted candidate identity. |
| Complete v2 remains | Load/return its existing identity, slots and history. Never initialize/remigrate or restore an older v1 copy over it. |
| Missing/corrupt/future/unreadable | Stop with unavailable evidence; no empty fallback, reset, repair or implicit initialization. |

A failure after rename or during temporary cleanup may mean v2 already committed.
The normal `load()` validates only v2; explicit `migrateV1()` fully validates either
supported committed version under lock. Neither can recover a lost/corrupt snapshot.
Legacy DraftStore load/save/delete reject v2 without replacing it, including
old in-memory revision-only edits. All callers must still be converted before
rollout; rejection is not a usable mixed-version app mode. Atomic rename/file sync
covers the tested process-restart boundary, not parent-directory fsync, power loss,
restoring old backups, or malicious replacement of trusted directory ancestors.

## Verification — 2026-10-27

`sh scripts/test.sh` passed with Temurin 17.0.20.1+1, Java 8 target and assertions.
`LocalV2MigrationTest` runs actual files, locks and child JVMs using synthetic data:

- Executes migration from the unchanged independent one-record and empty v1 goldens
  and compares the entire v2 output against the already approved independent
  expected frames, substituting only the random store identity for comparison.
- Preserves every field, active insertion order, revision, nanoseconds, optional
  values and signed-zero bits across all four types. Images, orphan image bytes,
  inbox bytes and the permanent lock file identity stay unchanged. A v1-deleted
  UUID does not become an invented v2 tombstone. Exhausted revisions are retained.
- Rejects stale legacy save/delete and validates versioned post-migration save,
  delete/re-add, committed Keep history and no-write repeated migration. A valid
  independent mixed-state/history v2 frame is returned byte-for-byte unchanged.
- Rejects missing, empty, truncated, corrupt, future, invalid count/text/UTF-8/enum/
  revision, duplicate UUID, trailing, oversized and symlinked sources unchanged.
- Migrates 1,000 slots; rejects an independently assembled **valid exactly 16 MiB
  v1 frame** whose v2 overhead exceeds the cap before temp creation/replacement.
- Exercises same-process and separate-JVM lock contention, competing migrations
  (one replacement/identity), and blocked legacy writes during migration.
- Injects partial-write/sync, unsupported atomic move, after-replacement and cleanup
  failures. Child JVMs halt before/after rename; reopening sees complete v1 or v2,
  never a promoted temp or a partly migrated snapshot.

Existing core/v1/inbox/v2/import/publication/100-device suites also pass. No SDK or
Android build, image-library suite, emulator, Docker, phone/network test, signing
upload or CI wait ran. No fixture generator or checked-in golden bytes changed.

Next: propagate store identity plus persistent state through every planner/form/
map/image writer, add controlled app activation and capacity/result handling, then
connect the optional import UI and verify the signed runtime. This migration alone
does not enable received objects, Data Sync ingestion or production upgrades.
