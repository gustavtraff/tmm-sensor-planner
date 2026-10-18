# Saved-marker restoration on plugin startup (#50)

## Problem and behavior

Previously, `SensorPlannerPlugin.onStart()` registered tools but did not create
`LocalPlanner`. Its private map layer and snapshot were loaded only after the user
opened TMM. Saved devices could therefore be intact on disk while absent from the
map after a host restart. Choosing Show on map incidentally triggered that lazy path.

The plugin now schedules the existing planner/load path at normal startup. It
restores the saved private feature layer without opening a list/review, selecting
a device, changing map position/scale or sharing anything. Markers outside the
current map viewport remain outside it; restoration does not promise centering.
The explicit toolbar still opens/reloads the normal list.

## Lifecycle and boundaries

- Both startup and toolbar entry use the same synchronized planner creation path.
  API 26 and the existing offline check run before constructing the planner. There
  is one owned planner per started plugin instance; its layer/listener set is
  created only when a valid read is ready to render.
- Snapshot loading stays on the planner's existing single worker, serialized with
  normal writes. Layer refresh and dialogs stay on the host UI queue. A toolbar
  request received during startup loading is remembered and opens the list when
  that read completes, rather than silently losing the click or starting two reads.
- Offline state is checked again before applying a completed read. A changed guard
  discards the view, leaving stored bytes alone. An initially blocked startup waits
  for a later permitted plugin reload or explicit toolbar open; no network observer
  or automatic retry is added.
- Empty storage restores an empty layer silently. Corrupt/unreadable/future storage
  clears the unusable local view and shows a generic startup toast directing the
  user to TMM. Explicit toolbar opening retains the non-destructive error dialog.
  There is no reset, migration, record skipping or replacement with an empty file.
- Stop serializes against planner creation, cancels sharing immediately and queues
  the existing close path. Closed instances ignore late load callbacks; private
  GL-layer disposal and worker shutdown remain owned by that instance. A later
  plugin load creates a new instance and reads the snapshot again.
- There are no new SDK APIs, permissions, normal ATAK Marker/MapItem objects,
  broadcasts, CoT/Data Sync calls, image decoding or changes to snapshot-v1/UUIDs.
  No record metadata, position, image reference or exception detail is logged.

This preserves the prototype's offline restriction; it does not establish normal
connected-phone privacy or standard-binary compatibility for newly changed code.

## Repeatable verification procedure

Use a dedicated synthetic SDK guest, not another agent's client or an operational
phone. Keep source snapshots, generated APKs and raw UI/diagnostic evidence private.
Retain the original valid snapshot outside the guest before fault injection.

1. Save synthetic records of all four types. Establish a known viewport using the
   host's normal quit path, then cold-start the baseline and fixed APK separately.
   After each start, do not open TMM, review a record or invoke Show on map. Compare
   the actual private-layer rendering, absence of planner dialogs and snapshot bytes.
2. Repeat cold start and normal Plugin Manager unload/reload. Require tools once,
   an absent layer while unloaded and restored records after reload without TMM
   interaction. Verify actual icon selection still opens the intended saved UUID.
3. Repeat with an empty valid snapshot, a checksummed future-version snapshot and
   checksum-damaged bytes. Empty storage remains empty. Invalid storage gets a safe
   indication, renders no misleading records and remains byte-identical. Explicit
   TMM opening must offer diagnosis without resetting anything. Restore valid bytes.
4. Disable only the airplane flag while radios stay off and guest egress remains
   blocked. Cold-start with the guard false; require no restoration. Restore the
   guard and reload the plugin; require normal restoration.
5. In this disposable guest only, replace the snapshot temporarily with an owned
   FIFO and stage valid fixture bytes separately. This holds the worker's read
   without blocking the UI. Click TMM, release the FIFO and require the requested
   list. Separately change the offline flag or unload before releasing it: require
   no late layer/dialog resurrection. Replace the FIFO with the valid regular file,
   reload and compare bytes again. This is deterministic test fault injection,
   not a supported storage format or recovery procedure for user data.
6. Build/lint/static-check with the supplied 5.6 SDK and run the core tests. Repeat
   the applicable lifecycle cases on the newly service-signed artifact in standard
   ATAK and then on the owner's phone; never bypass host signature validation.

## Evidence and remaining gates

### SDK lifecycle matrix — 2026-10-18

Implementation `a91c2da` was tested in a private API-35 arm64 SDK developer host
**5.6.0.24** clone at 1920×1080 / 160 dpi. Airplane mode was on, Wi-Fi/data/location
off; the first guest IPv4/IPv6 OUTPUT rule dropped all traffic. No reverse tunnel,
server profile, feed or send/share action was used. All 20 records were synthetic
(four types × five statuses); the original valid snapshot was retained privately.
Other agents' worktrees and test guests were not modified.

The full matrix used versionCode 7, APK SHA-256
`d9c647b55efb8d633717c80d34b7a78b336409d2b8031d4638646a3656bf04da`.
The old-build control used the previously tested symbol APK
`823b8051f6c4da913a77aa125be6ef924fe21e5570eb08d9f1fc2c79f11b41b7`.
A known synthetic viewport was saved with the host's normal quit path before the
matched comparison; no TMM/list/review/centering action was taken after the tested
cold starts. Actual map screenshots were checked, with a simple green/yellow-pixel
assertion distinguishing the complete grid from its absence; this is not a native
layer/listener-count measurement.

| Actual SDK check | Observed result |
|---|---|
| Matched old/fixed cold start | Old build had no grid; fixed build rendered all 20 saved points and their bearing cues automatically in the existing viewport, with no planner dialog. Regular snapshot bytes were identical. |
| Second fixed cold start | Same restored grid without TMM interaction or snapshot changes. |
| Normal unload/reload | Grid absent while unloaded, restored after reload without opening TMM; saved bytes unchanged. |
| Empty valid snapshot | No points or automatic list/dialog; explicit TMM opening showed zero drafts and left the file unchanged. |
| Future/damaged snapshot | No misleading points or automatic planner dialog; explicit opening showed the safe storage error. Both exact input byte sequences survived startup and explicit diagnosis unchanged. |
| Guard false at startup | With only the airplane flag cleared (radios still off and firewall drops intact), no restoration. Permitted reload after restoring the flag recovered the grid. |
| Blocked read / toolbar request | An owned FIFO held the worker while the UI opened Tools/TMM. No premature list appeared; releasing valid bytes opened the requested 20-record list. This proves the UI did not perform the blocking storage read. |
| Offline changed during read | Completion produced no layer after the guard became false. The valid regular fixture was then restored explicitly by the harness. |
| Unload before read completion | Releasing the old worker after unload did not resurrect a layer/dialog. Restoring the valid regular fixture and reloading recovered all points. |

ATAK sometimes presented its normal **Load Plugins?** recovery question after
forced process stops. The harness used **Load Plugins**, without disabling that
safeguard or changing signature validation. An initial test assertion captured the
host waiting for this choice; it was corrected to wait for actual plugin loading.
A separate normal-quit log check also exposed a pre-existing cleanup crash (below);
not every recovery question is attributed solely to forced stops. The plugin
cannot restore anything when the host intentionally skips/disables it.

`sh scripts/test.sh` passed (domain/forms/reviews/lab codec, storage/v1/failure and
separate-process/100-device checks). `sh scripts/check-sdk.sh` passed for the fix,
including Android build/lint and static APK signature/discovery/permission/packaging.
No domain, snapshot schema or wire contract changed.

### Integration with the parallel camera correction

Main `a4c5f74` (#49 / PR #54) was merged into this branch. There was no code conflict;
both appended test sections were retained in docs/TESTING.md. The combined
versionCode-8 SDK build/lint/static APK command passed. Its APK SHA-256 is
`9b4ac999e7760960d91e0fd07c97be5f47843024aeafd833f3a8b27ecef202df`.
That initial combined candidate restored the grid but exposed the shutdown fault
below, so it is not the final validated artifact.

### Shutdown correction and final combined artifact

Normal **Quit** reproduced a pre-existing main-thread NullPointerException in
`LocalDraftLayer.close()`: deferred cleanup queried `map.getGLSurface()` after ATAK
had cleared it. The same stack occurred in the old symbol build and the initial
restoration candidate. This was detected by checking runtime logs even though the
next startup could still render correctly.

The supplied 5.6.0.24 JAR was inspected with `javap`: AtakMapView teardown removes
layers, disposes the GL surface and clears its field; GLMapView.queueEvent delegates
to its retained render context. The layer now captures its owning GLMapView before
allocating a datastore and reuses that same queue during close. It rejects an
already unavailable renderer before allocating map resources. The previous
renderer-release-before-database-dispose ordering is preserved; no immediate UI-thread
datastore close or null-surface fallback is introduced. No SDK code/binary is published.

Final implementation `de63192`, including merged #49, built versionCode **8** /
`0.1.0-camera-image-fix.1`. Tested Android Debug APK SHA-256:
`f167ae4847b0d48bcd323e4277a6c915731014e072451c0e8c62e1da82456c06`.
SDK build/lint/static APK checks passed again. Actual final-artifact follow-up passed:

- Cold restoration of all 20 points before opening TMM, unchanged saved bytes.
- Actual restored PIR Active icon selection opened its revision-1 saved review.
- Tools contained exactly one planner and one CoT-lab entry.
- Normal Quit terminated the host; relaunch restored the grid without TMM interaction.
- Normal unload removed the grid; reload restored it without opening TMM.
- A checksummed future snapshot produced the generic startup toast, observed through
  the guest notification service and screen capture without opening TMM. Exact bytes
  survived; replacing the harness fixture with valid bytes restored all points.
- A fresh validation log window contained no AndroidRuntime fatal or closed-database
  diagnostics. The earlier failure evidence was retained privately before clearing
  the disposable guest's log buffers.

The valid synthetic snapshot was restored byte-for-byte and the dedicated guest
stopped after verification. Screenshots, APKs, assert-based driver scripts and raw
logs remain private/ignored. This is bounded SDK evidence, not a native-resource
leak audit, connected two-client privacy test or standard-signed runtime result.

### Standard-host and phone gate remains open

The modified code has not yet been supplied as a newly service-signed artifact.
The signed versionCode-7 result tested with standard ATAK-CIV **5.6.0.25** in
[STANDARD_CLIENT_VERIFICATION.md](STANDARD_CLIENT_VERIFICATION.md) predates this fix.
It cannot verify the changed startup path. No signature bypass, operational-phone
installation or external source submission was performed. After human review/merge,
a reviewed service build including both #49 and #50 is needed for standard-host
repetition and the owner's phone check. Issue #50 remains open for these gates;
green public CI and SDK-host results do not replace them.
