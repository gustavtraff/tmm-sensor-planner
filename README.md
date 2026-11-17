# TMM Sensor Planner

An ATAK-CIV API 5.6.0 plugin for planning Mini, Scout, PIR and Gateway devices.
Local planning requires Android 8 / API 26 or later. This is a trial prototype.

Start with [current status, next step and open gates](docs/NEXT_STEPS.md).
The [product requirements](docs/PRODUCT.md) describe the intended workflow.

## Development

With JDK 17:

```sh
sh scripts/test.sh
```

For Android setup, image-library checks and the separate private SDK build, follow
[development](docs/DEVELOPMENT.md) and [testing](docs/TESTING.md).

## Documentation

- [Project plan](docs/PROJECT_PLAN.md), [architecture](docs/ARCHITECTURE.md) and [contracts](docs/DATA_MODEL.md)
- [Signing](docs/SIGNING_SOURCE.md), [release procedure](docs/RELEASES.md) and [governance](docs/REPOSITORY_GOVERNANCE.md)
- [Open work](https://github.com/gustavtraff/tmm-sensor-planner/issues) and [archived evidence](docs/archive/README.md)

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md) before
contributing. [All rights reserved](LICENSE); contribution terms remain undecided.
Use only synthetic data in public artifacts. Releases and operational data sharing
require the owner's decision; merge rules are in [AGENTS.md](AGENTS.md).
