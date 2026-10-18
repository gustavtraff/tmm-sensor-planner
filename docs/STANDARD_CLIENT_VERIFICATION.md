# Signed standard-client verification (#7)

## Latest signed candidate — 2026-10-18

The owner supplied a second successful service result for reviewed main `d09ac31`
(PR #64). The following evidence applies to versionCode **10**,
`0.1.0-image-copy-notice.1`; the versionCode 7 record below is historical.
This candidate contains the image-size, startup-restoration and form-entry fixes,
the connected local trial, and the image-copy resource correction.

| Evidence | Observed value |
|---|---|
| Source commit | `d09ac31e9ff0cec0d0a8bb8b6034388a26c74a4e` |
| Submitted ZIP | `TMM-Sensor-Planner-source-main-d09ac31.zip` |
| ZIP SHA-256 | `fefa2957ac32a9837ff7d08094451ac9eadc621c365d72e1ed4fbf1387d9bb5d` |
| Service result | Returned build log contains `BUILD SUCCESSFUL` |
| APK SHA-256 | `584028794c55bd79fe917f3b942af2216e5b94d3e04be80fef50a76c47abd672` |
| Package / version | `se.tmm.sensorplanner.plugin`, 10 / `0.1.0-image-copy-notice.1` |
| Signing certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| Static verification | APK checker and v1/v2/v3 signature verification passed; existing unsigned metadata-entry warnings remain |

The basename still says `unsigned.apk`, but the APK is signed. Normal Android
upgrade from the previous service-signed APK succeeded; ATAK reported `Loaded`
without changing signature validation. The same isolated standard 5.6.0.25 / API 35
arm64 guest described below was used, with no operational connections.

### Upgrade and image checks

- The previous synthetic one-record snapshot was byte-identical after upgrade.
- Selected a generated JPEG through Android DocumentsUI: **3072 × 4096**,
  **199,165 bytes**. These match the reported phone photo’s pixel dimensions,
  not its content, byte size or content-provider behavior.
- The actual preview displayed the complete Swedish notice including
  `högst 1600 pixlar`; the host resource path no longer appeared (#62).
- Cancel left snapshot bytes unchanged. Confirmed Save produced a **1200 × 1600**
  PNG (**11,256 bytes**), with no EXIF/text chunks. Original JPEG bytes were
  unchanged. The saved image reopened.
- Cold host-process restart preserved the post-save snapshot byte for byte;
  the same synthetic device and image reopened. Normal Unload removed both
  TMM tools; reload restored exactly one of each.

### Connected-session UI checks (egress blocked)

Android reported an actual connected simulated Wi-Fi network with airplane mode off.
Both guest IPv4/IPv6 OUTPUT chains retained an unconditional first-rule DROP;
there were no ADB reverse tunnels or server profiles. This tested session policy,
not server transport or absence of draft transmission on a working connection.

- Cancel left the planner closed; explicit opt-in opened the `testläge` list.
- Metadata sharing was absent from saved review and the CoT lab did not open.
- Returning offline in that same session did not restore either lab action.
- Normal Unload/reload and cold process restart each required fresh consent.
- The post-save snapshot remained byte-identical after all session checks. The
  captured crash buffer had no fatal exception. Airplane mode, disabled Wi-Fi/data,
  unconditional IPv4/IPv6 egress blocks and no reverse tunnels were rechecked
  before stopping the task guest; no server or companion workflow ran.

The exact private phone photo/provider and phone build 5.6.0.12 remain untested.
Standard-binary two-client privacy with positive controls, a server and decrypted
observation remains the next network gate. No operational-network test is approved.

### Returned scanner report triage

The Fortify text summary says `Rendering 0 results`. This is a reported scanner
result, not a complete independent security review. Dependency-Check reports
15 scanned dependencies (13 unique), one vulnerable dependency and one finding:
CVE-2025-54057, attributed to generated `feature-images.jar` as Apache SkyWalking.

The reported generated JAR SHA-256 is
`8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85`.
It exactly matches a standard **22-byte empty ZIP**, independently generated
using Python `zipfile`. The scanner inferred several unrelated Apache products
from the same empty resource archive. The service SBOM contains 169 components,
including AndroidX ExifInterface 1.4.2, and no SkyWalking component; no SkyWalking
name occurs in the returned class mapping or APK DEX bytes. The source image
module declares ExifInterface as its only runtime dependency.

This is a false component identification of the empty generated resource JAR,
not evidence that SkyWalking is bundled. Apache’s [10.3.0 release notes](https://github.com/apache/skywalking/releases/tag/v10.3.0)
describe the actual CVE fix as URL validation for SkyWalking widgets. No dependency
change or broad scanner suppression was added. This bounded triage does not
certify the APK, its other dependencies or the complete returned report set as safe.
Raw service files, scanner reports and guest evidence remain outside Git.

## Earlier versionCode 7 result and scope — 2026-10-18

The owner submitted the reviewed `2.+` source archive to TAK.gov and supplied
its successful result. The returned TMM APK installs and actually loads in
**standard ATAK-CIV 5.6.0.25 (238484ee)**. Normal unload/reload, cold restart and
one synthetic local draft's persistence passed in a separate offline emulator.
This replaces the earlier "no signed artifact / no standard-host runtime test"
status. It does not complete A08, prove the reported phone build 5.6.0.12, or
approve a release or operational sharing.

## Artifact chain

| Evidence | Observed value |
|---|---|
| Reviewed source | Main `2d5efa6a56e42964325f4c0a4db36eee54223d5e` (PR #47) |
| Submitted ZIP supplied to owner | `TMM-Sensor-Planner-source-main-2d5efa6.zip` |
| ZIP SHA-256 | `60ded9a989cd6589c4b7463448724710765d15cba6e4c08f515e111de0263cd5` |
| Service result | Owner reported success; returned build log says `BUILD SUCCESSFUL in 2m 39s` |
| Returned APK basename | `app-civ-release-unsigned.apk`; despite its name, the actual APK **is signed** |
| APK SHA-256 | `c59b2940d8fdfe7261f8488b58f85a8897e70928517b82df0a8603af9bbd92f8` |
| Package / version | `se.tmm.sensorplanner.plugin`, versionCode 7, `0.1.0-metadata-share-lab.1` |
| Host API | `com.atakmap.app@5.6.0.CIV` |
| Android min / target / compile | 21 / 34 / 36; planner guarded to API 26+ |
| Signature / static contract | `apksigner verify --verbose` passed, v1/v2/v3, one signer; `scripts/check-plugin-apk.py` passed |

Signature checks emitted metadata-entry warnings. They prove APK signature
integrity, not independently pinned publisher identity or a clean vulnerability
scan. The signed artifact's acceptance was also tested in the normal standard
host below; no signature-validation setting was changed or bypassed.

The source declares service takdev `2.+`; the successful service result resolves
the practical source-build trial that previously blocked this path. The exact
resolved remote takdev version was not identified. A separate authorized
credentialed Maven preflight has **not** passed: direct metadata requests reached
Appgate HTML, as recorded in [the source procedure](SIGNING_SOURCE.md). Do not
report the remote service result as a successful local Maven preflight.

The owner-supplied result also contains an AAB, mapping, SBOM and security reports.
They remain private and have not received a complete security review. No claim
that those scans are clean is made. Source-to-output reproducibility is not proven.
The first failed 3.x trial's root cause remains unknown without its job log.

## Isolated test environment

- A separate AOSP API 35 arm64 emulator, 1920 × 1080, density 160; no phone touched.
- Standard host package `com.atakmap.app.civ`, versionCode `1792077359`.
  SHA-256 `5bada2d865f937bbcb295bcf08760a8bd4765902c91dc5511738d480c0b6b8c6`;
  its MD5 matched the owner's official-download screenshot. This is a separate
  target from SDK developer host 5.6.0.24 and reported phone 5.6.0.12.
- Airplane mode; Wi-Fi/data disabled; first IPv4 and IPv6 OUTPUT firewall rule
  unconditionally drops traffic. No reverse tunnels, server profiles, accounts,
  certificates or feed configuration. No send/share action was selected.
- The standard host's license text is byte-identical to the previously approved
  emulator agreement (SHA-256 `6e7a4a186e1e275c31c02d2c6076b93dc6713c16957e290ee9cb52565f2a34eb`).
  Existing owner authorization covered acceptance and manage-all-files in the
  isolated empty test guest.
- Foreground location, camera, microphone, notifications and manage-all-files
  enabled only in the test guest; no host camera/audio backend. Phone/media/nearby,
  background location and unrestricted battery operation were not granted.
  ATAK's built-in permission-request override was used for the reduced-permission
  lab. It is distinct from plugin signature validation, which remained unchanged.
- The established emulator EGL fallback was used. This is not field-GPU evidence.

At completion, all four plugins showed `Loaded`, the synthetic snapshot remained
byte-identical, firewall first-rule drops and absence of reverse tunnels were
rechecked. The captured runtime log contained no `FATAL EXCEPTION`; that does not
exclude all caught errors. The three task-installed companions were removed and
the isolated emulator was stopped. Earlier lab emulators/server containers and
the physical phone were not started or changed by this verification.

Private raw UI snapshots, logs and synthetic storage evidence remain outside Git.
No account-bearing result-folder path or raw scanner report is published here.

## Actual TMM checks

| Check | Result |
|---|---|
| Normal install and plugin-manager load | Passed: actual `Loaded`, beyond the compatibility confirmation |
| Tool registration | Exactly one `TMM Sensor Planner` and one `TMM CoT-labb` entry |
| Planner open / local save | Passed: one synthetic Mini / Planned draft, ASCII ID and owner, position 0/0; no image or sharing |
| Release storage | Valid snapshot checksum, schema 1, stable UUID, enum tags `MINI` / `PLANNED` |
| Normal unload | `Not loaded`; both TMM tool entries removed |
| Normal reload | `Loaded`; tool entries restored once |
| Cold host-process restart | Automatic TMM loading, one saved draft reopened with the same review fields |
| Persistence comparison | Snapshot byte-identical before/after restart, including UUID and enum values |

This is a bounded one-record release smoke test, not every-field migration,
image-provider, import/export, performance or phone acceptance. Earlier SDK lab
transport/privacy results do not automatically apply to this standard binary.

## Companion baseline and TMM comparison

Supplied packages: Data Sync **3.7.4 (58746181) [5.6.0]**, HV Reports
**1.1.9 [5.5.0]**, TAK Voice **2.1.0 (20251122) [5.6.0]**. Package hashes and
prior static checks are in [the coexistence investigation](SIGNING_AND_COEXISTENCE.md).
Voice is the supplied APKPure XAPK's base plus 19 config APKs; no APKPure installer,
patching, relabeling or re-signing was used. Its publisher provenance remains
independently unconfirmed.

A cold host process with TMM unloaded established the baseline. All three
companions then loaded normally. HV Reports displayed its older-5.5 stability
warning before loading; actual `Loaded` and its tool were subsequently observed.
Voice's optional overlay was left disabled and accessibility-service activation
was cancelled; background/volume-button PTT was not tested.

| Offline observation | Baseline without TMM | With TMM loaded |
|---|---|---|
| Data Sync | `Loaded`; Feeds view / `No feeds available.`; server setup cancelled | Same empty feed view |
| HV Reports | `Loaded`; `No reports in your current feed`, report filters visible | Same empty report view |
| TAK Voice | `Loaded`; Missions / `No missions to display` | Same empty missions view |
| Tool inventory | One entry per companion, no TMM entries | One per companion and one per TMM tool |

After normal TMM unload, all three companions remained `Loaded`; their tools
remained and the Voice missions view reopened. One earlier stacked-panel reopen
with TMM active produced a blank Voice panel. Closing the prior panel normally,
loading TMM again and reopening Voice produced the expected empty Missions view.
The transient navigation/rendering anomaly is recorded rather than treated as a
proved TMM conflict or a passed robustness test. Its root cause is unverified.

The SDK-host missing-class failures are historical and were **not** reproduced
by these standard-host load/open checks. This does not establish full workflow
compatibility: no report delivery/field preservation, feed synchronization, voice
session/audio, background PTT or server round trip occurred. A08 remains open.

## Remaining gates

- Physical-client acceptance, especially the reported ATAK-CIV 5.6.0.12 build;
  do not replace the operational host with the developer app for testing.
- Baseline/TMM-active/after-unload synthetic report, feed and voice workflows
  in an authorized isolated network environment, plus companion setting checks.
- Standard-binary two-client privacy, sharing/receive and persistence/upgrade,
  local-image/provider and field usability acceptance as applicable.
- Review returned security/dependency reports, distribution terms and release
  ownership before release; no public feed or catalogue publication is authorized.
- Original SDK archive provenance/checksum and distribution evidence in #2.
  This service result does not supply the missing original SDK archive.

Issue #7 tracks the outstanding signing/coexistence acceptance. #3's original
minimal developer-host loading criterion already has prior evidence; this adds a
standard-host result without silently closing the broader gates. A responsible
human approves merges, any release and operational data sharing.
