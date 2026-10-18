# TMM Sensor Planner

An ATAK-CIV plugin for planning and maintaining local TMM device drafts:
**Mini, Scout, PIR and Gateway**. The current application is a **local test
prototype**, not an approved field release. The active target is ATAK API 5.6.0;
planning requires Android 8 / API 26 or later.

Local features include manual device IDs, status/owner/position edits, bearing for
PIR/Scout, map symbols, nearest-device/gateway distances, persistent drafts and one
metadata-cleaned image per device. New image copies are reviewed before saving
and limited to a 1,600-pixel longest side; original files remain untouched.

## What has been verified

| Area | Evidence and limits |
|---|---|
| Public CI | Java core/storage, auth/archive/fixture checks and separate native Android image tests/lint; no private ATAK SDK |
| SDK development | Local 5.6.0.24 build/lint and bounded developer-host emulator results |
| Signed standard client | VersionCode 7 passed standard ATAK-CIV 5.6.0.25 emulator load/unload/restart and one synthetic draft's preservation; see [verification](docs/STANDARD_CLIENT_VERIFICATION.md) |
| Physical phone | Owner reports tools and local use; photo handling and delayed map restoration prompted [#49](https://github.com/gustavtraff/tmm-sensor-planner/issues/49) and [#50](https://github.com/gustavtraff/tmm-sensor-planner/issues/50). The merged versionCode 8 photo correction still needs new signing/phone verification |
| Connected use | Explicit session-only [local trial](docs/CONNECTED_LOCAL_TRIAL.md): bounded two-client SDK privacy checks passed; new signed standard-client/phone acceptance remains open |

Use synthetic data. Offline is the default; connected testing requires the separate
isolated procedure and explicit session consent linked above.
**TMM CoT-labb** and the metadata-sharing action are isolated-lab tools. Do not use
them on an operational server/feed. Local installation does not publish the APK.
No public APK release or automatic distribution channel is provided here.

## Development checks

With JDK 17 (core compiled for Java 8):

```sh
sh scripts/test.sh
```

This requires no ATAK SDK. For the independent Android image library, configure
the public Android SDK as in [development setup](docs/DEVELOPMENT.md), then run:

```sh
./gradlew -p images --no-daemon testDebugUnitTest lintDebug
```

Full plugin development also needs the separately obtained version-matched ATAK
SDK. Use [the local SDK check](docs/REPOSITORY_GOVERNANCE.md#separate-sdk-check);
passing public CI alone does not prove plugin compilation or runtime compatibility.

## Project documentation

- [Product and requirements](docs/PRODUCT.md) and [project plan](docs/PROJECT_PLAN.md)
- [Development environment](docs/DEVELOPMENT.md) and [test gates](docs/TESTING.md)
- [Local planner](docs/LOCAL_PLANNER.md), [images](docs/LOCAL_IMAGES.md) and [storage](docs/LOCAL_STORAGE.md)
- [Data contract](docs/DATA_MODEL.md) and [decision records](docs/decisions)
- [Signing procedure](docs/SIGNING_SOURCE.md) and [release gates](docs/RELEASES.md)
- [Open work](https://github.com/gustavtraff/tmm-sensor-planner/issues) and [governance audit](docs/REPOSITORY_GOVERNANCE.md)

## Contributions and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing code. `@gustavtraff` is the
sole authoritative human reviewer and merge/release decision maker. License and
contribution terms remain [undecided](LICENSE); no new license is granted here.
Use [SECURITY.md](SECURITY.md) for information handling and sensitive reporting.
Only synthetic examples belong in this public repository, logs and attachments.
