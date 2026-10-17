# Releases

No release exists. Local development APKs and an unsigned release-variant APK have
been built; none is a release candidate. 0.1.0 is the planned first trial version; the schema has
a separate version. Only tag reviewed commits on main.

Require a documented SDK/toolchain, signing and distribution decisions, a clean
installation, an information review and the relevant A01–A10 tests before release.

Accepted target: ATAK-CIV API 5.6.0, approved on 2026-10-12 in
[ADR 0002](decisions/0002-atak-5.6-target.md). The original API 5.5.0 target was
based on the HV Reports package label; that label is retained below as reported.

| Component | Reported version / target | Verification |
|---|---|---|
| TMM Sensor Planner | No release; planned 0.1.0 | Loading probe passed in SDK developer emulator; no standard-phone or production-signing evidence |
| ATAK API | 5.6.0 active target | User-approved target; integration unverified |
| Additional standard-client installer | ATAK-CIV 5.6.0.25 (238484ee) | Downloaded; screenshot MD5 matched and Android signature integrity passed; not installed or runtime-tested |
| Installed phone client | ATAK-CIV full build 5.6.0.12 | User-reported; plugin loading not tested |
| Supplied SDK | Directory labeled full build 5.6.0.24; app, guide and JARs present | Local debug and release-variant build/lint verified; provenance and standard-host runtime compatibility with 5.6.0.12 pending |
| HV Reports/8S | Inspected APK 1.1.9 [5.5.0], API 5.5.0.CIV | Android installation passed; loading failed in SDK host 5.6.0.24 with and without TMM active. Standard-phone behavior remains unverified; see [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md) |
| Data Sync | Inspected APK 3.7.4 (58746181) [5.6.0] | Installation passed; SDK 5.6.0.24 load attempt failed with missing host class. Standard-host coexistence and TMM integration unverified |
| TAK Voice | Inspected split package 2.1.0 (20251122) [5.6.0] | Installation/signature checks passed; SDK 5.6.0.24 load attempt failed with missing host class. APKPure provenance not independently confirmed; standard-host coexistence unverified |
| TAK Server | Unknown | Not needed for initial local build; required before server-dependent tests |

Issue #7 remains open. The official third-party signing service is a candidate
route only; no signing submission or standard-host acceptance test has occurred.
The unsigned release build and remaining R8/standard-host verification gaps are
recorded in [the preparation evidence](SIGNING_AND_COEXISTENCE.md#unsigned-release-preparation--2026-10-17).
The SDK host skips its signature check. Valid Android APK signatures and a plugin
manager compatibility prompt do not establish successful loading or production trust.
See [the partial signing/coexistence result](SIGNING_AND_COEXISTENCE.md).

API 5.6.0 and full builds 5.6.0.12/5.6.0.24 are distinct version facts. Matching
API versions do not replace installation, round-trip or coexistence tests.

Keep a changelog with schema changes, limitations and installation steps.
Rollback must not delete local drafts; the backup/migration policy must be tested
before releasing a version with persistent storage. Do not publish SDKs/certificates.

Repository protection, separate SDK-check procedure and unresolved licensing,
distribution and named-role decisions are recorded in
[repository governance](REPOSITORY_GOVERNANCE.md). Issue #9 remains open for those
gates; this CI work does not create a release or approve a distribution channel.

## Intended private trial distribution — 2026-10-17

The owner intends initial use by a private group, not public plugin publication.
Issue #7 tracks source-pipeline preparation and standard-client acceptance.
Source submission, signing, private APK delivery and public publication are
separate steps; no upload, feed publication or release is approved by this record.
The available 5.6.0.25 standard installer adds a test target and does not replace
phone verification on the reported 5.6.0.12 installation.
