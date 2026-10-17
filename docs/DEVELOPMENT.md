# Development

## Local GitHub token

Fill in `GH_TOKEN=` in `.env.github` without quotation marks. Git ignores the file.
`.env.example` is an empty template that may be versioned. The token file provides local
authentication for the GitHub API; Git push continues to use SSH.

Check authentication without printing the token or changing GitHub data:

```sh
python3 scripts/check-github-auth.py
```

On macOS, the script can use Homebrew's existing CA certificates if Python lacks
its own certificate file; TLS verification remains enabled.

This verifies the token's identity, not all repository/Issue/PR permissions.
Do not put the token in documentation, command-line arguments or chat.

## Run the core

Install JDK 17 and run sh scripts/test.sh. Alternatively, set JAVA_HOME to a
local JDK. The script compiles Java 8-compatible code and runs tests without libraries.
This JDK is only the core's test environment, not a decision about the ATAK toolchain.
The script includes local snapshot tests and launches separate writer/reader JVMs;
it needs a writable temporary directory and uses only synthetic data. No server is needed.
See [LOCAL_STORAGE.md](LOCAL_STORAGE.md) for schema and failure behavior. The APK
now calls it only through an API-26-guarded offline prototype; minSdk remains 21
and older clients show an unavailable message. See [LOCAL_PLANNER.md](LOCAL_PLANNER.md)
for private storage, lifecycle and the remaining Android/phone gates.

## Current ATAK target and SDK

The accepted plugin API target is **ATAK-CIV 5.6.0**; see
[the target decision](decisions/0002-atak-5.6-target.md).
The owner reports ATAK-CIV **5.6.0.12** on the phone. The provided SDK directory
is labeled **ATAK-CIV-5.6.0.24-SDK**. These full builds must be distinguished from
the `5.6.0` plugin API target. Compatibility between them is not established until
installation and runtime tests pass.

The SDK is present locally outside Git. Its inventory includes `atak.apk`,
`ATAK_Plugin_Development_Guide.pdf`, `main.jar`, `atak-gradle-takdev.jar`,
Javadoc and sample plugins. `VERSION.txt` reads `ATAK CIV SDK 7f2055b (1789391081)`.
This is inspected package metadata, not a successful Android build or plugin-load result.

Follow [the SDK intake and verification checklist](SDK_VERIFICATION.md), tracked in
[#2](https://github.com/gustavtraff/tmm-sensor-planner/issues/2). SDK archives,
APKs, JARs and signing material remain outside Git. Review package terms before
copying SDK-derived files into this public repository.

| Component | Inspected value / remaining evidence |
|---|---|
| SDK directory | ATAK-CIV-5.6.0.24-SDK; original archive checksum/provenance verification pending |
| Developer app | Manifest: `5.6.0.24 (7f2055b0)`, versionCode `1789391081`, package `com.atakmap.app.civ`; installation/load passed in isolated API 35 arm64 emulator; see EMULATOR_TEST.md |
| Guide | `ATAK_Plugin_Development_Guide.pdf` is present |
| SDK identifier | `7f2055b (1789391081)` from VERSION.txt |
| Java source/target | 17 in the HelloWorld sample; local build passed with Temurin 17.0.20.1+1 |
| Gradle | 8.14.3-all from the HelloWorld wrapper; distribution SHA-256 pinned below |
| Android Gradle Plugin | 8.13.0 from the HelloWorld root build.gradle |
| Android SDK | HelloWorld compileSdk 36, minSdk 21, targetSdk 34; local platform 36 and build-tools 35.0.0 installed and used |
| SDK plugin build tool | takdevVersion 3.6.0, Git-Version 853d6ffa from its JAR manifest |
| Plugin API | `ATAK_VERSION = '5.6.0'` in the HelloWorld app build.gradle |
| Phone and companion plugins | Owner-reported versions in [RELEASES.md](RELEASES.md); coexistence not tested |
| TAK Server | Version unknown; not a prerequisite for local compilation, required evidence for later server tests |

The local pure-Java core remains Java 8-compatible. Its passing tests do not prove
Android/ATAK compatibility. Use the actual SDK guide and sample to determine the
integration build; do not infer APIs from later public main-branch examples.

The first Android deliverable is the SDK's minimal plugin, built and installed in
the matching developer app. Compatibility with the standard phone app also requires
its own signing/installation/load test. Keep private local settings and phone data
out of Git and debug logs.

## Inspected package fingerprints

SHA-256 values identify the locally inspected files; no publisher-supplied checksum
has been compared and no original archive hash is available yet.

| File | SHA-256 |
|---|---|
| atak.apk | `99acfc58661c9866802b84317617d7eab5aad4652f0c90558da1c0923deca059` |
| main.jar | `5a111497ca5722c5e0dfdca4a7e5852fa68400b6723038eba7fbc657cc5c8d9b` |
| atak-gradle-takdev.jar | `3a9702f9abb2e5c5c94c615201087c5ae74d2609d31b26e79137cb4b1a5eaa2b` |
| ATAK_Plugin_Development_Guide.pdf | `14987c9b79e5f3bc090dadd5138ee897f2f63de27cc9578f2813bf39ca977e0f` |

The host APK manifest declares `plugin-api = com.atakmap.app@5.6.0.CIV`,
compileSdk 36, minSdk 21 and targetSdk 35. These host-app values are separate from
the HelloWorld plugin sample's targetSdk 34.

The SDK's LICENSE-atak-civ.txt restricts SDK copying/publication/distribution.
It has been read; this does not establish permission to distribute SDK-derived
project files or a release APK. No SDK binaries, guide, sample code or signing
material are versioned in this repository. SDK tooling may stage private inputs
in ignored build/cache directories. A project license and the final
plugin distribution path remain separate decisions.

## Build the minimal development plugin

Prerequisites: JDK 17, Android SDK platform 36 and build-tools 35.0.0, and the
inspected external ATAK-CIV 5.6.0.24 SDK. Set the local paths without committing them:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export TMM_ATAK_SDK_DIR=/path/to/ATAK-CIV-5.6.0.24-SDK
./gradlew :app:assembleCivDebug :app:lintCivDebug
python3 scripts/check-plugin-apk.py app/build/outputs/apk/civ/debug/app-civ-debug.apk
```

Alternatively, use ignored local.properties for Android's sdk.dir. TMM_ATAK_SDK_DIR
selects the external SDK plugin tool. No TAK repository account is needed for this
local SDK build. Public Android dependencies come from Google/Maven Central.
Without this environment variable, or with `takrepo.url` / `takrepo.force=true`,
the build selects the separate TPC Maven route described in
[SIGNING_SOURCE.md](SIGNING_SOURCE.md). Do not confuse local SDK success with
service-build verification.

The wrapper pins Gradle 8.14.3-bin with SHA-256
`bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531`.
AGP is pinned to 8.13.0 and build-tools to 35.0.0. The supplied PDF guide still names
JDK 11; the actual supplied sample/build tooling uses Java 17, which was verified locally.

Local evidence on 2026-10-12: assembleCivDebug and lintCivDebug passed, with zero lint
errors. The APK signature verifies as Android Debug. The artifact check confirms the
expected API/discovery registration, no permissions and no bundled JAR/key files.
This does not prove loading in ATAK or production signing. The first development
toolchain is staged temporarily outside Git; configure durable local paths for repeated use.

APK: app/build/outputs/apk/civ/debug/app-civ-debug.apk (ignored by Git).
Developer-host installation, load, toolbar click, unload, reload and host restart passed
on 2026-10-13; see [EMULATOR_TEST.md](EMULATOR_TEST.md). The SDK host skips signature
verification. Issue #3 remains open for the physical-client gate.
Public CI runs the SDK-independent checks; the local SDK build is separate evidence.

The earlier `0.1.0-map-probe` (versionCode 2) adds disposable synthetic feature-layer
controls. Use [LOCAL_MAP_PROBE.md](LOCAL_MAP_PROBE.md) for its separate test steps;
the earlier `0.1.0-dev` loading evidence remains historical.

The current development APK is `0.1.0-cot-probe.1` (versionCode 4). It retains the
local map probe and adds a separate, fixed-fixture **TMM CoT-labb** tool restricted
to the isolated loopback server and one explicitly selected recipient. Its SDK
round-trip, cancellation, failure and lifecycle results are recorded in
[COT_ROUND_TRIP.md](COT_ROUND_TRIP.md). It is not a production publishing adapter.

Issue #6 adds no APK changes. [DATA_SYNC_AND_ATTACHMENTS.md](DATA_SYNC_AND_ATTACHMENTS.md)
records the exact SDK's Mission Package API candidates, Data Sync deferral and a
separate synthetic attachment test through the host's stock Data Packages UI.
Generate its ZIP with `scripts/create-attachment-fixture.py`; keep generated files
and lab evidence in ignored `private-data/`. No Data Sync dependency is added.

Issue #7's offline companion-plugin attempt and standard signing prerequisites
are recorded in [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md). The supplied
HV Reports APK installed but failed to load in this SDK host, including with TMM
unloaded. Do not use that result to infer behavior on the standard phone build.

## Local planner prototype

The main tool now opens local draft forms/review and a private layer, version
`0.1.0-metadata-share-lab.1` / versionCode 7. Only synthetic offline tests on Android 8+
are supported by this prototype. Core sources compile directly into the app source
set without another dependency. `app/lint.xml` scopes NewApi suppression to the
SDK-independent core; all app callers are guarded/annotated for API 26. Keep that
boundary when adding callers. The former one-point map tool was removed; its older
APK evidence remains historical. The CoT lab tool retains its fixed fixtures and
adds an actual received-metadata inspector for the separately guarded
[saved-metadata sharing lab](METADATA_SHARE_LAB.md).

## Separate SDK verification command

With the environment above configured, run `sh scripts/check-sdk.sh` from the
reviewed worktree. It combines the existing Android build/lint and static APK check;
it does not upload outputs. Record the tested commit and bounded results in the PR.
See [repository governance](REPOSITORY_GOVERNANCE.md) for the currently unenforced
SDK status and remaining environment/owner decisions.

## Independent image library

Run `./gradlew -p images --no-daemon testDebugUnitTest lintDebug` with JDK 17 and
the documented Android SDK to verify #13's standalone library. It has its own Gradle
settings and needs no ATAK SDK or emulator. The plugin now uses this library;
see [LOCAL_IMAGES.md](LOCAL_IMAGES.md) for its integrated scope.

## Standard-client source preparation (#7)

[Signing-source preparation](SIGNING_SOURCE.md) documents the committed-source
archive command, private distribution boundary, Maven access restriction and
2.x/3.x tooling discrepancy. The release rules now consume the mapping provided
by the TAK build plugin and use a TMM-specific repackaging namespace. No service
upload, standard-host acceptance or release approval is implied by a local build.
