# Read-only import-source observer candidate (#139 / #25)

This implements the next bounded probe in [the source investigation](INGRESS_SOURCE_VERIFICATION.md).
It observes the public `MapEvent.ITEM_IMPORTED` base-listener surface; it does not
register an importer, change routing, use a logger as ingress, stage/apply an inbox
or infer a trusted source context. **No native runtime result is claimed yet.**

## Activation and exact scope

Candidate **22 / `0.1.0-source-observer.1`**, same package/API/permissions. Nothing
registers on plugin startup. In **TMM CoT-labb → Källkontext · syntetisk observation**,
review the scope and press **Starta nytt prov**. It requires the existing lab-only
policy: API 26+, lab access allowed, airplane/offline restriction, exactly one enabled
connected stream equal to the existing loopback test route. An operational-server
connection or consented connected-local session cannot enable it.

Only the four existing PUB18 canary UUIDs are eligible:
`00000000-0000-4000-8018-000000000001` through suffix `000000000004`
(Mini, Scout, PIR, Gateway). The local SDK check verifies this allowlist against
`PublicationEnvelopeFixtures`, not a guessed range of real device identifiers.
Other events do not have their extras inspected. The observer reads no coordinates,
CoT payloads, owner/comments, images, profiles, files or local-draft records.

The active dialog's **Fortsätt provet** leaves observation active while native
Data Sync/SDK controls are operated. Return and choose **Stoppa och visa** to stop
and inspect the snapshot. **Starta nytt prov** explicitly discards old memory
observations. There is no file export, log dump, background fetch or acknowledgment.

## Retained structural facts, not values

For at most **64** eligible callbacks, retain only canary index, extras-present flag,
classes of the SDK-inspected keys `from`, `serverFrom`, `endpointFrom`, and the
number of other keys. Other key names/values are deliberately not retained or
queried. An extras Bundle exceeding **64** keys invalidates the run before recording.
The known fields are classified as absent/null/nonstring, or equality to one of the
fixed canary UUIDs / literal `internal` / literal `StateSaver` / known test-route
string / other string. No arbitrary string/value is copied into the observation.
The optional UI lists only these fixed enums and counts, never raw Bundles or events.

These are **literal/shape classifications**, not authentication. A local/replayed
caller can supply the same hints. In the inspected network path `from` is the event's
own UID; server/endpoint hints are not a feed ID. Unknown extras are counted only:
this probe cannot establish that an uninspected key or another public surface lacks
useful provenance. No source namespace may be created from these observations.

## Stop, loss and boundedness

- Registration/removal uses the actual SDK's public base-listener methods, never an
  exact-type importer or listener-stack push/pop. SDK calls remain on the UI path;
  callbacks only update the bounded synchronized memory accumulator.
- Stop/overflow/unreadable extras and **observed** guard loss prevent further
  recording. Old listener tokens cannot affect a new explicit run. Plugin stop
  rejects callbacks immediately, captures the old probe for queued unregister and
  clears its memory. Connected-local consent closes the probe as well.
- At the event cap no evidence is evicted: the run becomes invalid with a dropped
  count and needs a fresh start. Unexpected/unreadable data does not become success.
- Failed unregister shows a generic instruction to unload/restart the test host;
  stopped/closed token guards remain inert. Native listener removal/lifecycle is
  still a runtime acceptance gate, not proven by the memory tests.
- There is no continuous connectivity monitor. A quiet loss/return between checks
  may not be detected. Guards are checked at activation/show/stop/eligible callback;
  independent before/after controls and firewall/route checks remain necessary.

A zero dropped count proves neither that ATAK emitted every original variant nor
that all native callbacks reached this surface. ITEM_IMPORTED is after successful
map import; losing conflict candidates may be absent. Current MapItems/re-exports
are not an original-input stream. Never report a complete selected-feed source
contract from these counters alone. No automatic continuation after restart/unload.

## Required signed two-client experiment (not run)

Use the existing isolated standard 5.6.0.25 / Data Sync 3.7.4 / lab-server setup,
synthetic records only, egress blocking and verified loopback routing. Verify the
returned APK version/signature/update compatibility first. Do not use a local debug
APK as evidence that a standard host accepts the future service-signed candidate.

| Control | Required observation / limit |
|---|---|
| Baseline with observer off, then on, then off | Native imports/feed workflow still behave normally; no ordinary importer is replaced; no records while stopped |
| Explicit local SDK PUB18 import | Positive before/after callback control for the exact eligible UUID; do not use the older unrelated CoT-lab canary and call zero a pass |
| Authenticated lab-server live PUB18 event | Compare actual public hint classes with local SDK/file import; use existing authorized lab harness, otherwise mark this control unrun |
| Native selected-feed download on B | Independently verify feed/UUID/exact metadata through native B export and lab observations; compare callback shapes without assigning authority |
| Same UUID in two feeds / native conflict choice | Establish which originals/current winners are observable; no correlation solely by UUID/order/time; losing variants cannot be assumed seen |
| Cold restore/history download/reconnect | Separate replay/current-state notifications from a new original receipt; keep real source-context registration blocked if ambiguous |
| Listener-stack changes, stop/restart/unload, guard loss | Positive native controls still work, prior tokens cannot record into a new run, no automatic restart; disclose timing not forced |

Start a fresh observation for each bounded case. If controls fail, overflow occurs,
unknown fields remain relevant or source/variant mapping is ambiguous, record that
limitation and an explicit route decision under #25. Do not substitute private
broadcasts, guessed endpoint-as-feed keys, a new REST client or contact transfer.
Only after actual source/variant evidence and review may an ingress adapter bind
an approved context and use the existing metadata/image candidate boundaries.

## Local verification actually performed — 2026-10-30

- Native API26/35 `IngressProbeStateTest`: **14 cases**, no failures/errors/skips;
  standalone image lint: zero errors/eight existing advisories. Tests cover exact
  allowlist, absence/null/fixed literals/types, no value stringification/retention,
  immutable snapshots, stale tokens, stop/close/restart, invalidation, capacity,
  concurrent observation and generation exhaustion. They do not exercise native
  dispatcher registration or the actual Data Sync path.
- Actual SDK 5.6.0.24 public signatures, including getType/getItem/getExtras/base
  add/remove/getMapEventDispatcher/getUID, plus fixture allowlist alignment passed.
  The command constructs no host and registers no listener.
- `scripts/check-sdk.sh` passed MGRS/publication/ingress checks, CivDebug assembly,
  app lint, all three app unit cases and debug APK signature/discovery/permission /
  packaging checks. Final debug/release assembly (including R8), debug lint/app
  unit tests and debug APK checker passed after callback guard review changes.
  App lint: zero errors, 57 advisories, including the new probe's API-annotation
  advice and existing UI/annotation/update warnings; none were suppressed.
- Release output is actually unsigned (signature-enforcing verification rejected
  it); development signing and local builds are not service signing/runtime proof.

No emulator, Docker/server, network import, native listener registration, phone,
signing upload or release ran. The v21 source/archive/APK remain unchanged. A source
ZIP can be prepared from reviewed merged main for the owner's later manual service
submission; actual returned-artifact and two-client results must be recorded anew.
