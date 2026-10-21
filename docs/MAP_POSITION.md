# MGRS and graphical local placement (#52)

VersionCode 14 / `0.1.0-mgrs-position.1` replaces the local form's separate decimal
latitude/longitude fields and saved-review coordinates with WGS84 MGRS.

## Use and commit boundary

Choose **Välj plats på kartan** in a new or existing device form. The editor is
hidden, retaining the same widgets and deployment-time value; it is not rebuilt.
A small dialog identifies the device and shows current MGRS. Pan/zoom the map
under the cyan sight, then choose **Använd plats** or **Avbryt platsval**. Back from
the picker also cancels. Invalid coordinate text can be replaced through the map;
when current form coordinates are valid, selection starts there at the existing
local-map centering scale. Cancellation can leave a different map viewport but
keeps the form position and all entered fields unchanged.

Accepting a position only updates the form. **Spara lokalt** validates/commits later;
cancelling the whole form saves nothing. Existing UUID, created time, image handle,
status and unrelated fields still use the same domain edit/metadata storage path.
A hidden editor is temporarily non-cancellable during the window transition so an
early Back cannot discard it. Unload closes owned dialogs/sight/listener and does
not restore an editor after disposal. All local actions keep the existing session
access gates. The sight is Android window UI, never a Marker, MapItem, CoT object,
persisted preview feature or image/metadata transport.

## Coordinate precision and validation

- Output: UTM zone/band, 100 km square and five digits per axis (nominal one-meter
  grid). Input accepts the same precision, compact or ASCII whitespace-separated,
  and either letter case. Other precisions, malformed/odd digits, invalid zones,
  grid columns/rows and unroundtrippable references fail inline. No silent clipping,
  hemisphere inference or correction to a different valid square.
- Use SDK `MGRSPoint.fromLatLng`, `decodeString`, `MutableMGRSPoint.offset` and
  `toLatLng` with `Ellipsoid.WGS_84`. Prefer the cell center for manual entry;
  for a cell crossing a zone/band boundary, try interior points of that same cell
  and require SDK reformatting to reproduce the entire reference. If no candidate
  round trips, reject it and offer graphical selection. This conservative boundary
  policy may reject an exceptionally thin valid boundary sliver; it never silently
  accepts another grid. Round-trip test tolerance is two meters per axis, not a
  promise of sensor/GPS accuracy. SDK descriptor formatting includes its own small
  numerical boundary tolerance.
- Supported latitude range: **80°S to 84°N**, UTM-based MGRS. UPS/polar input is
  explicitly unsupported in this first adapter. Display longitude +180 using the
  equivalent -180 grid; canonical saved/map longitude is preserved if untouched.
- Cache the exact original or accepted map position and its displayed grid. An
  unchanged reference (including case/whitespace-only edits) resolves to those
  exact numeric values. Only a changed manual grid is decoded to a cell position.
  Old polar records can be saved unchanged on unrelated edits; creating/replacing
  a position requires supported MGRS or an eligible map selection.

## Verified SDK surface

Inspected the supplied **5.6.0.24 SDK main.jar** signatures and bytecode before use.
Besides the coordinate APIs above, use existing `MapView.getPoint().get()`,
`forward(GeoPoint)` and controller `panZoomTo`, plus
`AtakMapView.addOnMapMovedListener/removeOnMapMovedListener`. Map callbacks post
UI updates to the map view. The listener and non-touchable sight exist only during
selection. No map event dispatcher stack, host tool callback or network API is
replaced. The #53 label renderer is untouched.

`sh scripts/test-mgrs-sdk.sh` compiles only the adapter, Position and synthetic
checks against the actual external SDK Java classes. Tests cover hemispheres,
zone/band boundaries, Norway/Svalbard cases, antimeridian, UTM limits, malformed
input/unsupported precision, failed-parse preservation and exact original/map
position retention. No SDK is committed or fetched by public CI. The core domain,
storage format and wire units are unchanged.

## Candidate verification

- `sh scripts/test-mgrs-sdk.sh` passed against actual SDK 5.6.0.24 classes,
  including UTM limits, hemispheres, Norway/Svalbard, antimeridian and boundary
  fixtures, invalid input and exact position-edit preservation. An initial
  southwest-corner round-trip check failed at a boundary; the cell-interior policy
  above was added and all final coordinate checks passed.
- `sh scripts/test.sh` passed, including snapshot/separate-process reopen and the
  100-device scenario. Final debug/release SDK assembly, app lint, all three app
  unit tests and the debug APK checker passed. Existing metadata-entry signature
  warnings remain. No image codec changes were made and image tests were not
  rerun locally for this issue; public CI results are not awaited or claimed.
- Actual isolated AOSP API 35 arm64 / SDK developer host 5.6.0.24: MGRS review/edit,
  map pan with live grid/sight, Cancel and stable Back, retention of unsaved
  ID/owner/comment/position, invalid manual input with unchanged storage, valid
  manual input, explicit map Use then Save, and graphical new-device creation
  passed. Use alone left the snapshot unchanged. A first harness attempt tapped
  before window focus; a second targeted the still-visible keyboard. Neither is
  counted as a product pass; final tests used settled windows and dismissed IME.
- Reading captured synthetic snapshots through the actual core store confirmed
  that an unrelated ID edit kept latitude/longitude bits exactly; a map move
  preserved UUID, createdAt, imageRef, type, status, owner, comment, deployment
  time and bearing while advancing revision. Creating another record preserved
  the prior record and made a separate UUID with the entered fields.
- Rebased the merged #53 label change without conflicts. The final combined APK
  built/linted/tested and upgraded the task guest without changing saved records.
  After cold restart, MGRS review/editor/map picker/Cancel worked and snapshot plus
  attached PNG bytes stayed identical. Only generated synthetic data was used.
  Airplane/Wi-Fi state, first-rule IPv4/IPv6 OUTPUT drops and no reverse tunnels
  were verified; the crash buffer contained no fatal exception. Only this task's
  guest was stopped; the parallel labels AVD was untouched. Raw evidence is private.

Final combined debug APK SHA-256:
`1123a5514c0c4bbb265fa01fe85c5b55ad938f392edf552c51699a4d267dc34c`.

Standard signed-host, physical keyboard/touch, portrait, rotated-map and
active-picker Unload remain distinct acceptance gates under #52/#7. The SDK test
uses blocked network egress, so it does not establish production connected privacy.
No new signing archive/upload, operational connection or release was performed.
