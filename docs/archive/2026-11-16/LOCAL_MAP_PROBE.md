# Offline feature-layer experiment (Issue #4)

This development APK (`0.1.0-map-probe`, versionCode 2) replaces the loading toast
with three Swedish test actions: create, move and delete one synthetic point.
It is an SDK experiment, not the Mini/Scout/PIR/Gateway planner or a saved draft.
Use only the isolated developer emulator from [EMULATOR_TEST.md](EMULATOR_TEST.md).

## Candidate representation

The independently written `LocalMapProbe` uses SDK 5.6.0.24 signatures verified with
`javap`: `FeatureSetDatabase2(File)`, `FeatureSet`, `FeatureLayer3`, `Point`,
`BasicPointStyle`, `MapView.addLayer/removeLayer` and `RenderStack.POINT_OVERLAYS`.
It creates the database with a null file and registers only its own rendering layer.
It does not create a `Marker`, add a `MapItem` to the root group, call persist/refresh
map events, register a CoT/export handler, or invoke a publishing API.

TAK's official source explains the null-file in-memory database and the feature-set
resolution bounds. Source is supporting evidence; the supplied SDK build and actual
host behavior remain the contract. [FeatureSetDatabase2](https://github.com/TAK-Product-Center/atak-civ/blob/main/takkernel/engine/src/main/java/com/atakmap/map/layer/feature/datastore/FeatureSetDatabase2.java),
[FDB2](https://github.com/TAK-Product-Center/atak-civ/blob/main/takkernel/engine/src/main/java/com/atakmap/map/layer/feature/datastore/FDB2.java).

Create uses latitude/longitude 0/0; move keeps latitude 0 and changes longitude to
0.001 degrees. Coordinates are deliberately synthetic. The probe pans/zooms to the
test point. Its random UUID label identifies only this disposable experiment.
The cyan dot is not a device symbol. No manual-derived icons are included.

After each mutation, the probe queries the database to check feature count and
geometry and checks that its UID has no registered ATAK MapItem. Errors produce an
error notification rather than a success toast. Duplicate creation and movement
without a point produce a clear message. Unloading removes the layer, closes the
dialog and disposes the database. Restart does not retain test points.

## Offline restriction

Opening the controls and each mutation require airplane mode, the Android Wi-Fi
setting off, no connected/connecting active network and the host's network-state
permission. Unknown/missing state fails closed. This code runs in ATAK's process;
its guarded host-context network-state query adds no permission to the plugin APK.
The checks are restrictions for this test build, not proof of N03 or a substitute
for externally verified network isolation. For the offline procedure below, keep server profiles and feeds absent. The separate
isolated connected experiment is described in [TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md).

## Repeat the probe

1. Build and statically check the APK with [DEVELOPMENT.md](DEVELOPMENT.md).
2. Start the separate emulator and confirm airplane mode, disabled Wi-Fi/data and
   empty guest route table before launching ATAK. Retain its generic EGL workaround.
3. Install the plugin APK by explicitly targeting `emulator-5580`. Load it in Plugins.
4. Open Tools → TMM Sensor Planner → **Skapa testpunkt**. Expect one cyan dot at
   the synthetic origin and the message that the point is only in memory.
5. Repeat create. Expect **Testpunkten finns redan**, with one point remaining.
6. Select **Flytta testpunkt**. Expect the point to move east and **Testpunkt flyttad**.
7. Select **Ta bort testpunkt**. Expect no dot/label and **Testpunkt borttagen**.
8. Repeat delete, then move. Deletion stays harmless; move requests creating first.
9. Create again, then unload. The dot, label and Tools entry must disappear.
10. Reload/restart. No old point must reappear. Creating must work again.

Keep screenshots, logs and APKs local. No developer APK belongs in a feed or server
upload. Only sanitized evidence belongs on GitHub.

## Evidence and remaining gate

Test environment: 2026-10-13, AOSP API 35 arm64-v8a, Android Emulator 37.2.12,
SDK developer ATAK-CIV 5.6.0.24, generic EGL workaround, native Mac acceleration.
Local assembleCivDebug, lintCivDebug and APK signature/discovery/permission/packaging
checks passed. The SDK host skips production signature checks.

Observed on this build:

| Check | Result |
|---|---|
| Create | One cyan dot/UUID label rendered at the synthetic origin; count and geometry readback passed |
| Duplicate create | Rejected; one point remained |
| Move | Dot moved east; query returned longitude 0.001 and latitude 0 |
| Delete / repeated delete | Dot and label disappeared; query returned zero features; repeating was harmless |
| Move without a point | Rejected with a request to create first |
| Unload with a point | Plugin Manager changed to Not loaded; point and label disappeared |
| Reload | Started empty; move requested creating first, then create succeeded |
| Host restart with a point | Old point did not reappear; move requested creating first, then create/delete succeeded |
| Offline guard | Controls did not open with the airplane-mode flag cleared while Wi-Fi/data stayed disabled and routes stayed empty; flag restored afterward |

No network was enabled for the negative flag test. It verifies the mode restriction,
not absence of transmissions on a connected client.

APK SHA-256: `e86cef68996cefa0d3c4613006f57882fa03ffd683bf8ef3c3b8809adb163660`.
Core and local GitHub-auth helper regression checks also passed. Source review found
no blocking defect for the experiment; it does not approve production privacy.

Issue #4 is still open. This single offline client cannot establish that another
client or TAK Server receives no event. A01/N03 needs a separate isolated two-client
and server/traffic observation test, including create/move/delete, reload/restart,
cancellation and relevant export/share surfaces. Record exact host/server builds
and synthetic UIDs, establish a baseline and observe all configured outputs.
Do not enable operational networking or accept this candidate for live draft use
until that evidence and an architecture decision are reviewed.

The subsequent connected two-client experiment is recorded in
[TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md). It passed the bounded CoT observation
checks, while production acceptance and export/share integration remain pending.
