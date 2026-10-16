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
| Data Sync / TAK Voice | Reported 3.7.4 [5.6.0] / 2.1.0 [5.6.0]; installers unavailable, not tested |

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
was tested. Data Sync and TAK Voice remain untested. Raw logs and UI evidence are
kept in ignored `private-data/coexistence-test/`.

After the baseline attempt, TMM was reloaded normally and its CoT lab tool opened
with zero received fixtures. No send action was selected. The task-installed HV
Reports package was then uninstalled from the test emulator and client A stopped.
The other emulator and all three task-owned lab containers remained stopped.
The existing TMM APK's static signature/discovery/packaging check passed; it still
reports the existing Gradle metadata signature warning. No app code, APK or domain
contract changed, so no Android rebuild/lint or domain-test rerun was required.

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
