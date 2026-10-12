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
| Developer app | Manifest: `5.6.0.24 (7f2055b0)`, versionCode `1789391081`, package `com.atakmap.app.civ`; installation/load not tested |
| Guide | `ATAK_Plugin_Development_Guide.pdf` is present |
| SDK identifier | `7f2055b (1789391081)` from VERSION.txt |
| Java source/target | 17 in the HelloWorld sample; exact runtime/toolchain verification pending |
| Gradle | 8.14.3-all from the HelloWorld wrapper; distribution checksum still to pin |
| Android Gradle Plugin | 8.13.0 from the HelloWorld root build.gradle |
| Android SDK | HelloWorld compileSdk 36, minSdk 21, targetSdk 34; installation/build-tools pinning pending |
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
material have been copied into this repository. A project license and the final
plugin distribution path remain separate decisions.
