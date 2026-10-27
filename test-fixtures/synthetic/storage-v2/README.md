# Independent local v2 proposal fixtures (#15/D)

Status: **exact local v2/TMMR bytes explicitly approved on 2026-10-27**, including
merged PR #102 test material. The [core migration](../../../docs/LOCAL_V2_MIGRATION.md)
now tests actual file conversion against these unchanged expected bytes. See
[the exact frame/fingerprint proposal](../../../docs/LOCAL_V2_FORMAT_PROPOSAL.md).
All values/identities/coordinates/images are synthetic. No ATAK, outgoing codec,
production v2 writer or migrator is used to generate or inspect them. The
in-memory production codec/fingerprint implementation now matches these same bytes.

`build-fixtures.py` uses Python standard-library struct, UUID, strict UTF-8 and
SHA-256. It takes no arguments, accepts no user store and rewrites only these fixed
fixture outputs. It pins the existing one-record v1 golden's hash and wraps its
unchanged record body to assemble an expected result; it is not a generic data
migration tool. `LocalV2FormatFixtureTest` is a separate Java test-only reader and
canonical-preimage encoder. It does not encode v2 snapshots or migrate data.

Each `.base64` file is one Base64 line plus LF; decoded bytes include the digest.
`manifest.json` contains decoded byte counts and SHA-256 values. Repository wrappers
and the test offsets are not part of the on-disk contract.

| Fixture | Decoded bytes | SHA-256 |
|---|---:|---|
| history-v2.base64 | 3,365 | `71a43709bb7b339fa3325c91f4957e2ab65f3c66f23ca0448865c4a54540dd43` |
| empty-v2.base64 | 64 | `abd92ed4d4e0ca3ba3cbf285bb04e799b8721218f41b2d6421e5db7e7fe3429a` |
| migrated-v1-v2.base64 | 246 | `a83aed3f900845346acd8de5b03054c79d99741dd20549e89fd31ea7ecb4ebfb` |
| empty-v1.base64 | 44 | `5241702b60b5c4e5ff5d0cb464a9674249c1159c7ca9b85968d9bf8ddac73e7d` |
| Existing ../storage/v1.base64 (unchanged source) | 217 | `f980a436617a2616fc93b33e5b1491b8718259515a50c3ab77b6c10007f55b14` |

## Identity and current slots

Every suffix N below expands to UUID `00000000-0000-4000-8015-` plus twelve decimal
digits, zero-padded, for N. Store = 100; inbox = 101; A/B/C/D/E = 201/202/203/204/205.
Slot insertion order is A, B, C, D; E is never-seen and has no slot.

| Slot | State | stateVersion | Current local revision | Current M | Current imageRef |
|---|---|---:|---:|---|---|
| A | Active | 6 | 9 | Result A | owned-new |
| B | Deleted | 3 | Absent | Absent | Absent |
| C | Deleted | 2 | Absent | Absent | Absent |
| D | Active (explicit re-add) | 9 | 1 | Add D | Absent |

All history M uses label `PIR V2 SYNTHETIC`, type PIR, owner `SYNTHETIC ROLE`,
createdAt `(0,123)` and longitude 1.25 unless specified below. Times are
`(epoch seconds,nanoseconds)`; absent optional values have no body.

| M | UID | Status | updatedAt | deployedAt | Comment | Latitude | Bearing |
|---|---|---|---|---|---|---|---|
| Before A | A | ACTIVE | (10,456) | (5,6) | Local before choice | -0.0 | 45.25 |
| Incoming A | A | DEPLOYED | (15,987) | Absent | Chosen remote åäö 🌲 | 0.25 (longitude **1.5**) | 90.0 |
| Result A | A | ACTIVE | (20,123) | (5,6) | Chosen remote åäö 🌲 | -0.0 | 45.25 |
| Add C | C | ACTIVE | (10,456) | (5,6) | Added then deleted | -0.0 | Absent |
| Add D | D | ACTIVE | (10,456) | (5,6) | Explicit re-add | -0.0 | -0.0 |
| Keep incoming | A | ACTIVE | (30,999) | (5,6) | Other received variant | -0.0 | 45.25 |
| No-op incoming | A | ACTIVE | (40,999) | (5,6) | Unselected remote comment | +0.0 | 45.25 |

## Committed operation history

Rows i=0..4 have operation suffix `300+i`, receipt suffix `400+i`, inbox 101,
context `SYNTHETIC-CONTEXT-A` and firstObservedAt `(100+i,i)`. Receipt times describe
arrival only, not decision clock/causal authority.

| i | Action / outcome | Before | Mask | Edit time | Result version / revision / M |
|---|---|---|---:|---|---|
| 0 | USE_SELECTED / UPDATED | A active v4 / rev7 / Before A; fingerprint includes synthetic owned-old | 16 (comment) | (20,123) | v5 / rev8 / Result A |
| 1 | ADD_LOCAL / ADDED | C never-seen v0 | 0 | Absent | v1 / rev1 / Add C |
| 2 | ADD_LOCAL / ADDED | D deleted v8 | 0 | Absent | v9 / rev1 / Add D |
| 3 | KEEP_LOCAL / KEPT | A active v6 / rev9 / Result A; owned-new | 0 | Absent | v6 / rev9 / Result A |
| 4 | USE_SELECTED / KEPT | A active v6 / rev9 / Result A; owned-new | 64 (position, numerically equal) | Absent | v6 / rev9 / Result A |

Rows 0/3/4 select Incoming A/Keep incoming/No-op incoming respectively; rows 1/2
select Add C/Add D. A had an ordinary image-only edit after row 0 (v5/rev8 → v6/rev9),
so historical application does not describe current revision/image. C was locally
deleted after its Add. B has no application history; deleted slots are not inferred
from operation rows. Raw `owned-old` is not in the snapshot history, only in the
synthetic fingerprint-vector preimage. Current `owned-new` is stored in A's slot.

## Canonical fingerprint vectors

`fingerprints.tsv` has 18 rows: name, Base64 TMMR preimage, lowercase SHA-256 hex.
The independent Java oracle reconstructs each state and compares **both** complete
canonical preimage bytes and digest. It checks unchanged numeric zero signs versus
differences in image replacement/absence, revision, state version, store/target
identity, nanoseconds, case-sensitive owner, absent bearing, exhausted counters,
never-seen absence and deletion/version. `active`/`positive-zero` agree; likewise
`all-negative-zero`/`all-positive-zero` normalize latitude, longitude and bearing.
Other vectors have distinct digests. These synthetic preimages intentionally carry
image references to pin canonicalization; historical operation records do not.

## Expected migration, and absent history

The source is the unchanged existing one-record TMMD v1 golden. The independent
expected v2 file copies its UUID/metadata/optionals/local revision **2** and image
handle `synthetic-image` exactly, sets stateVersion **1**, uses store identity 100,
and has zero operations/deleted slots. The new identity is fixed for this fixture;
a real future migration must generate one. Empty valid v1 expects empty v2 with a
new identity. The Java test loads v1 using production DraftStore and compares every
field/revision/image handle and double bits with the expected v2 inspection; it
never invokes a v2 migrator. A separate LocalV2MigrationTest now executes real
migration and compares its result against these fixed expected bytes, normalizing
only the random store identity for that comparison. The old reader rejects v2
and preserves its bytes.

V1 cannot supply deleted UUIDs, earlier incarnations/state versions, historical
local image/metadata variants, incoming/losing variants absent from separate inbox
storage, accepted/kept choices, receipt-operation bindings or operation IDs. An
inbox receipt is not evidence a local application committed. Do not fabricate
those histories or turn a saved local revision into stateVersion. No protection
against pre-migration delete/re-add is claimed, and old reviews cannot remain valid.

## Reproduction and rejection checks

From the repository root:

```sh
python3 test-fixtures/synthetic/storage-v2/build-fixtures.py
JAVA_HOME=<Temurin-17-path> sh scripts/test.sh
```

Reproduction must leave decoded hashes and all checked-in fixture text unchanged.
`offsets.properties` records fixed zero-based fields for re-sealed negative checks;
those offsets describe this fixture only. Java rejects bad framing/digests/version,
counts/lengths/UTF-8/enums/domain/nanos/flags, zero versions/revisions, duplicate IDs,
wrong target, overflowing transitions/masks, inconsistent history order/current
state and recomputable fingerprints. Original golden inputs stay unchanged.
Production decoding/re-encoding and fingerprints now match the same independent
bytes/oracle; the malformed corpus is rejected by both readers. Passing verifies
these approved in-memory bytes and current v1 read behavior, not
production storage, atomic migration/recovery, image ownership, reset, SDK or ATAK.
