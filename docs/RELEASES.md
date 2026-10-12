# Releases

No release or APK exists. 0.1.0 is the planned first trial version; the schema has
a separate version. Only tag reviewed commits on main.

Require a documented SDK/toolchain, signing and distribution decisions, a clean
installation, an information review and the relevant A01–A10 tests before release.

Accepted target: ATAK-CIV API 5.6.0, approved on 2026-10-12 in
[ADR 0002](decisions/0002-atak-5.6-target.md). The original API 5.5.0 target was
based on the HV Reports package label; that label is retained below as reported.

| Component | Reported version / target | Verification |
|---|---|---|
| TMM Sensor Planner | No release; planned 0.1.0 | No APK or compatibility evidence |
| ATAK API | 5.6.0 active target | User-approved target; integration unverified |
| Installed phone client | ATAK-CIV full build 5.6.0.12 | User-reported; plugin loading not tested |
| Supplied SDK | Directory labeled full build 5.6.0.24; app, guide and JARs present | Provenance, toolchain and compatibility with 5.6.0.12 pending |
| HV Reports/8S | 1.1.9 [5.5.0] | User-reported; coexistence on 5.6.0 unverified |
| Data Sync | 3.7.4 [5.6.0] | User-reported; coexistence and integration unverified |
| TAK Voice | 2.1.0 [5.6.0] | User-reported; coexistence unverified |
| TAK Server | Unknown | Not needed for initial local build; required before server-dependent tests |

API 5.6.0 and full builds 5.6.0.12/5.6.0.24 are distinct version facts. Matching
API versions do not replace installation, round-trip or coexistence tests.

Keep a changelog with schema changes, limitations and installation steps.
Rollback must not delete local drafts; the backup/migration policy must be tested
before releasing a version with persistent storage. Do not publish SDKs/certificates.
