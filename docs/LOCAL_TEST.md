# Private offline test: development loading probe

This test checks only plugin discovery, loading, the toolbar item and unloading.
The planning forms, map view, persistent drafts, images and sharing are not implemented.
Mini, Scout, PIR and Gateway are currently domain catalogue entries, not a working
device-creation screen. Issue #3 remains open for physical-client verification.

## Choose the correct host app

The supplied test plugin is an **Android Debug-signed development APK**, built against
the local ATAK-CIV 5.6.0.24 SDK and declaring plugin API 5.6.0.CIV. Use a separate
test phone or emulator with the **developer atak.apk included in that SDK**.
Developer-app loading has not been tested yet.

The reported standard phone app is ATAK-CIV 5.6.0.12. This APK is not a TAK-signed
production plugin and is not a validated installation for that app. A matching API
number alone does not establish signing or runtime compatibility. Standard-app
testing needs the appropriate TAK signing/build path.

Do not replace or uninstall the operational ATAK app to conduct this test. If the
test device already contains an ATAK installation or user data that must be kept,
stop and use a clean separate device or emulator. The SDK developer app uses the
same Android package name as standard ATAK, so they are not independent side-by-side apps.

TAK describes its developer host and third-party signing process in
[the official development process](https://tak.gov/pages/our-process).

## Keep the test private

1. Use a clean test client without production server profiles, certificates or feeds.
2. Enable airplane mode and explicitly turn off Wi-Fi and mobile data. Keep networking
   off throughout the test; some phones allow Wi-Fi while airplane mode is enabled.
3. Transfer the APK by USB to local storage. Do not upload it to a TAK Server,
   Data Sync feed, mission package, shared folder or app repository.
4. Install by opening the APK from Android's file manager, using the Android package
   installer. Do not use ATAK's Share, Send, Data Sync, feed or package-upload actions.
5. This plugin requests no Android permissions and its code only registers a toolbar
   item and displays a toast. It contains no CoT, feed, publication or server adapter.
   This source/artifact inspection is not a substitute for the later two-client
   network-observation test when map integration and sharing are introduced.

Local Android APK installation does not itself publish a file to a Data Sync feed.
Keeping the client disconnected provides a separate barrier for the host app and
other plugins, whose network behavior is outside this loading probe.

## Install and check

1. On the clean test device, install the SDK's developer `atak.apk` from your own SDK.
   Complete its initial setup locally; do not configure a TAK Server or feed.
2. Install `TMM-Sensor-Planner-0.1.0-dev-ATAK-5.6.0-CIV.apk` through Android's installer.
   If needed, allow installation from that file-manager app for this installation
   and revoke that permission afterward. Keep normal Android security checks enabled.
3. Open the developer ATAK app while still offline. Find TMM Sensor Planner in its
   plugin manager (menu labels can vary by ATAK build) and enable/load it.
4. Find the TMM Sensor Planner item in ATAK's tools/toolbar. Depending on the toolbar
   configuration it may be in the tools list rather than directly on the map toolbar.
5. Select it. Expected result: the message **Lokal prototyp** appears. No form or
   device marker is expected yet.
6. Disable/unload the plugin. Its toolbar item should disappear. Load it again and
   check that exactly one item appears. Restart ATAK and repeat.
7. Finish by unloading the plugin and, if desired, uninstalling only the **TMM Sensor
   Planner plugin** from the clean test device. Do not remove the host app or its data.

## Report results

Record the following without server addresses, credentials, device identifiers or
screenshots containing actual map positions:

| Check | Result |
|---|---|
| Test device model and Android version | |
| ATAK host full version and developer/standard distribution | |
| Plugin package and version | se.tmm.sensorplanner.plugin / 0.1.0-dev |
| Android installation | Pass / fail |
| Plugin discovery and load | Pass / fail |
| Toolbar item visible once | Pass / fail |
| Lokal prototyp message | Pass / fail |
| Unload removes the item | Pass / fail |
| Reload and restart | Pass / fail |

If ATAK reports incompatible/signature mismatch or cannot load the plugin, stop and
record the exact error text. Do not bypass validation or change the production host.
The standard app's signature checks and the developer/standard build distinction
are separate from the plugin API number. Testing with HV Reports, Data Sync and
TAK Voice comes afterward in a controlled environment.

For the completed headless developer-host loading probe, setup and graphics workaround,
see [EMULATOR_TEST.md](EMULATOR_TEST.md). Its result does not validate standard-app signing
or replace physical-phone and coexistence tests.

This loading-only checklist describes version `0.1.0-dev` from `c55709d`.
For the newer `0.1.0-map-probe` development APK, the toolbar opens test controls
instead of the old toast; follow [LOCAL_MAP_PROBE.md](LOCAL_MAP_PROBE.md).
