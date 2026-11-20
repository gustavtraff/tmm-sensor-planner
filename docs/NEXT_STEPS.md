# Current implementation and next steps — 2026-11-20

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
- **Candidate 32** (`versionCode 32`, `0.1.0-feed-sync-trial.2`, #252) is signed from
  owner-approved main `f89f1f4` (PR #276). Signature, package/version and APK packaging
  checks passed; its signer matches candidate 31. Build output contains the #268
  cancellation helper and usability/API-port markers. The private record is
  `private-data/releases/v32/verification.json`. Owner phone testing began on
  20 November: local creation/list access and native Data Sync add/update were
  observed. Integrated native two-client acceptance and owner go/no-go remain open.
- **Candidate 31** remains signed from `7df6347`; its private signing record is
  `private-data/releases/v31/verification.json`.
- The earlier SDK-host lab exercised follow, automatic fetch, upload, association
  and removal on TAK Server 5.8. Its native Data Sync step was a stand-in.
- Signed candidate 31 installed on both isolated standard lab clients. Native
  two-client acceptance remains **unverified**: the run was interrupted by host
  memory pressure before any acceptance point passed.
- The two-emulator plus Docker lab is stopped. Do not restart that combination on
  the 8 GiB development laptop. The private launchers are disabled; use the
  [resource preflight and guard procedure](TESTING.md#resource-bounded-lab-runs).
- Candidate 30 remains the bounded viewing-trial fallback
  ([contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md)). The owner decides its use.
- Candidate 32 includes usability/API-port updates (#270/#272), separating ordinary
  work from **Avancerat / test** and clarifying connection/publication handoff.
  New feed setup suggests ATAK's saved API port; existing following is unchanged.
  The guides describe candidate 32; candidate 31 lacks these changes.

## Immediate follow-up from owner phone testing (#252)

- **First: #280**, the owner sees the test item in native **Map Items**, but TMM
  receives no metadata after manual refresh. PR #285 adds read-only stage/category
  diagnostics; requests, validation and formats remain unchanged. Static retry
  text states an interval, not a countdown. Phone root cause and the next signed
  diagnostic candidate remain pending.
- **#278:** a transient native TMM menu now routes accepted local/adopted UUIDs to
  TMM review, replacing irrelevant sensor editing controls. It leaves item metadata
  and exports unchanged. SDK/resource compilation and UUID-gate tests passed;
  signed-phone rendering, Data Sync selection, restart and unload remain under #252.
- **#283:** toggling native **Show non-feed content** prompts publication of the
  existing test item without an intentional TMM edit. Actual unconfirmed sends are
  not established; reproduce visibility/persist events before choosing a fix.
- **#279:** make per-device feed verification visible in TMM, including an explicit
  check, matching UUID/current metadata, last successful check and unknown/stale
  state on failure. The list currently falls back to **Sparad lokalt** and cannot
  display confirmed publication. Keep metadata and photo confirmation distinct.
- **#277:** restore list/create-action access in landscape. The owner confirmed
  portrait as a temporary workaround. Retest affected dialogs in both orientations.
- Resolve these gaps before repeating the complete two-phone protocol. Data Sync
  log entries and visible map items alone do not pass the integrated acceptance.

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
5. The #268 fix is merged after owner approval and rechecks cancellation before
   each new photo request. Native cancellation/interruption acceptance stays open
   under #252; automated regression checks do not close that gate.

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
remaining work. [The Swedish quick guide](SNABBGUIDE.md) covers first use from the supplied APK;
[the full user guide](ANVANDARGUIDE.md) (#253) is the reference. [Testing](TESTING.md) owns reusable checks and release gates.
[Data model](DATA_MODEL.md) indexes contracts; dated evidence and old status are in
[the archive](archive/README.md). Replace this status on update; do not append history.
