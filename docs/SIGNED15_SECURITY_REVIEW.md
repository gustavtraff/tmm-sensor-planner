# Signed version 15 service-report triage — 2026-10-23

Tracking: #79, with release/compatibility #7 and governance #55.
This is a bounded artifact/report review, not a penetration test, comprehensive
source audit, new vulnerability scan or release approval. The parallel #4/#7
standard-client/server run is independent; no shared runtime was started/changed.

## Artifact and evidence identity

The reviewed output is service-signed versionCode **15**, versionName
**0.1.0-device-details.1**, package `se.tmm.sensorplanner.plugin`, built from
reviewed main `b643ee633cee2b9d16c4333fa7f14b7a0931622b`. Subsequent documentation
and offline-tool commits on main do not produce a different signed APK.

| Evidence | SHA-256 |
|---|---|
| Signed APK | 221017dd9f2eeb8d5c46ab2fd1db235f2644059f480fe7874865dc4e8fdd7645 |
| Source ZIP | f6bdc67c62a8ce36f389902f0fea192a0ece177fe7c7dd913ada1124b8aeb7d0 |
| Returned build.log | dc2139cd340090d75a8dca51469ee06eaf48f19d4bc85f7de8482b2990017ec4 |
| Dependency-Check HTML | 7cc88b83487ab0c0b4ab1728b62a737481f12ecc1a40af8d7168b866dc19b496 |
| Fortify text summary | ad889864b51c33c4d92b8f2aba1f68d4df32e57fda3808ef6b8d39f9ac846aa2 |
| Returned SBOM JSON | f352473c766503423bc1e5977af3141149c8e84641e25ee2ab1bf2480d8091d2 |

Raw result packages/reports, account-bearing folder names, FPR source archives,
mapping and signer details stay private. The prior static signature/discovery/API/
permission/packaging checks and owner-reported physical offline acceptance are in
[PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md). They do not replace this triage or the
remaining network/compatibility and release gates.

## Report results and coverage

| Report/check | Observed result | Limit |
|---|---|---|
| Fortify scan text | 53 source files; zero rendered results | A scanner result, not absence of vulnerabilities |
| FPR audit.fvdl | Zero Vulnerability elements | Independent consistency check of the returned container, not a second scanner |
| Translation log versus submitted source | All 37 tracked production Java source paths from app/core/images at b643ee6 appear; the other 16 logged paths are core/image tests | Paths logged as processed do not prove every code path, configuration, generated/native component or third-party binary was analyzed |
| Dependency-Check 12.2.2 | Report dated 2026-10-22 14:15:37 GMT; 15 artifacts, 13 unique, one vulnerable artifact and one CVE | Not a complete independent review of every dependency or all 169 SBOM components |
| SBOM | 169 components including AndroidX ExifInterface 1.4.2 and build/test components such as Robolectric | Component presence in this build inventory is not proof it is packaged in the runtime APK |

No full dependency-advisory sweep, license inventory approval, source-to-binary
reproducibility claim or supply-chain provenance verification is made here.
Fortify rule/version configuration and the service's full scanning policy were not
independently reproduced. No scanner settings or existing checks were weakened.

## Reported SkyWalking finding

Dependency-Check attributes **CVE-2025-54057** to generated `feature-images.jar`,
identifying it as Apache SkyWalking 9.6.0. The vendor describes the actual issue as
SkyWalking web UI script injection, fixed by widget URL validation in 10.3.0:
[Apache release notes](https://github.com/apache/skywalking/releases/tag/v10.3.0)
and [Apache advisory](https://lists.apache.org/thread/sl2x2tx8y007x0mo746yddx2lvnv9tcr).

The returned evidence supports classifying this specific report entry as a false
component identification:

1. The report assigns the generated JAR SHA-256
   `8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85`.
   A fresh standard 22-byte empty ZIP generated with Python zipfile has exactly
   that digest. The report also assigns unrelated Apache product identities to
   this same generated resource archive. Its original intermediate JAR is not
   returned; this identity check is against the report's declared digest.
2. No SkyWalking component appears in the service's 169-component SBOM. The
   source image library declares ExifInterface as its only runtime dependency.
3. Neither SkyWalking package spelling occurs in the returned APK's single DEX,
   and no SkyWalking name occurs in the R8 class mapping. These negative checks
   corroborate the identity assessment; string absence alone would not prove it.

This repeats the earlier versionCode 10 finding with current version 15 report
and artifact evidence; do not silently reuse the older report as proof for a new
artifact. No SkyWalking dependency is identified to upgrade in this app, and no
broad CVE/component suppression or unrelated dependency update is justified.
Reopen this assessment if the reported file digest, component evidence or runtime
dependency graph changes. A future targeted suppression, if needed by the service,
requires explicit review and exact artifact/evidence matching; none was added.

## SBOM versus installed code

The service SBOM includes build and test tooling. For example, Robolectric 4.16.1
appears there but neither its dotted nor slash package name appears in the checked
APK DEX. ExifInterface appears in the SBOM and its slash package spelling occurs
in DEX. This is a bounded sanity check, not a complete runtime bill of materials,
reachability analysis or proof that all obsolete components are absent.
Do not describe every SBOM component as installed on the phone, or describe the
APK as vulnerability-free because this single CVE is a false identification.

## Actions and remaining gates

- Record this returned-report review under #7; no app patch or new signing ZIP is
  needed for this finding. Keep underlying service behavior under observation.
- Continue #4/#7 connected-private and companion workflow verification. This
  review does not inspect or use operational-server data and cannot replace it.
- Retain release/security review for real runtime dependencies, build-tool updates,
  source/SDK/distribution provenance, license/contribution terms, dependency rights,
  named release ownership and artifact/history information review. Automated SBOM
  inventory and signed output are not those approvals.
- Keep production Data Sync/sharing/import and image transport acceptance separate
  (#25/#14/#15/#26). No security-report result authorizes operational sharing.

Local checks performed for this triage: evidence hashes, report count extraction,
FPR XML element count, source-path comparison against the exact signed source,
empty ZIP digest reproduction, SBOM component checks and APK DEX/mapping name checks.
The vendor release/advisory sources were rechecked. No build/test suite was rerun
for this documentation-only change, and CI results are not awaited or claimed.
