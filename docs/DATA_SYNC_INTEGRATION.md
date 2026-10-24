# Data Sync integration investigation — 2026-10-23

Tracking: #25, with #4/#7 privacy/compatibility and #14/#15 metadata/import.
**Result:** there are concrete documented user and server workflows to investigate.
A supported callable third-party Data Sync 3.7.4 Android API remains unidentified.
No TMM feed integration, automatic publishing or new transport is implemented.
This is a source/SDK investigation; no server, emulator or phone was started.

## Scope and evidence

The owner confirms their existing server already works with Data Sync and 8S/HV
Reports. That establishes a useful reported baseline, not TMM custom-field support
or the identity/version of that server. No operational profile was requested/read.
The owner wants Data Sync as the collaboration workflow; investigate it before
finishing a different production sharing route. #4/#7 can proceed independently.

| Primary input | What was inspected | What it does not establish |
|---|---|---|
| Supplied official ATAK SDK, full build 5.6.0.24 | main.jar public/protected signatures, Javadoc and importexportexample/HelloWorld samples | An Android contract for the separate Data Sync plugin |
| Supplied Data Sync 3.7.4 (58746181), API 5.6.0 APK | Read-only ZIP asset inventory and both bundled user manuals, with complete relevant PDF pages reviewed as text | A developer SDK, support promise, or behavior of every setting in our exact runtime |
| Official TAK-Product-Center/Server source | MissionApi, test client and mission membership integration test at commit 5187abd46d827d37cfc5708805eced197a837e49 | Compatibility of this main-branch snapshot with the installed 5.8-RELEASE-92 lab or the owner's server |
| Official ATAK source | Exportable, CoT detail handlers and wrapper sources at commit 9f6893dd657feacc35ec5de03dad721c2e44170e | A stable Data Sync export target or automatically safe publication |
| OpenTAKServer project documentation | Mission API support from 1.4.0, SSL requirement and stated early-stage limitations | That the owner's server is OpenTAKServer or that its behavior matches official TAK Server |

Data Sync APK SHA-256:
`d785c5819d727796e56fcd0124bfd67dcbc8e53de9cc1ac0545601c1a17d54a0`.
The two assets are `assets/ATAK_Data Sync User Guide.pdf` (11 pages, SHA-256
`2c4afb4a238a8f12b0e42d9e19837b493bfd92c2bd723f9eef3a000a608f18f5`)
and `assets/usermanual.pdf` (15 pages, SHA-256
`02235764fe566f40d75ec01d86dd857772a9c4e4f044e796a21561ebb8a57c39`).
Only sanitized findings are committed; PDFs, extracted text, SDK classes and raw
research inputs remain private. No APK internals were decompiled or private action
names inferred. Public search did not identify a version-applicable developer
contract; lack of a search result does not establish absence of such an API.

## 1. Existing user-controlled Data Sync workflow

The 11-page guide describes selecting a feed, then adding items through map or
overlay selection (pages 4 and 6). It lists supported standard map-item families,
photos and files (page 7). It also describes publication of later edits, queued
offline publication on reconnect, and a conflict dialog (page 7). The 15-page
manual describes corresponding operations on pages 5, 8–10.

**Candidate, not verified:** expose only an explicitly reviewed publication copy
through an intended ATAK export/map-item surface, then let the user select the
feed and content through Data Sync's own UI. This avoids inventing a private
Data Sync broadcast or requiring an HTTP client just to choose a feed.

The guide does NOT promise selection of TMM's private feature layer, arbitrary
Exportable implementations, custom CoT types or preservation of every TMM detail.
The stock selection method therefore needs a small lab proof before TMM code is
built around it. Data Sync's own item conflict dialog is not proof of durable
TmmDraft storage or the project's field/conflict policy. Existing CoT lab markers
and byte-equal Mission Package transfers do not prove this path.

### Privacy condition for the parallel #4/#7 test

The guide describes a per-feed setting that automatically publishes newly created
map items (page 4; corresponding 15-page manual page 5). It also describes deferred
upload after reconnect (page 7). Do not turn a private draft into a normal ATAK
MapItem simply to make Data Sync discover it. Publication-copy creation itself may
cross the privacy boundary, even before a feed picker is opened or cancelled.

In the isolated lab, test private create/edit/move/image/delete with Data Sync
loaded and a subscribed feed, first with auto-publication disabled, then enabled.
Bracket each phase with normal routed positive controls and inspect recipient,
server and decrypted traffic. Check reconnect, plugin unload/restart and absence
of queued private publication; restore test settings afterward. Do not enable this
setting on an operational feed as an experiment. If the active Data Sync/standard
client setup is unavailable, record this specific phase as blocked, not passed.

## 2. Server Mission API is a separate, evidenced candidate

Official server source and its example test client expose the following concepts
under the `/Marti/api` context. These are research observations, not a frozen
client implementation contract for the installed lab server:

| Operation | Source surface | Important boundary |
|---|---|---|
| List/read missions | GET /missions and /missions/{name} | Apply authenticated groups/mission access; names require safe encoding |
| Subscribe/unsubscribe | PUT/DELETE /missions/{name}/subscription with client uid | Subscription identity/role/invitation/password rules matter |
| Associate content | PUT /missions/{name}/contents; JSON hashes/uids | Associates identifiers; not a complete custom CoT ingestion transaction |
| Read changes | GET /missions/{name}/changes with time-window options | Cursor/replay/gap behavior and durable import need definition |
| Remove associations | DELETE /missions/{name}/contents | Not equivalent to erasing every local, server or historical copy |

MissionApi checks MISSION_READ/MISSION_WRITE and group constraints. Some operations
also have GUID variants. Content association branches on the server's
missionBrokerUidAddsFromApi configuration. Never assume adding a UID uploads the
current complete TMM event, performs an atomic publish, restricts a prior ordinary
CoT send to one feed or acknowledges recipient persistence.

A direct Mission API adapter would introduce HTTPS authentication, host/profile
selection, credentials access, TLS trust, authorization and notification/reconnect
handling. It requires a separately reviewed architecture decision and version-
matched API verification. The official load-test helper disables hostname checking;
it is evidence of endpoint use, NOT a secure production-client template. Do not
copy its TLS override or credential-handling pattern into the plugin.

## 3. Exact SDK surface check

A private compile-only Java probe passed against the supplied main.jar plus
Android API 36 android.jar using the pinned JDK 17 toolchain. It verifies:

- Exportable.isSupported / toObjectOf with ExportFilters;
- MissionPackageExportWrapper construction and addUID;
- CotDetailHandler subclass signatures and CotDetailManager register/unregister.

The probe was never executed or installed; it sends/registers nothing. These
methods support investigating export and custom-detail import/export. They do not
identify Data Sync's target class, handler registration or feed-selection callback.
The initially considered ExportMarshalFactory name was absent from both the SDK
and the inspected public source inventory; it is not a proposed API.
A filename-name search found no DataSync/MissionApi/MissionService/MissionChange/
MissionContent classes in main.jar/Javadoc. This bounded check does not rule out
an integration under another name or a separately supplied developer package.

## Recommended next experiment and decision gate

1. Finish/review #4/#7 standard-binary connected-private evidence, including the
   auto-publication phase above. Coordinate lab ownership with that agent; this
   research branch does not alter shared AVDs, Docker, profiles or transport code.
2. Request a Data Sync developer contract/sample from TAK for 3.7.4 / ATAK 5.6.0.
   Ask which export targets, selection APIs and custom-detail import/export hooks
   are intended for third-party plugins. Do not block documentation research on
   account credentials or infer support from guessed class/action names.
3. In a separately reviewed, synthetic-only spike, first establish a normal stock
   marker baseline through Data Sync's own chosen-feed UI. Then test one explicitly
   reviewed TMM-like metadata object with a stable UUID and a second similar test
   feed to detect wrong-feed delivery. No image, original photo or private draft
   conversion. Inspect recipient CoT and custom fields, not only visible symbols.
4. Test Cancel/no dispatch, read-only denial, same UUID repeat, one update,
   disconnect/reconnect, receiver restart and concurrent edits. Include exact type,
   status, owner/comment, UTC times, degree-stored bearing/mil display and position.
   TMM's current lab-only detail schema must not become a production wire contract
   merely because an event displays. Images remain explicitly excluded (#26).
5. If stock Data Sync reliably handles the reviewed published representation,
   implement the smallest adapter plus durable import under #14/#15 after contract
   review. If not, evaluate a version-matched Mission API adapter in a new decision;
   do not silently replace the selected route or bypass TLS/mission permissions.

Exit evidence: exact versions/artifact hashes, contract citations, chosen-feed and
non-recipient observations, preserved custom data/UUID, no duplicate or private
publication, explicit failures, and receiver persistence. A sender success flag,
HTTP 200, visible marker or manual stock-marker pass alone is insufficient.

### Support question

For Data Sync 3.7.4 [ATAK-CIV 5.6.0], what is the supported third-party plugin
integration surface for selecting a feed and adding custom map items? Please
provide SDK/API documentation or sample code for export targets, CoT detail
preservation/import callbacks, update/conflict handling and feed permission errors.
If none is supported, is the server Mission API the intended integration route?

## Sources and reproducibility

- [Official server MissionApi, pinned source](https://github.com/TAK-Product-Center/Server/blob/5187abd46d827d37cfc5708805eced197a837e49/src/takserver-core/takserver-war/src/main/java/com/bbn/marti/sync/api/MissionApi.java): content association at lines 1550–1640; changes at 1750 onward; subscriptions at 2344 onward.
- [Official server example client, pinned](https://github.com/TAK-Product-Center/Server/blob/5187abd46d827d37cfc5708805eced197a837e49/src/testing/load_test/mission_api.py): client setup/TLS at 19–62; contents/subscription at 187–225. Read only; not run.
- [Official mission membership integration test, pinned](https://github.com/TAK-Product-Center/Server/blob/5187abd46d827d37cfc5708805eced197a837e49/src/takserver-core/src/integrationTest/java/com/bbn/marti/tests/missions/MissionAddRetrieveRemove.java). Read only; not run.
- [Official ATAK Exportable, pinned](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importexport/Exportable.java).
- [Official ATAK detail handler, pinned](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/detail/CotDetailHandler.java). Exact SDK signatures independently checked above.
- [OpenTAKServer Mission API documentation](https://docs.opentakserver.io/mission_api.html). This is the implementer's documentation, not a TAK Product Center support guarantee.
- Bundled manuals identified by APK/asset hashes and page numbers above. Extracted privately; no document redistribution.

No full APK build, core/image suite, runtime/network test, developer-account access,
signing upload, release or operational-server connection was performed. CI is not
awaited. The investigation does not close #25 or supersede the privacy gate.

## Offline experiment preparation (#77)

[DATA_SYNC_PROBE.md](DATA_SYNC_PROBE.md) provides a bounded fixture/returned-event
checker using the existing experimental core codec. It adds no publisher or runtime
Data Sync adapter. A match establishes payload preservation only, not feed selection,
privacy, receipt or durable import. Lab execution remains gated and coordinated.

## Runtime follow-up — 2026-10-24

The original investigation above was read/compile-only. The subsequent
[isolated-feed verification](DATA_SYNC_FEED_VERIFICATION.md) normally loaded the
pinned Data Sync APK on two standard 5.6.0.25 clients, demonstrated explicit and
automatic native-marker delivery, and exercised private TMM operations with
auto-publication off/on, reconnect and cold restart. No private canary exposure
was observed in its bounded checks. That report is evidence for human review;
custom TMM metadata round trip, destination/permission/conflict behavior and a
callable third-party Android API remain unverified. #25 stays open.

## Metadata selection follow-up — 2026-10-24

[DATA_SYNC_METADATA_TRIAL.md](DATA_SYNC_METADATA_TRIAL.md) records the next actual
experiment after PR #82: the canonical `b-m-p-w` publication fixture was visible
on the map but rejected by Data Sync's chooser. A separate native-marker control
retained its UUID and all 15 custom fields through the selected feed to B's export;
the non-target feed stayed empty and selected-item cancellation sent nothing.
This narrows the next task to a supported selectable representation and its privacy/
destination boundary. It proves neither the canonical TMM route nor durable import.

## Selectable sensor-point candidate — 2026-10-24

[The #83 trial](DATA_SYNC_SENSOR_TRIAL.md) verified a separate `b-m-p-s-p-loc`
PIR case through the documented manual Local SD / selected-feed Overlays workflow.
The SDK and pinned public importer register that type in Mission; actual standard
clients accepted selection and B's own feed export retained UUID and all 15 fields.
A second subscribed feed with auto-publication on/prompt off received normal
controls before/after, but not the imported/cancelled or explicitly target-published
sensor. The old rejected waypoint and friendly diagnostic expectations remain intact.

[ADR 0011](decisions/0011-sensor-point-data-sync-lab.md) scopes this lab candidate.
It is not a supported callable Data Sync Android API, direct Marker-construction
proof, production integration or durable import. No private plugin interface or
REST publisher was added. Review this evidence before the remaining #25 cases.
