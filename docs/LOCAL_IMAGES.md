# Local images in the offline planner

Issue: [#13](https://github.com/gustavtraff/tmm-sensor-planner/issues/13), F09/A05.
Integration: [#35](https://github.com/gustavtraff/tmm-sensor-planner/issues/35).
The offline planner uses this library for one image per saved device. Host-side
library/metadata tests do not need an emulator or private ATAK SDK.

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

- Static JPEG/PNG detected from bytes, not a filename or supplied MIME type.
- Input at most 10,485,760 bytes, read with a bounded stream before decoding.
- Positive dimensions and at most 20,000,000 pixels checked before bitmap allocation;
  multiplication uses a long. No silent downscaling.
- Structural PNG chunk/CRC checks reject truncation, trailing content and APNG.
  JPEG framing checks reject truncation, concatenated pictures and MPO.
  Android decoding must then succeed. These checks do not guarantee detection of
  every damaged JPEG entropy bit that a platform decoder can tolerate.
- EXIF rotations and reflections are applied to pixels, including all eight
  orientations. Pixels are drawn to a fresh ARGB bitmap and encoded as a static PNG,
  without copying original EXIF/GPS, text, thumbnails or embedded metadata.
  Source color profiles are not preserved. Normalization is not forensic erasure
  and does not remove sensitive information visibly drawn in an image.
- The normalized output must satisfy the same byte/pixel limits. A JPEG that fits
  the input limit may expand to a PNG that is rejected. Low memory is an explicit
  preparation failure; the pixel ceiling does not promise every phone can allocate it.
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
selected document, and the preview displays only that normalized copy. **Spara bilden lokalt**
explicitly attaches it. **Ersätt bild**, **Visa bild** and confirmed **Ta bort bild**
operate on the same saved record. Deleting a draft commits metadata before safe
image cleanup. Viewing a missing/unsupported reference reports an error and retains
metadata. An uncertain save asks for a fresh reload rather than blind retry.

Preview decoding runs on the worker with a sampled display bitmap (approximately
1,024 pixels on its longest side). The stored normalized file retains its validated
resolution. Picker/read/normalization operations use the same serialized worker as
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
