# Signed SDK-import and native feed trial — 2026-10-25

Tracking: #14 / #83. **Bounded result: the signed plugin's actual SDK import
produced one verifiable sensor object, remained absent from both feeds before
native Done, and reached the intended feed and B's own export after Done.**
The unselected automatic comparison feed contained the native controls and no
sensor. This verifies the fixed synthetic PIR path on the lab combination; it
does not enable production draft publishing, durable ingress or image delivery.

## Candidate and setup

The [v16 experiment](PUBLICATION_IMPORT_PROBE.md) was built from source commit
`a2078a95138c7f2818705ed67dd3d185cb458c17`, subsequently merged through PR #95
(main `a3fadbb`). Later candidate documentation commits did not change build inputs.
The returned APK retains package `se.tmm.sensorplanner.plugin`, versionCode **16**,
versionName **0.1.0-publication-lab.1**, API target **5.6.0.CIV** and no requested
permissions. Despite its `app-civ-release-unsigned.apk` filename, apksigner verifies
the actual service signature:

- APK SHA-256: `0fee4f52c9674452120985fde7a2e0c1e5706cee07644ce418e6cff68b5ef4bb`.
- Signer certificate SHA-256: `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17`.
- Signer: TAK Product Center ATAK Untrusted Plugin Release.

Both existing Android **15 / API 35** arm64 guests ran standard ATAK-CIV
**5.6.0.25 (238484ee)**, Data Sync **3.7.4 (58746181) [5.6.0]**, and the same signed
v16 update. The existing isolated server was **TAK Server 5.8-RELEASE-92**.
No signature checking, CA/hostname validation or mutual TLS was disabled.
Only loopback IPv4/IPv6 egress and task reverses to the CoT/TLS and Mission HTTPS
ports were allowed. No operational server/profile or physical phone was used.

The callable path was exactly
`CotImporterManager.getInstance().importData(event, options)` with the plugin's own
`from=se.tmm.sensorplanner.plugin.publicationlab`. No StateSaver/MissionPackage
identity, serverFrom, external dispatcher or private Data Sync API was substituted.
The probe never reads a draft/inbox/image store. The legacy waypoint detail handler
was closed before this separate sensor experiment; actual host export preservation
was checked rather than implemented by a new sensor handler.

The fixed `sensor-pir` identity is
`00000000-0000-4000-8083-000000000003`, type `b-m-p-s-p-loc`. The unchanged independent
checker expects all 15 experimental metadata fields, exact inner values and the
SDK-rounded outer point. This is still a canary, not a production wire schema.

## Baseline and actual observations

Authenticated API readiness was obtained before the baseline. Initial startup TLS
EOFs were not counted as a pass. At **17:22:34 UTC**, the intended feed had four
members, comparison two, and neither contained the reused sensor UUID or event.
Existing history/log entries were retained and are not fresh-transfer evidence.
Client B was actually subscribed; intended-feed auto-publication was verified off.
Comparison auto-publication was enabled and Publish Prompt verified false by UI.

A native `DS14-SDK-BEFORE 1` control reached the comparison feed at **17:26:13 UTC**
and B's own item list. Execution then had an interruption before the active probe
phase resumed at **19:55 UTC**. The later fresh `DS14-SDK-AFTER 1` control reached
the comparison at **19:56:52 UTC**, without a prompt. The first control therefore
does not establish uninterrupted feed liveness during the intervening period.
The retained observer/server scan covers the longer baseline-to-finish interval.

All table times below are UTC on 2026-10-25. Feed counts refer to this sensor UUID,
not the feed's total content. Captures are actual API reads, not expected values.

| Check | Actual result |
|---|---|
| TMM review Cancel, 19:55:09 | Sensor absent from intended and comparison feeds. |
| TMM review Android Back, 19:55:17 | Still absent from both; no sensor in independent routed stream. |
| First explicit SDK import, 19:56:19 | SDK SUCCESS, exactly one root-group UUID match, actual CotEventFactory re-export verified; both feeds still had zero sensor members/events. |
| Fresh automatic native control, 19:56:52 | Comparison accepted the new native control while the sensor stayed absent. |
| Checked native selection cancelled, 19:59:01 | Sensor remained absent from both feeds. The repeated single-leaf selection's checked custom boxes were also captured visually before Done. |
| Native Done, 20:00:18 | Intended feed contained the sensor once and returned its exact event; comparison still zero. B's own intended-feed list showed the new sensor. |
| Explicit SDK repeat, 20:01:12 | SUCCESS, one host UUID, exact re-export; intended sensor count one, comparison zero. |
| Pending review followed by cold restart, 20:02:24 | No pending review resumed in the observed UI; intended membership stayed one, comparison zero. No confirmation was made before termination. |
| Pending review followed by Unload, 20:04:17 | Normal plugin manager confirmed TMM Not loaded; no additional feed membership. The manager activity was first observed through normal UI, then reopened over the pending main-activity dialog. |
| Reload and new explicit confirmation, 20:05:27 | Normal compatible-plugin Load succeeded. No review resumed automatically. New confirmation returned SUCCESS, one host UUID and exact re-export; intended one, comparison zero. |
| Final readback, 20:07:26 | Intended five total items including one sensor; comparison four native controls and zero sensor events/members. |

These lifecycle observations do not independently count the host object before
each new import or prove host persistence in every restart state. Retained feed
association, a host object and a private draft are separate states. Unload/restart
does not revoke completed native content; no erasure/rollback claim is made.

## Independent recipient export

The previous known synthetic B ZIP was retained privately and removed from its
output path before this export, preventing stale-output reuse. B's actual sensor
row menu **Export** generated a new package at **20:02:20 UTC**, captured at
**20:02:24 UTC**, with one CoT event for the expected UUID.

- B ZIP SHA-256: `1edbb43904ba17483d1e1024e43b947261d2ef785f836e29592736a8b9b6f1e4`.
- B event SHA-256: `375e1c409f41d342562d7f5519a9fa7c954d7b8b8f9f9f917874b2a6638b32bb`.

`sh scripts/datasync-metadata-probe.sh check sensor-pir FILE` passed on that B event,
the actual server event after native Done, and the actual server event after
reload/re-import. No expected-data cache filled the returned metadata. B's own
comparison-feed-only view independently showed both fresh native controls plus
the two older controls, and no sensor. Its original view preference was restored.

This proves B-held native feed content, not a TmmDraft import, source authentication,
application-level acknowledgment or live-root-map re-export on B. Data Sync's
overview still calls the sensor type Not Recognized; a production presentation
decision remains separate from the metadata/selection result.

## Private-state checks and cleanup

Both private snapshots were byte-identical before install and after the trial:

- A: `4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
- B: `ca334be7239ebd2138db9571b3799168bbb0708e955b79e9b1d7c422dc5baf8f`.

The final bounded scan at **20:07:27 UTC**, after router row **2432**, examined
**616 server rows**, **88,321 observer bytes / 126 complete events**, **25 private
canaries / five positions**, plus the recorded feed/header/CoT/recent-change API
resources. It found **zero canary/position matches and zero position-parse errors**.
Unique independent TLS before/after controls were present in the observer. No
full sensor event was observed in the routed stream; native feed transfer is not
the same channel as ordinary CoT broadcast. These bounded resources/encodings do
not represent an unrestricted inspection of every client database or network.

Both final crash buffers had zero FATAL EXCEPTION entries. The original comparison
auto-publication OFF, Publish Prompt ON and B's Show non-feed content ON settings
were restored with readback. Only task reverses were removed and both owned guests,
observer and the three owned containers stopped. Docker stop initially killed the
package wrappers, including the database wrapper; only the database was restarted
for recovery and a verified successful `pg_ctl -m fast -w stop` before its wrapper
was stopped again. Do not describe the initial Docker stop as a clean database stop.
Synthetic feed content, volumes and raw evidence remain private and retained.

Raw APKs, profiles, TLS material, API responses, UI captures and traces stay ignored
under `private-data/`; public documentation contains only synthetic identifiers
and bounded results. No app code, schema, build input, new archive or release was
changed by this evidence branch. No image suite, full build or CI wait was needed.

## Disposition and next work

Propose closing the bounded selectable/exportable-representation investigation
**#83** on human review of this result and the earlier manual trials. The original
waypoint failure remains a failure; this explicitly different sensor candidate
passed. This is not completion of #14, #25 or #15.

The next #14 step is a reviewed production artifact/identity contract aligned with
the inbox, then private Android preparation on the serialized storage/lifecycle
boundary. Before a user-facing host handoff, choose the explicit manual reviewed
snapshot limitation versus a supported integrated destination/freshness contract
in [PUBLICATION_PATH](PUBLICATION_PATH.md). Native Done has no verified callback
to revalidate a later-edited draft or acknowledge delivery to TMM. The new evidence
does not supply one. #15 still needs trusted ingress and separately approved draft
application; #26 owns images. All four device types, the reported 5.6.0.12 phone,
the owner's server combination and operational sharing remain separate acceptance.
