# Receiving metadata: verified SDK surfaces and source limits — #15/#25

Baseline: main `f9845c8` (#113/#114), versionCode 20. The owner assigned receive-path
and source-context verification while agent 2 independently integrates decision
coverage into the inbox UI. This investigation adds an optional SDK signature check,
not a registered receiver or a new source/wire/persistence contract.

## Exact evidence

Inspected local ATAK-CIV 5.6.0.24 SDK:

| Input | Bytes | SHA-256 |
|---|---:|---|
| main.jar | 32,853,773 | `5a111497ca5722c5e0dfdca4a7e5852fa68400b6723038eba7fbc657cc5c8d9b` |
| atak-javadoc.jar | 13,291,945 | `ab417c14abb3df4ef77f12f0295490c7e94b4b074fbfb42d9d5821c130d8f0de` |

Read the supplied Javadoc and inspected public signatures plus relevant `javap -c`
method bodies. Cross-checked control flow against official public source pinned to
`9f6893dd657feacc35ec5de03dad721c2e44170e`. Public source is corroboration, not proof
that an arbitrary later standard host or Data Sync build runs the same code. Raw
vendor source/Javadoc/disassembly stays outside Git in ignored private evidence.
No server profile, account, endpoint, key or operational event was inspected.

## API findings and consequences

| Surface | Verified information | Integration consequence |
|---|---|---|
| CommsLogger.logReceive(event, rxid, server) | Exact SDK method exists; the supplied docs allow null IDs/server information. The official interface explicitly discourages using a logger as a general message interception route. | Keep ShareLabReceiver a debugging experiment. Do not promote it to durable product ingress. |
| CotServiceRemote.CotEventListener / CommsMapComponent.addOnCotEventListener | Exact add/remove/callback signatures exist. Javadoc describes callbacks for events not otherwise handled. | This is not a complete subscription for normally handled markers such as the current production envelope type. |
| CotEventImporter.importData(event, extras) / CotImporterManager | Exact registration/removal and Bundle-bearing callback signatures exist. Import selection has control-flow consequences below. | A new importer needs an explicit runtime coexistence experiment; adding one is not a passive observer guarantee. |
| MapEvent.ITEM_IMPORTED / MapEventDispatcher base listener | Exact listener registration/removal, getItem and getExtras signatures exist. The inspected map importer forwards a copy of its supplied extras after successful import. | Candidate for a read-only runtime probe, not proof that every input/conflicting variant or feed identity survives. |
| CotDetailHandler.toItemMetadata | Callback takes MapItem, CotEvent and CotDetail, with no Bundle argument. | It can see envelope metadata but does not directly supply the import's channel/context evidence. |

[Official CommsLogger interface](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/comms/CommsLogger.java)
and [CotServiceRemote interface](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/comms/CotServiceRemote.java)
support the first two boundaries. The same cautions were checked in the actual
supplied SDK Javadoc, not inferred from method names.

### Transport hints do not establish the selected feed

In the inspected network receive method, `from` is populated with the **event's
own UID**. It is not evidence of an authenticated sending operator. A recognized
stream can supply `serverFrom`; another endpoint can supply `endpointFrom`.
Those describe connection/interface hints, not a mission/feed namespace. Internal
dispatch can also supply caller-provided extras. The source does not establish
feed membership, subscription authorization or origin authentication from these
strings. See [CommsMapComponent](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/comms/CommsMapComponent.java).

This is not a claim that Data Sync has no internal provenance. Its actual download,
conflict-choice and restart paths still need observation. No supported callable
third-party Data Sync Android contract has been established under #25. A valid
`tmmPublication` payload, callsign/owner, object UUID, server address or callback
name cannot by itself populate a trusted **selected-feed** InboxStore context.
A server-wide channel would be a different scope and must not silently replace it.

### Import interception can change normal handling

The inspected manager chooses an exact-type importer list when present, otherwise
its generic list. It stops on a non-IGNORE result; exhausting the chosen list goes
to the fallback importer. Consequently registering an exact-type importer for
`b-m-p-s-p-loc` can change which ordinary importer sees that type, even if the new
handler returns IGNORE. Native display/persistence/Data Sync behavior must not be
assumed preserved by a non-consuming test callback. See
[CotImporterManager](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/CotImporterManager.java).

The map importer notifies after successful import and has late-event handling.
A current MapItem or a later re-export therefore cannot prove receipt of every
original losing variant. The forwarded extras are candidate observations, not a
guaranteed original-input/source contract. See
[MapItemImporter](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java).
Do not correlate independently delivered detail and map callbacks using only UUID,
time or order and call that authenticated provenance.

## Verification command and actual result — 2026-10-27

With the documented private SDK/toolchain configured:

```sh
sh scripts/test-ingress-sdk.sh
```

The script compiles Java 8-targeted code against the actual SDK and Android 36
stub library, then reflects exact public parameter/return types for listener,
importer, map-event and detail-handler APIs. It does not construct a host, register
anything, call importData/sendCoTInternally, read an event, open stores or dispatch.
The command passed locally. `scripts/check-sdk.sh` now includes it as an optional
private-SDK check; public core/image CI is unchanged.

The supplied SDK method bodies were separately inspected for the receive extras,
listener fallback, importer routing and copied import extras described above.
These observations are not runtime callback-order, coverage or authentication tests.
No production source has changed, so no app build, core suite, full image suite,
CI wait, emulator, Docker, phone/network test or signing upload was run for this
investigation. Shell syntax, patch whitespace and documentation links were checked.

## Next bounded runtime probe and acceptance gate

Prefer a read-only ITEM_IMPORTED observation experiment before considering an
importer that changes routing. It must be separately guarded to the existing
isolated synthetic lab and fixed canary UUIDs. It must not initialize/stage an
inbox, mutate map content, modify drafts, export payloads or acknowledge anything.
Use base-listener registration/removal and stop-generation guards. Retain only
bounded in-memory structural observations (field presence/type, candidate channel
class and canary counts), not real endpoints, credentials, coordinates or raw
Bundle/event dumps in logs/screenshots. Any dropped observation invalidates a
completeness claim.

| Control | Question to establish before an ingress writer |
|---|---|
| Live server CoT versus local SDK/file import | Which hints occur, and can local/replayed origins be distinguished? |
| Native selected-feed download on B | What provenance survives the actual Data Sync 3.7.4 path? Does it identify the selected mission, merely a stream, or neither? |
| Same UUID in two selected feeds | Can namespaces remain independent without inferring feed identity from the UUID/payload? |
| Ordinary/native conflict variants | Are original candidates observable before host winner selection, or only the accepted current object? |
| Cold start / state restore / history download | Can replayed/current observations be classified without inventing a new arrival or source? |
| Cancel, listener-stack changes, stop/reload | No callbacks after stop, no missing callbacks hidden as successful complete coverage, no native behavior change. |

If the runtime path lacks a verifiable selected-feed identity or original variant,
record that limit and propose an explicitly scoped alternative through #25 before
changing source registration or deduplication semantics. No private broadcasts,
undocumented plugin hooks, endpoint-as-feed shortcut, new remote protocol or REST
client is authorized by this signature check. The real ingress adapter can then
validate PublicationCot, bind an approved opaque context, stage InboxStore and
expose the already implemented review/confirm flow. Arrival still never applies
to private drafts automatically.

## Signing archive prepared in parallel

Prepared the committed main `f9845c89c408d9c3bb941d00adda2c6f1c5c553d` source
archive privately for the owner's manual TAK signing-service submission:

- VersionCode 20, `0.1.0-versioned-storage.1`.
- Single `TMM-Sensor-Planner/` root; reviewed source archive helper/boundaries.
- Archive SHA-256: `4016e783eb01504378b2750613386a9461d1a774431ec685f1e282cbda37d2c1`.
- `python3 scripts/test-signing-source.py`: five tests passed. Archive creation and
  independent inventory/root/wrapper/version inspection passed.

The generated ZIP is ignored and excluded from this PR. It does not contain this
later investigation or any live ingress feature. No upload was performed. After
signing, verify the returned package/version/signature and test v1 upgrade Cancel,
explicit upgrade, field/image preservation, versioned edits/delete and restart in
a synthetic offline trial. Then evaluate the bounded observer experiment. The
archive is a concrete v20 test candidate, not operational sharing approval.
