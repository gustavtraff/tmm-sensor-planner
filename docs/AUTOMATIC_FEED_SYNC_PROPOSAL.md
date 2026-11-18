# Automatic selected-feed updates — proposal (#236 / #26)

**Status: U1–U8 explicitly approved by the owner on 2026-11-18** in the conversation,
against this document at commit `f79a0cc` (PR #237). The approval came from that
decision, not from the merge. P1 direction A was chosen the same day.
The product direction in [the image contract](IMAGE_SHARING_CONTRACT.md#confirmed-product-direction--2026-11-18)
is owner-confirmed. This document proposes how to deliver it. The new private
sync-state file (U6) needs its own exact-format review before its writer.

## Outcome

The planner creates a device and publishes it to the feed the team already uses.
The person deploying it updates the same UUID with actual position, status, bearing
and a placement photo, and publishes. Every other connected client picks the update
up within about a minute. The update becomes the device's current metadata and image
on that client, unless that client has unpublished local changes to the same device.
In that case both versions are kept and the user resolves the conflict.

## What is reused

| Need | Existing component, unchanged unless stated |
|---|---|
| Source selection and TLS GET | `MissionSourceChoiceUi`, `ConfiguredMissionSource`, `MissionSnapshotHttp` ([configured source](CONFIGURED_SNAPSHOT_SOURCE.md)). |
| Metadata read and retention | `MissionSnapshotInput` / snapshot intake into `InboxStore` (inbox format 1). Every variant stays retained. |
| Image read, verify, stage, associate | `MissionImageInput`, `MissionImageFetch`, `ReceivedImageIntake`, `ImageStageStore`, `ImageAssociationRegistry` (registry v1, exact-M binding). |
| Received image as local copy | `LocalImages.prepareReceived` (byte-identical owned copy, readback). |
| Local writes | Local v2 store and transaction (`LocalV2Store`), expected-state binding, `InboxDecisionCoverage`. |
| Image change on a draft | `DraftAttachments.attach`: commits the owned handle through the state-bound save and sets `updatedAt`. |
| Connection state | `ServerTrialConnectionWatch` (`CotStreamListener`). |

Not reused: candidate 30's Downloads export is a trial step, not the publication
route. See open item P1.

## SDK and route verification — 2026-11-18

The supplied ATAK-CIV 5.6.0.24 SDK `main.jar` and javadoc were searched for Data Sync,
mission and feed classes. They contain mission-package classes only
(`com.atakmap.android.missionpackage.*`, `commoncommo.MissionPackage*`). No public
Data Sync, feed-subscription or mission-change callback was found. This matches #25
and ADR 0012. Automatic fetching therefore repeats the existing, already verified
GET-only Mission API reads on a timer. The only new parts are an `android.os.Handler`
timer and the existing connection listener. Neither is a new ATAK API.

The real server is TAK Server 5.6. All Mission API evidence so far comes from the
5.8 lab. Repeated GETs, the content-by-hash GET and server-side last-write behaviour
for one UID must be rechecked on 5.6 before the trial. That check is still open.

## Decisions to approve

### U1 — Fetch lifetime and trigger

Fetching runs only while ATAK is running and the plugin is loaded. It does not
need the TMM pane to be open. No Android service, WorkManager job, alarm or wake
lock is added. If ATAK is stopped, nothing is fetched; the next load catches up.

A fetch starts on plugin load, when the selected profile becomes connected, every
60 seconds while connected, and when the user taps **Uppdatera nu**. Only one
attempt runs at a time. After a failure the interval doubles, up to 5 minutes, and
resets after a success. Existing per-attempt limits stay: 12 s for metadata, 60 s
per image.

### U2 — Following a feed is a stored, explicit choice

The user turns on **Följ feed** once and picks profile, HTTPS API port and feed with
the existing dialogs. The choice is stored (U6) and resumes after a restart without
asking again. This replaces the receive side's session-only grant for this purpose.
Publication consent under #178 stays session-only and is not affected.

One followed feed per client. If the stored profile disappears, changes its connect
string or the provider changes, following pauses with a visible reason. It never
switches to another profile. **Sluta följa** stops fetching and keeps all data.

### U3 — Conflict detection by stored base, not by time

For each followed device, TMM stores a **base**: the remote metadata M and image
digest H that the local device last matched. A fetch compares three states for
each device UUID: remote R, local L and base B. Metadata and image each have their
own base and are compared separately (U4).

| Case | Action |
|---|---|
| R = B | Nothing. Covers duplicate fetches. |
| R = L | Store B := R. Both sides already agree. |
| L = B, R ≠ B | **Apply R automatically.** Store B := R. |
| L ≠ B, R ≠ B, R ≠ L | **Conflict.** Keep L, keep R in the inbox, mark the device **Konflikt**. |
| UUID not present locally, no base | **Add automatically** with R. |
| Base exists, device deleted locally | Do not add it back. If R ≠ B, report a conflict. |
| Local device exists, no base yet | Store B := R if R = L, otherwise conflict. |
| UUID missing from the feed | Nothing. A missing member never deletes a device. |

"L ≠ B" is exactly "this device has local changes that are not yet published". The
list shows that as **Ej publicerad**. Arrival order and `updatedAt` never pick a winner.

Two remote writers updating the same UUID: the feed keeps only the server's latest
copy of that UID, and snapshots cannot see the earlier one. Clients converge on the
feed's version, not on a merge. This is a feed limitation and must be disclosed.

Resolution offers two actions. **Behåll min** keeps L and stores B := R, so it is
not reported again; L stays unpublished until the user publishes. **Använd
inkommande** applies R through the normal transaction and stores B := R.
Field-by-field merging is not proposed.

### U4 — Which image is current

**Amended and approved by the owner on 2026-11-18**, replacing the first U4 text, which bound the image to exactly the feed's current M. That version left a correct photo marked stale after a status-only update.

A device's remote images are the distinct verified `.tmmi` resources in the feed whose registry association binds the device UUID, for any M of that device in this source context.

- None: nothing changes. A missing image never clears a local image.
- Exactly one: it is the remote image H for U3.
- More than one: if exactly one of them differs from the image base, it is H. This covers the window before W4 removes the replaced photo. Otherwise it is a conflict: keep all, change nothing, ask the user.

Metadata and image are compared separately in U3. There is no "image from an earlier version" marker.

### U5 — Safe automatic image replacement

Applying a remote image: verify, stage and associate as today. Then make an owned
copy with `prepareReceived` and read it back. Then commit the draft's new image
handle with `DraftAttachments.attach`, bound to the expected store and state, using
the remote M's `updatedAt` rather than the local clock so that L equals R afterwards. Store B only after that commit is read back. Delete the old owned image
only after a fresh check that nothing refers to it. If any step is uncertain, keep
both copies and retry from the stored IDs on the next fetch.

If a download is interrupted, only staging is affected. The next fetch retries the
same hash. Equal hashes are deduplicated by the existing stage and registry. The
active image is never shown against metadata it is not bound to.

### U6 — One new private file: sync state v1

Store in host no-backup private storage, separate from drafts, inbox, stage and registry:

- the followed source: canonical HTTPS service, mission GUID, display name, API port
  and the selected stream connect string used only for the SDK credential lookup,
  with no credential, certificate or password;
- per device UUID: metadata base M (inbox projection), image base digest or none,
  conflict flag, and the IDs of any adoption that was started but not confirmed.

No change to inbox format 1, local v2, stage v1, registry v1 or the container.
Exact bytes, fixtures and recovery rules come in a separate format proposal, as
decision H requires. A missing or corrupt file stops following and never resets to empty.

### U7 — Offline and reconnect

Without a connection, everything already received stays readable and current.
Nothing is downloaded and nothing is lost. On reconnect, U1 fetches immediately,
then the U3 rules apply to whatever the feed holds now. Intermediate versions that
came and went while offline are not seen.

### U8 — No automatic outbound traffic

Fetching uses GET only. Applying an update locally, resolving a conflict or adding a
device never publishes, uploads, subscribes or sends receipts. Publishing stays a
separate explicit user action on the existing route. A client that took a remote
update has nothing to publish for that device, because L = B.

## Open item outside U1–U8

**P1 — image publication route.** Users need an explicit "publish device and photo"
action that does not require the candidate 30 Downloads and native File Select steps.
#178 currently forbids REST writes, and the SDK offers no callable Data Sync upload.
The owner chose the Mission API upload on 2026-11-18. Its route proposal, with
decisions W1–W5, is [the image publication route proposal](IMAGE_PUBLICATION_ROUTE_PROPOSAL.md).
Automatic receiving does not depend on it.

## Delivery in bounded PRs after approval

1. [Sync-state v1 exact format](FEED_SYNC_STATE_FORMAT.md) and fixtures (gated format, S1–S4).
2. Core: the U3/U4 decision function and the sync-state codec, with domain tests in
   `scripts/test.sh`. No Android code.
3. App: follow-feed UI, U1 scheduler, U5 apply path, conflict marker and resolution.
4. Signed candidate: two-client lab, then the authorized 5.6 target trial, then a
   user guide written around this online workflow.

## Acceptance checks for the integrated candidate

- A changes position, status, bearing and photo and publishes. B shows all of them as
  current within two poll intervals, with no prompt.
- B edits the same device first: B keeps its edit, sees **Konflikt**, and both
  resolutions give the expected result.
- Repeated fetches with no change leave every state file byte-identical.
- An image download interrupted mid-transfer, a process kill during U5, a restart
  and an airplane-mode toggle lose no data and never show an image from another version.
- A traffic capture during automatic fetching shows only GET requests.

## Implementation

The U3/U4 decision is `se.tmm.sensorplanner.core.FeedSyncDecision` (#240), a pure
function over in-memory values with no SDK or file access. The caller passes the
distinct verified digests bound to the device UUID for any M (amended U4). It returns separate
metadata and image decisions, uses `ImportReview.sameMetadata` for equality, and
documents its rule precedence in the class Javadoc. Tests are in `FeedSyncDecisionTest`
(run by `sh scripts/test.sh`). An image deleted-locally case with an UNKNOWN image base
returns NOTHING, because there is no base to compare against.

`se.tmm.sensorplanner.core.FeedSyncPlan` (#242) turns one completed fetch into a per-device
plan, again pure and without I/O. It looks up the base M from the inbox receipt, builds the
image base from the sync record, calls `FeedSyncDecision` per device, and picks the remote
image record for H (first in registry order when several share H). Its `nextSync` holds only
bookkeeping that needs no local write: remote receipt ids and ADOPT_BASE bases. APPLY and ADD
leave bases for the executor (U5). A missing base receipt fails closed. A new device that would
exceed the 256-record limit gets NOTHING and is listed in `overCapacity`. Tests are in
`FeedSyncPlanTest`.

### App: following and scheduler

`se.tmm.sensorplanner.plugin.local.FeedFollowUi` implements U1, U2, U7 and U8 for the
receive side, without local apply (U3–U5). **Följ feed** in the device list reuses
`MissionSourceChoiceUi` for profile and HTTPS API port, reads the feeds, and asks
for the existing explicit missing-history confirmation before it creates
`se.tmm.sensorplanner/feed-sync-v1/sync.bin` (following = 1, the existing
`mission-api-v1|…` context, feed name, selected stream connect string, no records).
**Sluta följa** and **Följ igen** rewrite only the flag. The format is unchanged.

An `android.os.Handler` on the planner lifecycle fetches on load, when the stored
profile becomes connected (`ServerTrialConnectionWatch`, whose `connected(server, up)`
callback the 5.6.0.24 SDK bytecode invokes in both directions), every 60 s and on
**Uppdatera nu**. One attempt runs at a time; a failure doubles the interval up to
5 min, and a success resets it. A missing, changed or disconnected profile, or a
changed provider, pauses with a visible reason and never selects another source.
Unload stops the timer and the attempt; later callbacks are ignored.

Each attempt uses only the existing GETs: feed list, metadata snapshot into the inbox
(as **Hämta till inkorgen**), image resource list, and the selected-feed image read
and intake for each `.tmmi` whose hash is not already referenced by the registry.
That hash is the SHA-256 of the staged entry's container v1 bytes, so a staged image
is never downloaded again. The sync-state records are not written yet. The hook
`FeedFollowUi.Host.onFetchCompleted(receiptIds, records)` is a no-op for the apply slice.

### Start gate after restart

[ADR 0013](decisions/0013-followed-feed-connected-use.md) (owner decision 2026-11-18)
makes an explicit **Följ feed** choice consent to connected use. When the planner opens
while connected, `LocalTrialSession.resumeFollowedFeed` reads `sync.bin` with
`FeedSyncState.read`; following = 1 opens the planner without the ADR 0009 dialog, so
`FeedFollowUi.start` fetches on load. A missing, unreadable or stopped file keeps the
ADR 0009 dialog and is never rewritten. As under trial consent, the lab tools are then off
for the whole session, also after going offline (`canUseLab`).
