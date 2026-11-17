# Next configured-server publication gate — #178

Status: investigation and proposed experiment only. No non-loopback publication,
new publisher, stored format or operational access is enabled by this document.

## Why the current guard stays

[Signed v28](SIGNED_V28_METADATA_ROUND_TRIP.md) proves the bounded metadata round
trip through normal SDK host copying and native Data Sync. It also demonstrates
that updating an existing host copy can open a native publication prompt even with
Auto-publish *new* content off. No declines publication but does not undo the SDK
mutation; normal native Add Items/Done can retry, and native OK can publish another
reviewed update. Do not infer one selected destination or an exclusive Done boundary.

The actual server/phone workflow remains #171/#25. Simply removing the loopback
check would not establish a destination policy. Other outputs, cached feeds,
possible queued native sends and the actual server owner's permission must be
understood. Human approval of source/destination scope is still needed before a
broader sharing mode is enabled. Private drafts/images remain private until the
separate explicit metadata handoff; unrelated CoT diagnostics stay restricted.

## Verified public SDK candidates, not runtime success

The supplied ATAK-CIV 5.6.0.24 SDK/Javadoc exposes:

- `CommsMapComponent.sendCoTToServersByMission(String uniqueIfaceKey,
  String mission, CotEvent event)`.
- `sendCoTToServersOnly(String uniqueIfaceKey, CotEvent event)`.
- `getAllPortsBundle()`, with arrays describing streams, outputs and inputs.

The Javadoc describes the first method as mission-routed server delivery, with the
SDK adding routing information. A non-null interface key selects a configured
stream; null selects all active streams. The inspected default-provider bytecode
resolves a specified key through the current streaming-interface map and returns
on an invalid key rather than sending to all streams. The method returns void. A further static trace through CotService, the default
streaming-interface map and TAKServer.getConnectString correlates that raw
connect string with the interface key in this supplied default-provider build.
Runtime alignment/freshness still needs the controlled probe.
These are actual public SDK surfaces, not guessed private Android Data Sync actions.
The optional SDK preflight verifies their signatures only, without creating a
client, registering a listener or sending anything.

**Not established:** whether the mission argument is the chosen mission name or
GUID in this exact server combination; current runtime key/interface alignment;
persistence/Data Sync content membership for a fresh UUID; role
enforcement; destination isolation; standard-host behavior; or any acknowledgment.
Routing an event to mission participants is not proof of creating durable feed
content. The server-only method is not a substitute for the Data Sync-feed goal.
No production caller or newly accepted publishing contract follows from a signature.

The SDK port-list contract could support a bounded inventory of enabled outputs
and streams. Do not dump Bundles or read/copy credentials. Verify exact supported
scalar keys and failure/unknown states before using that inventory as a guard.
It does not itself enumerate all native Data Sync cached/queued destinations.

## Recommended next experiment

Before choosing between a broader native handoff and a new sender, run one
explicit, fixed-fixture, isolated SDK mission-routing probe:

1. Inspect the exact public interface-key and server mission-argument semantics.
   Use the default supported provider, a non-null verified connected stream key,
   the existing approved metadata profile and fresh synthetic identities. Do not
   handcraft undocumented routing tags or choose arbitrary endpoints.
2. Establish fresh UID absence in all known accessible lab missions. Ensure the
   actual native client/creator is properly subscribed when the supported setup
   requires it; do not repeat the historical empty-client positive-control error.
3. After separate review/confirm, send once through only the candidate supported
   API. Observe routing, GUID overview membership, complete CoT and the independent
   native Data Sync item view. Match every field. Neither an API return nor a
   marker visible outside the feed is a successful feed-publication result.
4. Test invalid/stale interface keys without fallback, Cancel before send, source
   loss and read-only/unauthorized mission controls. A failed/uncertain send must
   not automatically replay or relabel possible effects as rolled back.
5. Check repeat existing-UUID updates and unrelated mission/output absence, including
   the tested server's current-UID cache policy. Do not claim independent per-feed
   versions. If the API only routes without durable membership, record that negative
   result and keep the proven native workflow; do not silently replace feed sharing
   with general CoT or an unreviewed REST publishing workaround.

Only after that evidence and an exact reviewed destination/operator contract should
product code enable a configured-server sharing trial. Use only synthetic data and
a separately authorized test environment; the agent does not contact the owner's
server during this goal. The owner still needs server-owner permission, software/
full build, HTTPS Mission API port/access and a dedicated test feed with known
roles before the separately authorized two-phone trial. No Android TMM APK needs
to be installed on that server.

The [trial contract proposal](CONFIGURED_PUBLICATION_TRIAL_CONTRACT.md) states the
exact source, destination and consent policy for owner review.

## Implementation boundary

Reuse fresh saved-selection checks, metadata encoding, explicit review and honest
uncertainty handling. Keep existing local-v2/inbox/image bytes and stable UUIDs.
This proposal authorizes no registry/receipt writer, automatic sharing, server-wide
fallback, direct REST publisher or image transport. #26 remains separate. A new
source/receipt/container layout still requires its own exact-format approval.
