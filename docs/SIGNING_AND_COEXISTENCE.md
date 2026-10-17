# Signing and companion-plugin verification

Tracking: [Issue #7](https://github.com/gustavtraff/tmm-sensor-planner/issues/7).
Date: 2026-10-16. **A08 remains open.** This records partial evidence, not a
standard-phone compatibility result or a release approval.

## Available inputs

| Input | Inspected value / availability |
|---|---|
| Test host | SDK developer ATAK-CIV 5.6.0.24, API 5.6.0.CIV, Android 35 arm64 emulator |
| TMM | Existing Android Debug-signed `0.1.0-cot-probe.1`, versionCode 4; no APK changes for this investigation |
| HV Reports | Provided `ATAK-Plugin-hvreports-1.1.9--5.5.0.apk`; package `com.atakmap.android.hvreports.plugin`, versionCode 1, versionName `1.1.9 () - [5.5.0]` |
| HV Reports API | Manifest declares `com.atakmap.app@5.5.0.CIV`; no native libraries in the APK |
| Phone host | Owner reports standard ATAK-CIV 5.6.0.12; its installer and a dedicated phone test are unavailable |
| Data Sync | Supplied APK 3.7.4 (58746181) - [5.6.0], API 5.6.0.CIV; follow-up below |
| TAK Voice | Supplied APKPure XAPK, base manifest 2.1.0 (20251122) - [5.6.0], API 5.6.0.CIV; follow-up below |

HV Reports APK SHA-256:
`7c9fdc6beb3f9282efd3be59c17eebd55ebfa969e0d5991245bc4ea49912afc9`.
Local `apksigner verify --verbose --print-certs` passed using APK signature schemes
v2 and v3, with one signer. This identifies the inspected file and verifies APK
integrity; no publisher checksum was compared and ATAK trust is a separate check.
No APK, certificate, private key or third-party source is versioned here.

## Offline SDK test

Only client A was started. The lab server/relay/database and client B remained
stopped. Guest IPv4/IPv6 egress was blocked; airplane mode was enabled, Wi-Fi/data
disabled and all ADB reverse tunnels removed. No phone or operational profile was used.

1. Install the provided HV Reports APK in the SDK emulator through ADB.
2. Open Tools → Plugins → Sync Now and confirm the inventory refresh while
   external traffic is blocked. The local product count increased from 8 to 9.
3. With TMM loaded, choose HV Reports → Not loaded → Load. The normal host prompt
   permits loading but warns that the plugin was built for ATAK 5.5.0 and may have
   stability problems on 5.6.0. No APK/API declaration or validation setting was changed.
4. Inspect the actual plugin state and private host diagnostics after the attempt.
5. Unload TMM normally, confirm its Not loaded state, and repeat the HV Reports
   attempt. This checks the failure with TMM inactive in the same host process;
   it is not a separate clean-process or standard-app test.

### Observed failure

Android installation succeeded. Both HV Reports load attempts failed. The host
could not instantiate `com.atakmap.android.hvreports.plugin.Reports`; the nested
`ClassNotFoundException` named `com.atakmap.android.maps.aj$a`. HV Reports remained
**Not loaded**. The SDK diagnostic also explicitly reported that its signature
check was skipped. A normal compatibility prompt is therefore not proof that
plugin construction succeeds or that a standard host trusts the signer.

The same missing-class failure after unloading TMM is evidence of a baseline
problem in this inspected HV Reports / SDK-host combination. It does not establish
the exact cause, compatibility with standard ATAK 5.6.0.12, or a general rule that
all 5.5 plugins fail on 5.6. Do not patch, re-sign or relabel the third-party APK.
Obtain a supported test build or test the intended standard host through its
approved signing path before drawing a product compatibility conclusion.

Because HV Reports did not load, no reporting workflow or successful coexistence
was tested. Data Sync and TAK Voice were not present during that attempt. Raw logs and UI evidence are
kept in ignored `private-data/coexistence-test/`.

After the baseline attempt, TMM was reloaded normally and its CoT lab tool opened
with zero received fixtures. No send action was selected. The task-installed HV
Reports package was then uninstalled from the test emulator and client A stopped.
The other emulator and all three task-owned lab containers remained stopped.
The existing TMM APK's static signature/discovery/packaging check passed; it still
reports the existing Gradle metadata signature warning. No app code, APK or domain
contract changed, so no Android rebuild/lint or domain-test rerun was required.

## Companion-package intake follow-up

Later on 2026-10-16, the owner supplied the Data Sync APK and TAK Voice XAPK.
The reported version families match the inspected APK manifests:

| Package | Inspected identity |
|---|---|
| Data Sync | `com.atakmap.android.datasync.plugin`, versionCode `1769093904`, versionName `3.7.4 (58746181) - [5.6.0]`, minSdk 21 |
| TAK Voice | `com.atakmap.android.gbr.vx.plugin`, versionCode `20251122`, versionName `2.1.0 (20251122) - [5.6.0]`, minSdk 26 |

Fingerprints identify the supplied bytes; no official publisher checksum or
independently trusted signer fingerprint has been compared:

| Input | SHA-256 |
|---|---|
| Data Sync APK | `d785c5819d727796e56fcd0124bfd67dcbc8e53de9cc1ac0545601c1a17d54a0` |
| TAK Voice XAPK | `4e25e102e15feaf79d946bd2c95a58323415218806eddfad13622869bb4535d8` |
| TAK Voice base APK | `6663a438ef1d8ee36ac5c8418140e7424593961ce7445b222620a41256d12832` |

The XAPK was inspected as a ZIP: CRCs passed, names were unique and flat, entries
were size-bounded and symlinks rejected before extracting only APKs into ignored
private storage. It contains a base APK and 19 configuration splits, including
arm64-v8a. All 20 Voice APKs have matching package/version and signer certificate;
all 21 APK signature checks, including Data Sync, passed. Android installed Data
Sync normally and Voice using `adb install-multiple` with the verified splits.
No APKPure installer app or replacement SDK was used.

Signature integrity and split consistency do not establish publisher provenance,
standard-host acceptance or operational suitability. TAK Voice declares microphone,
overlay, Bluetooth, notification and network permissions; Data Sync declares no
uses-permission entries in its APK manifest. Plugins still run through the host,
so manifest permissions alone do not establish privacy. Runtime checks use the
isolated SDK client with external egress blocked, no reverse tunnels and no server.

An installer is not a documented Data Sync integration contract. The direct TMM
integration remains deferred in Issue #25 and [ADR 0005](decisions/0005-defer-data-sync-and-image-sharing.md).

### Companion runtime results

Using the same offline SDK client, refresh the normal plugin inventory (8 → 10
local products), choose each companion's Not loaded action and confirm Load.
First attempt both with TMM loaded. Then unload TMM, verify its Not loaded state,
and repeat each companion attempt. This is a same-process inactive-TMM comparison,
not a clean-process or standard-host baseline.

| Check | Actual result |
|---|---|
| Data Sync with TMM loaded | Installation/discovery passed; extension loading failed, nested `ClassNotFoundException` for `gov.tak.api.plugin.a`; remained Not loaded |
| TAK Voice with TMM loaded | Split installation/discovery passed; same missing-class load failure; remained Not loaded |
| Both after TMM unload | Each reproduced the same missing-class failure with TMM confirmed Not loaded |

The host's compatibility prompt was not an actual successful load. No plugin/API
metadata, host validation setting or third-party binary was modified. There is no
passed Data Sync feed workflow, voice/audio workflow or full A08 result. Do not
infer a general API-version failure, publisher authenticity, or standard-phone
behavior from this SDK-host class-loading result. A supported host/plugin build
combination and the standard signing/phone gates are still needed.

Private evidence for the intake, four attempts, host diagnostics and plugin-state
snapshots is in ignored `private-data/coexistence-packages/`. No operational
data, server connection, audio session or signing-service submission was used.
After the attempts, TMM reloaded and its CoT lab tool opened with zero received
fixtures; no send action was selected. Both task-installed companion packages were
uninstalled from client A and the emulator stopped. Client B and the three lab
containers remained stopped. App source, domain contracts and the TMM APK are unchanged.

## Standard signing route

The [official TAK development process](https://tak.gov/pages/our-process), checked
on 2026-10-16, describes a third-party signing service and distinguishes it from
the official Product Center pipeline. This is a candidate route, not evidence of
account eligibility, a successful submission or acceptance by the intended host.

The current TMM artifact passes `scripts/check-plugin-apk.py`, including Android
signature integrity, discovery, API declaration, permission and packaging checks.
It remains Android Debug-signed. Developer-host loading skips a signature gate;
neither this check nor a self-signed release key proves standard-ATAK acceptance.

Before a standard-phone trial:

- Confirm the appropriate signing route, account access, release ownership and
  distribution rights. Record the applicable service instructions without credentials.
- Select a reviewed commit and build a dedicated trial artifact. Record the
  input/output hashes and actual plugin metadata; do not submit SDKs or secrets.
- Verify the returned signed artifact locally, then install on a separate clean
  standard client. Confirm discovery, load, tools, unload/reload and restart.
- Repeat baseline companion workflows without TMM, then with TMM active, then
  after unloading it. Use synthetic reports only. Verify no duplicate tool entries,
  lost report fields, unexpected sends or changed companion settings.
- Record the exact host, Android and plugin versions, errors and signing result.
  Test network workflows only in the isolated lab with known server configuration.

No signing service was used and nothing was uploaded or published in this investigation.
Do not replace the operational phone's host with the SDK app; they share an Android
package name. Do not disable standard-app validation to obtain a passing result.

## Unsigned release preparation — 2026-10-17

Issue #7 continues from reviewed main commit
`64fd923fa728c45eedf669f809dac9cac7441d53`. No application source, version,
signing configuration or SDK target was changed for this check.

The existing pinned toolchain and supplied 5.6.0.24 SDK completed
`:app:assembleCivRelease :app:lintCivRelease` successfully, including R8.
This is additional release-variant build evidence; earlier debug-host runtime
results do not cover this optimized artifact.

| Local output | Inspected value |
|---|---|
| Variant | `civRelease`, unsigned |
| Package | `se.tmm.sensorplanner.plugin` |
| Version | `0.1.0-metadata-share-lab.1`, versionCode 7 |
| Host API declaration | `com.atakmap.app@5.6.0.CIV` |
| Android min / target / compile SDK | 21 / 34 / 36; planner remains guarded to API 26+ |
| Size | 80,985 bytes |
| SHA-256 | `4449ccca169ad43fa0d96a3c68539ffd9fee761fd81314311926b9e5369c8cc3` |

One-off local assertions passed for ZIP CRCs and unique entry names, compiled DEX,
package identity, the discovery component and plugin XML interface/entry-class
registration. Badging declares no requested permissions or debuggable application.
No JAR or signing-key files covered by the existing APK check's suffix rules were
packaged. These checks do not establish absence of every possible sensitive value.

`apksigner verify --verbose` failed with `Missing META-INF/MANIFEST.MF`, as expected
for this unsigned output. The existing `scripts/check-plugin-apk.py` requires a
signed APK and was not reported as passing for it. This file is neither an
installable test APK nor a reviewed signing submission or release candidate.
It remains in ignored local build output; nothing was uploaded.

Release configuration still needs standard-host validation. The supplied SDK's
`mapping.txt` and generated host mapping were empty, and the generated R8
configuration did not contain an `-applymapping` directive. The SDK HelloWorld
sample has additional release mapping/optimization rules that this application
does not use. This difference is a verification gap, not proof of a runtime bug
or permission to copy sample signing credentials. Do not change rules speculatively:
confirm the official signing/build requirements and test the resulting artifact
against the intended standard host. Release persistence and metadata round trips
must also be checked because debug-runtime evidence does not exercise R8 output.

The available 5.8.0.5 directory is another SDK with a developer app. It does not
replace the missing standard 5.6.0.12 installer or approve an API target change.
The original 5.6 SDK archive/checksum and publisher provenance remain pending in
Issue #2; the standard-client load/unload gate in Issue #3 remains pending too.
The owner can log in to TAK.gov, but service access and its submission contract
have not yet been recorded. No third-party signing submission is authorized by
this preparation alone. Keep Issue #7 and A08 open.

## Source pipeline requirements and private distribution — 2026-10-17

The owner supplied the TAK.gov **Source Archive Requirements** text. It describes
source ZIP submission, remote compilation and signing, not unsigned APK upload.
The owner identified the page as [TAK.gov third-party builds](https://tak.gov/user_builds).
Public web/HTTP checks could not retrieve its requirements (direct HTTP 421);
additional submission-form conditions remain unverified. The unsigned local build above is preparatory evidence
only; it does not demonstrate pipeline compatibility.

Required work under Issue #7:

- [ ] Approve the exact source archive for submission. The branch provides a
  committed-source ZIP helper and offline boundary tests; inventory/content
  review and human upload approval are still required.
- [ ] Retain `assembleCivRelease` and the existing ATAK discovery activity.
- [ ] Verify service SDK resolution end to end. The branch now selects latest
  `atak-gradle-takdev` 2.x / TPC Maven when no local SDK is configured or Maven
  is explicitly requested. The separate local route is preserved. Remote
  resolution/build remains unverified; see the tooling discrepancy below.
- [ ] Verify standard-host mapping at runtime. The branch now applies the TAK
  plugin-provided mapping and a TMM-specific repackaging descriptor; local
  compilation passes, but the developer SDK's empty mapping cannot verify the
  standard host ABI.
- [ ] Record whether the prescribed Maven preflight is accessible. The supplied
  requirements restrict artifacts.tak.gov access to USG Federal/Military
  personnel; ordinary TAK.gov login does not establish repository access.
- [ ] Review additional upload-form conditions and obtain human approval of the
  exact source archive before submitting it to the external service.
- [ ] Inspect the returned artifact and test loading, restart, data preservation
  and companion workflows in the dedicated standard-client environment.

The new standard-host installer was inspected without installation:
`com.atakmap.app.civ`, version `5.6.0.25 (238484ee)`, versionCode `1792077359`.
Its MD5 `371f27d5fd5f58dc300f710ef86c2eba` matches the owner's TAK.gov screenshot;
SHA-256 is `5bada2d865f937bbcb295bcf08760a8bd4765902c91dc5511738d480c0b6b8c6`.
Android signature verification passed (v1/v2/v3), with metadata-entry warnings;
no independently trusted publisher certificate was compared. This is a separate
standard-client test target, not evidence for the reported phone build 5.6.0.12.

The owner's intended audience is a private group of friends, not a public plugin
catalogue. Signing and public publication are separate decisions: preparing or
signing a plugin does not authorize public release or adding it to a shared feed.
The documented third-party route uploads source to an external service even for
private distribution. Each changed APK intended for ordinary ATAK needs a valid
accepted signing/build result; the service is a build-time step, not a connection
required each time the installed plugin runs. We have not verified an alternative
private signing arrangement accepted by the stock client. Developer-host testing
remains a separate route and does not prove ordinary-client acceptance.

### Local preparation checks

The branch adds [the signing-source procedure](SIGNING_SOURCE.md). Both local
SDK variants assembled and linted successfully after the mapping/build-route
changes. The rebuilt debug APK passed `scripts/check-plugin-apk.py` with the
existing unsigned-metadata warnings. `sh scripts/test.sh` passed, as did four
synthetic archive tests (committed input selection/executable wrapper/no overwrite,
tracked private files, symlinks and missing required files). The archive tests
also run in the public core CI job; this does not give CI access to the SDK.

An explicitly forced Maven-only build without a local SDK was attempted offline.
It selected `com.atakmap.gradle:atak-gradle-takdev:2.+` and failed because no version
listing was cached. This is not an authenticated Maven preflight or proof of
service compatibility. The supplied SDK requests 3.x and ships tooling 3.6.0,
whereas the supplied service requirements mandate 2.x. Confirm current service
requirements and compatibility with pinned Gradle/AGP before submitting source.
No service credentials, signing keys or alternative validation bypass were used.
Release-runtime, standard-host and phone verification remain open.

## Obtain remaining test packages

Use [TAK.gov Products](https://tak.gov/products): ATAK-CIV for the standard host
APK, ATAK-Plugins for companion installers, using an authorized account. Seek the
reported versions first; historical availability was not verified. If unavailable,
ask the organization/provider that supplied the phone installation. Do not silently
replace the test matrix with newer versions or obtain arbitrary mirror builds.

Keep original installers outside Git. Record versions and hashes on receipt, and
inspect permissions/signatures before testing. If only newer official builds are
available, record and approve a separate compatibility matrix; that cannot prove
compatibility with the versions already reported on the phone.

## Pipeline investigation follow-up — 2026-10-18

The [source preparation procedure](SIGNING_SOURCE.md#build-tool-decision-and-actual-access-result--2026-10-18)
now separates the SDK-aligned 3.x candidate from the service's still-unconfirmed
2.x wording. Official immutable source examples match API 5.6.0 / takdev 3.x /
AGP 8.13.0 / Gradle 8.14.3; no working 2.x alternative was found or claimed.

Actual online 2.x and 3.x forced-Maven release attempts both failed at metadata parsing.
A separate credential-free HTTP check identified the cause: the metadata request
redirects to TAK's Appgate SDP HTML portal rather than returning Maven XML. This
is not a Gradle version-compatibility result and cannot be fixed by substituting
another major version. The temporary JDK was incomplete and was recovered from
the official Temurin release with published SHA-256 verification before that
attempt; system Java was not changed. No source, credentials or signing material
were uploaded. The original SDK archive provenance, signed artifact, standard
client runtime, companion workflows and physical-phone gates remain open.

For the 3.x candidate, local SDK debug/release assembly and lint passed (171 tasks
executed); rebuilt debug APK signature/discovery/packaging checks passed with
existing metadata warnings. Core tests, four archive tests and whitespace checks
also passed. This verifies the preserved local path, not Maven artifact availability
or service acceptance. See the source procedure for the distinct next gates.
