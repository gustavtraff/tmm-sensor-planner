# ATAK-CIV 5.5.0 SDK verification

Tracking Issue: [#2](https://github.com/gustavtraff/tmm-sensor-planner/issues/2).
Status on 2026-10-12: awaiting the official SDK, matching developer app and guide.
No Android toolchain or integration API has been verified.

## Obtain and identify the package

1. Sign in to the [official ATAK-CIV product page](https://tak.gov/products/atak-civ)
   through an authorized TAK account and locate the developer resources.
2. Obtain the **5.5.0 release family** SDK and its matching developer app and guide.
   Record the full build identifier from the package. Do not replace the target with
   5.5.1, 5.6 or a current main-branch example without a separate compatibility decision.
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
version-matched SDK and developer app. The local Java core's JDK 17 test environment
does not establish the Android build requirements.

## Record the evidence

Update [DEVELOPMENT.md](DEVELOPMENT.md) after inspecting the actual package:

| Evidence | Required value |
|---|---|
| Source | Official product/resource page, without authentication parameters |
| SDK release | Full release/build identifier, with evidence that it belongs to 5.5.0 |
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

Continue in [#3](https://github.com/gustavtraff/tmm-sensor-planner/issues/3) once
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
