# ADR 0013: A followed feed counts as consent to connected use

Date: 2026-11-18. Status: accepted. Decision authority: the owner, in the conversation
on 2026-11-18, recorded on #242. Amends [ADR 0009](0009-connected-local-trial.md).

## Decision

An explicit **Följ feed** choice counts as consent to connected use for that followed
feed. While following is on, the planner opens after a restart without the
session-only connected-trial dialog from ADR 0009, so U2 in
[the automatic feed sync proposal](../AUTOMATIC_FEED_SYNC_PROPOSAL.md) resumes as
approved. The ordinary trial warning stays for users who follow no feed.

"Following is on" means `feed-sync-v1/sync.bin` is read successfully with
`FeedSyncState.read` and its flag is 1. An absent, unreadable or corrupt file, or
flag 0, gives exactly the ADR 0009 behaviour; this check never creates, rewrites or
resets the file. It runs when the planner opens while connected and lasts until
plugin Unload or process restart, like ADR 0009 consent. **Sluta följa** during a
session therefore takes effect at the next load.

## What still holds

- No operational data, server details, accounts, keys or certificates in Git.
- No automatic publication, upload, subscription or receipt (U8). Fetching stays GET-only.
- The CoT lab, saved-metadata sharing and the other lab and diagnostic tools keep
  their existing gate (`canUseLab`): offline only, and never after connected-trial consent.
- Publication consent under #178 stays session-only and is not affected.

## Consequences

Implemented in `LocalTrialSession.resumeFollowedFeed` and `SensorPlannerPlugin.openPlanner`.
Unit tests cover a following, a stopped, a missing and a corrupt file. Actual ATAK
restart, connectivity and host-traffic behaviour still need a signed candidate on two
clients and the authorized 5.6 target.
