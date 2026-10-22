# Releases

No release exists. Local development APKs, an unsigned release-variant APK and an
owner-supplied third-party-service-signed trial APK exist; no release is approved.
0.1.0 is the planned first trial version; the schema has a separate version.
Only tag reviewed commits on main.

Require a documented SDK/toolchain, signing and distribution decisions, a clean
installation, an information review and the relevant A01–A10 tests before release.

Accepted target: ATAK-CIV API 5.6.0, approved on 2026-10-12 in
[ADR 0002](decisions/0002-atak-5.6-target.md). The original API 5.5.0 target was
based on the HV Reports package label; that label is retained below as reported.

| Component | Reported version / target | Verification |
|---|---|---|
| TMM Sensor Planner | No release; planned 0.1.0 | Signed version 15 passed owner-reported offline OnePlus acceptance; connected privacy, full coexistence and release review remain open |
| ATAK API | 5.6.0 active target | User-approved target; integration unverified |
| Additional standard-client installer | ATAK-CIV 5.6.0.25 (238484ee) | Installed in a separate offline API 35 emulator; signed TMM load/unload/restart and local draft preservation passed |
| Installed phone client | ATAK-CIV full build 5.6.0.12 | Previously reported build; signed version 15 offline phone workflow passed per owner, host build not freshly re-read |
| Supplied SDK | Directory labeled full build 5.6.0.24; app, guide and JARs present | Local debug and release-variant build/lint verified; provenance and standard-host runtime compatibility with 5.6.0.12 pending |
| HV Reports/8S | Inspected APK 1.1.9 [5.5.0], API 5.5.0.CIV | Android installation passed; loading failed in SDK host 5.6.0.24 with and without TMM active. Standard 5.6.0.25 load/empty-view check passed with the older-API warning; phone/report workflows pending |
| Data Sync | Inspected APK 3.7.4 (58746181) [5.6.0] | Installation passed; SDK 5.6.0.24 load attempt failed with missing host class. Standard 5.6.0.25 load/empty-feed-view comparison passed; synchronization and TMM Data Sync integration unverified |
| TAK Voice | Inspected split package 2.1.0 (20251122) [5.6.0] | Installation/signature checks passed; SDK 5.6.0.24 load attempt failed with missing host class. APKPure provenance not independently confirmed; standard 5.6.0.25 loading passed; empty Missions view reopened with TMM active after normal panel closure; transient stacked-panel blank view and audio/PTT acceptance remain unresolved |
| TAK Server | Unknown | Not needed for initial local build; required before server-dependent tests |

Issue #7 retains physical-client and full A08 gates. The owner successfully used
the third-party source-build/signing service for reviewed main `2d5efa6`.
[Standard-client verification](STANDARD_CLIENT_VERIFICATION.md) records the signed
artifact hash, actual loading/lifecycle/persistence and bounded companion results.
Signature validation was not bypassed in the standard test host. Independent
publisher provenance, scanner review and operational acceptance remain separate
from APK signature integrity and a normal plugin-manager `Loaded` status.

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

## Signed version 15 offline phone result — 2026-10-22

[PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md) records source/APK hashes, static checks, successful owner-reported upgrade/camera/zoom/MGRS/mils/lifecycle/orientation checks and their limits. No release is approved. [NEXT_STEPS.md](NEXT_STEPS.md) prioritizes standard-binary connected-private verification before production sharing.
