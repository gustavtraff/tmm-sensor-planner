# Data Sync permissions, conflicts and removal — 2026-10-25

Tracking: #25, following merged PR #88 and the
[controlled reconnect trial](DATA_SYNC_RECONNECT_TRIAL.md).
**Normal read-only publication was blocked; two different same-UUID edits produced
a native conflict dialog; explicit selection preserved the chosen metadata.
Local deletion left the server item downloadable. Server deletion removed the
feed association and reached the other client.** These are bounded operator-path
results for review, not completed production TMM integration or permission bypass
testing. No operational phone/server was used.

## Setup and evidence boundaries

Source baseline: main `deddf39`. Both retained clients used standard ATAK-CIV
**5.6.0.25 (238484ee)**, Android **15 / API 35**, Data Sync **3.7.4 (58746181)
[5.6.0]** and service-signed TMM **versionCode 15 / 0.1.0-device-details.1**.
Installed versions were read back on both guests; APK/signing identities are
unchanged from [the feed trial](DATA_SYNC_FEED_VERIFICATION.md). Server: isolated
TAK Server **5.8-RELEASE-92**, with retained synthetic data.

Guests retained loopback-only IPv4/IPv6 egress and the fixed task TLS/HTTPS reverses.
Mutual TLS, CA/hostname checks and normal plugin loading remained enabled. Private
drafts were neither edited nor converted to ordinary map items. No private Data
Sync action, callable API or REST publisher was invented. The exact manual
publication-copy path from [ADR 0011](decisions/0011-sensor-point-data-sync-lab.md)
was used, with automatic publication off. Read-only Mission API/SQL observations
were independent checks, not a substitute publishing route.

Initial startup did not meet the test preconditions: the owner role control was
missing and B's first exported baseline still held the earlier Active metadata.
After verified API readiness and deliberate precondition cold starts, role controls
appeared and B's fresh baseline passed `sensor-pir`. An unconfirmed owner Save also
left the server role unchanged until the separate Edit Feed Details confirmation
was accepted. These preliminary states were not counted as successful tests or
assigned a root cause. Check actual roles and exported content before testing;
visible feed lists and requested settings alone are insufficient.

## Distinct concurrent fixture

The offline tool now adds `pir-conflict` / `sensor-pir-conflict` to both envelope
families. Base, update and conflict share the PIR UUID, with three distinct payloads.
The new variant is Deployed with a 90-degree bearing, a CONCURRENT comment,
updated time plus 120 seconds and a different exact latitude. A uses the existing
Active/zero-bearing update. All 15 fields use the existing core encoder/decoder;
outer type, rounded-position rule and image/reference exclusions are unchanged.

The earlier five identities/payloads were compared with the preceding generated
fixtures and remained unchanged. Each variant rejects the others in the strict
checker. This adds test data, not causal versioning, a production wire schema,
snapshot migration or an automatic conflict policy. Republishing the fixed base
was a deliberate test reset, not approval of stale production updates.

## 1. Read-only publication boundary

The actual permission feed was the previously B-owned empty
`tmm-ds25-nontarget-20261024`. B set its default role to read-only and confirmed
the server update. Server readback showed only MISSION_READ. A then joined with
Download & sync; independent subscription-role readback showed one owner and one
MISSION_READONLY_SUBSCRIBER with only MISSION_READ.

At **12:25:39 UTC**, A's normal feed dashboard withheld Add Items. Selecting the
empty Map Items category did not expose a publication route. The feed retained
zero content members. The read/write selected-feed workflow on the same A client
remained usable in the following phase. This establishes native UI prevention
with actual assigned roles; no forged request/HTTP 403, revocation race or attempted
modification/deletion of existing read-only content was tested.

A newly created `tmm-ds25-readonly-20261025` had no default-role field during startup
and was **not** used as read-only evidence. It remains an empty setup artifact.
The original empty permission feed's default was restored to read/write afterward.
A's local Subscribed checkbox was turned off during cleanup; the server role list
still retained its read-only assignment. Do not interpret a local subscription
toggle as proven permission revocation. No role bypass or operational access was
involved.

## 2. Concurrent same-UUID edits

Both clients began from the checked Planned base for
`00000000-0000-4000-8083-000000000003` in `tmm-ds25-selected-20261023`.
B's task ports were temporarily TCP-rejected and its reverses removed; zero
established task sockets were checked. Both changes used normal Local SD Copy
import and explicit selected-feed overlay checkbox → Done.

| UTC | Independent observation |
|---|---|
| 12:28:16 onward | B was offline, imported its distinct Deployed/90-degree version and explicitly queued it. B's own export passed `sensor-pir-conflict`; the server still passed the base case. |
| 12:31:01–27 | A explicitly published its different Active/zero-bearing update. Server export passed `sensor-pir-update`; selected membership remained one PIR and comparison `tmm-ds83-auto-other` had none. |
| 12:31:42 onward | B's reverses were restored and precisely the temporary TCP rules removed. Its actual CoT socket re-established. |
| Before 12:34:00 | B displayed **Feed Change Conflict – 1 of 1**, offering both client changes. The server remained on A's update in the sampled reads while the choice was pending. Accessibility dumps failed for this dialog; a fresh private screenshot supplied the actual observed controls. |
| 12:34:00–36 | The operator explicitly selected A's server version and Done. Fresh B and server exports both passed the update checker; B's export rejected its divergent queued case. Membership stayed one selected-feed PIR and zero in comparison. |

B's prior export and both source variants were preserved privately before choosing.
The native dialog showed authors/relative ages, not a TMM field-by-field comparison.
This does not implement or verify durable conflicting TmmDraft storage, recovery
of a losing edit after app restart, authenticated TMM authorship or every concurrent
edit order. Those remain #15. Native conflict handling must not be silently treated
as the plugin's own import policy.

## 3. Cancellation, local deletion and server removal

| UTC | Action and actual result |
|---|---|
| Before 12:36:34 | Local-only deletion was selected then Cancel pressed. The row and server membership remained; no deletion was inferred from opening the dialog. |
| 12:36:34–54 | B confirmed Delete local content **on**, Delete from server **off**. B's item became Available; server membership and exact update remained. A's independent new export also passed the update checker. |
| 12:40:59 | Dashboard Download Items opened Pending Downloads. The item's Download → OK restored B's content; a new export passed the exact update checker. This was the missing-item download path, not the already-current Allow Full Sync dialog from PR #88. |
| 12:42:02–08 | B confirmed Delete from server **on**, Delete local content **off**. Server membership dropped from five to four, with zero matching PIR UID/events in the selected-feed CoT result; comparison retained its two native controls. The server recorded REMOVE_CONTENT at 12:42:02.439 UTC. |
| After notification | A's actual feed item list had four controls and no PIR. B's selected-feed-only list also had those four. |
| 14:52 onward | After an approval-review usage-limit interruption, the server still had four members and no PIR. Restoring B's non-feed view did not put it back in the feed. Global overlay search still returned two rows with the PIR label, one with a coordinate description and one with a package description; this is not evidence of two distinct UUIDs. No fresh retained-copy export was obtained from the selected-feed menu, so none is claimed. |

Removal here means removal of the current **feed association/content view**. It
does not prove secure erasure, revocation of previously downloaded copies, removal
from every host overlay/history, or a durable TMM tombstone. Private exports were
intentionally retained. No automatic re-add was observed in final server reads,
but offline resurrection/concurrent deletion permutations remain untested.

## Verification, privacy and next gate

- `python3 scripts/test-datasync-metadata-probe.py` passed: six cases per family,
  exact checking, three distinct same-UUID versions and mutual wrong-case rejection,
  existing bounds, safe errors, unchanged inputs and overwrite refusal.
- `sh scripts/test.sh` passed: core/forms/import/share review, metadata codec,
  snapshot/storage, separate-process reopen and synthetic 100-device checks.
- Sixteen checks on actual server/recipient captures produced the expected
  success/rejection outcomes. New ZIP timestamps and one-event/manifest provenance
  distinguish the actual B/A exports from fixtures or stale generated packages.
- Independent BEFORE, RECONNECTED and AFTER TLS controls reached the observer
  twice each. The final AFTER query and matching result were also verified in B's
  actual global search. The observer held no full routed PIR event; ordinary CoT
  routing was not substituted for feed delivery.

A's private snapshot remained byte-identical, SHA-256
`4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
The final **14:57:51.226 UTC** scan from server row **1828** covered **593 rows**,
**86,292 observer bytes / 125 complete events**, **25 private canaries / five
positions**, with zero matches and zero position parse errors. Feed headers/CoT
and applicable 24-hour changes covered the selected, comparison and permission
feeds; the unused setup feed's header/CoT were also checked. Numeric positions
were compared in server geometry/routed events. The window includes the tool-limit
pause; these remain bounded resources/encodings, not universal absence proof.
Both final Android crash buffers had zero FATAL EXCEPTION entries. Failed UI dumps
and startup preconditions above were recorded separately, not relabelled as passes.

Raw UI/screenshots, API/SQL reads, generated inputs, snapshots and exports remain
ignored under `private-data/datasync-acceptance-25/`. No real data or credentials
enter Git. No image suite, full APK build, signing submission or CI wait was run.

The bounded operator matrix now includes roles, conflicts and removal in addition
to PRs #85/#87/#88. Review this evidence and decide the intended integration route
before #14/#15 production work. No supported callable third-party Data Sync Android
API has been identified. File export alone must not mark a draft Shared or a
recipient durably imported. Keep #25 open for human review/route disposition;
production publishing, durable import/conflicts and images remain #14/#15/#26.

## Cleanup

Cleanup completed at **14:59:45.382 UTC**, after the tool-limit interruption.
The empty permission feed's original read/write default was read back restored;
its read-only assignment fixture and the unused empty setup feed were retained as
described above. B's non-feed view was turned back on. Temporary TCP rejection
rules had been removed and checked; both task reverses were removed and simulated
Wi-Fi disabled. The owned emulators, observer and relay/server/database were
stopped, with a confirmed clean fast PostgreSQL shutdown. Synthetic volumes and
private evidence were retained. No unrelated workload was changed. Cleanup
traffic is outside the final scan counts.
