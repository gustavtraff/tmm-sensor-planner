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
| [M1-01 / #10](https://github.com/gustavtraff/tmm-sensor-planner/issues/10) | 1 | Local domain and distances (F01/F03/F07/F08) | Accepted core rules, immutable edits and candidate filtering tested. Production SDK adapter remains in M1-03; no storage or APK-feature claim. |
| [M1-02 / #11](https://github.com/gustavtraff/tmm-sensor-planner/issues/11) | 1 | Persistent local storage (F04/A02/A09) | Restart/offline, errors without silent loss, schema and migration tests. Requires M1-01. |
| [M1-03 / #12](https://github.com/gustavtraff/tmm-sensor-planner/issues/12) | 1 | Map/forms/review (F03/F06/N08) | Field errors, distinguishable markers, clear local-draft state, practical screen test. Requires E0-03/M1-02. |
| [M1-04 / #13](https://github.com/gustavtraff/tmm-sensor-planner/issues/13) | 1 | Local image (F09/A05) | Add/view/replace/remove and missing-file handling; EXIF/size policy from E0-07. |
| [M1-05 / #14](https://github.com/gustavtraff/tmm-sensor-planner/issues/14) | 1 | One confirmed sharing route (F05/F12) | Only after E0-04; target/fields/image status in review, active confirmation, clear errors. |
| [S2-01 / #15](https://github.com/gustavtraff/tmm-sensor-planner/issues/15) | 2 | Import, updates and conflicts (F10/F11) | Same UID does not become a duplicate; concurrent changes preserved for manual review. Requires E0-04. |

All test data is synthetic. Each completed SDK investigation must provide reproducible steps,
versions, results and an ADR; a documentation proposal is not a passed test.

The operational server version is unknown. The isolated lab uses TAK Server
5.8-RELEASE-92; its bounded results are in TWO_CLIENT_LAB.md and COT_ROUND_TRIP.md.
Record and test the operational configuration before compatibility claims.

Issue #6 investigation: [DATA_SYNC_AND_ATTACHMENTS.md](DATA_SYNC_AND_ATTACHMENTS.md)
records a passed standalone synthetic Mission Package transfer in the isolated SDK
lab and explicit deferral of Data Sync integration and production image sharing.
This does not close local image Issue #13 or the product-policy decisions in #8.

Issue #7 remains open: [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md)
records failed HV Reports, Data Sync and TAK Voice SDK-host attempts. The standard
host installer, signing and physical-client gates remain open. A plugin-manager
compatibility prompt is not a successful load.

Issue #8's [decision record](decisions/0006-local-mvp-product-rules.md) records the
owner's status/distance and image choices and the manual-ID clarification (`PIR 134`).
Human merge of PR #28 adopted the remaining MVP defaults. #10–#13 implement the
rules; the decision record alone does not establish code behavior or freeze storage.

Issue #10 implements validated immutable domain edits and PLANNED/DEPLOYED/ACTIVE
distance candidates, with manual-ID, clock/revision, transition and neighbor tests.
Storage remains #11, forms/map and duplicate-warning UI #12, image files #13.
The later production SDK adapter is part of those UI/map gates, not a domain-test result.

Issue #11 adds versioned core snapshot storage with a fixed v1 fixture, separate-process
reopen and fail-closed tests; see [LOCAL_STORAGE.md](LOCAL_STORAGE.md). #11 stays open
for actual Android offline restart/upgrade evidence. #12 must integrate private storage,
resolve the API 21–25 NIO/Instant boundary, backup policy, field errors and map lifecycle.
No image-file or phone acceptance result is implied.

Issue #12 now implements the guarded API-26 offline planner adapter: validated forms,
review/duplicate warning, host-private no-backup storage and a private feature layer
with original type symbols and map selection. Bounded SDK-host results and remaining
physical/connected/rotation gates are in [LOCAL_PLANNER.md](LOCAL_PLANNER.md). #11/#12
remain open for actual-client acceptance; image files remain #13. No new sharing or
Data Sync capability is implied.

Issue #35 integrates #13's merged image library into the saved-device review:
local-only document picker, normalized preview, attach/view/replace/remove actions,
and metadata-first cleanup after detach or draft deletion. Image and storage
contracts remain snapshot v1. See [LOCAL_IMAGES.md](LOCAL_IMAGES.md) for bounded
SDK-host evidence and remaining physical-provider/standard-client gates.

Issue #4's actual saved-planner follow-up is documented in [TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md#actual-planner-follow-up--2026-10-17).
It extends the disposable-layer investigation to persistence, bearing geometry,
local images and lifecycle operations in the isolated connected CoT lab. Human
review and actual-client/other-transport privacy gates remain; this adds no sharing
implementation and does not remove the offline restriction.
