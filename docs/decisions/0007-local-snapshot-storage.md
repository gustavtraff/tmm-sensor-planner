# ADR 0007: Versioned local snapshot storage

Date: 2026-10-17. Status: proposed for human review in Issue #11's PR.

## Decision

Use a dependency-free, bounded binary snapshot in the Java core. Store every
current draft field, enum names and schema version, with a SHA-256 integrity trailer.
Read and validate everything before save/delete, coordinate with a file lock, and
replace the complete snapshot atomically after syncing the temporary file. Reject
unknown/corrupt data and stale revisions without replacing saved bytes. The exact
contract and Android handoff are in [LOCAL_STORAGE.md](../LOCAL_STORAGE.md).

## Rationale and alternatives

The existing core has no JSON/SQLite dependency. This small local-only collection
needs neither an ORM nor a database service. Java object serialization would couple
data to implementation classes. A fixed explicit format is testable without an SDK
or server. A golden v1 fixture anchors the first persisted format; no fictional
legacy migration is introduced. A real future schema change needs migration tests.

## Consequences

The whole snapshot is read/written for changes and is capped at 1,000 records / 16 MiB.
This is not encrypted storage, backup, a sharing format or a distributed conflict
policy. Atomic rename does not establish sudden power-loss durability. Unsupported
atomic replacement fails explicitly. Android NIO/minSdk, private storage/backup,
UI lifecycle and offline restart/upgrade tests remain required before APK use (#12).
Image-file handling remains #13. Human merge adopts the format decision, not an
ATAK or physical-device acceptance result.
