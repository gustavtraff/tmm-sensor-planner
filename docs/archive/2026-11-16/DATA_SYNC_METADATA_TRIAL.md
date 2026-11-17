# Data Sync metadata selection trial — 2026-10-24

Tracking: #25, with #14/#15. **Result: the canonical waypoint fixture could not be
selected for feed publication. A separate native-marker diagnostic preserved all
15 custom TMM fields through the chosen feed to a recipient export.** This narrows
the integration problem; it does not complete the TMM round trip or approve a
production representation, API, publishing adapter or durable importer.

## Candidate and isolation

The owner merged [the automatic-publication privacy evidence](DATA_SYNC_FEED_VERIFICATION.md)
in PR #82 and authorized the next experiment. Source baseline:
`7c375d3aed6b120ede0a2f2315b0988026547e4a`. No app, codec, checker, storage,
manifest, production transport or build input was changed in this trial.

The same two standard ATAK-CIV **5.6.0.25 (238484ee)** / Android **15, API 35**
clients, signed TMM **versionCode 15**, Data Sync **3.7.4 (58746181) [5.6.0]** and
isolated TAK Server **5.8-RELEASE-92** were reused. Exact APK/signing hashes are in
[the previous report](DATA_SYNC_FEED_VERIFICATION.md) and its linked inventory.
Guest IPv4/IPv6 egress allowed loopback only; only CoT/TLS 8089 and Mission HTTPS
8443 were reversed through the fixed loopback relay. No operational phone, profile,
feed, image or sensor location was used. Normal plugin loading and validating TLS
were retained. The delayed initial API startup was not counted as a working route;
subsequent authenticated GETs and fresh routed controls established readiness.

A's existing synthetic selected feed was `tmm-ds25-selected-20261023`, with
three retained ordinary controls. Its auto-publication checkbox was verified off.
B created the separate empty `tmm-ds25-nontarget-20261024` at
**15:58:25.990 UTC**. Both were public feeds in the isolated synthetic group;
this is destination-membership evidence, not an access-control test.

## Fixed canonical fixture

The unchanged [offline probe](DATA_SYNC_PROBE.md) generated fresh fixtures at
**15:47:45.405685 UTC**. The PIR case has UUID
`00000000-0000-4000-8000-000000000003`, outer type `b-m-p-w` and the existing
experimental `tmmShareLab` payload. It includes precise position and bearing,
Planned status, Unicode owner/comment, newline/tab/escaping and nanosecond UTC
metadata times. It contains no image, local revision, provider URI or image handle.

The private test copy added only `<archive/>`. The supplied 5.6.0.24 SDK's
`ArchiveDetailHandler` was inspected with `javap`: it maps that detail to marker
archive metadata and emits it on export. This is observed SDK behavior, not a
Data Sync integration promise. Input SHA-256:
`2a69b9dd00581b0af628b967cc047b592cf3197f472c2f83b194c743deb76ffd`;
copy SHA-256:
`711978b84d676c57408b81d21ef8278556534bd86ccc2699d85dd904f2457f9d`.
The unchanged probe checker returned MATCH for that copy before import, proving
only fixture validity.

The documented manual paths are Map Select and Overlays (Data Sync's bundled
user guide, page 6); its supported-content list on page 7 does not promise arbitrary
custom point selection. Manual hashes and source/SDK provenance are recorded in
[the integration investigation](DATA_SYNC_INTEGRATION.md).

## What actually happened

| Check | Observation |
|---|---|
| Local canonical import | ATAK's Local SD → Copy path logged the synthetic UID as imported. The object was later visible on the map and by name in the overlapping-object chooser. Import logging alone was insufficient evidence of usable feed selection. |
| Before active feed confirmation | Canonical UID was absent from selected-feed membership/CoT, new server rows queried for that UID and the independent routed subscriber. No publication was inferred from import. |
| Overlay selection | The canonical waypoint was absent from the inspected filtered hierarchy and label search. The native diagnostic below was selectable in that same hierarchy. The earlier suspicion of failed import was superseded by actual map/chooser evidence. |
| Alternate Map Select | The overlapping-object chooser listed the canonical PIR and the separate native diagnostic. Choosing the canonical PIR produced the visible toast **“Cannot select this item.”** |
| Done / retry | No canonical feed association appeared after Done, a later settled server read or the repeated selection. The observed rejection, not an empty feed alone, is the selection blocker. |
| Native diagnostic cancellation | Exactly the diagnostic row's checkbox was selected; closing before Done left it absent from both feeds. This is the successful selected-item cancellation check. Cancelling a rejected canonical selection is not an equivalent pass. |
| Native diagnostic confirmation | Explicitly checking only that row and choosing Done added it to the selected feed at **16:02:58 UTC**. B's actual Data Sync list and item overview showed its UID. The non-target feed stayed empty. |
| Recipient export | B's own item menu → Export created a ZIP containing one CoT event plus its manifest. The export was read from B and compared with the source, independently of the sender/server copies. |

The diagnostic was deliberately distinct: UUID
`00000000-0000-4000-8000-000000000103`, outer type `a-f-G` and contact label
`DS25-DIAGNOSTIC-NATIVE`; its **15 custom payload attributes were unchanged** from
the PIR fixture. This is a native-marker diagnostic, not a silent substitution of
the canonical event or a proposed friendly-unit classification for TMM devices.
The host import path, selected checkbox and actual returned type/UID were recorded.

At **16:05:11.657 UTC**, B's exported event retained that diagnostic UUID/type,
all **15 exact payload attributes** and the rounded outer point. This includes
precise embedded coordinates, degree bearing, status, owner/comment and UTC times.
Recipient ZIP SHA-256:
`ed1e45987b78999ed75fb9dce01788e70777a3b85816a05d653203af3dda8fa0`.
The original canonical checker correctly rejected this diagnostic event because
its UID/type differ; neither the checker nor expected canonical values were relaxed.
No source/server file was passed off as a recipient export.

This Data Sync export establishes recipient-held feed content, not a re-export of
B's live root MapItem, durable TmmDraft import, conflict persistence or a receipt
acknowledgment. A visible native icon alone would not establish these properties.

## Private-store and route cross-check

The final snapshot at **16:11:11.435 UTC** was byte-identical to the pre-experiment
snapshot: SHA-256
`4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
No private draft was converted or edited. An accumulated scan after prior server
row 1482 covered **85 rows**, **13,143 subscriber bytes / 20 complete events**,
**25 private canaries / five positions** and **zero matches or position-parse
errors** at **16:11:11.991 UTC**. This row bound also includes prior cleanup rows;
it is not an exact start-of-experiment counter. The chosen feed's header, CoT and
recent changes were additionally searched. The non-target membership check was
separate. This is a bounded absence check, not unrestricted-network privacy.

Fresh independent TLS controls `METADATA-BEFORE-20261024` and
`METADATA-AFTER-20261024` were sent with distinct UIDs and held connected for
20 seconds each. Both reached the decrypted observer; the after-control was also
found in B's actual global object search after the metadata operations. The earlier
native feed controls and diagnostic are additional positive evidence, not a
replacement for the failed canonical selection.

## Interpretation and next work

The result disproves treating the current `b-m-p-w` file as an already usable
Data Sync selection representation in this combination. It does not establish why
Data Sync rejects it or that every waypoint, SDK host or future version will fail.
No private Data Sync APK implementation was decompiled and no guessed action/API
was invoked. A generic “Data Sync loses custom fields” conclusion is also unsupported:
the separate native diagnostic retained the complete payload through recipient export.

Next under #25 is a **reviewed selectable publication representation**, tracked
in [#83](https://github.com/gustavtraff/tmm-sensor-planner/issues/83), using
version-applicable documented host/export surfaces and an intended Data Sync
selection contract. Ask TAK which custom point types/Exportable targets are
supported and how a third-party plugin should expose one for chosen-feed selection.
Use this reproducible `b-m-p-w` rejection and successful `a-f-G` diagnostic to make
the support question concrete. Do not silently change the wire type/checker just
to turn this trial green.

Creating a normal root MapItem is itself a privacy/destination decision: other
subscribed feeds may auto-publish new content. Therefore do not create such a copy
before active review or assume it will be limited to the subsequently chosen feed.
The next representation must be tested with a second feed's auto-publication on,
including Cancel, before it can become a production adapter. The prior private-
feature-layer privacy pass does not establish safety of an ordinary publication copy.

Read-only denial, canonical duplicate/update/reconnect, receiver restart, concurrent
edits and removal semantics were **not run**, because the canonical baseline could
not be selected. They remain required after a representation passes selection;
prior private-draft reconnect tests do not substitute for publication recovery.
No image delivery was attempted. #25 and #14/#15/#26 remain open; no new signing
ZIP, online phone test or release is justified by this evidence alone.

## Verification and retained evidence

Actually run: fixture creation, positive source-copy check, expected negative
canonical check on the distinct native diagnostic, exact recipient XML comparison,
UI/readback/GET/observer checks above. No product/domain/build input changed, so no
core, SDK or image unit suite was rerun. CI was not awaited.

Raw UI XML, screenshots, fixture variants, SDK inspection output, TLS/server data,
recipient package and private snapshots remain ignored under
`private-data/datasync-metadata-25/`. API bodies are retained privately; the helper
was restricted to printing status/length after a lab feed response included an
invitation token. No operational credential was used, and no raw response or token
is included in this documentation or Git.

## Cleanup

Both task emulators were stopped after removing their two task reverse tunnels
and disabling simulated Wi-Fi. The independent observer and only the task relay,
server and database containers were stopped; the database used a clean
`pg_ctl -m fast -w stop`. Volumes and private evidence were retained. Both final
Android crash buffers contained zero FATAL EXCEPTION entries. Auto-publication
remained off; Publish Prompt was not disabled in this run.

An attempted native-UI deletion of the empty task-created non-target feed was not
confirmed by the subsequent server read. It is therefore recorded as retained,
not a passing deletion/removal test. The selected feed and diagnostic also remain
in the stopped isolated database for reproducibility. No other workload, AVD,
phone or operational server was modified.
