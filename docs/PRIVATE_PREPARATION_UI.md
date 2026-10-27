# Private metadata preparation UI — #14

Version 17 / `0.1.0-private-preparation.1`, based on main `65390ec` after #99/#100.
This adds a saved-device review action and verifies the codec on actual Android.
It creates a private **in-memory** metadata snapshot; no file, ordinary host map
copy, feed selection, sender acknowledgment or network operation is added.

## Operator flow

Open a local device, expand **Fler uppgifter**, then choose **Förbered metadatakopia**.
The existing local-access policy still applies, including explicit consent for a
connected-local trial. Private preparation does not enable the separate CoT lab.

1. Read the current saved device on the planner's existing single worker.
2. Review its ID/type/status, owner/comment, UUID, MGRS position, true-north 6400-mil
   bearing and exact UTC creation/update/deployment times. Optional fields remain
   optional. A saved local image association shows **Bilden delas inte**, even if
   the referenced file is missing. No image file is opened by preparation.
3. **Förbered lokalt** reloads the saved record on the same worker as local
   edits/deletes/image changes. The existing one-use review checks UUID, revision,
   metadata and saved image association. A changed/deleted/unavailable record must
   be read and reviewed again; there is no fallback to the earlier snapshot.
4. Encode and decode the approved XML v1 profile in memory on that worker. Only a
   successful actual decode admits the resulting immutable metadata to the result
   dialog. The source draft/image is never rewritten and no stored state is added.
5. Display **Metadatakopia förberedd**, the decoded metadata and the explicit
   **Förberedd i minnet. Ingen feed vald. Inte skickat av TMM.** outcome.
   **Stäng** discards the result and returns to the underlying device review.

Cancel, Android Back and a connected-local-trial transition invalidate the pending
generation, close any pending core review and release the UI's prepared reference.
Unload first invalidates callbacks synchronously, then closes dialogs on the UI
thread. Already queued/in-flight memory work cannot display a late success after
cancellation; it creates no file/host/network side effect. Encoding/decoding and
storage reads run off the UI thread. Double confirmation queues only one operation.
No secure-memory-erasure claim is made; temporary arrays/strings follow normal GC.

Other planner actions remain busy-blocked while this modal flow owns the review.
An out-of-band change is still caught by the fresh saved-state comparison. This
is a reviewed snapshot, not continuous tracking of a draft after preparation.
Saved-state equality is a point-in-time check, not a lock across other processes
or an anti-ABA history token. Deletion/recreation with identical UUID/revision/values
is not distinguished by snapshot v1. No application or later handoff authority is
derived from this check; D's stronger commit/history rules remain separate.

## Why there is no artifact file

The verified SDK candidate takes an in-memory CotEvent. A standalone file is not
required to advance that route, so this slice avoids a second persistent cache,
retention/recovery rules and file cleanup. The private candidate is useful only
inside the current review; closing/Unload/restart requires preparing again.
No export, file provider or generic XML importer is exposed. The metadata profile
and snapshot/inbox formats are unchanged; no migration is needed.

Future host handoff must be an explicit new action, including its own current-state
checks and the accepted manual-native-Done limitations. The new production detail
tag/envelope still needs its own SDK/feed acceptance; the old experimental tag's
lab result is not silently reused. D's draft-application contract remains separate.

## Verification — 2026-10-25

- `sh scripts/test.sh`: passed, including existing codec, inbox, snapshot and
  separate-process/100-device suites.
- `sh scripts/check-sdk.sh`: passed on the final configuration: SDK probes,
  CivDebug build/lint, existing app unit tests and static APK checks.
- `./gradlew --no-daemon :app:assembleCivRelease`: passed, including release
  shrinking now that the app calls the codec. This is not a service-signed APK.
- Targeted `PublicationPreparationUiTest`: **12 passing cases** across Robolectric
  API 26 and 35. Covers actual reloaded metadata, unchanged snapshot/image reference,
  changed metadata, image-only changes, deleted/corrupt storage, Cancel/Back, absent
  image wording, cancelled queued results, duplicate clicks, stop and lost access.
- `assembleDebugAndroidTest` and library `lintDebug`: passed. The standalone test
  package initially inherited target SDK 21 and Android 15 correctly refused its
  installation. The library now declares target 34 for that test packaging; the
  consuming plugin already targets 34 and sets its own target. Installation was
  repeated normally, without bypassing Android's target/signature checks.
- `sh scripts/test-publication-android.sh emulator-5586`: **PASS** on one existing
  Android 15/API 35 arm64 guest, using actual `org.apache.harmony.xml.ExpatReader`.
  All four independent fixtures re-encoded byte-for-byte; 28 malformed cases were
  rejected. The runner is standard platform Instrumentation with no new dependency.

The optional native-probe script requires an explicitly selected running qemu
guest/API 26+, builds/installs only the library's standalone test APK, asserts the
PASS instrumentation result and removes its own test package. It neither boots
the emulator nor starts/updates ATAK, a server, or a transport observer. Fixture
assets are synthetic. The standalone APK has no access to ATAK's private store.

For this run, only one existing guest was started; loopback-only guest egress and
Wi-Fi-off were enforced, ATAK was kept stopped, and no Docker/server was started.
The probe package was removed and the guest stopped afterward; final crash buffer
had zero FATAL EXCEPTION entries. Raw build/runtime evidence stays ignored in
`private-data/`. No full image suite or CI wait was performed.

Actual Android parser execution is now verified for API 35, not every Android/OEM
combination. The dialog tests are Robolectric, not standard ATAK runtime evidence.
Signed v17 loading, physical phone/dialog acceptance and later new-tag/native feed
integration remain pending. No signing submission, operational test or release was
performed; a new source ZIP can be prepared when a signed test candidate is wanted.

## Signed standard-host follow-up — 2026-10-26

[The v18 native-host trial](PUBLICATION_ENVELOPE_RUNTIME.md) also exercised Cancel
and successful private memory-only preparation of an existing synthetic draft
with a local image. Image-exclusion/no-feed/no-send wording was observed and both
private snapshots remained byte-identical. This closes the bounded signed-dialog
check, not every failure/lifecycle case or physical-phone acceptance. Production
handoff and trusted ingress remain separate; no source change or new ZIP is needed.

## Separate saved-record map-copy candidate — 2026-10-27

[The lab-only map-copy pilot](SAVED_DRAFT_COPY.md) adds its own read/review/fresh
confirmation action. It cannot consume this private prepared result and leaves
this memory-only action unchanged. A shared metadata-summary method prevents
review fields drifting between the two views. New caller runtime needs signed v19.
