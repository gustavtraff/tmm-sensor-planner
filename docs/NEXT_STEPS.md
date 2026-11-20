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
- **Candidate 34** (`versionCode 34`, `0.1.0-feed-sync-diagnostics.2`, #252) was prepared
  from main `df9dc5c` under owner approval: candidate 33 plus the #291 fix, version
  metadata only. Signed: signature, package/version and APK packaging checks passed;
  signer matches candidate 33. Private record `private-data/releases/v34/verification.json`.
  Owner phone checks passed: update install, fetch (#280), toggle test (#283) and the
  #278 menu.
- **Candidate 33** (`versionCode 33`, `0.1.0-feed-sync-diagnostics.1`, #252) is signed
  from main `4967945` (PR #289): #285 stage diagnostics, #286 native TMM review menu and
  #288 rule labels. Signature, package/version and APK packaging checks passed; its
  signer matches candidate 32, and build output contains the #286/#288 markers. The
  private record is `private-data/releases/v33/verification.json`. Installed by the
  owner; the #278 menu passed on the phone (see #280 below). The guides still describe
  candidate 32 and do not cover the new menu.
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

- **#280 resolved** on signed candidate 34 (owner phone, 20 Nov): manual and automatic
  fetches succeed (1 metadata record, 0 images, nothing changed locally); **Mottagna
  metadata** lists the test device. Cause was the detail-child cap on native drawings
  (#291). Two-phone receipt, readback, reconnect and restart remain under #252.
- **#278 (merged, PR #286):** a transient native TMM menu routes accepted local/adopted
  UUIDs to TMM review, replacing irrelevant sensor editing controls. It leaves item
  metadata and exports unchanged. Passed on candidates 33/34: rendering, correct review,
  drawings keep native menus, Map Select and rotation. Restart/unload remain under #252.
- **#283 closed, not reproduced:** on candidates 33 and 34 no publication prompt
  appeared, including when **Show non-feed content** was toggled off and on.
- **#279 (in main after candidate 34):** list and review show session-only metadata
  evidence from the latest read of the followed feed: confirmed with time, absent,
  different, failed check (keeps only the last success time) or not checked. Review
  has **Kontrollera i feeden nu**; photo state is stated as unchecked. Restart resets
  to not checked; durable evidence would need a format proposal.
- **#277 (fixed in main after candidate 34):** device rows and feed controls now share
  one scroll view; the actions stay in the dialog button bar. Other planner dialogs
  already scroll. Phone check in both orientations is pending a later signed candidate.
- **Next: candidate 35** with #277, #279 and #297 (label the marker action **Öppna enhet**).
  The guides must describe the menu, layout and #279 states before it reaches users.
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
