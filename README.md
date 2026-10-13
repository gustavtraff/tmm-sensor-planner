# TMM Sensor Planner

Initial project setup for an ATAK-CIV plugin for local planning of TMM devices.
**Compatibility target: ATAK-CIV 5.6.0; not yet verified.**

The repository currently contains an SDK-independent Java core for local drafts,
validation, and horizontal map distances, plus a locally built minimal development
APK. Loading passed in the isolated SDK developer emulator; standard-app signing
and phone compatibility remain unverified. Production map integration, persistent storage and
sharing are not implemented. An [offline synthetic map probe](docs/LOCAL_MAP_PROBE.md)
is available for the next SDK investigation.

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
