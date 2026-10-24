# Data Sync isolated-feed privacy verification — 2026-10-24

Tracking: #25, with #4/#7. **Result: ordinary chosen-feed delivery works in the
isolated standard-client setup; no private TMM canary exposure was observed in the
checks below.** This supplies the automatic-publication privacy phase missing from
[the prior CoT trial](CONNECTED_SIGNED15_VERIFICATION.md). Human review remains
required. It does not establish TMM metadata round-trip support or finish #25.

No app, production transport, storage schema, signing configuration or release
artifact changed. The existing signed version 15 was used on both clients.

## Exact setup and isolation

The same two task emulators, standard ATAK-CIV **5.6.0.25 (238484ee)**, Android
**15 / API 35**, signed TMM **versionCode 15 / 0.1.0-device-details.1**, and isolated
TAK Server **5.8-RELEASE-92** from the prior trial were reused. Their artifact hashes
and Android build are recorded in [that report](CONNECTED_SIGNED15_VERIFICATION.md).
The signed TMM APK SHA-256 is
`221017dd9f2eeb8d5c46ab2fd1db235f2644059f480fe7874865dc4e8fdd7645`.

Both clients additionally installed and normally loaded **Data Sync 3.7.4
(58746181) [5.6.0]**, package `com.atakmap.android.datasync.plugin`, APK SHA-256
`d785c5819d727796e56fcd0124bfd67dcbc8e53de9cc1ac0545601c1a17d54a0`.
Signature checks were not bypassed. Plugin-manager local catalog refresh was
performed with remote repository updates and automatic synchronization disabled.
Loading into these standard hosts succeeded; the historical SDK developer-host
loading failure is a different test combination.

Guest IPv4/IPv6 egress accepted loopback and dropped other destinations. Only
loopback 8089 (CoT/TLS) and 8443 (Mission HTTPS) were reversed through the fixed
host-loopback relay. The latter route was added for this test. Database/server
containers had no published ports and retained their internal Docker network.
Mutual TLS, CA and hostname validation were retained. No operational phone,
profile, feed, account, image or sensor position was used.

A created a new synthetic feed using Data Sync's own UI. B found it and selected
Download & sync. The server normalized its name to `tmm-ds25-selected-20261023`;
creation actually occurred on **October 24**, despite the planned-date suffix.
This was a public test feed in the synthetic lab group, not a permissions test.
Independent Mission API reads used authenticated, validating TLS and GET only;
no REST publication adapter or private Data Sync action was implemented.

## Positive controls and actual feed delivery

| Control | Observed result |
|---|---|
| Explicit selection, auto-publication off | A created `DS25-STOCK-NATIVE 1` using ATAK Point Dropper. Add Items → Overlays → the verified single marker checkbox → Done associated its UID with the feed at **14:04:42.354 UTC**. B's actual Data Sync Map Items list showed that marker. |
| Automatic publication enabled | The per-feed checkbox was verified checked. Creating `DS25-AUTO-CONTROL 1` opened Data Sync's publication prompt. Always Publish / Don't ask again was chosen for this synthetic test; a hint confirmed that the content prompt was disabled. Feed membership was recorded at **14:16:45.917 UTC**, and B showed the new marker. |
| After private operations, reconnect and restart | The per-feed checkbox remained checked after cold restart. A new native marker `DS25-AUTO-AFTER 1` was published **without another publication prompt**, with feed membership at **15:04:10.344 UTC**. B's actual list showed all three native controls and no TMM draft. |

The feed's UID membership and server-returned CoT were read separately from B's
UI. Visible control receipt therefore accompanies server evidence, rather than
being inferred from an HTTP success code. No native control used a private canary
position. A fresh independent TLS CoT source and decrypted subscriber also checked
routing before/during/after the privacy window.

Setup attempts are not passes: an initial bare stock `.cot` import was not proven
selectable; tapping an overlay row title centered it instead of selecting it.
The successful stock baseline used its actual checkbox. A first final-control tap
outside the visible globe created nothing; only the subsequent on-globe control
with independently confirmed UID/feed/recipient evidence is counted.

## Private TMM operations

All private edits used TMM's actual form, map-position selector and generated-image
picker. The existing private feature-layer representation was retained; no private
draft was converted into an ordinary ATAK MapItem.

| Phase | Actual behavior and readback |
|---|---|
| Auto-publication off | Created `DS25-PRIVATE-OFF`, edited its comment, moved it graphically and separately saved the new position, then reviewed and attached a generated PNG. Snapshot readback retained one UUID and reached revision 4 with a private owned-image reference. |
| Auto-publication on, prompt disabled | Created `DS25-PRIVATE-ON`, edited its comment, moved/saved its position and attached the same generated source as a separate owned image. Snapshot reached revision 4. None of these actions produced a Data Sync publication prompt or a feed item. |
| Offline changes | Removed only A's two task tunnels and disabled its simulated Wi-Fi. Changed ON's comment again (revision 5), then explicitly confirmed deletion of OFF. Its identifiers, earlier positions and image canaries remained in the accumulated scan set. |
| Reconnect | Restored only the two local tunnels and simulated Wi-Fi. The retained ON draft stayed Planned/Local with its saved image and offline comment; OFF stayed deleted. No private queued feed publication was observed. |
| Cold restart | Force-stopped only A and normally loaded plugins after ATAK's expected unclean-exit prompt. TMM required fresh connected-local consent. Two retained drafts, including the pre-existing prior-trial canary, reopened. Data Sync stayed subscribed; the new native automatic control subsequently reached B. |
| Persistence | The post-deletion/reconnection snapshot and post-restart snapshot had identical SHA-256 `4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`. |

The deliberate force-stop warning is not counted as a spontaneous plugin crash.
This run does not repeat every Unload, background-viewer or consent-cancellation
case from the earlier CoT-only trial. It adds the Data Sync enabled/queued and cold
restart conditions that report explicitly lacked.

## Observation and limits

Private operations began after the **14:05:36.061 UTC** baseline, server CoT row
**1356**, decrypted-stream byte offset **144813**. Scans accumulated UUIDs, IDs,
owner/comment strings, owned-image names/stems, source-image name and private path
canaries from every saved stage, including the deleted draft. Plain and standard/
URL-safe Base64 forms were checked. Five accumulated private coordinate pairs were
compared against new CoT server geometry and parsed observer events within 1e-6
degree tolerance. The final scan ended at **15:09:18.038 UTC**: **126 new server rows** through row
**1482**, **23,967 decrypted-stream bytes**, **37 complete observer events**,
**zero XML position-parse errors**, **25 canaries**, **five positions**, and
**zero canary/position matches**. Final Mission responses were 1,427 bytes (header),
2,693 bytes (CoT) and 1,046 bytes (recent changes); no private canary matched.

Independent routed control `TMM-DS25-CONTROL-BEFORE-PRIVATE-20261024` was stored in
rows 1365/1368 at **14:08:14.143 / 14:08:29.006 UTC**. After reconnect/restart,
`TMM-DS25-CONTROL-AFTER-20261024` was stored in rows 1463/1465 at
**15:04:27.545 / 15:04:42.432 UTC** and found in the decrypted observer and B's
actual global object search. The earlier pre-operation control and the ordinary
selected-feed baseline preceded private creation.

B's global object index was searched for **24** accumulated identity, label,
owner/comment, image/path and historical MGRS strings, with zero rows. Each query
field was checked against its intended text. A final private-label-prefix search
again returned zero rows after the after-control was visibly received. This is an
index check, not forensic inspection of every recipient database or arbitrary
field encoding. Captured final crash buffers on both clients contained **zero
FATAL EXCEPTION** entries; caught errors are not excluded.

Periodic additional authenticated reads searched the chosen Mission's header/membership,
returned CoT and recent changes. The feed contained only the native controls and
no image/file content. These checks cover this chosen feed and time window, not
every HTTP resource, historical server record or possible encoding. The observer
receives routed peer events, not its own keepalives; a quiet interval alone was
never treated as proof of working observation.

Raw snapshots, UI XML, screenshots, server extracts, decrypted traffic, synthetic
profiles and certificates remain ignored in `private-data/datasync-feed-25/`.
No raw runtime evidence or credentials are committed.

## Next gate

Review this privacy evidence first. Then run the separately reviewed metadata-only
spike from [DATA_SYNC_PROBE.md](DATA_SYNC_PROBE.md): a synthetic publication
representation, chosen-feed versus second-feed checks, actual recipient-exported
custom fields/UUID, Cancel, permission denial, repeat/update, receiver restart and
conflict behavior. A stock marker's success is insufficient for that conclusion.
A supported callable third-party Data Sync Android contract remains unidentified;
no guessed private API or direct REST workaround is approved by this trial.

The exact phone host **5.6.0.12** and the owner's server remain separate test
combinations. No online phone acceptance, durable TMM import, production sharing,
image delivery, operational-network approval or release is claimed. Keep #25,
#4/#7 and the relevant #14/#15/#26 residual gates open for human assessment.

This is a runtime-evidence/documentation change. No domain, app or build input
changed, so core, SDK and image unit suites were not rerun. CI is not awaited.

## Cleanup and review boundary

A's per-feed automatic-publication checkbox was restored to **off** and Data Sync's
Publish Prompt preference restored to **on**, both with UI readback. Both task
emulators were stopped after removing their task reverse tunnels and disabling
simulated Wi-Fi. The independent observer was stopped. Only the task relay, server
and database containers were stopped. Their wrappers initially ended with Docker
exit 137. A subsequent database-only recovery/readiness check succeeded; PostgreSQL
was then stopped cleanly using its installed `pg_ctl -m fast -w stop`, and that
container was stopped again. An initial guessed binary path was absent and did
not perform a database shutdown; only the subsequent verified command is counted.
Their volumes, synthetic feed and private evidence were retained. No other Docker
workload, AVD, phone or server was stopped or modified. Post-window cleanup traffic
is excluded from the counts above.

Review this record before the next active custom-metadata experiment. This branch
provides the scoped privacy/ordinary-feed prerequisite, not a completed Data Sync
integration or authorization to use an operational feed.
