# Current implementation and next steps — 2026-11-18

## Target workflow (owner-confirmed 2026-11-18)

Users share one existing Data Sync feed. The planner creates a device. The person who
deploys it updates the same UUID with actual position, status, bearing and a placement
photo, then publishes. Every connected client fetches automatically and shows that
update as current when it does not conflict with unpublished local work. Conflicts
keep both versions and ask the user. Publishing is always explicit. Offline clients
keep their last data and catch up after reconnecting. The full statement is at the
top of [the image contract](IMAGE_SHARING_CONTRACT.md).

## Where the implementation is

- **Candidate 30** (`0.1.0-shared-image-view-trial.1`, signed) has been received.
  Its version, signature and static packaging checks pass, and its signer matches
  v29. It has not been installed or tested at runtime. It provides manual frozen-file
  export through native Data Sync, explicit selected-feed download and a separate
  read-only received-image viewer ([trial contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md),
  [UI](SHARED_IMAGE_TRIAL_UI.md)). It does not fetch automatically and does not make
  a received image current. It is a bounded trial, not the finished workflow.
- Metadata round trip, configured TLS Fetch ([source](CONFIGURED_SNAPSHOT_SOURCE.md)),
  inbox, local v2 transactions, image stage, association registry and byte-identical
  received copies are all in place. Automatic sync reuses them.
- The real server is TAK Server 5.6. All Mission API evidence so far is from the 5.8
  lab.

## Next steps

1. **#236:** U1–U8 in [the automatic feed sync proposal](AUTOMATIC_FEED_SYNC_PROPOSAL.md)
   were approved by the owner on 2026-11-18. Owner decision next: S1–S4 in
   [the sync-state v1 format](FEED_SYNC_STATE_FORMAT.md), needed before its writer.
2. **P1:** the owner chose direct upload (direction A). Owner decision next: W1–W5 in
   [the image publication route proposal](IMAGE_PUBLICATION_ROUTE_PROPOSAL.md). Its
   first step is a lab check of the server upload, write permission and 5.6 behaviour.
3. Optionally run the candidate 30 two-client lab and the authorized 5.6 trial (#200,
   #222) to check the GET routes on 5.6. This is useful evidence for #236, not the
   product.
4. After approval: sync-state v1 exact-format proposal, then a core decision function
   with tests, then the app scheduler, apply and conflict UI, then a signed candidate on
   two clients and the authorized 5.6 target. Write the user guide for that online
   workflow, not for the trial steps.

Related tracking: #26 parent for images, #25 Data Sync route, #171 selected-feed Fetch,
#178 configured publication (partial signed v29 controls; full matrix open).
G1–G5 for [#189 geometry](SHARED_GEOMETRY_UPDATE.md) are approved; its lab measurements
can continue in parallel.

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
remaining work. [Testing](TESTING.md) owns reusable checks and release gates.
[Data model](DATA_MODEL.md) indexes contracts; dated evidence and old status are in
[the archive](archive/README.md). Replace this status on update; do not append history.
