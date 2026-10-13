# Offline map probe

This independently written SDK experiment registers one ATAK toolbar item. It opens
Swedish create/move/delete controls for one disposable synthetic point in a separate
feature layer. It requires offline mode and clears the point on unload/restart.
It does not implement saved TMM drafts or sharing. See [the experiment](../docs/LOCAL_MAP_PROBE.md).

Target: ATAK-CIV API 5.6.0.CIV, supplied SDK developer app 5.6.0.24.
The earlier loading-only build passed its isolated emulator lifecycle test; see
[EMULATOR_TEST.md](../docs/EMULATOR_TEST.md). Standard phone/signing and network/privacy
verification remain pending. No SDK, signing files or permissions are added to the APK.
