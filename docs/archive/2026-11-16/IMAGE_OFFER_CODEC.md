# Approved image descriptor v1 codec (#136 / #26)

The owner approved the exact [descriptor version 1](IMAGE_OFFER_FORMAT_PROPOSAL.md)
on 2026-10-30. `ImageOffer` and `ImageOfferCodec` now implement those descriptor
values/bytes in the Java core. This is an in-memory codec only: it does not register
an importer, receive network data, stage files, apply images or acknowledge delivery.

## API and limits

- `ImageOffer` holds nonzero association/delivery UUIDs, the existing immutable
  `ShareReview.Metadata` projection, expected PNG SHA-256/length/width/height.
  It validates representability in metadata format 1; PNG bounds remain the fixed
  descriptor-v1 16 MiB / 1920 limits. The digest is copied on input and access.
  Existing device UUID rules are unchanged; a nil device UUID is not newly forbidden.
- `ImageOfferCodec.encode(offer)` returns a fresh canonical descriptor array. It
  reuses package-private metadata encoding shared with the unchanged public
  `PublicationCodec.encode(Prepared)` API. All four existing metadata golden files
  remain byte-identical; no new way to publish, approve or mutate a draft is added.
- `ImageOfferCodec.decode(stream)` requires one complete descriptor through EOF.
  It reads the fixed 108-byte header, validates version/IDs/PNG bounds/N before
  allocating the bounded metadata buffer (N <=262144), then reads metadata/CRC and
  probes at most one extra byte. Actual reads are at most **262257 bytes**, with
  bulk requests <=8192; `available`, skip and mark/reset are unused. Zero bulk reads
  fall back to one byte. Header/CRC/strict UTF-8 XML/profile/subject UUID and exact
  framing must pass; there is no salvage or metadata-only fallback.
- Input remains caller-owned on success/failure. Invalid data and provider failures
  return a generic IOException without raw messages/causes that could reveal a path
  or URI. No successful descriptor escapes a failed final EOF probe. Read deadlines,
  cancellation/unblocking, closure and bounded concurrency belong to the caller;
  this synchronous API cannot time out a blocked provider. Never use it on the UI thread.
- `offer.sameBinding(other)` compares UUID + full metadata projection + content
  measurements/digest with existing inbox metadata equality. It **does not compare
  association/delivery IDs or establish a source namespace**. Those IDs and validated
  source/destination bindings must be checked separately by a future coordinator.
  Nanoseconds/optionals remain meaningful; numeric signed zero follows existing
  domain equality, not XML-byte or descriptor-CRC identity.

A decoded descriptor contains **claims**, not verified PNG bytes, source authority,
a durable association or a receipt. An all-zero/incorrect claimed digest can be
shape-valid; actual PNG content must independently pass
[ImageTransferPng](IMAGE_TRANSFER_CONTENT.md), and trusted provenance/readback still
precede association. No model field may be treated as an attachment handle or URL.

## Canonical encoding versus frozen delivery

Supported XML whitespace/attribute ordering can vary on receive. Decoding retains
its metadata meaning, not the original XML spelling; encoding returns canonical
metadata/descriptor bytes. Thus encode(decode(noncanonical-but-supported input))
need not reproduce its original CRC/bytes, while binding equality remains unchanged.
Canonical H1/H2 goldens round-trip byte for byte. Sender retry must retain the
original explicitly reviewed frozen descriptor/payload bytes outside this codec;
re-encoding arbitrary received values is not a new send authorization.

## Compatibility and remaining gates

No existing metadata/inbox/local-v2 bytes, source image policy, local image handles,
store state or approved golden files were changed. There is no existing image
registry to migrate. Unknown descriptor versions fail closed. A later descriptor
version requires a separately reviewed format/compatibility change, not a fallback.

The [in-memory app image boundary](RECEIVED_IMAGE_PAYLOAD.md) now pairs this
descriptor with actual verified PNG bytes; no UI/transport is wired to that boundary. Exact container, receipt and
private registry/recovery formats remain unapproved before their writers. Source /
selected-feed ingress, actual recipient bytes, quotas, crash recovery, manual
adoption and two-client Data Sync-feed verification remain separate gates. Contact
transfer does not replace that target. The signed v21 APK/source ZIP are unchanged.

## Verification — 2026-10-30

`sh scripts/test.sh` passed using the pinned JDK 17 toolchain. The independent
Python-generated descriptors remain unchanged; the existing Java test-only oracle
now cross-checks the production decoder/encoder and every generated invalid frame.
Coverage includes all truncation lengths of the goldens, max descriptor size,
unknown version/CRC/shape/UUID/metadata errors, exact canonical goldens, domain
binding semantics, defensive arrays, no local-handle/revision serialization, safe
provider/interrupt/EOF failures, short/zero-progress streams, actual read bounds,
caller ownership and concurrent codec use. All existing metadata/inbox/v1/v2,
migration/fault/restart and 100-device checks in that script also passed.

Patch whitespace and relative links passed. No SDK/app/image build or image suite,
emulator, Docker, phone/server, signing submission, operational connection or actual
Data Sync delivery was run for this core-only change. Public required CI remains
an additional pre-merge gate, not ATAK runtime evidence.
