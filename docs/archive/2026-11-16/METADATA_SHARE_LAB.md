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

## Recorded results — 2026-10-17

Two dedicated API-35 arm64 SDK developer hosts **5.6.0.24** used the existing
isolated **TAK Server 5.8-RELEASE-92** harness. Android airplane mode was on,
Wi-Fi/data and location were off; guest IPv4/IPv6 OUTPUT blocking permitted only
loopback, with scoped ADB reverse tunnels. The internal Docker network and relay
ports bound to 127.0.0.1 were checked. No ordinary connected phone was tested.
The test harness disabled its unused HTTP API process; this run did not test HTTP
privacy. Raw evidence and generated artifacts remain private and ignored.

The final implementation at **59c0dd3** built versionCode **7** /
`0.1.0-metadata-share-lab.1`. The installed/tested APK SHA-256 was
`10efce9250fd0194cde6c6713a0331dd8448d0b401737d9c1da3b5aedb8d162d`.
An earlier same-contract APK (`420ab088a77ca5fc03378d086a5b3afe5f80359730d53abca3a23c3a12863080`)
provided the first four-type and picker/final cancellation controls. The final
build repeated positive receipt for all four types after adding inner-text rejection,
SDK-canonical dispatch and final-preflight ordering; the results below distinguish
source data edits made deliberately by the negative-test harness.

| Check | Observed result |
|---|---|
| Recipient picker and final-review cancellation | Initial server query contained zero of the four test UUIDs; both cancellations left zero. Recipient inspector had no validated event. Subsequent confirmed sends provided positive controls. |
| Final-build stale review | Harness changed the actual saved PIR after review. Final confirmation showed `Inte skickat`; server count remained four and receiver had no new validated event. |
| Final-build disconnected route | Relay stopped after review; confirmation showed `Inte skickat`, with server count still four. |
| Final-build process restart before confirmation | ATAK was force-stopped with an unconfirmed review. Server count stayed four; recipient inspector still had no validated event. New review after reconnect sent normally. |
| Final-build cancellation after receiver unload/reload | The inspector reset to no validated event; cancelled final review kept the server at eight and receiver unchanged. A fresh confirmation then added exactly one event, with one marker/export and one inspector arrival. |
| All four types on final build | PIR, Scout, Gateway and Mini each reached B. Independent server records showed one new event per confirmation and exactly one `marti/dest` for the synthetic selected recipient. |
| Actual recipient import/export | Inspector counted exactly one root marker for each UUID and re-exported through CotEventFactory. All 15 custom fields, UUID and position checks passed. Repeated UUID sends updated the same single marker. |
| Precision and text | Exact synthetic position beyond seven decimals survived in custom fields; standard waypoint matched SDK rounding. XML-sensitive characters, Swedish text, CR/LF, tab and emoji survived. Optional deployment time and true-north bearing were preserved; types without bearing retained absence. |
| Image and source-state boundary | Both attached and unattached drafts were reviewed explicitly. Server payloads had `imageIncluded=false`, no image handle/path/revision. Source snapshot and owned image were byte-identical before/after the first four sends and again across final-build sends (after deliberate stale-test edits). |

The independent server query progressed from zero, to four initial confirmations,
to five after final-build recovery, to eight after the other three final-build
confirmations, and nine after the unload/reload positive control. Sender UI only claimed an unconfirmed dispatch, never acknowledgment.

Automated checks actually run: `sh scripts/test.sh` (domain/forms/import-review/
share-review/lab-fields/storage/v1/separate-process reopen, plus the newly merged
100-device scenario after integration with main); standalone image and
DraftStore-coordination tests **34 passed** across API 26/35 plus image lint;
`sh scripts/check-sdk.sh` (Android development build, lint and static APK/signature).
These use the supplied SDK privately; public CI does not run SDK/client tests.

One initial guest startup encountered an Android GNSS/system-server ANR before
opening the new feature. The guests were restarted with location disabled; the
recorded sharing tests then completed. This does not establish phone reliability.

## Remaining gates

This is bounded F05/F12 and A06/A07 development evidence, not full production
acceptance. Physical 5.6.0.12 clients, supported signing, companion coexistence,
normal networks and other transports, lifecycle beyond these lab cases, operational
permissions/provenance and production wire/conflict/withdrawal policy remain open.
No TMM image transfer, Data Sync integration, delivery acknowledgment, automatic
sharing or received-draft import is implemented. Issue #14 remains open for those
applicable production gates; a human decides on merge and release.
