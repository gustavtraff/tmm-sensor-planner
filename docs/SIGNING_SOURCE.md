# Prepare source for the third-party build/signing service

Tracking: [#7](https://github.com/gustavtraff/tmm-sensor-planner/issues/7), with SDK
provenance in #2 and historical developer-host loading in #3. The owner supplied
TAK.gov's **Source Archive Requirements** on 2026-10-17, from
[the third-party build page](https://tak.gov/user_builds). The owner supplied the
page URL afterward. Our public web/HTTP checks could not retrieve its requirements
(the direct HTTP request returned 421), so the pasted text is the requirements
evidence; current form conditions remain unverified. Nothing has been submitted.

The intended audience is a private group. This is not public catalogue/feed
publication. The selected service receives source code, builds it and signs its
output. Human review of the exact archive and upload approval remain required.
The [official process](https://tak.gov/pages/our-process) distinguishes this
third-party service from the official TAK Product Center pipeline.

## Two build routes

The default local development procedure is unchanged when `TMM_ATAK_SDK_DIR`
identifies the inspected 5.6.0.24 SDK. Local debug output is Android Debug-signed;
release output remains unsigned. No sample key is configured for release.

With no local SDK environment variable, or with `takrepo.url` or
`takrepo.force=true`, Gradle resolves `com.atakmap.gradle:atak-gradle-takdev:2.+`
and SDK artifacts through the TPC Maven route. The default repository is
`https://artifacts.tak.gov/artifactory/maven`. A forced Maven build takes precedence
over a local SDK environment variable; it must not silently fall back to that SDK.
The `images` library and plain core do not reference ATAK SDK APIs, so neither needs
the TAK build plugin. The app owns all ATAK dependencies.

For an authorized Maven account, set `takrepo.user` and `takrepo.password` in a
protected user-level Gradle properties file, outside the repository and archive.
Do not put credentials on command lines, in chat or in build logs. Then the
requirements' preflight is equivalent to:

```sh
./gradlew --no-daemon -Ptakrepo.force=true \
  -Ptakrepo.url=https://artifacts.tak.gov/artifactory/maven assembleCivRelease
```

The supplied instructions reserve artifacts.tak.gov access to USG Federal/Military
personnel. Ordinary TAK.gov website access is not proof of repository access.
No such credentials have been requested or used here.

**Tooling discrepancy:** the web requirements specify latest 2.x; the supplied
5.6.0.24 SDK sample requests 3.x and the local JAR identifies 3.6.0. Remote mode
follows the supplied 2.x requirement, but compatibility with pinned AGP 8.13.0 /
Gradle 8.14.3 has not been established. Do not silently change major versions or
claim this source is accepted by the service. Obtain current service guidance or
an actual authorized preflight result before submission.

Release rules use the TAK plugin's `atak.proguard.mapping` property and
`atakplugin.tmm.sensorplanner` namespace. Shrinking/optimization are disabled in
line with the inspected SDK sample's conservative release behavior. Discovery
entry names remain kept. The local developer SDK has an empty mapping; this cannot
verify release-host ABI mapping. Standard-client runtime tests remain mandatory.

## Prepare the exact reviewed source

Commit the intended changes first. The helper reads **HEAD**, not working-tree
files, and never uploads anything:

```sh
python3 scripts/test-signing-source.py
mkdir -p private-data/signing
python3 scripts/create-signing-source.py private-data/signing/TMM-Sensor-Planner-source.zip
```

The output must not already exist. The helper prints the commit and archive
SHA-256. It selects committed build scripts, wrapper, app resources/source, core
source/tests, image-library source/tests and README. It excludes the repository's
Git history, private directories, credentials/configuration and generated output
by selecting explicit build inputs rather than zipping the working directory.
All entries share the single `TMM-Sensor-Planner/` root; `gradlew` retains its
executable mode. Checks reject symlinks, unsafe paths, known private/generated
paths, archives, SDK JARs and signing files; only the Gradle wrapper JAR is allowed.

These checks are bounded packaging checks, not a general content/secret scanner.
Review the complete file inventory and source contents, dependency/distribution
rights, root name and service form before approving the exact hash for upload.
Do not add SDK binaries or credentials to make a service build pass. The current
prototype retains lab-only sharing guards; signing does not remove them or make
it ready for operational use.

## After an approved submission

Keep the source hash, service build result and returned artifact hashes privately.
Verify returned package/version/API, signature integrity, discovery, permissions
and packaging with the documented Android tools and `scripts/check-plugin-apk.py`.
Passing that static checker is not host trust or runtime acceptance. Test clean
standard-client discovery/load, single tool entry, local data/images, unload/reload,
restart, upgrade and companion baseline/TMM-active workflows with synthetic data.
Record exact versions, including the separate standard host 5.6.0.25 and reported
phone 5.6.0.12. Repeat relevant privacy and sharing gates before a trial release.
