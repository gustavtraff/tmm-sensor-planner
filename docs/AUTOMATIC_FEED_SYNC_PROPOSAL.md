# Automatic selected-feed updates — proposal (#236 / #26)

**Status: proposal. U1–U8 below need explicit owner approval before any code.**
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

A device's current remote image is the verified `.tmmi` resource whose registry
association binds the device UUID **and exactly the feed's current M** for that
device. Metadata and image are compared separately in U3 because a photo is only
published together with a metadata change: `DraftAttachments` sets `updatedAt` on
every attach and detach, so M changes whenever the photo changes.

- No resource binds the current M: keep the current local image and show **Bild från
  tidigare version**. This covers metadata that arrives before its image.
- Exactly one binds it: it is the remote image H for U3.
- More than one binds it with different bytes: conflict. Keep all, change nothing,
  ask the user.

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
The choices are a Mission API upload, which needs its own outbound proposal and
approval, or keeping the native File Select step behind a shorter guided flow. The
owner decides. Automatic receiving does not depend on it.

## Delivery in bounded PRs after approval

1. Sync-state v1 exact-format proposal and fixtures (gated format).
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
