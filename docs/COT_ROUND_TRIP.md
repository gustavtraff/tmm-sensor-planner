# Synthetic CoT round-trip experiment (Issue #5)

Status: SDK experiment; not a production publishing or import implementation.
Target: API 5.6.0.CIV, supplied SDK developer hosts 5.6.0.24 and the isolated
TAK Server 5.8-RELEASE-92 lab described in [TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md).

## Scope and boundaries

The development APK adds a separate **TMM CoT-labb** tool. Open it on both clients
before testing: this registers the synthetic detail handler and transport observer.
The action list scrolls; **Visa labbdiagnostik** is the seventh action and may start
outside the visible list. Its counters expose filter stages without raw event logs.
The existing local feature-layer experiment remains separate. No real draft is
read by the CoT tool, and preparing a fixture creates no root MapItem.

Only the fixed Mini, Scout, PIR and Gateway fixtures at synthetic 0/0 can be sent.
The tool requires airplane mode, disabled Wi-Fi, exactly one enabled stream and a
connected `127.0.0.1:8089:ssl` lab server. Incoming fixtures must identify the same
stream. Guest firewall isolation and the ADB tunnel remain required harness parts;
Android's offline flags alone do not prove transport isolation.

A custom picker selects one individual contact. A second scrollable screen shows
its name/UID, server-only channel, object UUID, position and every fixture field.
Only **Skicka syntetiskt prov** calls the external dispatcher. Cancel/back do not
call it. Connectivity and the recipient are checked again at that final action.
There is no broadcast choice, null recipient, background resend or shared-state flag.

## Experimental payload

Root `event.uid` is a canonical UUID and `event.type` is the ordinary test waypoint
`b-m-p-w`. The point is 0/0, HAE 0, CE/LE 100; these envelope values are validated.
Event/start/stale timestamps are transport values, refreshed for each send with a
one-hour stale interval. They are separate from the draft timestamps below.

The `detail/tmmProbe` tag carries an `experiment=issue-5` discriminator and these
fixed schema-1 fixture fields: displayId, type, status, owner, createdAt, updatedAt,
deployedAt, comment, bearingDeg, revision and imageIncluded. Owner/comment exercise
Unicode, ampersands, angle brackets and quotes. Scout/PIR carry bearing 359.125;
Mini/Gateway carry an empty bearing. Status is ACTIVE, revision is 7, and the
metadata timestamps are synthetic January 2026 values.

This tag is a disposable experiment, **not the accepted production CoT schema**.
Local imageRef/path/blob is never included. Identity/conflict fields not yet in the
core model are outside this test. Unknown incoming details are not forwarded.

The SDK XML parse/serialize self-check runs for all four fixtures on tool startup.
Negative self-checks reject a future schema version, a local image reference and a
changed position. Received fixtures must match every expected field before display.
The handler stores only validated canonical fixture XML in a private marker metadata
key. On explicit view/return, the tool requires exactly one root marker with that
UUID, calls the host's actual marker exporter and validates the exported envelope
and custom detail. Only then does it reconstruct the approved fields for return.
This avoids concealing export loss by filling missing fields from a separate cache.

## Verified SDK APIs and limitations

Signatures and relevant routing behavior were inspected in the supplied `main.jar`
with JDK 17 javap and checked against its local Javadocs. No SDK code/binary is in Git.

- `Contacts.getAllContacts()` / `getContactByUuid()` and `IndividualContact` provide
  the explicit picker; the self contact is excluded.
- `TAKServerListener.getServers()` and `TAKServer.isEnabled/isStream/isConnected`
  provide stream preflight. `getConnectString()` identifies the exact lab stream.
- `CotMapComponent.getExternalDispatcher().dispatch(event, bundle, method)` reads
  `bundle["toUIDs"]`. This probe always supplies one nonempty UID and
  `com.atakmap.commoncommo.CoTSendMethod.TAK_SERVER`.
- A null recipient array takes the broadcast path. Do not use it as a default.
- The dispatch return collection is **not treated as a delivery acknowledgment**.
  Supplied provider bytecode constructs a separate native-contact vector while
  the original UID collection may remain unchanged; the documented unreachable
  list must not be used to declare delivery success or failure without evidence.
- `CommsMapComponent.registerCommsLogger()` receives events independently of normal
  marker import. The observer filters the exact stream and fixture tag in memory;
  it writes no raw event or recipient data to application logs. Its `server` argument
  resolves the native stream ID back to the original configured connect string.
- `CotDetailManager.registerHandler()` / `CotDetailHandler` handle the synthetic
  marker metadata. `CotEventFactory.createCotEvent(MapItem)` invokes detail export.
- `MapGroup.getItemsRecursive()` supplies the actual duplicate-marker count; the
  valid-arrival counter in the dialog counts messages, not unique objects.
- The logger and detail handler are unregistered, and the dialog closed, on unload.

After dispatch, the UI says **Sändningsanrop utfört – leverans obekräftad**. Exceptions
also leave delivery unconfirmed. A missing connection/recipient prevents dispatch
and says **Inte skickat**. These statuses do not assert server or recipient receipt.

## Lab character encoding

The first transport attempt exposed a lab setup defect: the minimal CA/JDK-based
server container started Java 17 with `file.encoding=ANSI_X3.4-1968`, while PostgreSQL
used UTF8. The comment’s `åäö` arrived in stored server metadata as `???`; the receiver
correctly rejected it. The strict equality check was retained. The private lab
startup now sets `LANG=C.UTF-8` and `-Dfile.encoding=UTF-8` for every server JVM.
Verify the runtime property and repeat the Unicode control before accepting results.
This is a correction to the custom lab image, not a plugin encoding workaround.

## Repeat the experiment

1. Start only the isolated lab services and two test guests, reapply guest egress
   isolation and ADB tunnels, and verify mutual TLS and both green ATAK indicators.
2. Install the exact development APK on both SDK hosts. Load the plugin and open
   TMM CoT-labb on both clients; leave B ready to receive. On the first B installation,
   Plugin Manager → Sync Now refreshed its local index and exposed the installed
   plugin for loading; no external feed was reachable through the guest firewall.
3. Prepare a fixture on A, select B and record its UUID from the complete review.
   Cancel and verify no record for that UUID on B/server/application observation.
4. Repeat with active confirmation. On B, use **Visa mottaget prov** to require one
   imported marker and validate its actual re-export, including UUID and all fields.
5. Use **Återsänd mottaget prov**, select A and confirm. Validate A's received marker
   export against the original UUID and fields. Repeat for all four device types.
6. Resend an existing UUID and verify the marker count remains one on each client.
7. Prepare a review while connected, remove the lab connection before confirmation,
   then confirm. Require an explicit not-sent result and no receiver/server event.
   Restore the connection and run a positive control to validate observation.
8. Test unload/reload and listener cleanup. Preserve only synthetic private evidence
   locally, stop task-owned lab processes and document exact results and limits.

Use actual recipient and server records as delivery evidence. A targeted event is
not necessarily visible to an unrelated subscriber; an empty observer capture is
not proof that a targeted send did not happen.

## Recorded result — 2026-10-15–16 Europe/Stockholm

Tested APK: **0.1.0-cot-probe.1**, versionCode **4**, Android Debug signature.
SHA-256: `c4f1661b65cc90e115e6b50c68ad72d27ba1b90818e80db41a2c01a6dc016bf6`.
Source working tree was based on merged main `eaa8f25`. Both clients used the SDK
**5.6.0.24** developer host on separate API 35 arm64 emulators; the isolated server
was **5.8-RELEASE-92**, with UTF-8 verified after the setup correction above.

The run was interrupted and resumed; these are separate per-fixture observations,
not one continuous capture window. All times in this table are **2026-10-15 UTC**.

| Fixture | A → B server record/time | B → A server record/time | Recipient result |
|---|---|---|---|
| Mini | 225 / 16:28:09 | 228 / 16:30:04 | Actual host exports verified on B and A |
| Scout | 382 / 21:41:11 | 383 / 21:42:03 | Same, including bearing 359.125 |
| PIR | 388 / 22:19:31 | 389 / 22:20:11 | Same, including bearing 359.125 |
| Gateway | 390 / 22:20:49 | 393 / 22:21:25 | Same, with no applicable bearing |

Every export required the original UUID and exactly one matching root marker.
An independent query checked **all 13 custom attributes**, waypoint type and the
complete point envelope in every successful server record. Swedish characters,
XML special characters, metadata timestamps and revision remained intact. Every
record had exactly one intended destination, TOUR (B) or PLANT (A).

| Additional check | Observed result |
|---|---|
| Repeated UUID | Gateway resent A → B → A; records 394/395. Exactly one actual marker remained on each host, with intact metadata |
| Cancel final review | UUID `cef27bc5-d8d5-4a1f-a741-c0b14dd37523`: zero server records; B still showed the preceding Gateway |
| Disconnect after review | The task-owned relay was stopped while the review stayed open. A's indicator became red; confirmation showed `Inte skickat: anslutning eller mottagare saknas`. UUID `a5358ca0-95be-4493-bd64-ddb6439fc03c` had zero server records |
| Recovery and lifecycle | Relay restored; A unloaded/reloaded the plugin and opened a fresh zero-arrival tool. One explicit B → A control produced one arrival, record 403 at 22:28:21, and a verified single-marker export |
| Negative checks after recovery | Both negative UUIDs still had zero server records after the positive recovery control |
| Local checks | Core tests and three auth-helper tests passed; SDK assemble/lint passed with zero errors and two existing warnings; final APK signature/discovery/permission/packaging checks passed |

Private evidence under ignored `private-data/cot-round-trip/` includes complete
review/received UI XML, per-type summaries, server JSON, encoding properties,
negative-case UUIDs/toasts and lifecycle results. Targeted sends were not assumed
to appear at the unrelated observer; recipient exports and server records provide
the positive evidence. No raw evidence, SDK material, certificate or APK is attached
to GitHub. The exact tested APK is retained privately.

## Conclusion and limits

The bounded E0-04 feasibility experiment passed: one selected recipient and active
confirmation, semantic metadata/UUID round-trip through actual host markers,
repeat-UUID behavior, cancellation and explicit rejection of a disconnected send.
This supports the tested SDK route; it does not implement production sharing.

The schema/tag, marker metadata key and fixture UI remain experimental. No domain
draft, persistence, image transport, Data Sync or conflict/update policy is added.
Transport handoff remains unconfirmed until separately observed; no SDK return value
is treated as recipient acknowledgment. Standard-app signing, the actual 5.6.0.12
phone, operational server configuration and companion-plugin coexistence are untested.
Only the tested Swedish/XML characters and fixed fixture fields are established here.

Both test emulators and all task-owned lab services were stopped after preserving
evidence. Local images, the database volume and private configuration remain for
repeat testing.
