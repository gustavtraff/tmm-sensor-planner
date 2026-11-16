# Byte-identical received image copy — #197 / #26

`LocalImages.prepareReceived(ImageTransferPng.VerifiedContent)` prepares a new
opaque owned PNG from already verified immutable content. It has no UI, ingress,
transport or adoption caller. It does not prove source/association authority,
select a device or change an existing attachment.

The approved [image contract](IMAGE_SHARING_CONTRACT_PROPOSAL.md) requires this
separate entry point: verified sanitized PNGs may be up to 16 MiB, while ordinary
JPEG/PNG input remains at most 10 MiB. The camera preparer would reject larger
valid received PNGs and could change the bytes whose digest was verified.

## Behavior and caller obligations

- Accept only immutable `VerifiedContent` from the existing strict verifier /
  exporter, not an arbitrary byte array, filename or provider URI.
- Write exact bytes under a fresh opaque UUID handle through the same forced-file
  write path as ordinary preparation. Read back and require byte-for-byte agreement
  before returning a pending copy. Never rescale, re-encode or overwrite an old file.
- Reuse `Pending`: preview reads the copy; Cancel removes only the new uncommitted
  copy; an ambiguous metadata callback retains it for reload/reconciliation. A
  second commit refuses. Existing owned images stay untouched.

Run off the UI thread in private no-backup storage. Preparation is not adoption:
the future caller must prove source, exact-M registry binding and current local
state, obtain explicit image-choice confirmation and use the separately reviewed
recoverable adoption transaction before committing. An arbitrary metadata callback
is not that transaction. No image-choice history or acknowledgment is added to
local-v2, and the 1920-edge / 16 MiB owned and 10 MiB source limits do not change.

## Verification — 2026-11-10

Native Robolectric cases on API 26 and 35 exercise actual byte-identical preview /
reopen, unique handles, preservation of old files, Cancel, ambiguous callback
retention without retry, absent verification and unavailable storage. A generated
high-entropy **1920 × 1920 RGB PNG** exceeds 10 MiB but fits the owned bound.
Ordinary `prepare` still rejects it; the new entry point preserves every byte,
SHA-256 and dimensions. This is not a small screenshot-only test.

No physical memory stress, Data Sync delivery, source association or adoption in
ATAK follows from these tests. Transport and phone evidence remain separate.
