# Offline local planner prototype

The main tool opens Swedish local-draft list/forms/review with private storage,
map selection, bearing and distances. It requires offline mode and Android API 26+;
older clients get an unavailable message, without changing APK minSdk 21.
The separate CoT lab tool retains fixed synthetic fixtures and explicit lab confirmation.

Target: ATAK-CIV API 5.6.0.CIV, supplied SDK developer app 5.6.0.24.
See [LOCAL_PLANNER.md](../docs/LOCAL_PLANNER.md) for behavior, SDK verification,
runtime evidence and limits. The old one-point map tool was replaced; its earlier
[experiment](../docs/LOCAL_MAP_PROBE.md) remains historical evidence.

Use only synthetic data in the isolated test emulator. This is not a release or a
verified standard-phone installation. No SDK, signing files or permissions are added
to the APK. Image files and production sharing remain separate work.
