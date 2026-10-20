# Local images in the offline planner

Issue: [#13](https://github.com/gustavtraff/tmm-sensor-planner/issues/13), F09/A05.
Integration: [#35](https://github.com/gustavtraff/tmm-sensor-planner/issues/35).
Phone-photo correction: [#49](https://github.com/gustavtraff/tmm-sensor-planner/issues/49).
Host resource correction: [#62](https://github.com/gustavtraff/tmm-sensor-planner/issues/62).
The offline planner uses this library for one image per saved device. Host-side
library/metadata tests do not need an emulator or private ATAK SDK.

The returned service-signed versionCode 10 now passes bounded standard ATAK-CIV
5.6.0.25 preview Cancel/Save, the corrected Swedish size notice and image reopening
after restart, using a generated 3072 × 4096 JPEG. See [the exact candidate and
limits](STANDARD_CLIENT_VERIFICATION.md#latest-signed-candidate--2026-10-18);
the owner’s original phone photo/provider remains an acceptance check.

## Build and test

The standalone `images` Android library uses the repository's pinned Gradle 8.14.3,
AGP 8.13.0, Android platform 36/build-tools 35.0.0 and JDK 17. Set JAVA_HOME and
ANDROID_HOME as in DEVELOPMENT.md, then run from the repository root:

```sh
./gradlew -p images --no-daemon testDebugUnitTest lintDebug
```

AndroidX ExifInterface 1.4.2 is the only runtime dependency. It reads JPEG/PNG EXIF
without maintaining a custom TIFF parser ([official release](https://developer.android.com/jetpack/androidx/releases/exifinterface)).
JUnit 4.13.2 and Robolectric 4.16.1 are test-only; tests explicitly request native
graphics and synthetic API 26/35 environments. Native codec tests are stronger than
mocked Bitmap results, but do not prove phone/provider behavior or ATAK compatibility.
The public `images` CI job runs these checks separately from required `core`.
A commit-pinned android-actions/setup-android v3.2.2 installs public command-line
tools build 12266719, platform 36 and build-tools 35.0.0 before the library build.
Tests compile the existing Java core as test-only sources to exercise actual
DraftStore commits, stale revisions and an error after metadata replacement.
No private SDK, signing material or image artifacts are uploaded. The `images` job
is not automatically added to main's required status rules by this PR.

## Image contract

The accepted [ADR 0006](decisions/0006-local-mvp-product-rules.md) remains authoritative:

- Primary JPEG still/PNG detected from bytes, not a filename or supplied MIME type.
- Input at most 10,485,760 bytes, read with a bounded stream before decoding.
- Positive source dimensions and at most 20,000,000 pixels checked before bitmap
  allocation; multiplication uses a long. New local copies are at most **1,600
  pixels on their longest side**, preserving aspect ratio with integer rounding.
  Smaller inputs are not enlarged. The save preview explicitly explains this
  reduction and offers Save locally or Cancel; the original stays untouched.
- Structural PNG chunk/CRC checks reject truncation, trailing content and APNG.
  JPEG framing checks validate the first image through its end marker. MPF metadata
  and trailing auxiliary data are allowed, but EXIF/bitmap decoding receives only
  the validated primary range. Extra images, gain maps, video and trailing bytes
  are discarded. Android decoding must then succeed. These checks do not guarantee detection of
  every damaged JPEG entropy bit that a platform decoder can tolerate.
- EXIF rotations and reflections are applied to pixels, including all eight
  orientations. Large sources are sampled during decoding rather than first
  allocating full-resolution bitmaps. Orientation and scaling are drawn together
  into one fresh bounded ARGB bitmap and encoded as a static PNG,
  without copying original EXIF/GPS, text, thumbnails or embedded metadata.
  Source color profiles are not preserved. Normalization is not forensic erasure
  and does not remove sensitive information visibly drawn in an image.
- The normalized output still has a 10 MiB byte limit. The bounded local dimensions
  prevent the previous full-resolution JPEG-to-PNG expansion failure in the
  reproduced camera-size fixture. Low memory remains an explicit failure; no
  every-device memory guarantee is made. Old full-resolution owned PNGs remain
  readable under the original 10 MiB / 20-million-pixel limits and are not rewritten.
- Source streams/files are never modified. The caller owns and closes the source
  stream. The original is never written into the image directory.

## API and save ordering

Construct `LocalImages` with a dedicated private, no-backup directory. Use API 26+
only, matching DraftStore's NIO requirement. The library manifest retains minSdk 21
so a guarded integration does not raise the plugin's installation floor; its Java
implementation is marked TargetApi 26. That annotation does not provide a runtime
guard. The caller must check the platform version before constructing or calling
LocalImages. No image support on API 21–25 is claimed. Operations and callbacks run on the caller's thread;
perform them on the planner's serialized I/O worker, never the UI thread.

1. `prepare(input)` creates and syncs a fresh normalized file with an opaque
   `<lowercase-UUID>.png` handle. It returns a `Pending`, without editing a draft.
2. `Pending.preview()` returns bounded normalized bytes to show for confirmation.
   The UI must display this copy, not the unsanitized source. Closing a pending
   image without committing cancels and deletes only that new owned copy.
3. After explicit user acceptance, call `Pending.commit(reference -> ...)` and
   persist a validated draft edit through DraftStore with the new handle. Preserve
   UUID, creation time and the normal revision check. Do not show success early.
4. The pending image is retained **before** invoking that save callback. If a save
   throws, both the old image and candidate remain: the snapshot may already have
   committed before the I/O error. Reload metadata to discover the saved state;
   preserve the user's edit and do not silently retry. A pending commit is one-shot.
5. After a known-successful save and fresh metadata read, `deleteUnreferenced(old)`
   may remove the detached old copy only if no saved draft references it. Serialize
   this read/cleanup with all draft/image edits. A cleanup failure is an orphaned
   copy, not a rollback of the committed metadata. Never delete an arbitrary path.
6. Removing an attachment saves the draft with `imageRef = null` **first**; deleting
   a draft similarly commits metadata deletion first. Only then clean up the owned
   copy after checking current references. A failed or ambiguous metadata operation
   must not delete either copy. No server or recipient deletion is implied.

`read(reference)` reopens the owned copy for preview/viewing after restart and
checks bounded bytes and image structure. Missing, damaged, nonregular or symbolic
link files raise an error; the caller must keep the stored reference and show a
translated error. Handles reject paths, URI schemes and traversal. The directory
itself must be private and trusted; this is not a hostile shared-filesystem API.
Translate filesystem errors at the UI boundary without logging paths or image data.

The app includes `:images` as a dependency. `DraftAttachments` serializes metadata
commit, fresh snapshot read and safe cleanup on the existing planner worker. `imageRef` remains an opaque optional string in snapshot v1.
Only new image-library handles use the UUID/PNG convention. Old arbitrary handles
(including the v1 compatibility fixture) are not rewritten or interpreted as paths;
unsupported/missing handles remain visible errors. There is no schema migration,
wire reference, attachment transport, CoT field or sharing implementation here.

## Offline UI

Save a device first, then use **Lägg till bild** in its detail view. The system
local-document picker requests JPEG/PNG; byte validation remains authoritative.
Cancel leaves the saved device unchanged. The worker copies and normalizes the
selected document, and the scrollable preview displays only that normalized copy
with the 1,600-pixel reduction notice. **Spara bilden lokalt**
explicitly attaches it. **Ersätt bild**, **Visa bild** and confirmed **Ta bort bild**
operate on the same saved record. Deleting a draft commits metadata before safe
image cleanup. Viewing a missing/unsupported reference reports an error and retains
metadata. An uncertain save asks for a fresh reload rather than blind retry.

Preview decoding runs on the worker with a sampled display bitmap (approximately
1,024 pixels on its longest side). New stored copies have the 1,600-pixel longest-side cap; legacy files retain their
existing validated resolution. Picker/read/normalization operations use the same serialized worker as
metadata; a slow document provider may delay later operations. No persistent URI
grant or original URI is stored. Close unregisters the result callback and discards
uncommitted pending copies. No broad photo/storage permission is requested.

The registry signatures and host callback forwarding were checked against SDK
5.6.0.24. ATAK also forwards activity results, including the source URI, to its own
in-process activity-result broadcast listeners. Other host plugins share the UID;
this is not isolation from ATAK or its plugins. TMM does not publish an attachment,
CoT or Data Sync resource. The prototype still requires offline mode.

The library declares ExifInterface 1.4.2; SDK main.jar contains 1.4.1 with compatible
used methods and PNG EXIF support. Parent-first host loading can select the host
version. Native library tests alone do not establish actual host codec behavior.

Physical-phone/provider acceptance, low-memory behavior and standard-client
signing/coexistence remain separate gates. No persistent orphan sweep is implemented:
a crash or ambiguous commit can leave bounded-size unreferenced copies; any future
cleanup must use a validated fresh snapshot and respect active pending previews.

## Recorded host verification

On 2026-10-17, `testDebugUnitTest` and `lintDebug` passed locally using Temurin
17.0.20.1+1 and the pinned standalone toolchain. All 20 native-graphics test cases
passed across synthetic API 26/35 environments, with no skipped tests. Lint had
zero errors and two update-availability warnings. `sh scripts/test.sh` also passed,
including the unchanged v1 storage fixture and separate-process snapshot reopen.
Neither result is phone or ATAK runtime evidence. File synchronization does not
make metadata and image writes one filesystem transaction or guarantee power-loss
durability; ambiguous saves and crashes may retain extra copies.

## Integrated SDK-host verification — 2026-10-17

The API-35 arm64 headless emulator used the supplied ATAK-CIV **5.6.0.24 developer
host**, guest IPv4/IPv6 egress blocking, airplane mode and disabled radios. All
coordinates and images were synthetic; no server or second client was involved.

- Picker opened through the actual host registry and local Downloads provider.
- Cancelling a normalized PNG preview removed the pending file and left the draft
  snapshot byte-identical. Confirming it advanced the saved draft revision.
- Replacing it with a rotated JPEG left exactly one owned image and removed the old
  PNG after metadata commit. Both source documents stayed byte-identical.
- Orientation-6 80×40 PNG/JPEG inputs became 40×80 normalized PNGs. The PNG text marker
  and EXIF chunks were absent; the rendered image had red above blue as expected.
- The saved owned image reopened in the plugin after force-stopping/restarting ATAK.
- Confirmed image removal advanced the revision, cleared the association and left
  the owned image directory empty; the device and other fields remained.
- Temporarily hiding the owned file produced an explicit missing-image error; the
  draft snapshot stayed byte-identical and the test file was restored.

`sh scripts/test.sh` passed. All 34 native image/metadata tests passed (20 library
and 14 attachment-coordinator cases across API 26/35). `sh scripts/check-sdk.sh`
passed with the supplied SDK: APK assembly, app lint (zero errors, six warnings)
and static signature/discovery/permission/packaging checks. Standalone image lint
also passed. A separate read-only review found no blocking data-loss/lifecycle bug.

This does not verify phone/OEM providers, all eight EXIF variants in the actual
host, low-memory/provider hangs, unload with a picker outstanding, standard
5.6.0.12 signing, image sharing, connected privacy or A05 on physical hardware.
Cancellation/cleanup I/O failures and process death can leave unreferenced copies.
Private test APK SHA-256:
`80c138bf304f2a445f4e733944baffa71bfdea522d27ecd44e1c20fcb246b65b`.

## Phone-photo failure and correction (#49) — 2026-10-18

The owner reported that an approximately 4.4 MB, 3072×4096 camera JPG failed with
the generic preparation/limits message, while a 2400×1080 screenshot succeeded.
The JPG's 12,582,912 source pixels and reported bytes are below both input caps.
No original phone photograph was obtained; its exact failure cause remains
unverified. A synthetic detailed JPEG at the same dimensions (JPEG quality 40,
seeded noise, EXIF orientation 6) reproduces the previous failure during PNG
compression. A separate unbounded PNG encode of the same decoded fixture is
asserted to exceed 10 MiB. The regression failed against the old implementation
before the fix.

New copies are explicitly reviewed at a maximum 1,600-pixel longest side. This
replaces the former reject-instead-of-resize default through human review of #49;
it does not change the owner-confirmed 10 MiB input cap or metadata-removal rule.
The compromise is reduced detail in the local copy; the source photo stays intact.
There is no full-resolution attachment option in this fix and no image sharing.

Preparation errors now distinguish oversized original bytes, excessive source
pixels, unsupported/undecodable still images, low memory, oversized normalized
output and copy-write failure. Unclassified provider/read failures recommend a
local file without displaying raw exception text or a URI. Existing attachments
are preserved. Failed/ambiguous metadata saves still use the existing separate
reload-before-retry message and retention policy.

Tests additionally cover the camera-size copy's dimensions/EXIF removal/reopen,
source immutability, 1,599/1,600/1,601-pixel and transparency boundaries, unchanged
legacy PNG bytes, safe failure categories and translated messages. The snapshot
v1/UUID-PNG handle contract is unchanged; this is not a storage migration.
Phone/provider and standard-host revalidation require the newly signed version;
the already signed versionCode 7 APK is unchanged by this branch.

### Local verification of the #49 branch

- Standalone `testDebugUnitTest lintDebug`: passed, **39 tests**, no failures or
  skips (24 native image cases across API 26/35, 14 attachment-coordinator cases
  across API 26/35, one safe-message test); image lint has zero errors and one
  `UseRequiresApi` warning.
- `sh scripts/test.sh`: passed, including the existing snapshot-v1 fixture,
  separate-process reopen and the synthetic 100-device scenario.
- SDK 5.6.0.24 debug/release assembly and both app lint variants: passed using
  the pinned toolchain. Debug APK signature/discovery/permission/packaging checks
  passed with the existing unsigned metadata-entry warnings. Local debug signing
  and unsigned release output are not a new service-signed phone artifact.

VersionCode 8 / `0.1.0-camera-image-fix.1` distinguishes this correction from the
owner-tested versionCode 7. Actual ATAK picker/preview layout and the owner's
original camera/provider need retesting after a reviewed source submission and
returned signed output. No new emulator or physical-phone runtime test, service
upload, feed publication or release is claimed by these local checks.

## Image-copy notice resource boundary (#62) — 2026-10-18

The connected SDK trial exposed a resource collision: the copy-size disclosure
displayed `res/animator/fragment_fade_enter.xml`. `LocalImageUi` was looking up a
plugin string ID through the host map context. The plugin entry point now resolves
and formats the notice through its existing `PluginContextProvider` context, using
`LocalImages.MAX_COPY_EDGE`, and passes the finished text to the local image UI.
Dialogs, views, content access and the activity-result picker retain the host
context. No additional SDK API, image limit, normalization rule, data contract,
permission or transport is introduced.

The candidate is versionCode 10 / `0.1.0-image-copy-notice.1`, based on main `6cf1e48`.
Android Debug APK SHA-256:
`66f8b22059aceb7da9360e6734961b00587383ad468c4bbd021121a2b7c1547b`.

Checks on this candidate:

- SDK 5.6.0.24 debug/release assembly, app lint with zero errors, all three app unit
  tests and the debug APK contract/signature/permission/packaging checker passed.
- All 40 independent image/coordinator/session tests and image lint passed.
- Actual AOSP API 35 arm64 / ATAK-CIV developer 5.6.0.24 showed the complete Swedish
  notice with the formatted **1600** limit and original-file assurance through the
  real local Downloads picker. The incorrect resource path and format placeholder
  were absent.
- At 1920×1080 / 160 dpi, Cancel preserved the snapshot bytes and old owned image;
  only the pending copy was removed. At 1280×720 / 320 dpi, the complete notice and
  both controls remained readable; confirmed Save committed a new local image,
  removed the unreferenced old copy and preserved the generated source bytes.
- The saved image reopened after a cold host restart with unchanged snapshot
  bytes. Read-only viewing did not display the replacement/save disclosure. The
  guest crash buffer contained no AndroidRuntime fatal exception.

Only the existing synthetic guest and a generated PNG were used: airplane mode,
Wi-Fi/data off, IPv4/IPv6 OUTPUT drop and no ADB reverse tunnel or running test
server. An initial small-picker tap fell in the clipped part of a file tile and
selected nothing; it was corrected to the visible tile before the passing check.
Raw XML/screenshots, snapshots and APK remain private. This does not verify the
owner's original photograph/provider or the newly service-signed standard host.
Keep #62's standard-client check and #49/#7 phone acceptance pending until a new
reviewed signed artifact is available.
The task guest was stopped after verification.

## Camera JPEG primary-still compatibility (#49) — 2026-10-20

The owner retested signed versionCode 10 on a OnePlus Nord 2T 5G / Android 14,
using the available Files picker. Screenshots worked, but existing and newly taken
ordinary camera photos failed with the unsupported-still-image message. The camera’s
high-resolution option was off. Actual encoding/container remains unknown; no
original phone image was obtained. This is not a confirmed diagnosis of that file.

The old JPEG validator unconditionally rejected MPF and required its end marker to
be the final byte of the entire input. Android’s official [Ultra HDR format](https://developer.android.com/media/platform/hdr-image-format)
uses a primary JPEG plus an appended gain map and MPF metadata; [Motion Photo](https://developer.android.com/media/platform/motion-photo-format)
can append video to a still. A synthetic MPF-indexed two-JPEG fixture reproduces
`INVALID_IMAGE` against the old code before the change. This demonstrates a real
compatibility defect, not proof that this phone emitted either format.

VersionCode 11 / `0.1.0-camera-primary-still.1` validates JPEG framing through the
primary end marker, then passes only that range to EXIF reading and both bitmap
decodes. It does not follow MPF offsets or parse/decode/copy auxiliary pictures,
HDR recovery data, video/audio or trailing content. Concatenated JPEGs likewise
produce only the first still, shown for explicit confirmation. The preview now
discloses this primary-still-only policy as well as resizing. This replaces the
blanket MPF/trailing-JPEG rejection; it does not add HDR rendering, motion playback,
MPO browsing or HEIF/HEIC support. PNG/APNG checks are unchanged.

The complete source (including discarded bytes) must still fit **10 MiB**; primary
pixels remain limited to 20,000,000, and normalized PNGs to 1600 pixels / 10 MiB.
Original files, stored handles, snapshot v1 and safe metadata commit/Cancel ordering
are unchanged. Higher-detail viewing and zoom remain separate in #66.

The regression covers MPF plus a second JPEG, a tagged arbitrary trailer, a second
concatenated JPEG and zero padding. It compares normalized pixels/bytes to the
ordinary primary, reopens saved output and verifies immutable source bytes. It also
checks the whole-input cap even when excess bytes are discarded. Truncated primary
framing followed by another valid JPEG and invalid segment lengths remain rejected.
The fixture is generated, not a full Ultra HDR encoder/conformance suite. Phone
acceptance needs the next service-signed APK; keep #49 open until that retest passes.

### Verification of versionCode 11

- Regression failed with `INVALID_IMAGE` against the unchanged validator, before
  applying the primary-range fix. The final independent image/coordinator/session
  suite passed **42 tests**, no failures/skips, on API 26/35 native graphics;
  image lint passed. MPF before and after EXIF/header metadata are both covered.
- `sh scripts/test.sh` passed, including snapshot-v1/separate-process reopen and
  the synthetic 100-device scenario. SDK 5.6.0.24 debug/release assembly, app lint,
  all three app unit tests and the debug APK checker passed. Existing unsigned
  metadata-entry signature warnings remain.
- Actual AOSP API 35 arm64 / ATAK-CIV **5.6.0.24 developer host** accepted a generated
  3072 × 4096, **402,456-byte** JPEG containing MPF, a secondary JPEG and trailing
  synthetic data. The full Swedish primary-still/1600-pixel notice was visible.
  EXIF orientation 6 rendered red above blue and saved as a **1600 × 1200 PNG**.
- MPF after EXIF was also accepted. Cancelling replacement preserved the committed
  snapshot and old PNG byte for byte and removed only the pending copy. Both
  generated source files remained unchanged. After a cold process restart the saved
  image reopened and snapshot/image bytes were identical; no EXIF/text chunks were
  retained. The captured crash buffer had no fatal exception. Airplane mode, disabled
  Wi-Fi, first-rule IPv4/IPv6 OUTPUT drops and no reverse tunnels were verified
  before stopping the fresh synthetic guest. No server profile was configured. The fixture matches the phone's pixel dimensions, not its
  content, compressed size, provider or complete container implementation.

An early test fixture accidentally contained two conflicting EXIF packets from the
synthetic encoder/insertion step. It was rejected; that is not a passing camera test
or proof of a new phone defect. The corrected fixture has exactly one primary EXIF
packet. Orientation validation was not weakened. Temporary synthetic diagnostics
were removed before the final build; its DEX contains no diagnostic tag. Initial
harness reads before async save completion were also discarded in favor of completed
UI/storage observations. Raw screenshots, inputs and logs remain private.

Tested Android Debug APK SHA-256:
`6b90553582fdc87818289c5ccdf18d631bf79f0bd5d41cb5fea17f108e538650`.
This SDK result is distinct from a service-signed standard-host/physical-phone
result. No new signing upload, release, operational connection or image sharing
was performed. The OnePlus camera/provider gate remains open in #49/#7.
