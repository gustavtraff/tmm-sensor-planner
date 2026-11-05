# Explicit Mission snapshot intake pilot — #171 / #164

Candidate **26 / `0.1.0-snapshot-inbox-lab.1`** composes the existing metadata
codec, inbox, review, decision preparation and local-v2 transaction. It is a
loopback-only synthetic lab pilot. No operational server, background polling,
new publisher, image transfer or new stored byte format is enabled.

The owner approved snapshot-first delivery on 2026-11-05 and continued bounded
implementation/self-merges on 2026-11-06. Intermediate server changes may remain
unseen; complete history is investigated separately and is not promised.

## Source and explicit actions

From the local list choose **Hämta metadata · labb**, select an accessible feed
returned by the authenticated server, then explicitly confirm **Hämta till
inkorgen**. Merely opening the planner or review performs no network request.
A missing inbox requires separate **Starta ny inkorg** consent acknowledging that
missing history might be loss, not proof of a first installation. A corrupt,
unknown or unreadable existing inbox is never reset. Local v2 must load before
initialization or receipt; the local storage UI retains its explicit migration.

Only the already verified `https://127.0.0.1:8443` logical service is allowed,
with exactly the existing connected loopback stream, airplane/offline restriction,
lab access and default comms provider. The actual SDK supplies fresh strict-hostname
TLS and selected-profile client credentials without key/password copies. Feed
paths use canonical UUIDs returned by the authenticated mission listing; names
are display labels and no payload-provided URL is used. All requests are GET;
no mission subscription or publishing is performed by this adapter.

The existing opaque InboxStore context is assigned by this verified adapter as
`mission-api-v1|https://127.0.0.1:8443|<canonical-mission-guid>`. It identifies the
configured logical service plus mission, not an author or immutable historical
version. A stored context string alone grants no access and is never trusted from
a payload. This is an adapter mapping inside the already approved context field,
not a source-registry file, new frame or migration. General operational host
selection remains disabled until its own reviewed compatibility/source checks.

The owner-approved current-snapshot meaning is the latest accessible CoT for the
selected mission's membership. The actual 5.8 server cache and strict-membership
policy are recorded in [the v25 evidence](MISSION_METADATA_LAB.md). Do not infer
independent per-feed payload histories for a common UUID or a globally atomic
point-in-time snapshot.

## Boundaries and preservation

Each read has one worker with no queued requests and a 12-second accepted-result
deadline, plus cancellation and sampled route/provider guards. Reads/headers,
redirects, authentication, encoding and cleanup reuse the verified v25 policy.
Each response is at most 64 KiB; feed/member counts are at most 64. JSON framing
is checked by the platform's strict JsonReader, including duplicate key rejection.
The caller-owned body is consumed through EOF; unsupported/oversized/partial input
fails rather than silently importing a prefix.

Fetch reads GUID overview membership, complete CoT and a second GUID overview.
Name/GUID must agree and membership sets must match. Every returned event UID must
match that membership exactly, without duplicates. Concurrent changes and an ABA
membership transition between requests cannot be excluded; no atomic remote
snapshot or full intermediate history is claimed. Ordinary non-TMM records are
counted but not applied. TMM records require exactly the approved metadata profile,
UUID/type/point consistency and parseable outer times. Raw XML is validated without
passing arbitrary server data to the SDK logger/parser. No remote image references
are accepted and no raw errors, metadata or credentials are logged.

The whole fetched metadata set is validated before one existing-format inbox
replacement. `InboxStore.receiveBatch` retains old receipts/conflict variants,
deduplicates exact context+metadata keys and records fresh local observations.
An empty set changes nothing; missing members never delete local data. A validation,
capacity or pre-replacement failure preserves prior bytes. Replacement/cleanup
errors may already have committed: the UI reports uncertainty and allows inspection
or another explicitly requested Fetch, not rollback or a blind automatic retry.

**Mottagna metadata** loads existing genuine snapshots and opens InboxReviewUi
with decision coverage. ImportDecisionUi/ImportCommitUi still require separate
preview/Save, fresh inbox/local checks and the approved local-v2 transaction.
No fetch auto-applies or republishes. Current image handles and bytes remain local.
A new stored context or differing metadata is a separate receipt; time/order never
selects a winner.

The current session's successful fetch grants application access only to that
inbox identity, source context and observed membership. Other-source/absent-member
history remains readable but cannot be applied through that grant. Restart,
Unload, Cancel or observed lab/access loss revoke it; a fresh Fetch is required.
This local grant is not a continuous server-permission lease or atomic remote
revocation mechanism. A previously started private file transaction may finish
after UI stop; stale callbacks/results are suppressed, not described as rolled back.

## Verification record

Core suite and the full helper/image suite pass locally: **415 test cases, zero
failures/errors, one intentional skip**, image lint and standalone APK/fixture checks.
Actual supplied-SDK debug/release assembly, app lint, three app unit cases and
SDK ingress reflection/compile checks pass. Added controller cases cover explicit
initialization, review/queued-store Cancel, source loss, late results, malformed/
wrong-feed responses, corrupt/missing local/inbox files and repeat deduplication.
Additional exact scope tests and final artifact identifiers are recorded before merge.

The actual SDK host has completed explicit empty-local-v2/inbox initialization,
server-provided feed selection, a full new-UUID snapshot intake, read-only variant
comparison and separate exact Add preview/Save. Native outcome is **Enheten tillagd
lokalt**, local version 1 / revision 1. There is no automatic application at Fetch.
This first run uses the development APK; final code/runtime and source-side
publication evidence are recorded separately below.
The correctly subscribed new-UID control now succeeds using the connected native
client UID, explicit subscription and matching creatorUid. This corrects the v25
empty-client test setup without weakening server membership policy. The returned
CoT contains one new synthetic TMM UUID with all 15 metadata attributes.

Standard service-signed v26, two independent standard-client/native Data Sync
publish-back, phone/OEM and operational-server acceptance remain separate gates.
The SDK pilot must not be advertised as their completed product acceptance.
