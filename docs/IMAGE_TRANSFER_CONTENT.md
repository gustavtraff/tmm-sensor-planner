# Verified image content boundary (#26 / #126 / #130)

The owner approved PR #118's logical recommendations A–F/H on 2026-10-29,
with **Data Sync-feed as the target for G**. The [decision record](IMAGE_SHARING_CONTRACT_PROPOSAL.md#owner-decision-record--2026-10-29)
keeps exact wire/container/receipt and private recovery formats gated before their
writers. This first implementation covers only preparation and verification of
image **content**, not the association or delivery of that content.

## Implemented API

`ImageTransferPng` in the standalone Android `images` library requires API 26+.
It uses the existing Android decoder and Java cryptography/zlib APIs; it adds no
dependency, file writer, SDK call, app caller or UI entry point.

- `prepareExport(ownedPng)` accepts an already owned PNG, bounded at 16 MiB. It
  makes a separate normalized PNG copy using the existing orientation/scaling
  policy, removes encoder ancillary chunks, validates the resulting closed profile
  and freezes its exact bytes, SHA-256, byte length and dimensions. It never modifies
  the supplied bytes or an owned file. Legacy images may be reduced to a 1920-pixel
  longest edge; smaller images are not enlarged. A future sharing UI must preview
  **this export copy**, disclose its dimensions and obtain explicit inclusion and
  destination review. Merely preparing it is not consent to share it.
- `verify(payload, expectedIdentity)` verifies the **exact received bytes** against
  the expected SHA-256, length, width and height, and returns a defensive copy with
  the verified content identity. It never sanitizes, resizes or re-encodes received
  bytes. A mismatch fails; it cannot retain an expected identity for changed bytes.
- `readAndVerify(payloadStream, expectedIdentity)` reads one complete image payload
  through EOF, accumulating at most the expected length (itself <=16 MiB), with
  bulk reads of at most 8192 bytes. It rejects early EOF and reads **at most one
  extra byte** to reject longer/endless sources without draining them. Short reads
  are supported; a zero bulk read falls back to one single-byte read instead of
  spinning. Only complete exact-length content reaches the same strict verifier.
  No `available`, mark/reset, skip, filename or claimed MIME type is trusted.
- `Identity` and `VerifiedContent` defensively copy byte arrays. Identity is a
  content measurement, not a device UUID, source authority, image offer, delivery
  identity or persisted record. Its constructor alone does not verify bytes.

All preparation/verification methods return in-memory content only and may throw
a safe image-preparation error. Invalid content cannot change any draft or local image through this API.
Out-of-memory failures during preparation/verification use the existing low-memory
error category. This does not guarantee that every device has enough memory.

For complete byte-array input, upstream code must still bound actual bytes before
allocating it. Prefer `readAndVerify` when the complete payload is exposed as a stream:
its accumulation grows from at most 8 KiB rather than allocating the entire claimed
larger length up front. It leaves the caller's stream open on success and failure; use
try-with-resources at the owning boundary. I/O failures (including interruption and
a failure during the final EOF probe) propagate without a verified result. Translate
provider exceptions safely before displaying/logging them.

The stream must represent exactly **one completed payload**, not a live connection
containing subsequent frames or an indefinite producer. EOF validation consumes at
most one extra byte on a rejected oversized frame; discard that frame rather than
continuing from the consumed position. Read deadlines, cancellation/unblocking and
closing the source belong to the transport/provider owner. This synchronous API
cannot time out a blocked provider read. Never use it on the UI thread or start
unbounded concurrent verifications. It does not enforce aggregate staging/history
quotas or protect an earlier network/container/decompression allocation. It is not
a new container parser, storage writer or network integration.

## Closed receive profile

The validator requires all of the following before returning verified content:

- At most 16,777,216 bytes; positive dimensions, each at most 1920 pixels.
- PNG signature, one 13-byte IHDR, consecutive IDAT chunks and final empty IEND;
  valid chunk lengths/CRCs, no trailing bytes or other chunks.
- Eight-bit RGB or RGBA, standard compression/filter methods and no interlace.
  No EXIF, text, ICC, ancillary/unknown chunks, palette or animation data.
- One complete zlib stream across all IDAT chunks, no dictionary, trailing or
  concatenated compressed stream. Output is checked incrementally against exactly
  `height * (1 + width * channels)` bytes and every row filter is in 0–4. An oversized
  expansion is rejected before a full uncompressed image buffer is allocated.
- Actual Android decoding succeeds and its dimensions match the checked header.
- SHA-256, complete byte length and dimensions match the expected identity on receipt.

Normal Android-owned PNGs can contain sRGB/sBIT chunks. Thus existing
`ImageFormat.checkOwnedPng` remains the permissive local structural check; it is
not the closed transfer verifier. Export reuses the existing owned-image decode /
normalize code and strips ancillary chunks from that fresh encoder output. Receipt
rejects them. Export bytes/hash can differ from the local copy even with identical
pixels; no hash of decoded pixels or filename substitutes for the exact bytes.

## Compatibility and remaining work

The original picker input remains JPEG/PNG <=10 MiB and <=20 million pixels.
The original `LocalImages.prepare` still validates that source limit before calling
the extracted internal normalization helper. Existing image handles, original
files, local snapshots/v2, metadata format 1 and inbox formats are unchanged.
No active attachment is rewritten, adopted or cleaned up by the new API.

Verified in-memory bytes are **not** a durable stored-byte readback or verified
association under PR #118. Source namespace, exact device/metadata binding, offer /
delivery identity, persistent staging/recovery, capacity accounting, manual adoption
and optional consented feedback remain separate work. Exact formats and independent
fixtures must be reviewed before new storage/wire writers. The actual Data Sync-feed
route and two-client source/recipient evidence remain gates. Contact transfer is
not a substitute, and metadata publication still excludes images.

The already submitted version-21 signing ZIP is unchanged. This library-only
change does not create a new signed APK or release.

## Verification — 2026-10-29

Using the pinned JDK 17 / standalone Android toolchain:

```sh
./gradlew -p images --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx1536m testDebugUnitTest \
  --tests se.tmm.sensorplanner.images.ImageTransferPngTest \
  --tests se.tmm.sensorplanner.images.LocalImagesTest lintDebug
```

**58 cases passed**, no failures/errors/skips: 28 new content cases and 30 existing
local-image regression cases across native-graphics API 26/35 environments. Lint
passed with zero errors and eight warnings (target/dependency updates, API annotation
advice and existing custom-view warnings). The new class follows the library's
existing API-26 annotation convention and adds one `UseRequiresApi` advisory.

Coverage includes independent H1/H2 exact-byte golden files, real owned-encoder
output, immutable source/pending bytes, metadata stripping only on export,
11 MiB owned-PNG export versus the unchanged 10 MiB original-input rejection,
legacy resizing/no upscaling, wrong identities, CRC/framing errors, unsupported
headers/chunks, bad filters, dictionaries, truncation, over-expansion, trailing /
concatenated zlib streams and splitting compressed bytes at every IDAT boundary.

No full image suite, core suite, ATAK/SDK build, physical-phone test, emulator,
Docker, network transfer, persistence fault test or signing submission was run for
this content-only slice. The focused tests establish neither association/storage
durability nor end-to-end Data Sync image delivery.


## Stream-input verification — 2026-10-30 (#130)

The focused `ImageTransferPngTest` suite and `lintDebug` passed with the pinned
standalone toolchain: **44 cases**, zero failures/errors/skips across native API
26/35 (the original 28 plus 16 new stream cases). Lint has zero errors and eight
existing advisories. The eight new scenarios cover exact one-pixel/golden payloads,
short/zero-progress reads, EOF/declared-length mismatches, an endless generated
source stopped after 16 MiB + one actual byte, an exact maximum-length invalid
source requiring both EOF and content checks, early EOF under a maximum claim,
read/interruption/EOF-probe failures, unchanged strict hash/dimension/profile checks,
caller-owned closure and the safe low-memory category via a synthetic exception.
These are not measured device memory-pressure, blocked-read deadline or transport
acceptance tests. No new fixture, core/data format, UI, app caller or signed artifact
changed. No core/full-image suite, SDK build, emulator, Docker, phone/server or CI
wait was run for this content-only slice.
