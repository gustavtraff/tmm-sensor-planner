# Data Sync and attachment feasibility

Tracking: [Issue #6](https://github.com/gustavtraff/tmm-sensor-planner/issues/6).
Investigation date: 2026-10-16. Target: ATAK-CIV API 5.6.0, supplied SDK full build
5.6.0.24. This is a feasibility investigation, not an implemented image-sharing feature.

## Follow-up — 2026-10-23

[DATA_SYNC_INTEGRATION.md](DATA_SYNC_INTEGRATION.md) now identifies bundled Data
Sync user manuals, exact-SDK export/detail surfaces and pinned official server
Mission API sources. These give concrete user-workflow/server candidates, not a
verified callable Data Sync Android API. [ADR 0010](decisions/0010-investigate-data-sync-workflows.md)
proposes the next experiment while retaining ADR 0005's production deferral.
The historical SDK loading failure below is not the latest standard-client result;
see [STANDARD_CLIENT_VERIFICATION.md](STANDARD_CLIENT_VERIFICATION.md).

## Data Sync decision

Defer direct Data Sync integration. The supplied `main.jar`, `atak-javadoc.jar`
and sample inventory do not provide a Data Sync plugin integration contract.
At the initial investigation, no separate Data Sync API documentation or installation
package was found in the inspected Downloads inventory. Later on 2026-10-16, the
owner supplied Data Sync 3.7.4 [5.6.0] and an isolated SDK loading attempt was made;
see [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md). The installer does not
provide a documented TMM integration contract, so direct integration remains deferred.

This is a limit of the available evidence, not a claim that no such API exists.
Do not reverse-engineer private plugin internals, use guessed broadcasts, add a
direct server REST client or treat ordinary CoT as feed publication. Revisit when
official documentation for the intended plugin version and an authorized test
package are available. Then test feed selection, permissions, cancellation,
updates, attachments and removal with two subscribed clients.

The bounded [CoT round trip](COT_ROUND_TRIP.md) remains evidence for an explicit
individual-recipient route only. No feed is created or modified by this project.

## Attachment APIs inspected in the exact SDK

Primary sources are the supplied SDK Javadoc and `main.jar`, identified in
[DEVELOPMENT.md](DEVELOPMENT.md). SDK files and extracted documentation stay outside Git.

| API | Documented behavior / limitation |
|---|---|
| `MissionPackageApi.CreateTempManifest(name, bImport, bDelete, onReceiveAction)` | Creates a temporary package manifest and specifies receiver import/deletion instructions. Temporary storage is periodically cleaned; it is not draft storage. |
| `MissionPackageManifest.addFile(File, contentType, attachedUID)` | Adds file bytes, optionally associated with a map-item UID. A local path is an input to packaging, not a remotely usable image reference. |
| `MissionPackageManifest.addMapItem(uid)` | Adds a map item; its export and attachment behavior need separate validation before use with TMM drafts. |
| `MissionPackageManifest.toXml(localManifest)` | `true` includes local data; `false` produces a package manifest. Inspection of this SDK's implementation confirms removal of `localpath` and `isCoT` content parameters for the latter. This is not a general sanitizer for arbitrary added parameters. |
| `MissionPackageApi.Save(context, manifest, callbackClass)` | Requests compression/save. Its boolean means the intent was populated and broadcast, not that a ZIP was saved or delivered. |
| `MissionPackageApi.SendUIDs(context, manifest, callbackClass, toUIDs, sendOnly)` | Requests compression/send to specified contacts; omitted recipients may open the stock selector. Never use an empty/default destination as a TMM product workflow. The return value is not a recipient acknowledgment. |
| `SendDialog.Builder.addAttachment(File, contentType, MapItem)` | Documented attachment candidate, with recipient/task callbacks. It does not itself prove explicit TMM review, safe draft handling or delivery. |

No new production adapter or plugin dependency is introduced. The test below uses
ATAK's stock Data Packages UI, not these APIs through a new TMM toolbar control.

## Synthetic fixture

Generate a fixed standalone image package without reading any input image:

```sh
mkdir -p private-data/attachment-test
python3 scripts/create-attachment-fixture.py private-data/attachment-test/TMM-IMAGE-PROBE.zip
```

The output path must not exist. The generator uses only Python's standard library.
It reopens the ZIP, checks its entries and XML, validates PNG chunk CRCs and
decompressed pixel size, and rejects local-path/URI fields in its own fixed manifest.
It is a fixture generator, not a validator or importer for untrusted packages.

- Package UID: `08a051c9-320b-4851-a31c-ff1e7ea65d87`.
- Name: `TMM-IMAGE-PROBE`; two entries: `MANIFEST/manifest.xml` and `fixture/checker.png`.
- PNG: 64 × 64 red/cyan checkerboard, 210 bytes, only IHDR/IDAT/IEND chunks.
- Image SHA-256: `d6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5`.
- No map item, coordinates, external source file, EXIF, text, URL, `imageRef` or
  `localpath`. Import enabled; automatic package deletion disabled for inspection.

The manifest layout was checked against the exact SDK's serialization annotations
and import-instruction constants. Runtime acceptance must still be checked below.
The fixed UID is for this disposable test only; it is not a production identity policy.

## Isolated two-client test

Use the same two SDK developer 5.6.0.24 clients and TAK Server 5.8-RELEASE-92 as
[TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md). Retain guest IPv4/IPv6 egress blocks and
airplane mode. The only allowed network path is guest loopback via ADB to the
task-owned local relay. In addition to the CoT TLS tunnel, file transfer may need
guest `127.0.0.1:8443` forwarded to the relay's host loopback HTTPS port. Keep TLS
hostname and CA verification enabled. Never use operational server profiles.

1. Record that B has neither the fixture package nor `checker.png` before import/send.
2. Push the generated ZIP only to A's Download directory. Import using Tools →
   Import → Local SD → select ZIP → OK → Copy. Inspect Data Packages locally.
   If the PNG placement tool starts, choose End and confirm Cancel without
   selecting a position. Do the same on B after reception, before opening the image.
3. Review the package contents; select only the other lab client. Cancel once and
   verify B still has no fixture. Then repeat and explicitly confirm sending.
4. On B, inspect the actual received package and extracted image. Compare image
   SHA-256 with the generator, inspect the transmitted manifest for local references,
   and open the image using the host's Open action. A sender success notification
   alone is insufficient.
5. Remove the test package through the host UI and check whether extracted bytes
   remain. Record actual behavior; do not promise secure erasure or remote withdrawal.
6. Stop both emulators and the three task-owned lab containers after testing.

### Results on 2026-10-16

Both clients used the existing `0.1.0-cot-probe.1` plugin; this experiment changed
no APK code. A was the synthetic contact PLANT; B was TOUR. No Data Sync plugin
was involved. UI automation used headless ADB hierarchy inspection and screenshots.

| Check | Observed result |
|---|---|
| Local import | Manifest accepted; Data Packages showed one 210-byte `checker.png`. |
| Cancel before Send | Only TOUR selected, Select All unchecked, picker dismissed without Send. B had no matching ZIP or extracted image before import and after cancellation. This checks recipient absence, not server upload absence. |
| Confirmed contact transfer | Selected only TOUR and pressed Send. B's actual package and extracted image appeared through the local lab route. |
| Byte integrity | B's ZIP was byte-identical to A's generated ZIP. Extracted image matched the 210-byte source and SHA-256 above. ZIP SHA-256: `9035ff536301b310d245fda0d65a58f30274c237d1ee179f515da9c4d416631b`. |
| Server evidence | Lab resource row 1, submission `2026-10-16T06:02:03.084Z`, 681 actual stored bytes identical to the generated and received ZIP; verified from stored bytes, not just the hash column. |
| Remote manifest | Exactly the expected relative entries; no `localpath`, `imageRef`, absolute storage paths or URI references. PNG CRC/decompressed-size checks also passed on the received ZIP. |
| Image viewing | B's package Open action launched an Android image viewer; the red/cyan checkerboard rendered correctly. This is not a TMM image-view UI test. |
| Recipient deletion | Chose Delete → Remove Contents → confirmed Remove Contents. Package row, ZIP and extracted PNG disappeared from B. A's ZIP and the server's 681-byte resource remained. No secure-erasure or remote-withdrawal claim. |
| Shutdown | Both task-owned emulators and the lab relay/server/database were stopped after testing. |

Two limitations were observed during setup. An initial fixture with
`onReceiveImport=false` extracted the file but did not remain visible in the stock
Data Packages list; the successful fixture enables import. Stock PNG import on
the successful path also started a map-placement tool on each client. Both were
explicitly cancelled without selecting any position. A production adapter must
choose and test its import behavior; a raw PNG import is not a private draft UI.

Raw UI evidence, generated packages, receipts and server bytes remain in ignored
`private-data/attachment-test/`. Only the synthetic generator and this sanitized
summary are versioned. The automated fixture check can be repeated without a
server; the two-client transfer result requires the actual lab procedure.

Local checks passed on the same date: `sh scripts/test.sh` with JDK 17.0.20.1+1,
the three auth-helper tests, fixture generation/reopening, refusal to overwrite
an existing file and rejection of a deliberately added `localpath` field. No
Android build or lint was rerun for this change; the APK and app source are unchanged.

## Product boundary and remaining gates

Local image support is still Issue #13. The opaque local `imageRef` must never be
copied into CoT or treated as a shared attachment identifier. The existing CoT
fixture rejects `imageRef` and explicitly reports `imageIncluded=false`.

Keep production image transfer deferred until the image-size/EXIF policy in
Issue #8, object-to-attachment identity, review/cancellation, real file-transfer
failure handling, recipient acknowledgment, restart, replacement and deletion
are implemented and tested. A transferred standalone PNG does not establish these.
Before the first metadata-only TMM share, show **Image will not be shared** when
the local draft has an image. Never mark that image as delivered from a CoT send
or a Mission Package API boolean.

Data Sync feed publication, standard phone signing/coexistence, operational server
compatibility and release/distribution approval remain separate gates.

## Reviewed association contract follow-up — proposed 2026-10-28 (#26)

[IMAGE_SHARING_CONTRACT_PROPOSAL.md](IMAGE_SHARING_CONTRACT_PROPOSAL.md) separates
metadata receipt, received/verified PNG bytes, durable verified association and
explicit private image adoption. It maps this historical contact-package evidence
to its actual limits, defines proposed content/UUID/metadata binding and scoped
acknowledgment, and supplies synthetic examples/required acceptance. No new transport,
selected-feed image proof, recipient acknowledgment or runtime result is added.
The proposal does not authorize a contact-route substitute for selected-feed scope.
