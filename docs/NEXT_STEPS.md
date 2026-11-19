# Current implementation and next steps — 2026-11-19

## Target workflow (owner-confirmed 2026-11-18)

Users share one existing Data Sync feed. The planner creates a device; the deployer
updates the same UUID with position, status, bearing and a photo, then publishes.
Connected clients fetch automatically, apply conflict-free updates and keep both
versions of conflicts. Publishing is explicit. Offline clients retain their data
and catch up after reconnecting. See [the image contract](IMAGE_SHARING_CONTRACT.md).

## Current candidate

- Automatic receiving (#236/#242), **Publicera** with photo (#243/#264), and geometry
  updates (#189/#259) are merged. The approved decisions are U1–U8, S1–S4, W1–W5,
  M1–M7 and G1–G5; their contracts remain authoritative.
- **Candidate 31** (`versionCode 31`, `0.1.0-feed-sync-trial.1`, #252) is signed from
  main `7df6347`. Its hash, signature and APK packaging checks passed; its signer
  matches candidates 29/30. The private record is
  `private-data/releases/v31/verification.json`.
- The earlier SDK-host lab exercised follow, automatic fetch, upload, association
  and removal on TAK Server 5.8. Its native Data Sync step was a stand-in.
- Signed candidate 31 installed on both isolated standard lab clients. Native
  two-client acceptance remains **unverified**: the run was interrupted by host
  memory pressure before any acceptance point passed. Installation is not loading
  or workflow acceptance; the signing record predates this installation attempt.
- The two-emulator plus Docker lab is stopped. Do not restart that combination on
  the 8 GiB development laptop. The private launchers are disabled; use the
  [resource preflight and guard procedure](TESTING.md#resource-bounded-lab-runs).
- Candidate 30 remains the bounded viewing-trial fallback
  ([contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md)). The owner decides its use.
- **Usability updates (#270/#272)** separate ordinary work from **Avancerat / test**,
  clarify connection settings and the native publication handoff, and update the
  guides. New feed setup suggests ATAK's saved API port; existing following stays
  unchanged. These source changes are newer than signed candidate 31; a new
  owner-approved signed package and its device checks are still needed.

## Remaining acceptance (#252)

1. Prepare a host memory budget and supervised, staged lab startup. Validate one
   client before considering two; keep all server access inside the isolated
   loopback lab. No lab startup while the owner needs this laptop for normal work.
2. Run the native two-client checks in [Testing](TESTING.md#candidate-31-pending-acceptance):
   native import and update prompts, second-client deployment update/photo on the
   same UUID, native **Nej**, conflicts, restart, reconnect and interrupted work.
   Use actual Data Sync; a scripted send cannot pass these checks.
3. Owner runs [the exercise protocol](EXERCISE_TRIAL_PROTOCOL.md) on TAK Server 5.6
   and records go/no-go. Lab Server 5.8 evidence does not close this step. Operational
   access is outside agent authorization.
4. Check the Swedish guide's native steps against the signed candidate before
   handing it to users. Physical/coexistence and release gates remain in
   [Testing](TESTING.md#manual-acceptance-and-release-gates).
5. Resolve #268 before closing cancellation acceptance: static review found that
   cancelling during photo upload can still be followed by a new association
   request. Its outbound-behavior proposal awaits owner approval before code.

## Maintenance and acceptance

- #221: owner selected a required self-hosted SDK build. No runner is registered;
  the proposed host is a Windows Dell with 32 GB RAM, using an isolated Linux VM.
  Draft PR #235 prepares the workflow; runner provisioning and failing-build
  enforcement must still be verified.
- #224 and #225 are complete (merged PRs #234 and #232). #231 consolidates remaining
  local physical/accessibility/rotation/active-dialog unload acceptance; #12/#52/#66/#67 are closed as implemented.
- #226: inventory merged/closed PR branches and stale worktrees. Owner confirmation
  of the exact list is required before deletion. #227 waits until after the exercise.
- #2/#220: SDK provenance/distribution terms and repository visibility remain open;
  all rights are reserved. Releases and operational sharing require owner decisions.

## Where to look

[Open issues](https://github.com/gustavtraff/tmm-sensor-planner/issues) own bounded
remaining work. [The Swedish user guide](ANVANDARGUIDE.md) (#253) covers the online workflow. [Testing](TESTING.md) owns reusable checks and release gates.
[Data model](DATA_MODEL.md) indexes contracts; dated evidence and old status are in
[the archive](archive/README.md). Replace this status on update; do not append history.
