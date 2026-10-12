# TMM Sensor Planner

Initial project setup for an ATAK-CIV plugin for local planning of TMM devices.
**Compatibility target: ATAK-CIV 5.6.0; not yet verified.**

The repository currently contains an SDK-independent Java core for local drafts,
validation, and horizontal map distances. There is no installable Android app,
map integration, persistent storage, or sharing yet.

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

The next deliverable is a minimal plugin built with the official 5.6.0 SDK.
APIs, local map privacy, and the sharing path must be verified before integration.
Only synthetic test data belongs in this repository. The license decision is pending.
