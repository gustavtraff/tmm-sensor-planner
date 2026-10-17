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
`takrepo.force=true`, Gradle resolves `com.atakmap.gradle:atak-gradle-takdev:3.+`
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

## Build-tool decision and actual access result — 2026-10-18

Use **3.x as the SDK-aligned candidate** for this project's Maven build. The
inspected 5.6.0.24 SDK requests 3.x and contains tooling 3.6.0. The official
HelloWorld example at immutable source commit
[`9f6893dd657feacc35ec5de03dad721c2e44170e`](https://github.com/TAK-Product-Center/atak-civ/tree/9f6893dd657feacc35ec5de03dad721c2e44170e/plugin-examples/helloworld)
also declares API 5.6.0, takdev 3.x, AGP 8.13.0 and Gradle 8.14.3, matching our
pinned Android build. The same example's 3.x choice was observed in public tags
5.5.1.8, 5.5.1.9 and 5.5.1.10; tag labels are not SDK-host version evidence.
PR #44 provisionally selected 2.x from the supplied website text. This follow-up
changes that candidate explicitly rather than downgrading our verified toolchain
or claiming the service text has been corrected. No compatible 2.x build was
established. Latest 3.x Maven availability and service acceptance remain unproven.

A credential-free HTTP request for the exact takdev Maven metadata URL followed
a redirect to `https://access.tak.gov/client`, returned HTTP 200 / `text/html`,
and had the title **Appgate SDP**. It was not Maven metadata. Actual online,
forced Maven-only `assembleCivRelease` attempts with empty credentials and JDK 17
failed while parsing that response as XML for both PR #44's 2.x candidate and this
SDK-aligned 3.x candidate. Unlike the earlier
offline cache miss, this establishes a concrete repository-access failure. It does
not establish that 2.x or 3.x is available, incompatible, or accepted by the signing
service. No credentials were sent, no Appgate login was attempted and no access
control was bypassed. TAK.gov website login does not establish Appgate access.

The service's supplied text still explicitly says 2.x. Treat it as an unresolved
**service acceptance** question, separate from the SDK-aligned build-tool choice.
An authorized repository preflight or a human-approved source-pipeline trial is
needed to settle it. A local developer without repository access cannot claim to
have completed the USG Maven preflight. The public third-party service is described
separately from that restricted access; do not infer that a failed direct Maven
request means the owner cannot submit a source build through their existing TAK.gov
account. No pipeline request or account eligibility test has occurred here.

Release rules use the TAK plugin's `atak.proguard.mapping` property and
`atakplugin.tmm.sensorplanner` namespace. Shrinking/optimization are disabled in
line with the inspected SDK sample's conservative release behavior. Discovery
entry names remain kept. The local developer SDK has an empty mapping; this cannot
verify release-host ABI mapping. Standard-client runtime tests remain mandatory.

## Local verification of the 3.x candidate

On 2026-10-18, local SDK 5.6.0.24 debug/release assembly and lint passed (171
executed tasks). The debug APK passed `scripts/check-plugin-apk.py` with the
existing metadata-entry signature warnings. `sh scripts/test.sh`, all four
source-archive boundary tests and patch-whitespace checks passed. These use the
SDK's local tooling 3.6.0, not a resolved Maven 3.x artifact. No service, standard
host or physical phone runtime result is claimed. The only application-build
change is the remote plugin major-version candidate; domain/UI/data contracts
and local SDK selection are unchanged.

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
