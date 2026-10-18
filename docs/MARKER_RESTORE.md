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

Runtime observations, exact artifact identifiers and unresolved verification are
recorded here after the checks complete. SDK evidence and service-signed standard
host evidence must be reported separately.
