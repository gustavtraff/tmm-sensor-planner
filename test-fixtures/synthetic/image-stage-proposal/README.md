# Proposed private image-stage v1 layouts — synthetic only

Not adopted, not a production store. See `docs/IMAGE_STAGE_LAYOUT_PROPOSAL.md` for
exact names/marker/quota/recovery semantics and explicit history limitations.

Each fixture has an exact eight-byte `TMMISTG1` marker and an empty `stage.lock`.
Ready/pending pairs copy the independently pinned offer-h1/h2 descriptors and
H1/H2 PNGs; there are no real images/positions, accounts, source contexts or receipts.

| Fixture | Ready | Pending | Logical bytes | Interpretation |
|---|---:|---:|---:|---|
| empty | 0 | 0 | 8 | Initialized; no candidates present now, not proof of no prior loss |
| ready-one | 1 | 0 | 887 | One unassociated coherent H1 pair |
| ready-two | 2 | 0 | 1776 | H1/H2 alternatives for the same metadata, no automatic winner |
| pending-partial | 1 | 1 | 997 | Ready H1 plus 37-byte descriptor /73-byte PNG prefix for H2; recovery required |
| pending-complete | 0 | 1 | 897 | Complete H2 bytes still pending, not automatically published |

Local directory UUIDs end 301/302 and are deliberately separate from device,
association and delivery identities. Partial files are prefixes only, not pictures
or metadata suitable for use. The test-only inspector never rewrites/promotes them.

Generate these layouts by copying `offer-hN.bin` to `offer.bin` and `hN.png` to
`image.png` under the specified ready/pending directory; the partial case truncates
to 37/73 bytes. Marker is `b'TMMISTG1'`, lock is `b''`. Existing descriptor/PNG README
files pin their complete hashes; the inspector validates actual integrity, expected
totals and no input mutation. Sparse quota/control mutations exist only in isolated
temporary test directories and are never treated as valid ready image payloads.
