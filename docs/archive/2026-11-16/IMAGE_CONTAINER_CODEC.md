# Approved one-file image container codec — #195 / #26

`ImageContainerCodec` implements the owner's approved
[container v1](IMAGE_CONTAINER_FORMAT_PROPOSAL.md). It has no UI, network, native
importer or storage caller. The codec returns only coherent immutable descriptor /
PNG content, not source authority, association, adoption or acknowledgment.

## Reading

`read(input)` reads the fixed 20-byte header, rejects unsupported magic/version
and lengths before allocating, then reads at most the declared bounded descriptor.
The existing descriptor codec checks that exact slice through EOF. Its declared
PNG length must match the outer header before any PNG read. Finally the existing
`ReceivedImagePayload` boundary validates actual complete PNG bytes, dimensions,
SHA-256 and the closed PNG profile. Because PNG is last, its expected-length/EOF
check also rejects outer trailing data, reading at most one excess byte.

The maximum is 17,039,492 actual bytes plus at most one rejection probe byte, not
unbounded stream draining. Short/zero bulk reads are supported. Invalid or partial
content returns no candidate and creates no file, receipt, stage or local image.

## Writing

`write(output, offer, verifiedImage)` checks the frozen pair's digest, dimensions
and length, and encodes the descriptor before any write. It then writes the exact
header/descriptor/PNG bytes without re-encoding the image or adding local handles.
The immutable verified-content type comes from the existing strict verifier /
exporter. An incoherent pair writes nothing. An output/provider failure after
writing begins may leave an incomplete prefix; it is not rollback or a safe retry.

`ImagePublicationReview.Prepared.writeContainer(output)` exposes this writer for
the already frozen and one-shot-confirmed sender candidate. It does not choose a
file, authorize a source/destination, publish, create a host object or invalidate
the caller's obligation to prove freshness at its real handoff boundary.

Both APIs leave streams open and never flush them. Call off the UI thread. The
owning adapter still limits live candidates, sets/unblocks read/write deadlines,
handles cancellation and manages output retention/reconciliation. Raw provider
errors and causes are not exposed; synthetic allocation failures use the existing
low-memory category. This is not physical-device memory stress evidence.

## Verification — 2026-11-12

API 26/35 native Robolectric cases decode and re-encode both independent goldens
byte for byte, preserving the exact approved offer and actual PNG. They test
invalid header bounds before body reads; invalid descriptors and length mismatches
before PNG reads; every truncation of the first golden; trailing content with a
one-byte rejection probe; short/zero reads; mixed content; matching digest over
an invalid PNG; rejected writer pairs with zero output; safe private provider
errors, partial writes, stream ownership and synthetic allocation failure.

The existing saved-image review test now follows actual owned bytes → frozen
export → one-shot confirmation → container writer → real container reader and
pair validator. Source identity and local adoption are deliberately absent.

The earlier [native file trial](IMAGE_CONTAINER_NATIVE_TRIAL.md) independently
proved actual selected-feed/server/recipient byte equality. It does not activate
this new codec or supply product source/adoption evidence. Keep #26 open.
