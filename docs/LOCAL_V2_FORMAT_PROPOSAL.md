# Local draft storage v2 and review-state fingerprint — exact proposal (#15/D)

**Status: exact byte format proposed for human review, 2026-10-26. No production
v2 reader/writer, migrator or reset operation is implemented.** This makes the
[adopted D contract](IMPORT_APPLY_CONTRACT_PROPOSAL.md) concrete; logical approval
of D does not alone adopt these bytes. Baseline main `510558f` includes #99–#101.
Outgoing metadata, preparation, Android and transport remain separate work.

## Isolation and primitives

This is private per-device `drafts.bin`, under the existing permanent draft lock,
not a wire protocol or the TmmDraft domain schema. Keep TMMD magic, but use local
**file format version 2** independently of `TmmDraft.SCHEMA_VERSION == 1`.
Inbox TMMI format 1, current v1 writer/fixtures and incoming comparison stay unchanged.
D still needs approved expected-state transaction APIs and migration of every local
writer before rollout. Mission-cleanup activation/ownership encoding is not defined
or approved by this document; see [its separate gate](LOCAL_MISSION_CLEANUP_PROPOSAL.md).

All numbers are big-endian. int = signed 32-bit; long = signed 64-bit; doubles =
finite IEEE 754 binary64. UUID = most/least-significant longs (opaque 16 bytes).
Instant = epoch seconds long + nanoseconds int (0–999,999,999), within java.time.Instant.
String = nonnegative int UTF-8 byte length then strict UTF-8, at most 65,536 bytes;
reject malformed encoding rather than replace it. No modified UTF, BOM removal,
normalization, trimming or case folding. Required text must be nonempty and already
equal to Java trim(). Flags are bytes exactly 0/1; absent optional bodies are omitted.

Metadata **M** is exactly Inbox format 1's projection and order: UUID, displayId,
type name, status name, owner, createdAt, updatedAt, deployedAt flag/Instant,
comment, latitude double, longitude double, bearing flag/double. Use existing enum
names/domain validation, including UTF-16 comment length <=2,000, finite valid
position/bearing, type/bearing compatibility and updatedAt >= createdAt. M excludes
imageRef and every local/foreign revision. **B** means M without its leading UUID.
**L** (local body) means B, imageRef flag/required string if present, positive revision
long. B/L's UUID is supplied by the containing slot or fingerprint target.

On disk preserve finite signed-zero bits and exact instants; metadata comparisons
still use numeric +0/-0 equality as ImportReview does. Fingerprints below deliberately
normalize zero. The checksum is accidental-corruption detection, not authentication,
encryption, a signature, source permission or power-loss protection.

## Complete frame, in exact order

| Order | Encoding |
|---|---|
| Header | int `0x544d4d44` (TMMD), int `2`, UUID localStoreId, int slotCount |
| Slots | slotCount repetitions: UUID deviceUid, positive long stateVersion, byte stateTag; stateTag 1 = active followed by L; 2 = deleted with no further slot body |
| History count | int operationCount |
| History | operationCount operation records below, in commit insertion order |
| Trailer | 32 raw bytes SHA-256 of all preceding bytes; no trailing payload/file bytes |

Empty initialized v2 is 64 bytes. File including checksum <=16,777,216 bytes;
slotCount and operationCount each range 0–1,000, checked before allocation/loops.
Deleted slots consume slot capacity. Count limits are independent, not a combined
budget; existing TMMI's combined receipt/observation limit is unchanged. Capacity
never authorizes eviction of a slot or decision. Decode the full bounded frame or
fail unchanged; no skipping unknown/malformed rows or implicit empty fallback.

Slot UUIDs are unique and stay in first-create insertion order across edits,
deletion/re-add. Never-seen absence has **no slot**: it means unseen since this dataset
initialization/migration, never proof that the UUID did not exist before v2. Each mutation increments that
UUID's stateVersion, including image association changes/delete/re-add; no wrap at
Long.MAX_VALUE. Deletion leaves a slot with its advanced version; re-add makes it
active at another advanced version with local revision 1. A migrated active record
can have stateVersion 1 and revision 2 or higher; never compare these counters as
if they shared a meaning. They are local guards, not distributed causal versions.
The frame contains neither general edit history nor a claim to know pre-v2 absence.

## Canonical reviewed-local-state fingerprint (TMMR format 1)

The preimage is **not** a whole-file hash, metadata hash, image-content hash or
persisted grant of consent. Its exact concatenation is:

`int 0x544d4d52 (TMMR); int 1; UUID localStoreId; UUID targetUid;
byte stateTag; long stateVersion; optional canonical L`

- stateTag 0 = never-seen absence: version must be 0, no L.
- stateTag 1 = active: version positive and L present, including exact local
  imageRef presence/text and revision. The containing target supplies L's UUID.
- stateTag 2 = known deletion: version positive, no L.

Absence/deletion preimages are 49 bytes. All L rules match above, except **every
zero latitude, longitude and present bearing is encoded as positive zero**, eight
zero bytes, regardless of original sign. Nonzero finite doubles retain their exact
bits. Optional null differs from any present value, including zero bearing. Text
and nanosecond Instants are exact; no extra normalization, delimiters or padding.
Hash the entire preimage with SHA-256; store 32 raw digest bytes, not hex/Base64.
Test TSV Base64 and hex are repository wrappers only. The fingerprint format has
its own version and domain prefix distinct from the TMMD frame and TMMI inbox.

The primary stale-review check is store identity + target + persistent version,
plus the complete reviewed live state. Even equal metadata/hash after delete/re-add
cannot restore an old version. The fingerprint binds the historical full reviewed
image association without serializing a raw historical image handle into history.
It does not pin/validate image bytes or expose a recipient-readable attachment.
A mutation that changes only zero sign still advances stateVersion if committed;
canonical zero must not erase that mutation identity.

## Operation record, in exact order

Only **committed** decisions are stored; there is no pending intent to resume.

| Field | Encoding / meaning |
|---|---|
| operationId | UUID, unique in this store history; retries use the same identity and binding |
| action | byte 1 ADD_LOCAL, 2 USE_SELECTED, 3 KEEP_LOCAL; fixed codes, not enum ordinals |
| targetUid | UUID |
| Selected receipt | UUID inboxId, UUID receiptId, required string contextId, Instant firstObservedAt, incoming M |
| Reviewed local state | byte beforeTag (0/1/2 as TMMR), long beforeVersion; if tag 1 only: positive long beforeRevision then before M |
| Reviewed fingerprint | 32 raw bytes of TMMR above for this localStoreId/target and exact reviewed local state |
| Selection | int fieldMask with only bits 0–7 below allowed |
| Edit time | flag plus Instant if present |
| Outcome | byte 1 ADDED, 2 UPDATED, 3 KEPT |
| Committed result | positive long afterVersion, positive long afterRevision, result M |

Before tag 0 requires version 0. Tags 1/2 require positive version. All three M
UUIDs (when before M exists) must equal targetUid. Present beforeRevision belongs
only to beforeTag 1. Incoming/retained/result M never include image references or
revisions. Current active slots alone contain raw local handles. Store identity
comes from the common frame header; never accept a command from another identity.

Field bits: 0 displayId, 1 type, 2 status, 3 owner, 4 comment, 5 deployedAt,
6 position (latitude/longitude together), 7 bearing. No bits for UUID, createdAt,
updatedAt, image or revision. Selected null is explicit removal. Before/result M,
selected receipt/provenance, action, mask, time, versions/revisions and reviewed
fingerprint constitute the immutable operation binding. Compare those typed values
and fingerprint on ID replay, not arbitrary snapshot bytes or timestamps alone.

### Whole-record/history validation

- ADD_LOCAL: beforeTag 0 or 2, mask 0, edit time absent, outcome ADDED;
  afterVersion = beforeVersion +1, afterRevision =1, result M equals incoming M.
  No auto-recreation is implied by deleted state. The known-deletion warning and
  explicit re-add consent are transaction/UI gates, not a flag asserting permission.
- USE_SELECTED: active before, nonzero mask, incoming/before createdAt equal.
  Build the candidate by taking selected editable values from that one incoming M
  and every unselected value from before M. Validate the combined object; never
  silently drop a bearing after a type selection. If editable values differ, require
  outcome UPDATED, before version/revision below Long.MAX_VALUE, both advanced by
  one, edit time present and >= before.updatedAt. Result uses before.createdAt,
  that exact previewed edit time as updatedAt and the chosen editable values.
- If USE_SELECTED changes no editable value under existing numeric/text equality,
  require outcome KEPT, no edit time, unchanged version/revision and result M equal
  to before M. Retain the nonzero requested selection mask so retries cannot switch
  intent. Unselected incoming differences stay available as evidence.
- KEEP_LOCAL: active before, mask 0, time absent, outcome KEPT and unchanged
  version/revision/result M. Incoming createdAt mismatch is permitted for this
  keep-local choice; it blocks USE_SELECTED. Existing complete SAME_METADATA does
  not produce any history row: it is prompt-free/no-write under the adopted flow.
- Every record's target must still have a slot. Slot version >= afterVersion;
  if equal, slot must be active and revision/M must match result. A current deleted
  slot must therefore be strictly newer than the last operation result. This
  distinguishes “added then deleted” from a currently live Add.
- Per-target history is nondecreasing: each later beforeVersion >= preceding
  afterVersion. At equality, later before state must be active with that preceding
  result M/revision. Gaps are normal local mutations, not missing history errors.
  Keep decisions can share a version. Other UUIDs' order imposes no causal rule.
- Repeated (inboxId, receiptId) must retain the same context, receipt time and
  incoming M. Distinct receipt IDs/source contexts remain independent provenance.
  No global resolved bit, source ranking or losing-variant eviction is encoded.
- Absence/deletion fingerprints are fully recomputable from the row/header. For
  an active historical before state its imageRef is deliberately unavailable. When
  current slot version equals this operation's afterVersion, recompute the before
  fingerprint using current imageRef (which the transaction must preserve).
  For older rows, enforce digest length/binding but **do not claim to reconstruct
  or authenticate historical image ownership**. Future writer/fault tests must
  separately prove preservation at commit; a checksum cannot prove a past action.

Invalid counts/tags/flags/enums/text/domains/instants, duplicate slot/operation IDs,
unknown masks/versions, missing slots, impossible transitions/history, computable
fingerprint mismatch, digest failure, truncation or extra bytes reject the whole
frame unchanged. Do not treat malformed history as a reason to forget it. Capacity
warnings/full/unknown reporting and process-restart commit/readback behavior remain
as specified in D. Fingerprints are not authorization; source trust remains external.

## Expected migration from v1 — fixture, not a migrator

Under the permanent local lock, a future migration must validate all v1 bytes,
assign one new opaque store UUID, and copy each active record in order to a slot
at stateVersion **1**, retaining its original local revision and every field/image
handle/signed-zero bit/Instant. Start with zero operation records and no deleted
slots. Never invent history from current metadata, timestamps, revision counters,
inbox observations or server history. Migration does not write/change the inbox,
images or outgoing records, nor increment the domain schema/version.

**Irrecoverably absent in v1:** UUIDs removed before migration; how often any UUID
was created/deleted/recreated; older local metadata/image associations; received or
losing variants not retained elsewhere; operator choices, receipt/decision bindings
and completed operation IDs; any persistent incarnation/version guard. The existing
v1 revision survives as a local write counter, **not** evidence that the new
stateVersion starts at that value. Inbox format 1 history survives independently,
but cannot prove a local application or fill those migration gaps. In-flight old
reviews must be discarded; protection against delete/re-add starts only at v2.

Empty valid v1 migrates to an empty v2 with a new identity. Missing/corrupt/future
storage is not an empty migration source. Additional v2 overhead may exceed the
16 MiB cap even for a valid v1 file: fail before replacement, preserve v1, do not
truncate fields or delete records to fit. The future migrator still needs atomic
replacement/fault/restart tests. The legacy reader already rejects version 2; no
fallback/rewrite is allowed. Dataset reset/restore/history loss remains a separate
explicit operation; this frame is not a rollback detector or mission selector.

## Independent fixtures and actual verification boundary

[Fixture values, hashes, offsets and reproduction](../test-fixtures/synthetic/storage-v2/README.md)
include a 3,365-byte mixed-state/history frame, 64-byte empty frame, 246-byte
expected result for the existing 217-byte v1 golden, a 44-byte empty-v1 source, and 18 TMMR preimage/digest
vectors. Python standard-library struct/UUID/UTF-8/hashlib encodes the fixture bytes;
a test-only Java inspector checks the independently specified domain values and
recomputes canonical preimages/digests. Existing production v1 reads the source
fixture, and rejects the proposed v2 bytes without rewriting them. This is not
executing a v2 migration or proving its atomicity.

`LocalV2FormatFixtureTest` runs with plain core tests. It checks pinned digests,
active/deleted/never-seen states, revision versus version, committed historical
versus current results, Unicode/nanoseconds/optional values, selected/keep/no-op
records, canonical zero and image/reference/identity/counter-sensitive fingerprints,
v1 field preservation and re-sealed malformed frames/history. It contains no v2
filesystem writer or generic migrator. Future production decoding/writing must
match the independent fixture and add real transaction/migration/failure tests.
No Android/SDK/device/transport compatibility or cleanup activation is inferred.
