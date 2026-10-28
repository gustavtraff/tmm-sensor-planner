# Signed v20 connected-private regression — #4/#7

Date: 2026-10-28. This is a bounded isolated two-client follow-up to the
[offline v20 upgrade acceptance](EMULATOR_RECOVERY_V20.md) and
[earlier connected v15 trial](CONNECTED_SIGNED15_VERIFICATION.md). It is not an
operational-server approval, production Data Sync integration or image transfer.

## Exact tested candidate and environment

| Component | Actual test target |
|---|---|
| Signed plugin on A/B | versionCode 20, `0.1.0-versioned-storage.1`, `se.tmm.sensorplanner.plugin` |
| APK SHA-256 | `ae02b633e1db626ad7bc7d62bbf7b44bba04dd24220a71298f469c768c3e7d83` |
| Certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| Associated submitted source | `f9845c89c408d9c3bb941d00adda2c6f1c5c553d`; source association is not reproducible-build attestation |
| Standard host on A/B | ATAK-CIV 5.6.0.25 (`238484ee`) |
| Companion on A/B | Data Sync 3.7.4 (`58746181`) [5.6.0] |
| Guest/tooling | Android 15 / API 35 default AOSP arm64 revision 2; emulator 37.2.12.0 build 16428233, `-gpu swangle`, 2 GiB and two virtual cores per guest |
| Server | Existing isolated TAK Server 5.8-RELEASE-92 lab, same private database and fixed-destination relay |

Both standard hosts show TMM and Data Sync **Loaded** through native Package
Management. Host signature checks were not patched. The documented EGL marker is
present in both guests. A reuses the recovered private test profile; B is a new
empty durable recipient because old temporary AVD configuration files were gone.
Original temporary profiles were not repaired or reset. First attempts before the
Android framework completed boot did not establish readiness and failed package
installation on B; these were repeated successfully after readiness. Native package
Sync discovered the already installed Data Sync package on A. These are setup
attempts, not passing trial cases.

The separate [#120 fix](https://github.com/gustavtraff/tmm-sensor-planner/pull/121)
was independently reviewed and merged during this work session, with both required
CI checks green. **That fix is not in this signed v20 APK.** Its deterministic
Robolectric results and future signed-host regression remain separate evidence.

## Isolation and observation

Only the two task guests and existing `tmm-lab-db`, `tmm-lab-server` and
`tmm-lab-relay` containers ran. Guests used disconnected socket network backends,
not ordinary NAT. IPv4/IPv6 OUTPUT rules accepted guest loopback and dropped other
traffic before host launch. Simulated Wi-Fi/connected state was then enabled;
only loopback task tunnels mapped guest 8089/8443 to host 19089/19443. Actual
established 8089 sockets and Android connectivity dumps were recorded. The server
and database had no published host ports; the relay bindings were 127.0.0.1 only.
Existing mutual TLS, CA and IP-hostname checks were retained.

Only existing generated lab profiles, synthetic map positions and generated images
were used. No operational phone, feed, server, account or real image was touched.
Raw snapshots, UI XML/screenshots, TLS material and logs remain ignored privately.

A separate authenticated TLS observer captured decrypted CoT. Independent SQL
queried new `cot_router` rows. Each expanded scan also fetched **all four** known
synthetic feeds' membership, CoT content and bounded last-24-hour change responses:
12 authenticated HTTP responses per stage. Oversized/truncated responses and
invalid expected JSON/XML were errors, not empty evidence. This covers Mission API
writes that need not appear in the router/observer stream. It is still a bounded
readback, not a guarantee of every transient write or arbitrary retention period.

Canaries accumulate actual UUIDs, display IDs, owners/comments, image handles,
deleted UUIDs from real pulled v2
snapshots. The source-image name and known private-path strings are additional
explicit scanner canaries, not fields recovered from those snapshots. The existing codec validates the frames; an evidence-only dumper exposes
synthetic active/deleted data, not a new production parser. Scans check plain and
Base64 forms, decoded known publication text, outer event coordinates and canonical
`tmmPublication` coordinates with 1e-6 tolerance. No actual import/acknowledgment is
inferred from a metadata payload. Recipient observations are native UI views,
separate from the server and observer.

## Controlled private operations

The initial v2 baseline has 21 active records and one retained deleted slot from
the offline acceptance. Its frame is 3,525 bytes, SHA-256
`2e0144df281fa57f85bfc9a8d0908761de8aed1ed8ad9269f20f2424c4db9dbb`.
The existing 21 active records, deleted slot and dataset identity remain exact
through the new canary edits.

The synthetic comparison feed initially has six ordinary historical controls.
A and B explicitly subscribe using native **Download & sync**. On A, feed
Auto-publish is changed from OFF to ON and Publish Prompt from ON to OFF for this
trial. A unique ordinary native **V20-NATIVE-BEFORE 1** marker is created via Point
Dropper. It appears in an independent feed read and B's actual Map Items list
before private-operation conclusions. First-use point-dropper hints initially
prevented placement; only the subsequent real placement is counted.

| Action | Observed result |
|---|---|
| Connected-local Cancel/reopen | Opening requires explicit consent; Cancel and explicit opt-in preserve the exact baseline bytes. List opens as test mode. |
| Private create | Native form saves one unique canary with exact ID/owner/comment and a distinct MGRS position. Actual v2 has 22 active records, original deleted slot and zero decisions. New state/revision are 1/1. |
| Local image | Native DocumentsUI selects a generated PNG and explicit Save commits an opaque image handle. New state/revision are 2/2. Scans cover observed image handles/paths; image asset bytes and all transfer/storage channels were not exhaustively inspected. |
| Graphical move | Pan under the actual sight, Use position, then Save locally. MGRS/persistent position changes; state/revision are 3/3. |
| Cold restart | Force-stop A, remove only its task TLS reverse, relaunch with simulated connected state. TMM requires fresh consent; Cancel leaves the frame byte-identical. |
| Reconnect before consent | Restore the task tunnel; observe an established TLS socket. Expanded scans show no delayed private publication before renewed consent. |

The create/first consent scans initially covered SQL/CoT only. The subsequent
expanded readbacks include all four feeds, including their bounded changes; the
narrow earlier scans are not described as feed-privacy proof by themselves.

After renewed consent, the retained canary image opens again. Expanded device
review shows the private metadata-preparation action but omits the lab map-copy
and metadata-send actions. Clicking the separate CoT-lab toolbar entry opens no
lab menu in connected-local mode. This is a native UI observation, not proof of
all possible forged requests or hidden transport entry points.

Confirmed local deletion retains the new canary as a deleted slot at version 4.
The final collection has 21 active records, two deleted slots and zero decisions;
all original active/deleted data and dataset identity are unchanged. The now
unreferenced owned image is removed, and the original source PNG remains exact.
Final frame: 3,550 bytes, SHA-256
`73f41d3b69762d1efc83619e249f5dcfd3977fc0394ce683892a68f71ce90f55`.
Local deletion does not establish remote deletion or secure erasure.

A distinct ordinary **V20-NATIVE-AFTER 1** marker then arrives automatically in
the feed and B's actual Map Items list. The two native controls have new SQL router
records; they did **not** appear in the separate TLS observer. A separate trailing
authenticated classic-CoT source control does appear in that observer. These
channels have different routing/observation scope; feed receipt is not asserted
as observer-stream receipt.

B's **global Overlay Manager** search finds the after-control, then returns zero
rows for eight checked private queries: canary display ID, UUID, owner, comment,
image handle, source-image name and two private-path canaries. Each actual query
field is checked against the intended value and its native XML retained privately.
This is an index check, not forensic inspection of every receiver file/database.

## Final scans and cleanup

Measured observation began **2026-10-28 19:20:56 UTC**, SQL baseline row 3,209 and
observer offset 3,310 bytes. The active private-operation/auto-publication checks
finished before settings restoration. A's original **Auto-publish OFF** and
**Publish Prompt ON** were restored and read back in native UI. The generated
profiles/subscriptions, synthetic controls and private evidence are retained;
there is no claim that every test copy or subscription was erased.

The work was interrupted before cleanup and resumed after the requested session
endpoint. The isolated services consequently remained running during an idle
interval. Final readback at **22:56:29 UTC** covers **658 new router rows**, **96,597
observer bytes**, **129 complete observer events**, **56 canaries** and **23
positions**. It reports **zero canary matches, zero position matches and zero
position-parse errors**, including the 12 feed responses. Auto-publication was not
left ON throughout this extended observation interval. No claim of continuous
hands-on testing during the interruption is made.

Both test hosts were force-stopped, task reverses removed and airplane mode/Wi-Fi
shutdown applied before both emulators received shutdown. The observer exited.
The relay and server exceeded their stop grace period and exited **137**; their
shutdown was not clean. The database wrapper also initially forced exit 137. Restarting only the isolated
database recovered all 658 recorded rows unchanged, with two later cleanup rows
kept separate. PostgreSQL was then shut down successfully with its own fast-stop
command before stopping the wrapper; this does not make the earlier forced stops
clean. The recovery/stop result is retained privately. No host firewall or unrelated container was changed.
Both captured final Android crash buffers contain zero Java FATAL EXCEPTION entries;
this does not exclude caught errors or unobserved native failures.

## Verification scope and next gate

The native, actual-storage, actual-recipient and independent bounded observations
above pass for the exact signed v20/standard 5.6.0.25/Data Sync 3.7.4 combination.
The evidence PR changes only documentation; relative file links and whitespace
checks pass. No core/image suite, app/SDK build, signing submission or release was
run for this runtime evidence. Required PR checks remain separate merge evidence.

Physical v20/OEM behavior, HV Reports/Voice workflows in this combination, arbitrary
failure timing, packet-complete/forensic absence guarantees, trusted source-bound
TMM receipt/application, operational authorization and image transport remain open.
There is still no verified callable third-party Data Sync Android API. This trial
neither enables a production publisher nor completes #25/#26 or all of #7. The
#121 source fix needs a new signed candidate and host/device regression; these
signed-v20 observations cannot be attributed to that later source revision.
