# Manual saved-draft metadata sharing lab

Issue #14 / F05/F12. This implements an **experimental, synthetic-data-only** path
from the saved planner to one individual in the isolated SDK lab. It is not a
production publishing route, draft synchronization, image transport or a release.
The normal offline/API-26 guards remain in place. The two-client privacy evidence
in PR #39 supports this bounded integration, not arbitrary transports or phones.

## User flow and invariants

1. Open a saved device and choose **Dela metadata i isolerat labb**. The serialized
   planner worker reloads the saved UUID; a missing/unreadable record fails explicitly.
2. Select one individual contact. Self, groups and SDK special aliases **All Chat
   Rooms** / **All Streaming** are excluded. No contact is automatically selected.
3. The scrollable review displays destination name/UID, the fixed lab-server channel,
   UUID, ID, type/status, owner, exact coordinates, all three times (UTC), true-north
   bearing, comment and explicit image exclusion. It also shows the standard CoT
   waypoint's seven-decimal coordinates. No field is silently rounded in saved data.
4. Only **Skicka metadata i labbet** queues a one-use send attempt. Cancel/back and
   plugin stop invalidate outstanding reviews. There is no background retry/outbox.
5. On the same worker as normal draft/image writes, reload the saved record, encode
   the immutable reviewed metadata and recheck the actual contact/name, exact route,
   offline state and ShareReview freshness immediately before SDK dispatch. A changed
   revision, field, image association or destination needs new explicit review.

A missing connection/recipient, stale review, unsupported data or unreadable snapshot
says **Inte skickat** before any dispatch. SDK exceptions after entering dispatch say
**Sändningsfel – leverans obekräftad**. A normal SDK return says **Sändningsanrop utfört
– leverans obekräftad**. Neither means recipient/server acknowledgment. All drafts
remain local; sending never writes the snapshot, advances its revision, clears an
image, marks it shared or deletes it. A local image, including a missing owned copy,
is always excluded and is never opened by this operation.

The worker serializes this path with the planner's normal local edits. Plugin stop
invalidates queued sends before deferred UI cleanup and unregisters receiver handlers.
A dispatch already handed to the SDK cannot be recalled. This is not a transaction
with external same-UID writers, a server lock, an exactly-once guarantee or remote
withdrawal. A later production adapter must define those boundaries and permissions.

## Verified SDK route and scope

The supplied 5.6.0.24 `main.jar` signatures/bytecode were inspected again for contacts,
server state, CoT envelope/detail attributes, dispatch and marker import/export.
`ChatManagerMapComponent.isSpecialGroup(uid)` identifies the two special aliases;
**IndividualContact alone is insufficient** because the SDK represents its chat
broadcast contact as an individual. Both picker and final send preflight reject
these aliases; the older fixed-fixture CoT picker/send received the same safeguard.

Exactly one enabled stream must be connected to `127.0.0.1:8089:ssl`, with the
existing airplane/Wi-Fi/active-network restrictions satisfied. Dispatch supplies
`toUIDs = {selectedUid}` and `CoTSendMethod.TAK_SERVER`; no null, broadcast or fallback
recipient is supplied. There is no network configuration, HTTP upload or Data Sync
integration. Source code creates a temporary CotEvent, not a source MapItem/marker.

This condition is not TLS identity pinning or a firewall: the approved harness also
requires guest IPv4/IPv6 egress blocking, explicit ADB reverse tunnels, the internal
Docker network and localhost-only relay. Android reports offline while those scoped
CoT tunnels work. Do not enable normal phone networking or repoint the relay to an
operational service and treat that as an approved test.

## Experimental wire contract

Tag: `detail/tmmShareLab`, `experiment=issue-14`, independent `schemaVersion=1`.
Root UID is the stable device UUID. Root type is test waypoint `b-m-p-w`, how
`h-g-i-g-o`; transport time/start are refreshed and stale is one hour. Stale is not
persistent retention/deletion policy. Altitude/CE/LE are SDK UNKNOWN, not measured.

Exactly 15 attributes are accepted, with no nested content:

| Attribute | Rule |
|---|---|
| experiment / schemaVersion | `issue-14` / `1` |
| displayIdUtf8 / ownerUtf8 / commentUtf8 | Canonical padded Base64 of strict UTF-8; preserves XML-sensitive text, CR/LF, tab and Unicode. Base64 is encoding, not encryption. |
| latitude / longitude | Canonical Double strings for exact saved coordinates. |
| type / status | Existing enum names, with ordinary domain validation. |
| createdAt / updatedAt / deployedAt | Canonical Instant strings; deployedAt may be empty. |
| bearingDeg / bearingReference | Canonical optional Double / `true-north`; type/range rules apply. |
| imageIncluded | Exactly `false`. No imageRef, filename, URI/path, blob or local revision counter. |

The inspected CotPoint serializer rounds lat/lon using Math.round(value × 10^7)/10^7.
The receiver requires the actual root point to match that projection while retaining
the exact custom coordinates. Standard clients therefore see a rounded waypoint;
full precision requires the validated custom fields. Identity/type/status/times and
text are not inferred from the standard waypoint's display label.

The decoder rejects noncanonical UUID/text/numbers/times, missing/unknown attributes,
future versions, invalid UTF-8/Base64, images, invalid enums, range/type conflicts,
and a changed envelope point. Text fields are bounded to 65,536 UTF-8 bytes, matching
the snapshot string ceiling (the existing comment limit is smaller). Encoded strings
are capped before decoding; other fields are capped at 128 characters. This changes
no local snapshot-v1 bytes or migration rules. Wire schema/version, provenance and
causal revisions for production still require explicit decisions; the lab counter
used internally for validation is not a sender revision and is never exposed/saved.

## Receiving and verification

On the recipient, open **TMM CoT-labb** before sending to register the lab inspector.
**Visa mottagen enhetsmetadata** checks exactly one actual root marker for the last
validated UUID, invokes the host's actual CotEventFactory exporter and validates
all custom fields and root point. It does not merely display the logger's packet.
The registered detail handler retains only canonical validated metadata in a private
marker key. The logger is filtered by tag, exact lab stream and current lab guard;
it logs no raw event, field, file path or recipient. Handlers/logger/dialogs close
on unload. No received event is written to DraftStore or given import/conflict rights.
Repeated UUIDs use ATAK's normal lab waypoint update; they are not a reviewed merge
of local TMM drafts. Same-UID host plugins remain outside the isolation boundary.

## Acceptance procedure

Use only the dedicated two SDK 5.6.0.24/API-35 guests and isolated TAK Server
5.8-RELEASE-92 lab. Keep raw UI/traffic/server records, credentials and APKs private.

- Seed synthetic saved metadata of all four types, including special text, optional
  time/bearing, one owned local image and coordinates beyond seven decimals.
- Cancel the picker and final review; query the exact UUIDs and inspect the recipient,
  bracketing absence checks with actual positive receipt.
- Confirm each type; inspect the actual recipient marker export and independent
  server fields/destination. Compare source snapshot/image bytes before and after.
- Change saved metadata after review, disconnect after review and restart with an
  unconfirmed review: require no new corresponding server/recipient event.
- Restore the lab, review again and confirm; repeat a UUID and require one marker.
- Unload/reload the inspector and repeat a positive control, checking one arrival.
- Run core tests, isolated image/coordinator tests, SDK build/lint/static APK checks.

Actual run evidence and remaining gaps are recorded below after verification.
