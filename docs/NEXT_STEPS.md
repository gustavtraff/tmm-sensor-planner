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

- **Automatic receiving is built (#236, #242).** On main: **Följ feed**, GET-only
  fetching every 60 s and on reconnect, automatic application of conflict-free
  metadata and photos, base-based conflict detection, crash recovery for photo
  replacement, and resuming after restart ([ADR 0013](decisions/0013-followed-feed-connected-use.md)).
  Decisions: U1–U8 with U4 amended twice, S1–S4 and W1–W5, all approved 2026-11-18. See
  [the proposal](AUTOMATIC_FEED_SYNC_PROPOSAL.md) and [sync-state v1](FEED_SYNC_STATE_FORMAT.md).
- **Photo upload is verified in the lab only (#241, closed).** Upload, association and
  removal work on TAK Server 5.8 with a token-less request on an open feed whose default
  role is Subscriber. That matches the owner's feed.
- **Candidate 30** (signed, not runtime-tested) is the bounded viewing trial
  ([contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md)), not the finished workflow.
- Nothing above has run on ATAK, a phone or TAK Server 5.6.

## Plan and owners

| Step | Work | Who | Depends on |
|---|---|---|---|
| A1 | #242: **Konflikt** and **Ej publicerad** markers, **Behåll min** / **Använd inkommande** | Sonnet agent, Opus review | — |
| A2 | #243: photo upload, association and W4 removal as a non-UI service with fake-transport tests | Opus agent | — |
| A3 | #251: proposal for the metadata route of ordinary **Publicera** (P2) | Opus agent, then owner decision | — |
| B1 | #243: **Publicera** UI joining metadata (A3 decision) and photo (A2) | Opus agent | A1, A2, A3 |
| B2 | #252: version bump and SDK-host two-client lab acceptance on 5.8 | Opus agent | B1 |
| B3 | #252: signing handoff and signature check | Owner signs, Opus verifies | B2 |
| B4 | #252: step-by-step owner protocol for the 5.6 two-phone trial | Sonnet agent, Opus review | B1 |
| B5 | #253: Swedish user guide for the online workflow | Sonnet agent | B1 |
| C1 | #252: two-phone trial on the real 5.6 server, then go/no-go | Owner | B3, B4 |

Gated merges need explicit owner approval: A2 and B1 (outbound transport) and B3
(signing). A1, A3, B2, B4 and B5 may be merged once checks are green.

Related tracking: #26 parent for images, #25 Data Sync route, #171 selected-feed Fetch,
#178 configured publication. G1–G5 for [#189 geometry](SHARED_GEOMETRY_UPDATE.md)
remain approved and independent.

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
