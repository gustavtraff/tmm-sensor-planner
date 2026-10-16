# TMM Sensor Planner

Initial project setup for an ATAK-CIV plugin for local planning of TMM devices.
**Compatibility target: ATAK-CIV 5.6.0; not yet verified.**

The repository currently contains an SDK-independent Java core for local drafts,
validation, horizontal map distances and a versioned local snapshot store, plus a locally built minimal development
APK. Loading passed in the isolated SDK developer emulator; standard-app signing
and phone compatibility remain unverified. The [offline local prototype](docs/LOCAL_PLANNER.md)
now connects forms, review, private storage and map selection on Android API 26+.
Production connected privacy, image files and sharing remain pending. An [offline synthetic map probe](docs/LOCAL_MAP_PROBE.md)
and a separate [synthetic CoT lab tool](docs/COT_ROUND_TRIP.md) support SDK investigations.
The CoT tool sends fixed test fixtures only after recipient selection and confirmation
through the isolated localhost lab; it does not publish domain drafts.
A separate [image-package investigation](docs/DATA_SYNC_AND_ATTACHMENTS.md) verified
standalone synthetic file transport; Data Sync and production image sharing remain deferred.

## Getting started

With JDK 17 (Java code is compiled for Java 8):

```sh
sh scripts/test.sh
```

The tests require no external libraries or ATAK SDK.

- [Project plan](docs/PROJECT_PLAN.md)
- [Product and requirements](docs/PRODUCT.md)
- [Development environment and SDK gate](docs/DEVELOPMENT.md)
- [SDK intake and integration probes](docs/SDK_VERIFICATION.md)
- [Feasibility backlog](docs/BACKLOG.md)
- [Testing](docs/TESTING.md)

The minimal plugin builds and loads in the local 5.6.0.24 SDK developer app.
See [the emulator result](docs/EMULATOR_TEST.md). Physical-client verification remains pending.
APIs, local map privacy, and the sharing path must be verified before integration.
Only synthetic test data belongs in this repository. The license decision is pending.
