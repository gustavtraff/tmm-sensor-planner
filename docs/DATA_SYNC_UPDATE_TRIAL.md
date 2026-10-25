# Data Sync same-UUID update and recovery trial — 2026-10-25

Tracking: #25, following the reviewed [sensor baseline](DATA_SYNC_SENSOR_TRIAL.md)
and PR #85. **Repeated publication did not duplicate the object. The server
accepted a coherent same-UUID update; B exported its exact updated metadata after
an online ATAK cold start. Reconnect alone and feed-list refresh still produced
the old metadata in the observed window. Automatic recovery is not a pass.**

## Scope and setup

Baseline: merged main `7f97393fccb7f7ba2b54d8bb3e81b397fd183661`.
The retained isolated clients used standard ATAK-CIV **5.6.0.25 (238484ee)**,
Android **15 / API 35**, Data Sync **3.7.4 (58746181) [5.6.0]** and the same
service-signed TMM **versionCode 15 / 0.1.0-device-details.1**. Versions were read
back on both guests. Artifact hashes remain those in the
[feed privacy report](DATA_SYNC_FEED_VERIFICATION.md). Server: isolated TAK Server
**5.8-RELEASE-92**, with the retained synthetic database.

Both guests retained loopback-only IPv4/IPv6 egress rules. Only the task's fixed
CoT/TLS and Mission HTTPS reverses were enabled; mutual TLS and CA/hostname
validation remained enabled. The Mission API was actually ready before the
baseline was recorded; startup TLS EOF errors were not transfers. The independent
subscriber was restarted after server readiness. No operational server, phone,
profile, sensor position or image was used.

This is the manually imported publication-copy workflow from
[ADR 0011](decisions/0011-sensor-point-data-sync-lab.md), not new app integration.
The selected feed was `tmm-ds25-selected-20261023`; comparison was
`tmm-ds83-auto-other`. Automatic publication was **off** in both during this run.
The earlier auto-on/prompt-off experiment was not repeated or extended by these
results. B was subscribed to both; no permission denial or non-member secrecy
was tested.

The fixed PIR identity was `00000000-0000-4000-8083-000000000003`, outer type
`b-m-p-s-p-loc`. Fresh `create-sensor` output supplied the base and update cases.
The update's outer time/start/stale were refreshed before import; canonical
embedded fields were unchanged. Its status is Active, bearing is zero, deployment
time is present, and updated time, precise embedded latitude and Unicode comment
differ from the Planned baseline. No image handle, path, local revision or private
draft was converted or sent.

## Observed sequence (UTC)

| Time | Action and independent result |
|---|---|
| 09:56:09 | Server baseline: selected feed had five members, including exactly one PIR event; comparison had two native controls and no PIR. Server event passed `check sensor-pir`. |
| 09:57:27 | A selected that same PIR through Add Items → Overlays → Markers → Mission → checkbox → Done. Mission changes recorded another ADD_CONTENT for the UID. |
| 09:57:35–55 | Selected membership remained five with exactly one PIR; comparison remained two with none. B displayed one PIR row and generated a fresh export passing the base checker. This is bounded duplicate prevention, not exactly-once delivery. |
| 09:57:57 | B's simulated Wi-Fi was disabled and both task reverses removed. This disconnect was maintained through source update publication and the offline export. |
| 09:58:52 | After A imported `DS25-SENSOR-UPDATE.cot` through Local SD → Copy, the server event still passed the **base** checker. Import alone had not changed the published version. |
| 09:59:58 | A explicitly selected the same item and confirmed Done again. The server change record was ADD_CONTENT for the same UID; its event now passed `check sensor-pir-update`. Membership remained one PIR in the selected feed, zero in comparison. |
| 10:00:16–24 | B generated a new export while still offline. It passed the base checker: the updated server event had not magically become recipient evidence. |
| 10:00:33 | B's two task reverses were restored and simulated Wi-Fi enabled. |
| 10:00:50–58 | B's new post-reconnect export still contained Planned/base metadata and failed the update checker. |
| 10:02:38–47 | After normal feed-list refresh and reopening the feed, another new export still passed the base case and failed the update case. B's loopback TCP connection to port 8089 was established in this interval. List refresh is not a demonstrated full item re-download. |
| 10:03:02 | B's ATAK was deliberately force-stopped and cold-started online. No data or subscription was cleared. |
| 10:05:02–08 | B again displayed one PIR row. A new item-menu Export produced an event passing **all exact update checks**. Server still had one updated selected-feed PIR and none in comparison. |
| 10:05:46–10:08:09 | B's tunnels were removed again, Wi-Fi disabled and ATAK cold-started offline. Its one PIR row remained available; a newly generated export still passed the update checker. This proves bounded local retention in the host/feed workflow, not TmmDraft persistence. |

The duplicate, offline, reconnected, refreshed and restarted files came from B's
own item-menu Export ZIPs. Each package contained one CoT event plus its manifest;
new ZIP entry times were checked. They were not copied from source/server or filled
from expected values. Export rewrites host envelope timestamps, so a fresh outer
time alone must never be treated as fresh embedded metadata or receipt proof.

Selected recipient ZIP SHA-256 values:

| Capture | SHA-256 |
|---|---|
| After repeat | `228cfd3ced6041c54935572132c34e31e6cd522d163de6ac65628fdac0a3029a` |
| Offline after server update | `c2fe955e169e4a3ad1192140b92ed731ef35323a83a8a68a49839c83a73af75d` |
| After reconnect | `9cd9ea7104b9a389bb671a2e5e66b0abff84f6e616fa3c2a6bf22301bd2228a8` |
| After list refresh | `5085ba0483d143dd699accafb51d17c28c0a9f005d8ca672343f19faf6fb91cb` |
| After online restart | `78de8581d5cdb52351533efc64f1c5153aeb18e8f5b579278ceb0c3430aeb088` |
| After offline restart | `464bc2ef3574b69ad0ffc6973c7372b9252df66f4f9817c9a2fa04d3519a4ea2` |

## Interpretation and next gate

This establishes same-UUID server replacement without duplicate membership, exact
recipient update preservation after online cold start, and a real stale-recipient
interval. It does not establish why reconnect/list refresh retained the old
metadata, whether a later background retry would succeed, whether the stale state
was the host MapItem or Data Sync's own storage, or whether full Download Items /
history-version download recovers without restart. No private Data Sync internals
were inspected. Enabling simulated Wi-Fi did not establish an associated Android
Wi-Fi network in this loopback-only setup; actual socket/API/content evidence must
be distinguished from radio/network recovery. Do not generalize this as a confirmed
Data Sync defect on physical phones.

Next under #25: reproduce with observed subscription/sync state, independently
received before/after routing controls and actual full-download/history controls.
Separate transport reconnection, feed refresh, host MapItem state and exported
metadata; bound retry time and check exact payloads after each action. Then test
read-only denial, concurrent edits and local/server removal separately. Keep the
automatic-recovery gate open until that evidence exists.

The manual describes download/history and conflict workflows; their presence is
not proof of this custom-field path. Do not implement a private broadcast or new
REST publisher to bypass the result. #14 owns explicit app publishing/review;
#15 owns durable TmmDraft import/conflict handling. Data Sync retaining an item
is not a saved TMM record, recipient acknowledgment or accepted production schema.
Images remain #26. No operational-network approval follows from this trial.

Raw captures, generated fixtures, local snapshots and logs remain ignored under
`private-data/datasync-update-25/`. Only sanitized observations enter Git.

## Checks and private-state boundary

Thirteen strict checks were run on actual server/recipient captures, including
expected rejection of the stale recipient as an update and rejection of the
updated recipient as the base case. All expected success/rejection outcomes held;
the checker was not relaxed. No domain/app/build input changed, so core, image and
SDK suites and a full APK build were not rerun. CI was not awaited.

A's private snapshot remained byte-identical before/after the trial, SHA-256
`4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
At **10:08:36.299 UTC**, the scan from actual starting server row **1702** covered
**51 rows**, **9,514 observer bytes / 15 complete XML events**, **25 private
canaries / five positions**, with zero canary/position matches and zero position
parse errors. Both feeds' headers, CoT and recent changes were searched; numeric
position checks apply to server geometry/routed events. These are bounded
resources/encodings, not every database or HTTP resource.

The unique recovery TLS control reached the independent subscriber twice. No full
routed PIR event appeared there; feed delivery was assessed using Mission API and
B exports instead. The recovery control was not independently searched on B, so
it does not prove B's transport recovery during the stale interval. Both final
Android crash buffers had zero FATAL EXCEPTION entries; this does not exclude
caught errors or prove an absence of every crash.

Cleanup completed at **10:09:27.706 UTC**: the observer and both owned emulators
were stopped, simulated Wi-Fi disabled and task tunnels absent. Only the task
relay/server/database containers were stopped; PostgreSQL confirmed a clean fast
shutdown. Synthetic feeds, volumes and private evidence were retained. No feed
auto-publication or prompt preference was changed in this run. Cleanup traffic
is outside the scan counts above.
