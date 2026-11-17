# Publication metadata candidate 1 — independent fixtures

**Adopted for codec implementation after owner authorization and PR #98 review.** See
[the complete profile](../../../docs/PUBLICATION_FORMAT_PROPOSAL.md).
All identifiers, positions and text are synthetic; no camera, operational location,
account or SDK input was used. These plain XML files are not CoT events and must
not be imported into ATAK as test instructions or published as device data.

The files were encoded independently with Python standard-library
`xml.etree.ElementTree` and `base64.b64encode(value.encode('utf-8'))`, then written
with an XML UTF-8 declaration and a final newline. Field strings were supplied
explicitly; no Java/domain/lab encoder generated them. The Java test-only inspector
has separate literal expected values and pinned SHA-256 hashes; it now also
compares the production codec against that oracle and exact fixture bytes. Hashes identify
these fixture bytes, not duplicate/causal/source identity in the proposed protocol.

Common values: root `tmmPublication`, schemaVersion `1`, owner `SYNTHETIC ROLE ÅÄÖ`,
createdAt `2026-01-01T00:00:00.123456789Z`, updatedAt
`2026-01-01T00:00:10.123456789Z`, true-north bearing reference, imageIncluded `false`.
UUIDs use `00000000-0000-4000-8014-` with suffixes below, independent of lab identities.

| File / UUID suffix | ID / type / status | Exact lat, lon / bearing degrees | Optional data |
|---|---|---|---|
| `mini-v1.xml` / `000000000001` | Mini 1 / MINI / PLANNED | 0.0, 1.25 / absent | No deployedAt; empty comment |
| `scout-v1.xml` / `000000000002` | Scout 1 / SCOUT / DEPLOYED | -0.0, 1.25 / 0.0 | deployedAt `2026-01-01T00:00:05.000000042Z`; comment `Synthetic Scout` |
| `pir-v1.xml` / `000000000003` | PIR 134 / PIR / ACTIVE | 0.123456789012, -0.234567891234 / 123.45678901234567 | Same deployedAt as Scout; comment below |
| `gateway-v1.xml` / `000000000004` | Gateway 1 / GATEWAY / RETRIEVED | -90.0, 180.0 / absent | No deployedAt; comment `Synthetic Gateway` |

PIR comment is exactly `Synthetic only\nTab:\tåäö < & > 🌲`, where `\n` and `\t`
denote actual newline/tab characters. Base64 preserves them across XML attributes.
The boundary-coordinate Gateway is a mathematical format case, not a placement.

| File | Bytes | SHA-256 |
|---|---:|---|
| mini-v1.xml | 433 | `4278f3b72d42c976a53e32de111547513276bbd71359e91c8836b59fbc015c74` |
| scout-v1.xml | 493 | `223989fdee91f97dd67f00c71d16457cee7341e26365cb3e067814f8ab569224` |
| pir-v1.xml | 557 | `0f61cecfa1a33ec5f729e83a5c89dda38d0dfcd9e59af44ecdb10ea477aa0f26` |
| gateway-v1.xml | 469 | `97090a7a9aec483c4bc2af54302d8a15911bdd193463c2b4b2a1c79530b1e49f` |

`sh scripts/test.sh` runs the inspector and the existing core/inbox/storage tests.
Malformed cases are generated in test memory; the fixed fixture bytes are never
rewritten. The production codec is now exercised alongside the independent inspector. No
storage format, lab codec/checker, Android adapter, SDK input, emitter or network
endpoint is changed by these fixtures.
