# Local image library and adapter handoff

Issue: [#13](https://github.com/gustavtraff/tmm-sensor-planner/issues/13), F09/A05.
This is the independent image component for later integration into #12's planner;
there are no picker, form, preview-widget or APK behavior changes in this PR.
No emulator, phone, server or ATAK SDK is needed for its host-side tests.

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

For UI integration, include the standalone library as `:images` and add it as an
app dependency after #12; this PR deliberately leaves app/build.gradle and the
planner untouched. `imageRef` remains an opaque optional string in snapshot v1.
Only new image-library handles use the UUID/PNG convention. Old arbitrary handles
(including the v1 compatibility fixture) are not rewritten or interpreted as paths;
unsupported/missing handles remain visible errors. There is no schema migration,
wire reference, attachment transport, CoT field or sharing implementation here.

## Remaining #13 acceptance

Keep #13 open for integration after #12: system-picker cancellation and input
permissions, preview before confirmation, one attachment per record, view/replace/
remove actions, missing-file messages, cleanup after draft deletion and failure UI.
Test actual private/no-backup paths, API guard, all edit/save ordering and image bytes
across offline ATAK/phone restart. Add actual device/provider evidence for rotated
JPEG/PNG, size limits and memory failures. Host-native tests alone do not pass A05,
phone A02/A09 or signing/coexistence. No persistent orphan sweep is implemented:
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
