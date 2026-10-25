# Independent proposed inbox fixture

Status: **candidate only**, not an accepted format or runtime store. See
[the proposed byte contract](../../../docs/INBOX_FORMAT_PROPOSAL.md).
`candidate-v1.base64` is one Base64 line plus LF. Decoding yields a 964-byte
binary frame with a 32-byte SHA-256 trailer. It was assembled with Python's
standard-library big-endian struct/UUID/UTF-8 primitives, not DraftStore or a Java
writer. The separate Java test-only inspector checks the independent bytes.

- File SHA-256: `b836b42f3fc195046817fb4746faecbc9f9c130e486c4db80505bffdd1f4e513`.
- Payload/trailer digest: `538448e713eb8091dd79c05ce82eeda1b1e4e7338535c34b2195aa5ae5362f87`.
- Magic `TMMI`, format 1, inbox ID `00000000-0000-4000-8015-000000000001`.
- All metadata uses device UID `00000000-0000-4000-8015-000000000003`, display ID
  `PIR INBOX15`, type `PIR`, owner `SYNTHETIC ROLE`, createdAt epoch seconds 0 /
  nanos 123. Coordinates are synthetic, not sensor locations.
- A receipt ID below expands to `00000000-0000-4000-8015-` plus the displayed
  twelve-character suffix. Context A/B expands to `SYNTHETIC-CONTEXT-A/B`.

| Receipt suffix | Context | First observed (epoch seconds, nanos) | Incoming M | Local observations |
|---|---|---|---|---|
| `000000000010` | A | (110, 10) | Base | Revision 7 / Local 1; revision 8 / Local 2 |
| `000000000011` | A | (111, 11) | Different remote variant | Absent |
| `000000000012` | B | (112, 12) | Base with +0.0 latitude/bearing | Absent |

| Metadata | Status | updatedAt (seconds, nanos) | deployedAt | Comment | Latitude / longitude | Bearing |
|---|---|---|---|---|---|---|
| Base | PLANNED | (10, 456) | Absent | `Synthetic åäö 🌲` | -0.0 / 1.25 | -0.0 |
| Different remote variant | DEPLOYED | (20, 987) | (15, 42) | `Remote variant` | 0.25 / 1.5 | 90.0 |
| Local 1 | ACTIVE | (12, 456) | (5, 6) | `Local before review` | +0.0 / 1.25 | 45.25 |
| Local 2 | CANCELLED | (13, 789) | Absent | `Local concurrent edit` | +0.0 / 1.25 | Absent |

The count budget is seven entries: three receipts plus four local observations.
Absent observations count toward the cap. Base metadata in contexts A/B compares
equal despite different zero bits; differing context keeps distinct provenance.
Changing B to A creates an invalid duplicate logical receipt, not a second device.
No image handle, foreign revision or accepted/resolved status is encoded.

## Zero-based offsets used by malformed-input checks

| Offset | Value start |
|---|---|
| 0 / 4 / 8 / 24 | Magic / version / inbox UUID / receipt count |
| 28 / 530 / 734 | Receipt UUIDs |
| 44 / 546 / 750 | Context string length prefixes (each value is 19 UTF-8 bytes) |
| 67 / 75 | First receipt time seconds / nanos |
| 121 | First incoming status UTF-8 bytes |
| 170 | First incoming deployedAt flag |
| 196 | First incoming latitude double |
| 221 / 729 / 927 | Observation counts |
| 226 / 386 | Local revisions 7 / 8 |
| 234 / 394 | Local metadata UUIDs |
| 772 | Last context's B byte; changing it to A duplicates receipt 10's logical key |
| 932 | Start of 32-byte digest trailer |

These offsets describe this fixed golden fixture, not a dynamic parsing algorithm.
The test re-seals structural mutations to distinguish schema/domain rejection from
checksum rejection. Passing its read-only checks does not establish crash recovery,
idempotent insertion, initialization, filesystem durability or migration behavior.
