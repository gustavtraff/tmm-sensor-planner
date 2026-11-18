# Publishing a device photo directly to the followed feed — proposal (P1 / #236)

**Status: W1–W5 explicitly approved by the owner on 2026-11-18** in the conversation,
against this document at commit `2caf8bb` (PR #238). The approval came from that
decision, not from the merge. W5's lab check comes before any upload code is activated.**
The owner chose direction A for open item P1 of
[the automatic feed sync proposal](AUTOMATIC_FEED_SYNC_PROPOSAL.md) on 2026-11-18:
TMM itself sends the photo to the feed when the user taps **Publicera**. This
document proposes how. It is outbound transport, so it is a gated class.

## User outcome

The deployer edits the device as today and attaches a photo exactly as offline. They
tap **Publicera**, check one preview of the metadata, photo and feed, and confirm.
The metadata goes out on the existing native route. TMM uploads the photo itself.
No Downloads file, no File Select. Other clients pick both up through U1–U5.

## Is it possible?

**Probably yes. The client side is verified from the SDK; the server side is not yet.**

| Part | What is known | Status |
|---|---|---|
| HTTPS client | ATAK-CIV 5.6.0.24 `com.atakmap.comms.http.TakHttpClient` exposes `execute(HttpRequestBase, boolean)`. Existing reads pass an `HttpGet` to it. `HttpPut` and `HttpPost` are subclasses of the same `org.apache.http` base class, so the same strict TLS factory, client certificate and no-redirect policy apply. | Checked with `javap` on the SDK on 2026-11-18. Not yet run. |
| Associating a file with a feed | Official TAK Server MissionApi exposes `PUT /missions/{name}/contents` with JSON hashes ([research table](DATA_SYNC_INTEGRATION.md#2-server-mission-api-is-a-separate-evidenced-candidate)). | From pinned official source, not tested on any server by TMM. |
| Uploading the bytes | The server's content store is what `GET /Marti/sync/content?hash=` already reads from. The matching upload operation must be taken from the pinned official server source and tested. TMM does not guess it. | **Open.** First lab step. |
| Permission | MissionApi requires MISSION_WRITE on the feed. Users who can only read cannot publish. | **Open.** The server owner must confirm the feed roles. |
| Real server | TAK Server 5.6. All lab evidence is from 5.8. | **Open.** Must be tested on 5.6. |

If upload or association does not work on 5.6, or users lack write access, W1 fails
and the fallback is direction B, a guided native File Select step.

## Decisions to approve

### W1 — Route

On **Publicera**, metadata is sent on the existing native host-copy route (#178),
unchanged. The photo is sent by TMM as the existing approved `.tmmi` container v1
(no new wire format). TMM uploads it and then associates it with the followed feed,
using the same configured TLS client, profile and HTTPS API port as fetching. This
lifts #178's "no direct REST writes" rule for this one operation only.

### W2 — Destination

The only destination is the followed feed from U2. It is rechecked immediately before
upload: following on, same profile, connected, same service and mission GUID. Any
change refuses. Unlike the native route, this write targets exactly one feed.

### W3 — Explicit, one attempt, no queue

Each publish needs one tap and one confirmation of a fresh preview. There is no
background retry and no queue. If the device is offline or the upload fails, the
device keeps **Ej publicerad** and the user taps again later. If the outcome is
unknown, for example a timeout after sending, TMM shows **Okänt om bilden kom fram**.
The next fetch then shows whether the photo is in the feed. The same bytes are never
re-sent automatically. Fetching (U8) stays GET only.

### W4 — Old photo in the feed

The reader accepts at most 256 feed resources (`MissionImageInput.MAX_RESOURCES`),
and every published photo adds one. After about 250 photo updates in one feed, every
client would stop accepting image lists. Recommendation: after the new photo is
associated and seen in a fetch, TMM removes from the feed only the resource that was
this device's **base image** (the photo it just replaced), with
`DELETE /missions/{name}/contents`. It never removes a resource it did not see as the
device's current photo. This is removal from the feed only. Copies already
downloaded elsewhere stay. The alternative is to raise the cap and accept growth.

### W5 — Verification before a candidate

1. Lab: identify the upload operation in pinned official server source, then test
   upload, association, removal, missing write permission, repeat, and interruption on
   a 5.6 lab server if the package exists, otherwise on 5.8 with the difference recorded.
2. A traffic capture shows only the intended requests to the selected service.
3. The server owner confirms roles on the real feed before the authorized trial.

## What does not change

Receive rules U1–U8, all stored formats, and native metadata publication. No
receipts, no publication of devices the user did not choose, nothing sent during
fetching.

## Implementation — service only (part of #243)

`FeedPhotoPublication` (app `plugin/local`) implements W1–W4 for the photo, with no
UI and no metadata. Nothing calls it yet; the Publicera UI is a later step.

- **Container.** `review(device)` freezes a preview of the saved device and its owned
  photo for the followed feed. `publish` confirms it, writes container v1 with
  `ImageSnapshotExports` into the private no-backup directory
  `se.tmm.sensorplanner/feed-photo-outbox/`, reads it back, checks it and hashes it.
  The file is deleted after the attempt; a file left by a crash is deleted by the next
  attempt. Nothing is written to Downloads.
- **Requests** (`MissionSnapshotHttp`, sharing one request lifetime with the reads):
  `POST /Marti/sync/upload?name=<uuid>.tmmi&creatorUid=`, then
  `PUT /Marti/api/missions/guid/{guid}/contents?creatorUid=` with `{"hashes":[h]}`, and
  for W4 `DELETE /Marti/api/missions/guid/{guid}/contents?hash=&creatorUid=`.
  `creatorUid` is the ATAK device UID the caller supplies. The reply's `Hash` must equal
  the local SHA-256. The body entity is not repeatable, so the HTTP client cannot
  replay it. Deadlines: 60 s for the upload, 12 s for the others.
- **W2.** Before the upload, before the association and before a removal, the stored
  sync state is reread: following on, same syncId, contextId (service and mission
  GUID), connect string and feed name; ATAK's default provider; the profile connected.
- **W3 outcomes.** Succeeded; refused (any 403); failed (nothing sent, a non-403 4xx
  answer, a hash mismatch or a destination change); unknown (the request may have
  reached the server: a timeout, lost connection or 5xx after sending). A request is
  consumed by its first attempt and nothing is resent.
- **W4.** At publication the replaced base is the one feed resource, in the latest
  fetch passed to `fetchCompleted`, whose registry record has this device and the
  stored image-base digest. If there is not exactly one, nothing will be removed.
  `removeReplaced` removes it only once a later fetch shows the new photo for the
  device and the old one is still listed. One attempt, failures are reported. This
  state is in memory for the session only, so after a restart the old photo stays in
  the feed.
