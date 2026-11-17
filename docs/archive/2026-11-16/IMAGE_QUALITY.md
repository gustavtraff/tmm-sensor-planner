# Full HD local image copies (#66)

VersionCode 13 / `0.1.0-full-hd-images.1` changes new owned PNGs to a maximum
**1920-pixel longest edge**, maintaining aspect ratio and never enlarging smaller
sources. A 3840 × 2160 image becomes 1920 × 1080; an orientation-6 3072 × 4096
camera JPEG becomes 1920 × 1440 after rotation (1440 × 1920 without that rotation).
No forced 16:9 crop, padding or distortion is applied. Very wide panoramas naturally
have a shorter short edge; the policy is a dimension limit, not an assertion that
every aspect ratio has 1080 vertical pixels.

## Bounds and encoding

- Complete original input: **10 MiB**, at most **20,000,000 pixels**; unchanged.
- Owned metadata-free static PNG: **16 MiB**, with newly normalized dimensions at
  most 1920 × 1920. Existing supported PNGs may have larger dimensions within the
  existing 20-million-pixel limit; opening them never rewrites them.
- PNG remains lossless relative to the resized pixels. No extra lossy quality
  setting, new codec, dependency, image-handle format or snapshot migration.
- Source decode is sampled before a single orientation/scale draw into the clean
  bitmap. With the existing power-of-two sampling, the decoded longest edge is
  below twice 1920 when reduction is needed: the clean pixel buffer is at most
  14.1 MiB, and the sampled pixel buffer is below approximately 56.3 MiB. Encoded
  input/output buffers, temporary array copies and host allocations are additional;
  this is not a total-heap or every-phone memory guarantee. Preparation is serialized
  on the existing worker; low-memory failure preserves the old image.
- The output cap is separate because 1920-square ARGB pixels alone occupy
  14,745,600 bytes before compression. A shared 10 MiB limit needlessly rejects
  some detailed PNG representations. Reads are bounded at 16 MiB before validation;
  sources are still rejected above 10 MiB before decode. No smaller-resolution
  fallback silently violates the requested detail policy.
- Preview decode remains at most 1024 pixels per edge; the zoomable viewer uses
  an independent at-most-2048 decode, so it retains all current stored detail.

## Preservation and limits

The preview gets its 1920 value from the actual normalization constant through
plugin resources, before explicit Save locally. Originals, primary-JPEG handling,
EXIF orientation/removal, transparency, existing opaque handles and metadata
commit/Cancel ordering stay unchanged. Old 1600-pixel copies cannot regain detail;
select the original again to replace them. A new version can read old images and
snapshot v1 directly. Older APKs limited to 10 MiB owned reads may not show new
copies above that size; downgrade compatibility is not claimed.

The historical 1600-edge policy addressed a reproduced synthetic PNG-expansion
case. It did not establish 1600 as necessary, nor diagnose the owner's OnePlus
photo. The JPEG-validator correction is separate (#49). No new signed phone or
standard-host acceptance, distribution or image sharing is implied by core/native
or SDK checks. Physical camera/provider, placement detail and gestures remain open.

## Verification

- **52 native API 26/35 image/coordinator/session tests passed**, no failures,
  errors or skips; image lint passed. Cases include a detailed seeded-noise
  3072 × 4096 camera JPEG with EXIF orientation, immutable source and clean metadata,
  16:9 and portrait dimensions/no upscaling, 1919/1920/1921 boundaries,
  transparency, old-copy byte preservation and independent 1920-detail display.
- A generated high-entropy 1920-square RGBA PNG exceeds the old 10 MiB cap but fits
  16 MiB. The owned-read path reopens it byte-identically while the source path
  still rejects it as over 10 MiB. Both owned validation and bounded file reads
  reject 16 MiB + 1 without deleting the file. This is a synthetic storage-boundary
  fixture, not a claim that an imported phone photo produced those bytes.
- `sh scripts/test.sh` passed, including snapshot/separate-process reopen and the
  100-device scenario. SDK 5.6.0.24 debug/release assembly, app lint, all three app
  tests and final debug APK checker passed. Existing metadata-entry signature
  warnings remain. A first new test run failed because its fixture directory was
  absent; the fixture setup was corrected before the complete passing suite.
- Actual isolated AOSP API 35 arm64 / **ATAK-CIV 5.6.0.24 developer host**:
  installing over versionCode 12 preserved the existing snapshot and 1600-edge
  image bytes. The old image opened normally. Importing the generated 3072 × 4096
  MPF JPEG displayed the actual Swedish **1920** notice. Enlarging and cancelling
  preserved the old snapshot/image and cleaned the pending copy. Explicit Save
  produced an orientation-correct **1920 × 1440 PNG**. Zoom/pan/Fit/Close and cold
  restart reopening preserved saved bytes; the generated source stayed unchanged.
- Airplane mode, disabled Wi-Fi, first-rule IPv4/IPv6 OUTPUT drops and no reverse
  tunnels were checked before stopping the task guest. No fatal exception was in
  the captured crash buffer. A startup System UI wait prompt was dismissed before
  the product checks. Raw generated inputs, screenshots and logs remain private.

Tested debug APK SHA-256:
`f171d947680bd9b80bf61df5e530a21d077fbe55e35be6ef2473dab05e512bb0`.

No new signed standard-host or physical-phone result is claimed. OnePlus camera
provider acceptance (#49), physical placement detail/gestures, portrait and
active-viewer Unload remain open under #66/#7. No ZIP, signing upload or release
was performed.
