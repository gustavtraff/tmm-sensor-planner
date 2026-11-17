# ATAK-CIV 5.6.0 SDK verification

Tracking Issue: [#2](https://github.com/gustavtraff/tmm-sensor-planner/issues/2).
Status on 2026-10-12: the owner has provided a local SDK directory labeled
`ATAK-CIV-5.6.0.24-SDK`, containing the host APK, developer guide, API JAR, tooling
and samples. Package metadata has been inspected and the local debug build/lint now pass.
Update on 2026-10-13: developer-host loading passed in an isolated emulator; see
[EMULATOR_TEST.md](EMULATOR_TEST.md). Standard-app signing, phone compatibility and
integration behavior remained unverified at that stage. On 2026-10-18, the owner-supplied
service-signed artifact passed bounded standard-host 5.6.0.25 checks; see
[the result](STANDARD_CLIENT_VERIFICATION.md). Phone 5.6.0.12 and the original SDK
archive hash remain pending.

## Obtain and identify the package

1. Sign in to the [official ATAK-CIV product page](https://tak.gov/products/atak-civ)
   through an authorized TAK account and locate the developer resources.
2. Obtain the **5.6.0 release family** SDK and its matching developer app and guide.
   Record the full build identifier from the package. Do not replace the target with
   5.5, another release family or a current main-branch example without a separate compatibility decision.
3. Review the package's license and distribution terms before copying any content
   into this public repository. An SDK download is not permission to redistribute it.
4. Keep archives, extracted SDK files, developer APKs and signing material outside Git.
   Do not record credentials, authenticated download URLs or local account paths in Issues.
5. Compute the archive's SHA-256 locally, for example:

   ```sh
   shasum -a 256 "/absolute/path/to/the/downloaded-sdk.zip"
   ```

   If an official checksum is supplied, compare against it. A locally computed hash
   identifies the inspected file; it does not by itself establish authenticity.
6. Inspect archive contents before extraction. Reject unexpected absolute paths or
   parent-directory traversal. Read the guide and build files before running scripts.

The public source repository is useful reference material, but does not replace the
version-matched SDK and developer app. The provided SDK sample specifies Java 17;
its runtime environment and build results still need verification.

### Historical source inspection before the target change

The [official source repository](https://github.com/TAK-Product-Center/atak-civ)
was checked on 2026-10-12. Its published Git tags are in the 5.5.1 release family;
no 5.5.0 tag was found. The current HelloWorld example specifies `ATAK_VERSION = '5.6.0'`.

The root `pluginsdk.zip` was downloaded and its SHA-256 checked against the Git LFS
pointer: `f2111985b8ac507d8b6c7db8841b4994d9886130b0743a3338bc1aa929600d8a`.
Its six entries contain Espresso test helpers and `Build_Environment_Changes.pdf`.
It does not contain a host developer APK, the ATAK API JAR or a complete 5.5.0 SDK.
Those public archive contents were not copied into this project or executed.

## Record the evidence

Update [DEVELOPMENT.md](DEVELOPMENT.md) after inspecting the actual package:

| Evidence | Required value |
|---|---|
| Source | Official product/resource page, without authentication parameters |
| SDK release | Full release/build identifier, with evidence that it belongs to the accepted 5.6.0 target |
| SDK archive | Archive name and SHA-256 |
| Distribution terms | Reviewed license and permitted use of SDK-derived files |
| Developer app | Full version/build identifier and SHA-256 |
| Guide | Title and version/date |
| JDK | Exact vendor and version required by the SDK |
| Gradle | Wrapper version and distribution checksum |
| Android Gradle Plugin | Exact version from the SDK example |
| Android SDK | Compile/target/min SDK and build-tools requirements |
| Plugin build tools | Version/hash of SDK-supplied plugin tooling |
| Test target | Emulator/device and Android version; no private device identifiers |

Mark missing evidence as pending. Do not infer compatibility from filenames alone.

## Minimal plugin gate

The local package is now available. Complete the evidence table in DEVELOPMENT.md
and continue in [#3](https://github.com/gustavtraff/tmm-sensor-planner/issues/3) once
the package and toolchain are identified. Build the SDK's minimal example with its
documented commands. Record the build result, APK identity and installation/load
result in the matching developer app. Only copy SDK-derived files into Git when
their distribution terms permit it. Keep signing credentials and APKs out of Git.

Plugin loading in a developer app does not prove installation in the standard ATAK
distribution. Verify that route separately in [#7](https://github.com/gustavtraff/tmm-sensor-planner/issues/7).

## Integration probes

Use an isolated test environment and synthetic devices only. Record exact SDK/API
references, reproduction steps, results and remaining uncertainty for each probe.

| Probe | Required evidence | Issue |
|---|---|---|
| Local map representation | Create, move and delete a draft on client A; observe client B and server traffic to establish that no event is sent | [#4](https://github.com/gustavtraff/tmm-sensor-planner/issues/4) |
| Metadata round trip | Stable UUID and all TMM fields survive export/import and repeated transmission without duplicates | [#5](https://github.com/gustavtraff/tmm-sensor-planner/issues/5) |
| Explicit sharing | Target and fields are reviewed before confirmation; cancellation sends nothing; failure never reports shared status | [#5](https://github.com/gustavtraff/tmm-sensor-planner/issues/5) |
| Image transport | Verify attachment delivery separately from metadata, including missing files, size limits and removal; never send local file paths as attachment delivery | [#6](https://github.com/gustavtraff/tmm-sensor-planner/issues/6) |
| Data Sync | Identify a documented integration API and test actual feed behavior, or explicitly defer support | [#6](https://github.com/gustavtraff/tmm-sensor-planner/issues/6) |
| Coexistence | Verify plugin loading and baseline HV Reports/8S and Data Sync workflows with recorded versions | [#7](https://github.com/gustavtraff/tmm-sensor-planner/issues/7) |

Keep private traffic captures and device logs outside the public repository.
Publish only sanitized results. Capture an ADR for the verified local representation,
sharing route, CoT schema and image strategy before dependent integration work starts.
