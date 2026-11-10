# One-file image container v1 — proposal (#195 / #26)

**Not approved.** Merge is not format approval. Decision H requires the owner's
explicit approval of C1–C3 below against a named commit before a production codec
or writer. The synthetic fixture generator/oracle is test-only reference material.

## Purpose and recommendation

Carry exactly one approved image descriptor and its exact sanitized PNG in one
bounded file. Recommend a simple uncompressed frame rather than a general ZIP:
there are no entry names, paths, duplicate members, compression, manifests or
automatic PNG import instructions to interpret. Native Data Sync's ability to
carry a PNG is proven separately; this new file type still needs its own native
File Select / recipient-byte / no-automatic-import experiment. No third-party
Data Sync Android API or product HTTP adapter is established by this format.

Use `.tmmi` as a suggested filename suffix and `application/octet-stream` as the
content type. Neither identifies the device, supplies source authority or selects
a destination. A filename may change without changing any binding. If the native
route refuses the type, do not silently rename it to PNG or add another route.

**Native follow-up, 2026-11-10:** the [isolated standard-client trial](IMAGE_CONTAINER_NATIVE_TRIAL.md)
now verifies a 6.2 MB `.tmmi` file, sender/recipient Cancel, chosen-feed membership,
actual server/recipient byte equality and host restart. This narrows transport
uncertainty without approving the format, source mapping or adoption.

## Exact bytes

Integers are big-endian, four-byte nonnegative values checked against these bounds
before conversion/allocation. There is no padding, optional field or trailing byte.

| Offset | Value |
|---|---|
| 0–7 | eight literal bytes `TMMIPKG` followed by `00` |
| 8–11 | version `1` |
| 12–15 | descriptor length D, 113 through 262,256 |
| 16–19 | PNG length P, 1 through 16,777,216 |
| 20 through 20+D−1 | one complete approved descriptor-v1 frame, unchanged |
| 20+D through 20+D+P−1 | the exact complete PNG bytes named by that descriptor |

Total length is exactly **20 + D + P**, at most **17,039,492 bytes**. Reject future
versions, negative/zero/out-of-range lengths, truncation and trailing data. Check
arithmetic before allocation. The minimum descriptor bound follows its 112-byte
fixed envelope plus at least one metadata byte; actual metadata/descriptor validity
still comes from the existing codec, not from that lower bound.

The embedded descriptor must pass `ImageOfferCodec`, including the existing
metadata profile and device identity. P must equal its PNG length. The actual PNG
must pass `ReceivedImagePayload` / `ImageTransferPng` with the descriptor's exact
SHA-256, dimensions and closed PNG profile. Never re-encode received content and
retain its old identity. There is no separate container digest: descriptor CRC and
PNG digest detect their respective accidental changes, not an authenticated author.

Use a bounded descriptor slice so its EOF check cannot consume PNG bytes. Validate
the descriptor before reading the PNG. Verify the complete outer EOF too. Invalid
input returns no pair; it does not create a stage entry, receipt, association or
local image. Upstream still owns deadlines, cancellation/unblocking, total actual
bytes and a one-live-candidate memory budget; header lengths are not a timeout.

## Meaning and integration limits

The file contains only claims and bytes. It carries no authenticated context,
server/feed address, local handle, provider URI, local revision, native package
manifest, sender credentials or receipt. Metadata stays format 1 with
`imageIncluded=false`: that statement describes those metadata XML bytes; this
separate container does not silently change the metadata wire contract.

Source context must come from an approved adapter, never this file. Verified
association still requires the exact retained inbox metadata M, matching source,
ready private stage and approved registry commit. An arbitrary file-picker import
may at most produce an unassociated candidate until that source proof exists.
No received pixels replace a local image automatically. Adoption and outbound
receipt formats remain separate decisions. Local deletion is not remote withdrawal.

## Owner decisions

| ID | Recommendation |
|---|---|
| C1 | Approve the exact frame, bounds and reuse of descriptor-v1 / closed PNG above |
| C2 | Approve a future inactive bounded reader/writer; enable no sender, native importer or HTTP adapter from this approval |
| C3 | Keep source/exact-M association, receiver adoption and optional receipt separate; verify actual native file delivery before activation |

The proposed first codec is in-memory/stream-only and creates no persistent state.
It must interoperate with both independent goldens and reject malformed lengths,
future versions, mixed pairs, invalid components, truncation and trailing bytes.
Production storage/transport integration remains a separate reviewed change.

## Independent fixtures and verification

`test-fixtures/synthetic/image-container-proposal/` contains two frames built by
Python's standard library from the unchanged approved offer goldens and generated
H1/H2 PNGs. The test-only `scripts/test-image-container-proposal.py` pins their
bytes and independently checks framing and component coherence. It is not a
production codec, trusted-source mapper, native importer or storage writer.
