# Initial backlog

GitHub Issues and milestones were created on 2026-10-12.
The bootstrap PR is linked to [#1 – project setup](https://github.com/gustavtraff/tmm-sensor-planner/issues/1).
Milestones: 0 Feasibility study, 1 MVP, 2 Collaboration and sync, 3 Maintenance.
Proposed labels: api-verification, security, bug, feature, docs, decision.
Active target changed to ATAK-CIV API 5.6.0 with user approval on 2026-10-12;
see [ADR 0002](decisions/0002-atak-5.6-target.md). Phone full build 5.6.0.12 and
supplied SDK directory full build 5.6.0.24 must be tracked separately.
The repository owner assigns responsible people. Use the Issue template when registering these on GitHub.

| ID | Milestone | Goal | Acceptance / dependencies |
|---|---|---|---|
| [E0-01 / #2](https://github.com/gustavtraff/tmm-sensor-planner/issues/2) | 0 | Verify the supplied API 5.6.0 SDK and pin the toolchain | Directory labeled 5.6.0.24; official source/terms, hash, JDK/Gradle/AGP/Android SDK documented. No SDK in Git. |
| [E0-02 / #3](https://github.com/gustavtraff/tmm-sensor-planner/issues/3) | 0 | Minimal plugin | Built APK loads in the API 5.6.0 developer app; record exact app build and verify against the reported phone build 5.6.0.12; installation evidence and tool versions. Requires E0-01. |
| [E0-03 / #4](https://github.com/gustavtraff/tmm-sensor-planner/issues/4) | 0 | Local map privacy (F04/F06/N03/A01) | Create/move/delete on A; B/server see nothing. Two clients, synthetic data, traffic observation. Requires E0-02. |
| [E0-04 / #5](https://github.com/gustavtraff/tmm-sensor-planner/issues/5) | 0 | CoT round trip and manual target (F05/F10/A06/A07) | UUID and all metadata survive; cancellation sends nothing; errors never claim the object was shared. Requires E0-02. |
| [E0-05 / #6](https://github.com/gustavtraff/tmm-sensor-planner/issues/6) | 0 | Data Sync and image transport | Documented API or explicit deferral. Test attachments separately, no unsafe file references. Requires E0-04. |
| [E0-06 / #7](https://github.com/gustavtraff/tmm-sensor-planner/issues/7) | 0 | Coexistence / signing (A08) | Test reported ATAK 5.6.0.12, HV Reports 1.1.9 [5.5.0], Data Sync 3.7.4 [5.6.0], TAK Voice 2.1.0 [5.6.0], phone and standard signing route; coexistence is unverified. Requires E0-02. |
| [E0-07 / #8](https://github.com/gustavtraff/tmm-sensor-planner/issues/8) | 0 | Finalize the product catalog | Names Mini/Scout/PIR/Gateway selected; status transitions, owner/ID standards, bearing reference, distance filters, symbol mapping and image policy still need decisions. |
| [E0-08 / #9](https://github.com/gustavtraff/tmm-sensor-planner/issues/9) | 0 | CI and repository/release decisions | Core CI, separate SDK check, license, distribution decisions, responsible people and main protection established; failing PR blocked. |
| [M1-01 / #10](https://github.com/gustavtraff/tmm-sensor-planner/issues/10) | 1 | Local domain and distances (F01/F03/F07/F08) | Initial core/tests exist; finalize provisional choices in E0-07 and later SDK adapter. |
| [M1-02 / #11](https://github.com/gustavtraff/tmm-sensor-planner/issues/11) | 1 | Persistent local storage (F04/A02/A09) | Restart/offline, errors without silent loss, schema and migration tests. Requires M1-01. |
| [M1-03 / #12](https://github.com/gustavtraff/tmm-sensor-planner/issues/12) | 1 | Map/forms/review (F03/F06/N08) | Field errors, distinguishable markers, clear local-draft state, practical screen test. Requires E0-03/M1-02. |
| [M1-04 / #13](https://github.com/gustavtraff/tmm-sensor-planner/issues/13) | 1 | Local image (F09/A05) | Add/view/replace/remove and missing-file handling; EXIF/size policy from E0-07. |
| [M1-05 / #14](https://github.com/gustavtraff/tmm-sensor-planner/issues/14) | 1 | One confirmed sharing route (F05/F12) | Only after E0-04; target/fields/image status in review, active confirmation, clear errors. |
| [S2-01 / #15](https://github.com/gustavtraff/tmm-sensor-planner/issues/15) | 2 | Import, updates and conflicts (F10/F11) | Same UID does not become a duplicate; concurrent changes preserved for manual review. Requires E0-04. |

All test data is synthetic. Each completed SDK investigation must provide reproducible steps,
versions, results and an ADR; a documentation proposal is not a passed test.

The server version is unknown and is not a prerequisite for the local build.
Identify it before server-dependent or two-client transport experiments.
