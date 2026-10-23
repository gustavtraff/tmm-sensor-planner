# Connected local privacy trial (#4 / #7)

This procedure tests private drafts while ATAK is connected to an isolated test
server. It does not enable production synchronization or authorize operational
data sharing. Use synthetic records and a separate test environment only.

## Signed standard-client follow-up — 2026-10-23

Service-signed version 15 was tested on two standard ATAK-CIV 5.6.0.25 emulators
against the existing isolated TAK Server 5.8-RELEASE-92 lab. Before/after controls
reached the actual recipient, server and independent decrypted observer; no tested
draft/image/position canary was observed in those channels. Consent, local edits,
images, deletion, connectivity transitions and lifecycle checks are recorded in
[the exact version 15 evidence](CONNECTED_SIGNED15_VERIFICATION.md). This newer
standard-binary result is separate from the historical SDK trials below. Human
review, isolated physical-client testing and broader acceptance gates remain open.

## Candidate behavior

Offline operation remains the default. When connected, open **TMM Sensor Planner**
and choose **Starta lokalt test** after reviewing the warning. **Avbryt** leaves
connected local planning locked. The list identifies the test mode. Consent lasts
only until plugin **Unload** or ATAK process restart; it is not stored in settings.
Connected startup does not restore private markers until consent is given again.
Saved drafts and images remain local and retain their existing format.

TMM's metadata-sharing action is hidden and its CoT lab is blocked throughout the
trial, even after returning offline. This restriction concerns TMM's tools; ordinary
ATAK self-location, contacts and other plugins can still send their own traffic.
There is no automatic publication of TMM drafts when connectivity changes.

## Controlled test procedure

1. Record candidate hash, signing certificate, host full builds, Android version and
   server build. A developer-host test is distinct from a service-signed standard
   client test. Never replace a phone's standard ATAK with the developer host.
2. Use two empty synthetic clients and a server with no operational connections.
   Keep private profiles, certificates, raw captures and screenshots outside Git.
   Verify Android reports an actually connected network, rather than merely clearing
   the airplane-mode flag. Preserve any additional guest/host egress restrictions.
3. Connect both clients and an independent decrypted CoT observer. Send an ordinary
   synthetic positive control; verify it reaches the server and recipient before
   interpreting an absence of private drafts as meaningful.
4. Cancel connected-trial consent; verify no local list/layer is loaded. Then opt in.
   Create synthetic drafts with unique ID, owner and comment canaries. Edit and move
   one, attach a synthetic image, reopen it, and delete another. Verify no sharing
   action is available and the separate CoT lab refuses to start.
5. Check the recipient's map/object list, server records and decrypted traffic for
   draft UUIDs, labels, comments, positions and image names/handles. Do not rely on
   encrypted packet contents or an empty database alone. Bracket observations with
   positive controls before and after; record the actual observation interval.
6. Restart ATAK and Unload/reload TMM. Verify consent resets, connected startup does
   not render drafts, opting in restores saved data, and no duplicate layer appears.
   Check that returning offline within an opted-in session does not re-enable labs.
7. Stop only the task's lab resources. Keep #4/#7 acceptance outstanding for any
   missing runtime, standard-binary, physical-client or coexistence checks.

## Verification status

Candidate: `0.1.0-connected-local-trial.1`, versionCode 9, based on main `7927e2c`
(including #51). Final combined Android Debug APK SHA-256:
`9a16440e98ec4bcd3b72a5b7daefaf089897cff262d408c469a429b04771113a`.

Automated checks on the combined source passed: `sh scripts/test.sh`, all 40
independent image/coordinator/session tests and image lint, all three app unit tests,
SDK debug/release assembly, app lint and the debug APK contract/signature/permission/
packaging checker. Release assembly is not service signing or standard-client loading.

### Initial connected runtime — 2026-10-18

Two AOSP API 35 arm64 SDK developer clients ran ATAK-CIV 5.6.0.24 against the existing
isolated TAK Server 5.8-RELEASE-92 lab. Android reported connected Wi-Fi and airplane
mode off on both clients. Guest IPv4/IPv6 OUTPUT accepted loopback first and dropped
everything else; TLS used only the synthetic loopback profile and ADB reverse relay.
No operational server, phone or feed participated. The unused lab HTTP API role was
stopped after initial server startup; this was a CoT observation, not a Data Sync test.

This first debug APK, before integrating #51 and closing any pending sharing dialog
at consent, had SHA-256
`001c3b5767889de9b2f7902ff9c6fd638c93bf72fa27eb986a75a836fbe5b72b`.
Keep its results distinct from the final combined binary:

| Check | Observed result |
|---|---|
| Consent | Cancel left the planner locked; explicit consent opened an empty list marked `testläge` |
| Local actions | Created a canary draft, changed its comment and longitude, attached a generated PNG, then created and confirmed deletion of a second draft |
| Server / decrypted observation | Successive scans at 16:10–16:15 UTC found no draft UUID, identity, owner, comment, local image handle/name or moved-position match; scans included both identities before deletion |
| Positive controls / recipient | Unique control event UIDs at 16:03, 16:06 and 16:16 UTC reached the independent TLS observer; B displayed a control-source contact, and its global `TMM-PRIVATE` object search had zero rows after local operations |
| Retrospective capture check | All three control UIDs remained present in 14,497 captured bytes; all 12 current draft/image/path canaries were absent. The retained server snapshot was negative. Raw captures and synthetic snapshots remain private |
| Lab block | Saved-device review omitted metadata sharing; opening the separate CoT tool did not open a lab dialog. Returning offline within the opted-in session still did not open the lab |
| Restart / lifecycle | Cold process restart required new consent and preserved snapshot bytes exactly. Normal Unload/reload also required new consent. The retained draft and its local image reopened |

### Final combined-artifact confirmation — 2026-10-18

The final APK identified above passed actual SDK-host startup, explicit consent,
unchanged snapshot bytes after installation, saved review and local image reopening.
Its form included main's #51 date/time controls. The two-client connected privacy
check was then repeated on this binary, with the same SDK hosts and isolated server:

- Both Android clients again reported connected Wi-Fi and airplane mode off, with
  loopback-only IPv4/IPv6 egress and only synthetic TLS profiles.
- A unique ordinary contact control at **20:15:44 UTC** reached the server,
  independent decrypted observer and B's actual global object list.
- Changed the retained draft's comment and longitude, saved locally, created a
  second disposable draft through the form, then confirmed its local deletion.
  The retained image handle remained local; the image had reopened successfully.
- Scans after move/create/delete and at **20:17:59 UTC** found no canary match or
  moved-position event. The final scan contained 17 new server rows; retrospective
  checking of 6,771 captured bytes found none of the 16 actual draft/image/path
  canaries accumulated across both runs. B's global private-ID search had zero rows.
- A differently labeled ordinary contact control at **20:17:44 UTC** also reached
  server, observer and B. Both source and event UIDs were checked against server
  records and capture, so this was not a cached before-control. Both guest crash
  buffers had zero AndroidRuntime fatal entries for this final run.

The interrupted lab initially failed to restart PostgreSQL because of a stale Unix
socket lock. With `pg_ctl` reporting stopped and no PostgreSQL process present, only
the stale socket/lock was removed, preserving the database. The server was then
restarted with the database healthy. Early failed connection/control attempts are
not passing observations; the final interval began only after real routed controls.
The normal server roles were retained during this final CoT-only observation; no
HTTP/Data Sync or companion workflow was exercised. The task observer, both guests
and the three task containers were stopped afterward. Raw logs, profiles, captures
and screenshots remain private; no signing submission or release was performed.

### Remaining gates

The service-signed versionCode 10 APK from reviewed main `d09ac31` contains this
change, #49/#50, #51 and the #62 resource correction. Its bounded standard-client
image/lifecycle and egress-blocked session-UI checks are recorded in [the candidate verification](STANDARD_CLIENT_VERIFICATION.md#latest-signed-candidate--2026-10-18).
The older versionCode 7 APK does not contain those changes. Keep #4 and #7
open for standard-binary two-client privacy, physical-client acceptance and the
remaining companion workflows. No operational-network trial is approved.

The SDK trial also exposed the pre-existing image-copy notice resolving through host
resources instead of plugin resources; #62 tracks it. A generated image saved and
reopened, but the size disclosure was wrong. PR #64 fixes the notice, now checked
in both the SDK host and the returned versionCode 10 standard-host candidate before
the next phone image trial. No claim of full A05 or A08 acceptance follows from this run.

## Current evidence and next gate — 2026-10-22

The owner-reported version 15 offline phone test passed the local workflow; see [PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md). It does not close the connected privacy gate. The next priority is the signed standard-binary two-client procedure in [NEXT_STEPS.md](NEXT_STEPS.md#1-verify-connected-private-drafts--4-with-7), using an isolated server and positive controls, before considering an authorized phone online trial.
