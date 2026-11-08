# Offline lifecycle and rotated-map acceptance preparation — 2026-10-25

Tracking: #12, #52 and #66; supporting A02/A05/N02/N08. **L01–L07 were later run
on signed v28; see [the execution record](#execution-record--2026-11-08). L08/L09
remain NOT RUN.** The original preparation text follows. The owner requested preparation without
starting another emulator while the parallel #25 agent uses the two-client lab.
No emulator, Docker service, Gradle build or local unit suite was started for this
preparation. Existing lab clients, tunnels, profiles and containers were not changed.

This protocol supplements [image inspection](IMAGE_VIEWER.md),
[map placement](MAP_POSITION.md) and the bounded
[owner-reported phone acceptance](PHONE_ACCEPTANCE.md). It is not a new acceptance
result, a privacy/transport trial or permission to close those issues.

## Candidate and resource gate

Use the existing service-signed candidate first; no new APK or signing submission
is needed solely to run this protocol. Both available files were freshly checked
with streaming SHA-256 during preparation:

| Input | Expected identity / SHA-256 |
|---|---|
| TMM | VersionCode 15 / `0.1.0-device-details.1`, source `b643ee633cee2b9d16c4333fa7f14b7a0931622b` |
| TMM APK | `221017dd9f2eeb8d5c46ab2fd1db235f2644059f480fe7874865dc4e8fdd7645` |
| Standard ATAK | 5.6.0.25 (238484ee), `com.atakmap.app.civ`, versionCode `1792077359` |
| Standard host APK | `5bada2d865f937bbcb295bcf08760a8bd4765902c91dc5511738d480c0b6b8c6` |

Preparation source baseline: `7f97393fccb7f7ba2b54d8bb3e81b397fd183661`.
No changes to `app/`, `images/` or `core/src/main/` were found between that baseline
and the candidate source. This source comparison and the file hashes are not a new
signature verification, installation, runtime check or reproducible-build claim.

When runtime work is resumed by the owner:

1. Confirm the other agent has released the lab resources. Do not stop its guests,
   Docker, ADB server, tunnels or builds to make room. On the current 8 GiB host,
   use at most one additional task-owned guest after the two-client work is stopped.
2. Create a separate empty AVD, user directory and unused emulator port. Record its
   actual Android release/API/full build, emulator version, RAM, resolution/density,
   host/plugin versions, hashes and signing certificate. Recheck APK signatures and
   normal standard-host loading; do not substitute the developer host.
3. Use airplane mode, disabled Wi-Fi/data and verified guest IPv4/IPv6 egress drops.
   Configure no server/feed/profile and no ADB reverse tunnel. No Docker is needed.
   Target every ADB operation at the recorded task serial.
4. Do not overlap Gradle/native image tests with runtime work. No product or domain
   change is proposed here; choose regression checks later only if a defect is fixed.

## Synthetic baseline and evidence

Create a Mini and a PIR through the normal form, using conspicuous synthetic IDs,
owner/comment text, supported synthetic positions and a known PIR bearing. Attach
one generated asymmetric grid PNG; retain a visibly different generated replacement.
Use local files only, with distinct source hashes and recorded pixel dimensions.
No real photograph, location, account or server package belongs in this trial.

After saves settle, retain private baseline copies of `drafts.bin`, its SHA-256,
all draft fields/UUIDs/revisions and owned PNG filenames/bytes/hashes. Record source
hashes separately from normalized copies. Use the actual snapshot reader or a
checksum-validated readback; screenshots alone do not establish data preservation.
Keep all raw captures, UI trees, snapshots, APKs and source/provider paths in ignored
private storage. Publish only synthetic summaries, versions, timestamps and results.

For every read-only/cancel/Unload case, compare saved bytes after work settles,
after reload and after a cold ATAK restart. Record UI observations and crash-buffer
results. An unchanged early read is insufficient if a queued callback may still run.
Do not infer bitmap release or a leak-free heap solely from a dismissed window.

## Cases and expected observations

| ID | Procedure | Required observation | Status |
|---|---|---|---|
| L01 | Open the saved image, zoom/pan/Fit, then Close; repeat with Android Back | Return normally; saved snapshot, owned PNG and original source bytes unchanged | PASS, 2026-11-08; zoom by buttons |
| L02 | Choose the replacement, enlarge its pending preview, then Back/Close; cancel the preview | Inspection returns to the same explicit Save/Cancel preview; Cancel keeps the previous image/reference and removes only the uncommitted copy | PASS, 2026-11-08 |
| L03 | Keep the saved-image viewer active and unload through the host Plugin Manager | Viewer and TMM tools close; no stale viewer reappears; reload restores tools once and the original saved image | PASS, 2026-11-08; lab route |
| L04 | Keep DocumentsUI active, unload TMM, then return Cancel; repeat with a selected synthetic file if the route permits | Late picker results cannot create a preview or commit after Unload; saved fields/image remain unchanged | PASS, 2026-11-08; lab route, both variants |
| L05 | Keep an unconfirmed replacement preview, or its enlarged viewer, active and unload | No implicit Save; pending work settles without deleting the saved image or reopening dialogs; reload/restart preserves the baseline | PASS, 2026-11-08; lab route, preview only |
| L06 | Edit ID/owner/comment and a PIR bearing without saving; open map selection, pan, then Cancel; repeat with Back | Same unsaved form values and original accepted position return; storage remains unchanged. Cancelling the whole form then discards those edits | PASS, 2026-11-08; ID field only, Scout |
| L07 | Keep map selection active over an unsaved form and unload | Sight, controls and hidden editor are disposed; no late editor returns; reload/restart preserves saved data and registers each tool once | PASS, 2026-11-08; lab route |
| L08 | Repeat map selection at actual map headings 0°, 90°, 180° and 270°; pan/zoom and cancel at each heading | Sight tracks the selected map location; Cancel preserves form/storage, without confusing map heading with device orientation or bearing | NOT RUN |
| L09 | At a verified rotated heading, choose Use position; inspect storage before Save, then explicitly Save once | Use alone changes only the form. Save preserves UUID/createdAt/image/unrelated fields, advances revision once and stores the selected position; reopen/restart agrees | NOT RUN |

For L03–L05/L07, **prove the target is still active when Unload occurs**. Closing it
first and then unloading does not exercise the intended case. Record how Plugin
Manager was reached and whether the target window and, for L07, its hidden editor
still existed immediately before the normal Unload action. Do not count a direct
call to the plugin's `onStop()` as a standard-host runtime result.

The inspected standard-host manifest declares
`com.atakmap.android.update.AppMgmtActivity` non-exported. Do not assume an ordinary
shell intent can open it. Any diagnostic root/instrumented navigation must be
verified on that exact host, labeled as a lab-only route and leave the signed APK
unchanged; it is not a supported public integration API or proof of a field-user
navigation path. If no route preserves the active target, mark the case BLOCKED
with that exact limitation rather than reporting it as passed.

Cancellation within an editing session must preserve unsaved form values. Unload
is different: the contract disposes the editor and preserves **saved** data; it does
not promise that uncommitted edits survive Unload. These cases deliberately avoid
Unload after an explicit Save has begun. A committed or ambiguous in-flight Save
requires separate ordering/readback evidence, not an unconditional unchanged-file
expectation.

For L08/L09, record the actual map heading and accepted coordinates. Device screen
rotation is not map rotation. Use verified SDK/readback or stock grid evidence to
relate the sight and selected point. A displayed one-meter MGRS cell alone cannot
prove exact floating-point camera coordinates; state that precision limitation if
exact center readback is unavailable. Do not introduce a guessed ATAK rotation API.

## Execution record — 2026-11-08

Run 22:14–22:35 CET by an agent, on one task-owned emulator. No Docker, server,
tunnel or phone was used. Guest IPv4/IPv6 egress was loopback-only, with airplane
mode on and Wi-Fi and data off. The guest has been stopped and its AVD copy removed.

| Field | Value |
|---|---|
| TMM | Service-signed v28, `0.1.0-configured-receive-trial.1`; installed APK SHA-256 `35e2727b493b0d6273aa5449a1120c86e96ce198ea41a0d60d21b6f0090c2cd6` |
| Host | Standard ATAK-CIV 5.6.0.25 (238484ee) |
| Guest | Android 15 / API 35 arm64 AOSP emulator image, Emulator 37.2.12, 1536 MiB, 720×1280 at 240 dpi override, shown landscape |
| Starting data | 22 synthetic drafts, one with an owned PNG; `drafts.bin` SHA-256 `c18879892d62634dd6a4b116b43e483fd1b6206bfa819114219145afdaba76d5` |

**Deviations from this plan.** The candidate is v28, the current signed build, not
v15. The guest is a private copy of the retained signed client, not an empty AVD,
so the synthetic baseline was inherited rather than created through the forms. The
retained AVD itself was not started or changed.

**Plugin Manager route.** `AppMgmtActivity` is non-exported, so it was opened with
`am start` from the emulator's root shell. This is a lab-only route, not a field-user
path. Unload and Load then used the host's normal row and confirmation dialog.
For L03, L04, L05 and L07 the target window was confirmed active immediately before
that navigation, through `dumpsys window` and, for the picker, the focused activity.

| Case | Observed |
|---|---|
| L01 | Zoom in, zoom out, Fit, Close and Android Back all returned to the same review |
| L02 | Enlarged pending preview returned to the same Save/Cancel preview through Close and Back; Cancel removed the uncommitted copy |
| L03 | Viewer and review closed; ATAK windows went from three to one on return. Reload listed 22 drafts and reopened the saved image |
| L04 | With DocumentsUI active, Unload then Back returned to ATAK with no preview. In the second variant, a synthetic file selected **after** Unload created no preview or copy |
| L05 | Pending preview closed without an implicit Save. The uncommitted copy was removed after Unload |
| L06 | Unsaved ID text survived map-selection Cancel and Back, with the accepted position unchanged. Cancelling the whole form discarded it |
| L07 | Sight, controls and hidden editor were disposed. No editor returned |

After every case, all five private TMM files (drafts, inbox, their locks, the owned
PNG) matched the starting SHA-256 values, apart from L02 and L05's expected temporary
copy while its preview was open. After L05 they still matched after settling, after
reload and after a force-stop and cold ATAK start, when the 22 drafts and the saved
image were again available. Rendered map markers were not checked. The crash log
stayed empty for the whole run.

**Not covered.** L08 and L09 need actual map headings. `adb input` offers no verified
two-finger rotation and this plan forbids guessing a rotation API. L05's enlarged-viewer
variant, L06's owner, comment and PIR-bearing fields, and pinch gestures were not
run. Each case ran once, on an emulator. A force-stop is not a phone restart. This
does not cover physical phones, TalkBack, OEM picker apps or memory pressure, and it
says nothing about a leak-free heap.

## Execution record template

For each case record: date/time/timezone, exact candidate/host/Android versions,
starting baseline hash, actions and actual heading/viewport, outcome
(PASS/FAIL/BLOCKED/NOT RUN), post-settle/reload/restart storage and image comparisons,
UI/crash observations, evidence location and residual limitations. Reset only this
task's synthetic baseline between cases. Do not mark a case passed from source
inspection or earlier tests of another artifact.

Stop the task guest and remove only its temporary resources after execution.
Report any reproduced defect under its owning issue before changing code; keep
transport, Data Sync, signing and other agents' adapters outside this scope.
Physical phone gestures/TalkBack, unrestricted networks, companion coexistence,
memory stress and complete A01–A10 acceptance remain separate gates.
