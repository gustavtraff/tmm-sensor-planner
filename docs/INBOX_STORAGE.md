# Durable private review inbox (#15)

On 2026-10-25 the repository owner explicitly approved decisions A–C and the
concrete format from PR #92 in the conversation. D, applying received metadata to
local drafts, remains deferred. [The rationale](IMPORT_PERSISTENCE_PROPOSAL.md)
and [exact format 1](INBOX_FORMAT_PROPOSAL.md) define that boundary.

`core/InboxStore` stages metadata for later review. There is no Android caller,
receiver, transport acknowledgment, resolution UI or draft application. Existing
DraftStore v1, image ownership and outgoing publication work remain unchanged.

## API and identity

Construct `InboxStore` with a separate private, plugin-owned directory. All writers
must use the permanent `inbox.lock`; never delete or replace it. Ancestors of the
owned directory must be trusted. The store rejects symbolic links at the directory,
committed file and lock, but does not defend against hostile ancestor replacement.

- `initializeNew()` explicitly creates a 60-byte empty `inbox.bin` and random
  inbox UUID. It refuses any existing committed file, including invalid, empty or
  unsupported data. The caller must authorize starting without receipt history;
  absence alone is not evidence of a fresh installation.
- `load()` returns an immutable snapshot with the stable inbox UUID and receipts
  in insertion order. Missing, unreadable, corrupt or unsupported data throws;
  it never returns a fabricated empty inbox or promotes an abandoned temp file.
- `find(contextId, incoming)` returns a receipt or null **only after** reading and
  validating the entire committed inbox. Null means a missing logical key, not
  uninitialized/unreadable storage. This is the readback operation after an
  uncertain receive outcome.
- `receive(contextId, incoming, observedLocal, firstObservedAt)` atomically reads,
  validates, deduplicates and, if new, commits a receipt under the inbox lock.
  The key is context + device UUID + ImportReview metadata equality. A duplicate
  returns its original receipt ID/time and observations without rewriting bytes.
  A different incoming variant or context retains a separate receipt. Equal labels
  do not merge different UUIDs. Timestamp/arrival order never selects a winner.
- `retainLocalObservation(receiptId, observedLocal)` explicitly appends review
  evidence without replacing the incoming candidate or previous observations.
  Present observations deduplicate by local revision + UUID + compared metadata;
  null records observed absence once. Unknown receipt IDs fail. This operation
  preserves receipt identity/time and does not change a local draft.

The caller supplies immutable validated domain objects, an observation for the
same device UUID (or null), a nonnull observation time and a canonical, stable
source context. The store does not read DraftStore, authenticate provenance or
validate that a supplied local observation is still current. Context mapping and
source/schema validation require a separately reviewed adapter. Owner/callsign,
mutable labels, credentials and provider URIs are not trustworthy context IDs.

Incoming image handles are rejected. Incoming local revision counters are ignored;
the returned metadata-only TmmDraft carrier has revision 1 and no imageRef. Stored
local observations retain their actual local revision but strip imageRef. Inputs
are not mutated; the live DraftStore and current image files are untouched. The
historical metadata does not pin images or claim delivery/absence of their bytes.
Image-only local edits may produce another observation at a new local revision
while ImportReview still reports SAME_METADATA. Reassessment/application must
later bind to the fresh complete live draft, including its current image handle.

## Commit, concurrency and failure behavior

Each operation is synchronized per instance and takes a nonblocking exclusive file
lock across instances/processes. Contention reports a fixed busy IOException; the
caller decides whether to retry. There is no automatic retry or cached snapshot.
Successful competing writers therefore compare against the latest committed state.
A caller may observe busy rather than success; after explicit retry both distinct
variants survive and identical variants share one receipt ID.

The full snapshot is encoded and validated before creating a same-directory temp
file. The temp bytes are flushed and file-synced, then atomically replace the
committed file. There is no non-atomic fallback. Before-replacement failure leaves
the prior file unchanged; a killed process may leave an ignored temp file. A
replacement or cleanup error may occur **after** commit: the IOException does not
promise rollback. Reopen/read back the same key (or receipt observations) before
retrying; report found, absent and unreadable as distinct outcomes. After an
uncertain initialization, load and retain any existing inbox identity rather than
blindly initializing again. Error messages expose neither fields nor raw filesystem
paths/causes; callers must also avoid logging input objects and storage contents.

The format caps the file at 16 MiB including SHA-256, each UTF-8 string at 65,536
bytes, and the combined count of receipts plus observations at 1,000. Absence counts
as one observation; every receipt has at least one. Domain limits also apply.
Capacity errors preserve the committed file; a duplicate still succeeds at capacity
without a write. There is no silent eviction, expiration, reset, deletion, export,
restore or resolved/APPLIED status. These require later review. The checksum detects
accidental corruption, not malicious edits or authenticity. Storage is plaintext;
an eventual Android integration must choose private no-backup storage and background
I/O rather than exposing files or blocking the UI.

The durability claim is committed-file reopening after **process restart** only.
Parent-directory sync, sudden power loss, app-data deletion, old-backup rollback
and permanent replay suppression across lost history are not covered. Inbox identity
is not a monotonic rollback detector. Inbox and DraftStore are separate files;
there is no two-file transaction or authorization to apply staged data.

## Verification — 2026-10-25

`JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh` passed on macOS with
`javac --release 8` and assertions enabled. Only synthetic data were used.

- The independent Python-encoded golden frame remains byte-identical. Its test-only
  inspector and production decoder agree on every field; production re-encoding
  matches exactly. Both reject re-sealed malformed framing, UTF-8, domain values,
  flags, versions, observation revisions/UUIDs and duplicate logical keys/IDs.
- Storage tests cover initialized/missing/corrupt/future/oversized files; stable
  IDs, timestamps and conflict/local variants on reopen; context/UUID identity;
  ignored foreign revision and signed-zero deduplication; immutable results;
  string/combined-entry/output-file limits and duplicates at full capacity.
- Tests preserve actual separate DraftStore bytes and synthetic current image
  bytes/handles while staging, retaining observations and reopening. An image-only
  local edit remains current and both historical metadata revisions are retained.
- Separate JVM writers exit before later JVMs retry/reopen. Paired JVMs are forced
  to encounter a held file lock, then explicitly retry same-key and different-variant
  insertions. Child JVMs halt before/after atomic replacement to verify committed
  readback, ignored abandoned files and released process locks.
- Injected errors before and after replacement cover initialization, receive and
  observation retention. Readback and retries do not duplicate committed evidence;
  pre-replace errors preserve old bytes. Lock contention and symlink refusal are
  exercised. Existing DraftStore v1 fixture/reopen and all core tests also pass.

No emulator, Docker container, Android build, SDK/device/phone, operational server
or transport test was run. NIO/time usage shares the existing API 26+ platform
boundary, but Android/filesystem behavior and actual two-client ingress remain
unverified. Passing these core checks does not prove ATAK compatibility. D needs
its own approved transaction/recovery and freshness/deletion/clock rules before
any live-draft application is implemented.

## Read-only review projection — 2026-10-25

[InboxReview](IMPORT_REVIEW.md#read-only-inbox-review-15--2026-10-25) groups an
already loaded snapshot's receipts by device UUID and compares each variant with
supplied local drafts using ImportReview. It preserves all receipt/provenance and
historical observations, exposes the full current supplied local draft separately,
and performs no store operation. Reassessment is explicit; it neither appends
observations nor applies data. Format 1, initialization, locking and receipt
identity/equality are unchanged. D remains deferred.
