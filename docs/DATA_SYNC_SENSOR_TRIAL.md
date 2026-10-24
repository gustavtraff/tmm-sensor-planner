# Sensor-point Data Sync trial — 2026-10-24

Tracking: #83 / #25. **Result: the explicit sensor-point PIR candidate was selectable,
reached the intended feed and passed the strict checker on B's own export. It
remained absent from a second subscribed feed with automatic publication on and
the content prompt off.** This is a bounded manual-workflow result, not a production
plugin integration, unrestricted privacy claim or completed #25 acceptance.

[ADR 0011](decisions/0011-sensor-point-data-sync-lab.md) proposes the lab envelope
and limits. The [earlier waypoint rejection](DATA_SYNC_METADATA_TRIAL.md) remains
valid; neither its failed case nor the separate friendly diagnostic is relabelled.

## Tested setup and candidate

Source baseline: reviewed main `f1c8e602cf596973b58bc9de6ea0261bb81395f5` (PR #84).
The same two standard ATAK-CIV **5.6.0.25 (238484ee)** / Android **15, API 35**
clients, signed TMM **versionCode 15**, Data Sync **3.7.4 (58746181) [5.6.0]** and
isolated TAK Server **5.8-RELEASE-92** were reused. Exact APK/signing hashes are
linked from [the feed privacy report](DATA_SYNC_FEED_VERIFICATION.md). This branch
changes only offline fixture/checker tooling and documentation; the installed APK
and production model/transport were unchanged.

Both guests allowed loopback-only IPv4/IPv6 egress and only the fixed CoT/TLS 8089
and Mission HTTPS 8443 task reverses. Mutual TLS, CA/hostname validation and normal
plugin signature loading remained enabled. Startup API failures were followed by
actual authenticated readiness, not counted as successful transfers. No operational
phone, profile, feed, image or sensor location was used. No private Data Sync code
was decompiled, guessed action invoked or REST publisher implemented.

The exact supplied SDK registers `b-m-p-s-p-loc` with the Mission marker importer;
its MarkerImporter selects the sensor icon. The [official pinned source](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/CotImporterManager.java)
agrees on that registration. Host Marker supports MissionPackageExportWrapper;
this does not identify Data Sync's private implementation or callable target.
The manual's documented Add Items/Overlays workflow was the only publishing surface.
Changing envelope/group resolves the observed selection in this sample; the exact
internal reason for the old waypoint rejection remains unproven.

The explicit `sensor-pir` fixture uses:

- Outer type `b-m-p-s-p-loc`, host `<archive/>`, contact `TMM-SENSOR-LAB-PIR`.
- Stable synthetic UUID `00000000-0000-4000-8083-000000000003`; its namespace avoids
  collision with the prior waypoint and friendly diagnostic. It is not a production
  UUID migration or a change to a saved draft's identity.
- The same 15 experimental `tmmShareLab` attributes as the base PIR case, including
  exact embedded coordinates, degree bearing, Planned status, Unicode owner/comment,
  newline/tab/escaping and nanosecond UTC metadata timestamps. No image/path/revision.

The actual source was generated at **18:59:13.661203 UTC**, stale at 19:59:13 UTC,
and imported at **19:03:10.943 UTC**, while fresh. SHA-256:
`07011708e82e450a805fac3e13caa455a2a42d0b70439acfee9a505956816b8c`.
A separately prepared hand-edited candidate was not imported or counted; the
runtime source came from the new reproducible `create-sensor` command.

## Actual checks

A was subscribed to the intended `tmm-ds25-selected-20261023` with auto-publication
off and the new `tmm-ds83-auto-other` with auto-publication on. B subscribed to both.
Both were public synthetic feeds in the isolated lab group; no ACL denial or
non-member secrecy is inferred from membership checks.

| Check | Evidence |
|---|---|
| Automatic comparison control before import | Native `DS83-AUTO-BEFORE 1` triggered Data Sync's prompt. Always Publish / Don't ask again was selected for this synthetic feed; membership appeared at **18:58:34.805 UTC**, and B displayed the control. |
| Prompt mode | Cleanup UI readback confirmed Publish Prompt **false**; the later native control also published without a prompt. The observation is not based solely on a requested setting. |
| Candidate creation/import | Standard Local SD → Copy imported the generated file. At **19:06:52.408 UTC**, candidate membership was absent from both target and automatic comparison feeds. |
| Selection | Add Items → Overlays → Markers → Mission contained exactly one leaf: the sensor candidate. Its checkbox was visibly selected. No private feature-layer object was converted. |
| Cancel before Done | After closing that checked selection, at **19:08:03.209 UTC**, both feed memberships and the independent routed stream still lacked the candidate UID. |
| Explicit confirmation | A reselected the single sensor and chose Done. Target membership appeared at **19:09:19.075 UTC**. Server CoT had one matching sensor UID; the other feed had none. |
| Recipient | B's actual target-feed Map Items list showed `TMM-SENSOR-LAB-PIR` as Mission content. Its own item menu → Export created a ZIP containing exactly one CoT event and its manifest. |
| Strict returned-event check | The event extracted from B's export passed `check sensor-pir`: correct UUID/type, all 15 exact metadata attributes and matching rounded outer point. The same checker also passed the separate server event. |
| Automatic comparison control after publication | New native `DS83-AUTO-AFTER 1` reached the comparison feed at **19:11:03.978 UTC**, without another publication prompt. This confirms the comparison mode stayed live. |
| Final destination check | At **19:11:58.820 UTC**, target had the candidate once; other had only the two native controls, not the sensor. B's comparison list with Show non-feed content off independently showed both controls and no sensor. |

B's export captured at **19:11:06.905 UTC** had SHA-256
`c7222e16609cc69cda22a1a7f18ea247a6654c0280dd929b1d19b12b7f765e7b`.
It was read from B's generated ZIP, not copied from source/server or filled from
an expected-data cache. It proves recipient-held feed content, not re-export of
B's live root MapItem, durable TmmDraft storage or an acknowledgment protocol.

The independent subscriber contained no full routed event for the sensor or either
native feed control; Data Sync feed membership/export and ordinary CoT broadcast
must not be conflated. The before/after native controls establish actual feed
liveness through B and server reads. A separate final TLS control checks subscriber
liveness; absence checks are bounded to the observed stream/feed/API resources.

## Scope and next gate

This establishes one **manual file-import/selected-feed PIR** path. It does not
verify direct Marker construction, a plugin's internal dispatcher/source tag,
automated feed selection, every companion version, all four device types at runtime,
map-edit payload coherence, same-UUID update/duplicates, read-only denial, reconnect,
receiver restart, conflict/removal semantics or image delivery. Never substitute
the native type to claim a silent production wire migration. Stock map edits may
change the outer point without updating exact custom metadata; that consistency
must be tested rather than assumed.

Review ADR 0011 and this evidence before the remaining #25 metadata acceptance
cases and any #14 publishing/#15 durable-import adapter. The real phone host
5.6.0.12 and the owner's server remain different combinations. The publication
copy must follow explicit TMM metadata review in any integrated workflow, and its
exact SDK creation path needs its own before-confirmation/wrong-feed tests. Do
not borrow a stock source tag to make an untested path appear equivalent.

Raw screenshots/UI XML, APKs, certificates/profiles, generated inputs, server/API
reads, decrypted stream, B's package and private snapshots remain ignored under
`private-data/datasync-representation-83/`. No raw trace or credential enters Git.

## Checks run

- `python3 scripts/test-datasync-metadata-probe.py`: both envelope families, five
  cases each, exact payload equality, disjoint expected UUID/type, same-UUID update
  mismatch, input bounds, unchanged-file/overwrite refusal and safe diagnostics.
- `sh scripts/test.sh`: core, forms, import/share review, fields and storage suites,
  separate-process reopen and synthetic 100-device checks passed.
- Actual runtime operations, source/server/recipient readbacks and strict returned
  sensor checker described above. Core/image/SDK CI was not awaited; no image suite
  or full APK build was run because app/build inputs did not change.

## Private-state check and cleanup

The final A draft snapshot at **19:14:00.032 UTC** remained byte-identical to the
pre-experiment snapshot, SHA-256
`4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
No saved draft was edited or converted. At **19:18:03.879 UTC**, the cumulative
scan after prior metadata-run row **1583** covered **105 server rows** through
**1688**, **14,249 subscriber bytes / 22 complete XML events**, **25 private
canaries / five positions**, with **zero canary/position matches** and **zero
position-parse errors**. The bound includes prior cleanup rows and is not an exact
new-run starting counter. Header, CoT and recent changes for both test feeds were
searched; numeric position comparison applies to server geometry/routed events.

The final independent TLS control `TMM-DS25-CONTROL-DS83-AFTER` reached both the
subscriber and B's actual global object search. Two final private-label searches
returned zero rows, with each actual query text checked. This supplements the
unchanged-store and feed checks; it does not inspect every recipient database or
all possible HTTP resources/encodings.

The comparison feed's auto-publication was restored off, Publish Prompt on and
B's Show non-feed content view preference restored on through normal UI controls.
Both owned emulators were stopped after removing task reverses and disabling
simulated Wi-Fi; the observer and only the task relay/server/database were stopped.
PostgreSQL used a clean fast shutdown. Both final Android crash buffers contained
zero FATAL EXCEPTION entries; caught errors are not excluded. Synthetic feeds,
volumes and private evidence were retained. No operational phone/server or other
workload was started, stopped or changed. Cleanup traffic is excluded from the
counts above.
