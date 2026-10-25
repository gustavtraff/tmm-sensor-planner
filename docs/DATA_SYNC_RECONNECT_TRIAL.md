# Data Sync controlled reconnect and version download — 2026-10-25

Tracking: #25. **The coherent same-UUID PIR update reached B without restarting
ATAK or manually syncing the feed in this controlled run. Full sync preserved it.
Local history downloads also returned the exact older and latest versions without
changing the published server version.** The stale interval in
[PR #87's earlier trial](DATA_SYNC_UPDATE_TRIAL.md) was not reproduced; its cause
remains unknown. This is bounded lab evidence for human review, not a code fix,
physical-network result or production integration approval.

## Setup and interruption boundary

Baseline: merged main `5a65b5f`, including PRs #87 and #86. The retained isolated
standard clients were ATAK-CIV **5.6.0.25 (238484ee)** on Android **15 / API 35**,
Data Sync **3.7.4 (58746181) [5.6.0]** and service-signed TMM **versionCode 15 /
0.1.0-device-details.1**. Host and plugin versions were read back. Artifact hashes
and server setup are unchanged from the [feed trial](DATA_SYNC_FEED_VERIFICATION.md):
isolated TAK Server **5.8-RELEASE-92** and the retained synthetic database.

The selected feed remained `tmm-ds25-selected-20261023`, with five members including
one sensor PIR UUID `00000000-0000-4000-8083-000000000003`. Comparison feed
`tmm-ds83-auto-other` retained two native controls and no PIR. No feed was created
or deleted. Automatic publication was off; B's actual feed settings showed
Subscribed and Notifications checked. This is a read/write subscriber test, not
permission-denial evidence.

Only the owned emulators/server were used. Guests retained loopback-only IPv4/IPv6
egress; the fixed task CoT/TLS and Mission HTTPS tunnels used normal mutual TLS,
CA/hostname validation and plugin loading. API readiness was actually verified;
startup EOF failures were not transfers. No operational device, server, feed,
position or image was used. No private plugin internals or guessed API was used.

The earlier simulated Wi-Fi procedure is unsuitable as the sole proof of a
loopback interruption. This run temporarily rejected guest TCP traffic to **8089
and 8443** and removed B's task reverses. An initially retained socket disappeared;
a subsequent sample showed **zero established task sockets** and the feed sync
indicator was visually red before update confirmation. Reconnection restored the
two reverses and removed precisely those temporary rejection rules. Wi-Fi was
not toggled during the interruption/recovery phase. This is a controlled tunnel/TCP
test, not a cellular or real Wi-Fi handover test.

Fresh `create-sensor` fixtures and fresh outer envelope times were used. The
previous Planned/base case was deliberately republished as a **test reset** before
the coherent Active/update case. This reverses the fixtures' embedded dates during
setup; it is not a test or approval of production stale-update/conflict policy.
Canonical metadata, private snapshots, the checker and the installed APKs were
unchanged. Only the explicitly reviewed synthetic publication copy was imported
through Local SD → Copy and published through Data Sync's selected-feed overlay
checkbox → Done workflow from [ADR 0011](decisions/0011-sensor-point-data-sync-lab.md).

## Observations (UTC)

| Time | Actual observation |
|---|---|
| Before interruption | A republished the base case explicitly; B's fresh export passed `sensor-pir`. The unique BEFORE routing control appeared in B's actual global search, with the exact query and one result checked. Both guests had established CoT sockets. |
| 11:50:48 onward | B's two task ports were blocked and reverses removed. Subsequent socket and red-indicator checks established offline state before source confirmation. |
| 11:51:25 | Importing the update on A alone still left the server on the base case. |
| 11:52:29–42 | Explicit Done replaced the server's same-UUID event with the exact update. The Mission changes entry was ADD_CONTENT, not a new identity. Selected membership stayed one PIR; comparison stayed zero. |
| 11:53:04 | A newly generated B export still passed the base case while blocked. Only afterward were B's tunnels restored and temporary TCP rules removed. |
| 11:53:34 onward | B had an established CoT socket. A distinct AFTER routing control appeared in B's actual global search with its query and one result checked. The independent TLS subscriber received both BEFORE and AFTER controls twice. |
| 11:54:45–55 | Before full sync/history actions, B generated a new export passing `sensor-pir-update`, including all 15 exact attributes and matching rounded outer position. The feed indicator was visually green; one PIR row remained. This is an upper observation bound, not a measured delivery latency. ATAK had not been restarted. |
| 11:55:56–11:56:30 | Dashboard Download Items opened **Allow Full Sync**, explicitly offering to re-download the entire change log. After OK, B's new export still passed the update checker. This differs from feed-list refresh. The recipient was already current, so this does not prove full sync cured the earlier stale interval. |
| 11:57–11:58 | Item menu → View Details → History exposed five versions. The previous baseline version was selected; **Publish to Server was explicitly unchecked** before Yes. B's new export passed the base checker while the server still passed the update checker. |
| 11:59:15–25 | Selecting Latest in history and confirming the local revert restored B's exact update without restart. This confirmation had no Publish to Server option. Server membership/content and its change records remained unchanged by both local history downloads. |

All recipient captures came from B's own newly generated item-menu Export ZIP,
each containing one CoT event and manifest. New package times were recorded; no
source/server event was substituted for receiver evidence. As in the earlier
trial, export refreshes outer timestamps, so only checking those would hide an
old embedded payload.

| Recipient capture | ZIP SHA-256 |
|---|---|
| Offline after update | `dd9807351ef6462186b474a86064d8d8e480fce948357a706b591b787f759c26` |
| Reconnected, before manual sync | `44c47903909128f3ec33adbebe1fd3fd566cc0d1bbacee1150172f8bf9531391` |
| After full sync | `fb22a35ae6fd0f61dda7c13dadf2d9537a7168b0353c20db9df81e63d2420e31` |
| Local older history | `27180d481b2760522902b0317d975eb6d67ec7043b7b9afdc3f22549ef256662` |
| Local latest history | `b801e4b484eac6993e2fa43f435ea110a42f4997f22ecd5fa823de79a74f95dc` |

## Interpretation and next work

This provides a controlled positive recovery result and actual full-sync/history
workflow evidence on the declared combination. It does not identify the earlier
stale interval's cause or justify relabelling that observation as a pass. A restart
is not demonstrated to be universally necessary. Do not promise recovery on every
server, physical network or host build from this single run.

Data Sync's item overview described the outer type as
`Not Recognized [b-m-p-s-p-loc]`, although selection, history and exact field exports
worked. Host selectability does not establish a polished Data Sync classification
or a supported callable third-party Android contract. Production representation
and receiver presentation still need review under #14/#15.

Next under #25: read-only publication denial, concurrent edits and local/server
removal semantics. Then scope the exact app publication-copy creation path (#14)
and durable TmmDraft import/conflicts (#15). No production adapter, schema,
acknowledgment, draft conversion, image delivery (#26) or new signing input was
added. Raw UI, packages, observations and fixtures remain ignored under
`private-data/datasync-reconnect-25/`.

## Verification and private-state checks

Sixteen strict checks on actual server/recipient captures produced the expected
success/rejection results, including rejecting the offline/older payload as the
update and the recovered/latest payload as the base case. Both actual B control
searches checked the submitted query and exactly one matching result. Final UI
readback retained B's subscription/notifications and disabled automatic publication;
A's automatic publication was also read back off.

A's private snapshot stayed byte-identical, SHA-256
`4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
The final **12:02:06.451 UTC** scan from starting server row **1758** covered
**60 rows**, **11,062 observer bytes / 17 complete events**, **25 private canaries /
five positions**, with zero matches and zero position parse errors. Both feeds'
headers, CoT and recent changes were scanned; numeric positions were compared in
server geometry/routed events. No full routed PIR event is required for or inferred
from feed delivery. This is bounded observation, not every resource/encoding or a
new recipient-wide private-data search.

A's crash buffer had zero FATAL EXCEPTION entries. B had **one UI-automation
failure at 11:49:15 UTC**, from two overlapping operator dump commands
(`UiAutomationService ... already registered` in the uiautomator launcher).
The commands were serialized afterward and fresh captures were obtained. This was
before the verified baseline/interruption; it is not silently counted as a clean
test-tool run. B's ATAK PID **1664** still dated to approximately **11:45:29 UTC**
when checked at 12:03:07 using process start ticks/uptime. Thus the successful
recovery was not explained by an unnoticed ATAK process restart. Caught errors and
every possible failure mode are not excluded.

No app/domain/build input changed. Core, image and SDK suites and an APK build were
not rerun; CI was not awaited. Documentation links and whitespace were checked.

Cleanup completed at **12:03:44.913 UTC**. Temporary interruption rules had already
been removed and checked. Both task reverses were removed, guest Wi-Fi disabled,
and the owned emulators, observer and relay/server/database stopped; their stopped
state was verified. PostgreSQL confirmed a clean fast shutdown. Feeds, volumes and
private evidence were retained; no unrelated workload was changed. Cleanup traffic
is outside the scan counts above. No feed preferences were changed by this run.
