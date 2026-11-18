# Metadata route for ordinary Publicera — proposal (P2 / #251)

**Status: proposal. The owner must approve or change M1–M7 before #243's Publicera UI
is built.** Merging this document approves nothing. It is outbound transport, so it is
a gated class.

## Question

One explicit **Publicera** must send the deployer's position, status, bearing and
photo to the followed feed. The photo route is approved
([W1–W5](IMAGE_PUBLICATION_ROUTE_PROPOSAL.md), lab go on
[#241](https://github.com/gustavtraff/tmm-sensor-planner/issues/241)). Metadata M
today goes out only through the [#178 trial](CONFIGURED_PUBLICATION_TRIAL_CONTRACT.md):
session-only consent, synthetic data only, native handoff, destination not enforced.
How should M go out in ordinary use?

## Facts the options depend on

- Receivers read current M from the feed's **CoT members** with GET-only snapshots
  ([ADR 0012](decisions/0012-explicit-mission-snapshot-intake.md)). U3 compares that
  M with the stored base ([U3](AUTOMATIC_FEED_SYNC_PROPOSAL.md#u3--conflict-detection-by-stored-base-not-by-time)).
- Every `.tmmi` container already carries one exact M in its descriptor
  ([container v1](IMAGE_CONTAINER_FORMAT.md)), and intake records it in the inbox
  ([intake](SELECTED_FEED_IMAGE_INTAKE.md)). U4 uses it only to bind a photo to the
  device and to the feed's current M. A container cannot exist without a PNG (P ≥ 1).
- On the tested 5.8 server, feed membership is by GUID but the latest CoT is by UID
  (ADR 0012). An update that reaches the server for a UID that is a member of the
  followed feed is therefore what followed-feed receivers read, whichever feed the
  native step chose. Not checked on 5.6.
- The native route: TMM writes the host map item through the SDK; native Data Sync
  then publishes. An existing-item update opens native **Publish Content to Feeds**
  even with Auto-publish *new* content off; **Nej** does not undo the SDK write; a new
  item needs native **Add Items → Done** ([signed v28](archive/2026-11-16/SIGNED_V28_METADATA_ROUND_TRIP.md#existing-host-update-and-native-publication-boundary)).
- The production host update still refuses position and bearing changes. G1–G5 are
  approved but #189's lab measurement and implementation are open
  ([geometry](SHARED_GEOMETRY_UPDATE.md)).
- The MissionApi contents PUT accepts `uids`, but only associates an identifier that
  already exists on the server. It does not ingest a CoT event
  ([research table](DATA_SYNC_INTEGRATION.md#2-server-mission-api-is-a-separate-evidenced-candidate)).
  No REST route that writes CoT has been identified, and TMM does not guess one.

## SDK check — 2026-11-18

`javap` on the supplied ATAK-CIV 5.6.0.24 `main.jar`. Nothing was executed.

- No class whose name contains `datasync`, `missionapi` or `feed` (count 0). Mission
  classes are mission-package transfer only. There is no callable Data Sync publish.
- `CommsMapComponent`: `sendCoTToServersByMission(String, String, CotEvent)`,
  `sendCoTToServersOnly(String, CotEvent)`, `sendCoTToEndpoint(CotEvent, String)`,
  `sendCoTInternally(CotEvent, Bundle)`, `getAllPortsBundle()`. All return void.
- `MapItem.persist(MapEventDispatcher, Bundle, Class<?>)` and `refresh(...)`;
  `PointMapItem.setPoint(GeoPoint)` and `setPoint(GeoPointMetaData)`;
  `OpaqueHandler.toMarkerMetadata(MapItem, CotEvent, CotDetail)`. These are the
  methods the existing host-update path and G2 use.
- `TakHttpClient.execute(HttpRequestBase)`, `execute(HttpRequestBase, boolean)` and
  `execute(HttpRequestBase, AtakAuthenticationCredentials)`, as W1 records.

## Options

| | (a) Native handoff only | (b) M only in the container | (c) Native handoff + M in the container | (d) `sendCoTToServersByMission` |
|---|---|---|---|---|
| Non-TMM ATAK users | Native map item at the device position, with label | **No map item.** Only a `.tmmi` file they cannot open | As (a). The photo is a `.tmmi` file they cannot open | Possibly a map item; durable feed membership unproven |
| Destination enforced | No. Data Sync decides | Yes, W2 | M: no. Photo: yes | Unknown; interface key is checked, mission routing is not proven to persist |
| Prompts | TMM preview; native Publish (update) or Add Items/Done (new) | TMM preview only | As (a), photo adds none | TMM preview only, if it works |
| Device without photo | Works | **Cannot publish** (container needs a PNG) | Works, metadata only | Works |
| Status-only update | One host update, no upload | A new container with the same PNG each time; feed grows toward the 256-resource cap | Host update only, photo stays current (U4 rule 2) | One send |
| U3 | Unchanged: R read from CoT | Receivers must read M from containers; with several containers per device there is no current M without ordering by time, which U3 forbids | Unchanged | Unchanged if membership is durable |
| U4, photo bound to M | Not applicable | Circular: the photo defines M | Rule 1 binds when the container's M equals the feed's M | Not applicable |
| Snapshot reader change | None | New: containers become a metadata source | None | None |
| Evidence | v28 round trip on 5.8, signed clients | W5 lab C1–C12 on 5.8 | Both | Signature and static trace only ([gates](CONFIGURED_PUBLICATION_GATES.md)) |
| Unverified on 5.6 | Native publish of the TMM payload, latest-by-UID, snapshot GETs | Upload, association, removal | Both | Everything |
| Gated decisions | D2–D4 changes, real data | Reader change, new metadata source, no-photo gap | D2–D4 changes, real data, ordering | New sender, D1 reversal, full probe |

(b) fails two product needs outright: devices without a photo and non-TMM users. It
also breaks U3's rule that time never picks a winner. (d) has no runtime evidence.

## Recommendation

**Option (c).** It is what W1 already says: metadata on the native route, photo by
TMM. M stays authoritative in the feed's CoT member; the container's M is used only
for binding. The receive side, all formats and U1–U8 stay unchanged. The cost is that
the metadata destination cannot be enforced, so success is reported from readback.

## Decisions to approve

### M1 — Route

Metadata goes out through the existing reviewed host copy (create) or host update,
then native Data Sync. The photo goes out under W1–W4 with the same M in its
container. Receivers keep reading current M only from CoT members. (b) and (d) are
not built. **Changes:** none of D1 (it confirms D1). Extends the route beyond the trial.

### M2 — Availability: the followed feed replaces trial consent

Publicera is available while following is on (ADR 0013) and the followed profile is
connected. The source is the U2 followed profile; there is no separate selection.
The trial's single-stream rule stays, with the followed profile's stored connect
string in place of the trial selection. Freshness is rechecked at preview and
immediately before the host write; any change refuses. Each Publicera needs one tap
and one confirmation of a fresh preview (W3). The four consent statements are not
shown. The #178 trial mode stays as it is for synthetic lab use.
**Changes:** D3 (offline requirement lifted while following, not only during trial
consent); D4 (no per-session statements in ordinary use); ADR 0013's "publication
consent under #178 stays session-only and is not affected" and U2's same sentence;
ADR 0009's "do not register domain drafts as normal ATAK markers or add any
transport" for a followed-feed session.

### M3 — Destination and wording

Accept that Data Sync decides where metadata goes. The preview names the followed
feed and says: the native step decides the destination; choose only this feed.
TMM never asks the user to turn Auto-publish on. For a new device the preview lists
the native Add Items steps. After the write, the device shows **Ej publicerad**
until a fetch of the followed feed reads back exactly the published M (U3, R = L).
Only then does it show **Publicerad**. TMM never claims that other feeds did not
receive it. **Changes:** D2 (no one-feed test profile required); the trial wording
rule "never `Delad`, `Levererad` or a single-feed claim" is replaced by readback-based
**Publicerad** for the followed feed only.

### M4 — Real data

Ordinary Publicera may carry real data only after the #252 trial on the real 5.6
server gives go, and the owner records that decision. Until then the candidate is for
synthetic data. **Changes:** D2's "synthetic data only" and consent statement 1, at
that point only. Per AGENTS.md a human decides operational data sharing.

### M5 — Order of metadata and photo

1. If M changed, do the host write first. If nothing changed, skip it (existing reuse).
2. If a new photo is attached, TMM reads the followed feed's current M for the UUID
   once, with the existing GET. Only if it equals the published M does TMM upload the
   container (W1). Otherwise it uploads nothing and shows **Metadata ännu inte i
   feeden, tryck Publicera igen**. A later tap skips the unchanged host write.
3. No photo, or an unchanged photo: metadata only, no upload, no new feed resource.

This keeps a photo from being bound to M that never reached the feed, and keeps the
image bound by U4 rule 1. Alternative: upload right after the host write and rely on
U4 rule 2 and W4; then a new photo can show next to old metadata.
**Changes:** none of D1–D5; it fixes W1's order.

### M6 — Position and bearing

Position and bearing use the approved G1–G5 path, not a new one. #243 includes
geometry only after #189's measurement and implementation. Until then Publicera
refuses a position or bearing change with a clear message and never silently drops it.
**Changes:** the trial's refusal of position and bearing changes, as G1 already approved.

### M7 — Verification before a candidate

Lab, synthetic data, two standard clients, 5.8 unless a 5.6 package exists:

1. A client that did not create the device updates it (natively received item, or a
   new TMM host copy of the same UUID). Record which works; no duplicate UUID.
2. The native update prompt for an item in one feed; and a second subscribed feed as
   the negative control (#178 control 5).
3. Native **Nej**: device stays **Ej publicerad**; no photo is uploaded (M5).
4. Status-only, photo-only and no-photo devices; U4 rule 1 binding on the receiver.
5. #178 controls 2, 6, 7 and 8 under M2's gate.
6. Repeat 1–4 on the real 5.6 server in the #252 trial.

## What does not change

U1–U8, W1–W5, inbox format 1, local v2, stage v1, registry v1, container v1, sync
state v1, the snapshot reader and fetching (GET only). No automatic or background
publication, no queue, no retry, no receipts, no CoT diagnostic tools.

## Prerequisites outside TMM

The deployer joins the followed feed once in native Data Sync. **Följ feed** does not
subscribe natively (U8). The server owner confirms the feed's default role (#241).

## Verification actually run

Reading of the listed documents and issues #178, #25, #189, #241, #243 and #251, plus
the `javap` checks above. No build, emulator, server, phone or signing step was run.
