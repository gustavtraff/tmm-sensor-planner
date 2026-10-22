# Owner-reported offline phone acceptance — 2026-10-22

Tracking: #74; local features #49/#50/#51/#52/#53/#62/#66/#67 and umbrella #12.
This records the owner's answers in the project conversation, not an independently
instrumented phone test. No phone photograph, location, account, raw log or screenshot
was collected. It is bounded offline acceptance, not a release or network approval.

## Candidate and environment

- Source: reviewed main `b643ee633cee2b9d16c4333fa7f14b7a0931622b` (PR #73).
- Service-signed APK: package `se.tmm.sensorplanner.plugin`, versionCode **15**,
  versionName **0.1.0-device-details.1**.
- APK SHA-256: `221017dd9f2eeb8d5c46ab2fd1db235f2644059f480fe7874865dc4e8fdd7645`.
- Source ZIP SHA-256: `f6bdc67c62a8ce36f389902f0fea192a0ece177fe7c7dd913ada1124b8aeb7d0`.
- Prior owner-supplied phone inventory: **OnePlus Nord 2T 5G / Android 14**;
  standard ATAK-CIV full build **5.6.0.12** was previously reported. The host build
  was not freshly read from the phone in this test; reconfirm it before online work.
- The owner followed the offline checklist after an update installation. Airplane
  mode/Wi-Fi isolation was requested; actual phone network state was not instrumented.
- Local static artifact checks passed: signature integrity, package/version, API
  5.6.0.CIV, discovery registration, no requested APK permissions and packaging.
  The signing certificate matches the earlier versionCode 10 artifact. Existing
  unprotected META-INF metadata-entry warnings remain; these checks are not a
  complete security/dependency review or standard-host runtime proof by themselves.

## Owner observations

| Check | Reported result and boundary |
|---|---|
| Update and prior data | Existing records/images retained; planner opens |
| Cold ATAK restart | Saved map markers appear without first opening the planner or choosing Show on map |
| Device review | ID/type/status clear; position/image/bearing reachable; More details works |
| Map labels/status | Identity and Local remain without lifecycle-status words; manual status editing works |
| MGRS | Display, copy, manual edit/save/reopen work |
| Graphical move | Map selection/Use/Save and cancellation work |
| Bearing | 6400-mil checklist reported working, including cardinal directions, 6400-to-0 and invalid input checks; no physical compass calibration measured |
| Camera import | Previously failing camera-photo scenario now works; other photos also work. No original bytes/container obtained, so the exact historical file cause remains unproven |
| Image inspection | Thumbnail/full viewer, zoom and useful detail reported working; no on-phone pixel-dimension measurement performed |
| Replacement cancellation | Previous image retained, including after restart, per checklist response |
| Forms/time | Keyboard completion and date/time checklist reported working; no exhaustive DST/timezone proof inferred |
| Persistence | Final restart preserves edited position/status/bearing/image per checklist response |
| Picker cancellation | Android Back first navigates up from Camera to its parent; one further Back returns to ATAK. No forced photo selection is required; this observation does not justify a cancellation bug |
| Orientation | Owner reports orientation works very well; exact display sizes and each rotation transition were not recorded |
| Background/return | Home then return to the open image works; viewer closes normally |

New owned copies retain at most a 1920-pixel longest edge without upscaling, cropping
or aspect-ratio distortion. Old 1600-edge copies do not regain detail without
reselecting the source. The phone feedback confirms usability, not full-resolution
source retention or an independent metadata/encoding audit. The original camera
format/provider is still unknown. Local state is not a delivery acknowledgment.

## Remaining acceptance

Do not mark all A01–A10 or #7 complete from this run. Still outstanding: signed
standard-client two-client privacy with traffic/recipient/server observation; actual
companion report/feed/voice workflows; production sharing/import/update/conflicts;
image transport; broader security/dependency/provenance/distribution review.
Physical TalkBack, active-picker/viewer Unload, exact rotated-map behavior and
adversarial/missing-file cases were not newly exercised by the owner. Earlier
bounded automated/SDK evidence remains in the feature documents. No phone reboot,
clean install, byte-level storage comparison or universal device compatibility is
claimed. The draggable bearing control remains optional and deferred.

The service built the same source archive successfully after jobs 48999 and 49044
failed after successful Gradle builds with `AAB signing has failed`. No code change
was required between submissions; the underlying signing-service cause is unknown.
Raw result packages and signer details remain private. No release was published.

See [NEXT_STEPS.md](NEXT_STEPS.md) for the proposed sequence and exit gates.
