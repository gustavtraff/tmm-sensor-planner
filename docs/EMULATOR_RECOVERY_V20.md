# Emulator recovery and signed v20 upgrade acceptance — #7

Date: 2026-10-28. This follows the [blocked v20 trial](SIGNED_V20_UPGRADE_VERIFICATION.md).
The signed APK and production source were unchanged. The new evidence supersedes
the graphics blocker for this isolated emulator configuration, not physical-phone
or online acceptance. Implementation issue #15 is closed; that does not itself
establish its signed-runtime gates.

## Recovered prerequisite

The earlier report omitted checking the already documented
[generic-EGL fallback](EMULATOR_TEST.md#graphics-workaround) in the fresh profile.
The official [TAK emulator guidance](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/README.md#using-an-emulator)
describes the `opengl.broken` marker. This was a missed test prerequisite, not a
new TMM graphics fix or proof of an emulator-version regression.

On the retained private synthetic profile, with the TMM package still disabled:

1. Cold-boot Android Emulator 37.2.12.0 / build 16428233 with Android 35 default
   arm64 image revision 2, `-gpu swangle`, 2 GiB RAM and two virtual CPU cores.
2. Confirm no marker at `/sdcard/atak/opengl.broken`; launch standard ATAK-CIV
   5.6.0.25 (`238484ee`). A fresh crash buffer records one GLThread fatal exception,
   `IllegalArgumentException: No config chosen`.
3. Force-stop ATAK, create the empty marker in that guest and relaunch. The stock
   host logs generic EGL configuration `8,8,8,16,0`; the globe renders. Complete
   the empty native device setup and cold-restart the host: the globe renders
   again without the prior EGL fatal exception.

This bounded A/B control establishes a working prerequisite for the following
acceptance. It does not explain the earlier disposable-overlay stall, prove every
renderer works or evaluate field-device graphics quality/performance. No APK was
patched and no signature/API/GLES-capability check was bypassed.

## Signed baseline and explicit upgrade

The returned [v20 artifact record](SIGNED_V20_UPGRADE_VERIFICATION.md#returned-artifact)
retains the exact APK, certificate and submitted-source hashes. This trial used
that same versionCode 20, `0.1.0-versioned-storage.1` service-signed APK.

- Re-enable signed v18 (`0.1.0-publication-envelope.1`), load it through native
  Package Management → Load → OK, then stop the host before seeding its previously
  absent planner directory. Use the known synthetic 22-record v1 fixture and its
  owned 70-byte 1×1 PNG; restore correct guest application ownership.
- V18 opens 22 records; a pulled committed file is byte-identical to the 3,482-byte
  fixture. Four device types, five statuses and long/near-overlap labels are covered.
- Install signed v20 with Android update installation: Success; PackageManager
  confirms versionCode/name. Opening TMM shows the explicit upgrade dialog.
- Before choosing and after native **Avbryt**, the committed file remains exactly
  equal to v1. Reopen and choose **Uppgradera och öppna**: the 22-record list opens.
- Pull the actual 3,700-byte migrated frame. The real codec and an evidence-only
  comparator match the complete expected frame after substituting only the random
  store identity: every field/bit, order, revision and image reference is preserved;
  all slot versions start at 1 and decision history is empty. The owned PNG bytes
  are unchanged. The migrated image opens in the actual viewer.

The comparator validates actual guest output; it is not an invocation of the
migration method used as a substitute for native acceptance.

## Local mutation checks

All actions use the signed native UI and actual pulled snapshots:

- Rename the first device from synthetic `Mini 131` to `Mini 131 V20`. Its state
  version advances 1→2 and revision 2→3; only the intended ID/update fields change.
- Use **Flytta på kartan**, drag under the reticle, **Använd plats**, then
  **Spara lokalt**. The displayed MGRS changes; the persistent position changes,
  state version/revision advance once and unrelated fields/records remain exact.
- Replace its image with a generated metadata-free 1920×1080 PNG through Android's
  Downloads document provider. Cancel the pending preview first: snapshot bytes
  remain unchanged. Repeat with explicit Save: a new opaque image reference is
  committed at the next state version/revision. All other state remains exact.
  The actual owned output is 1920×1080, 10,615 bytes. The old unreferenced image is
  removed after the confirmed save; this is local cleanup, not remote deletion.

Exact mutation checks reconstruct the expected full frame from the preceding
snapshot with only the requested edit and actual update timestamp. Unchanged
store identity, other slots, history and nonedited fields are compared bytewise.

## Restart and restored image

The actual large viewer accepted Zoom in, Zoom out and Fit controls. Close it,
center the device, then force-stop and relaunch ATAK. Before any subsequent TMM
opening or **Visa på kartan** action, the map already shows restored local TMM
symbols/labels. Native map zoom exposes the cluster including the renamed device.
This is visual restore evidence, not a machine-count of every rendered symbol.
The committed snapshot is byte-identical to its pre-restart state. Opening the
renamed device and its viewer displays the red/blue synthetic replacement again.
There was no repeated upgrade prompt and no new Java fatal in the checked buffer.

Local image detach then advances the selected slot/revision once, sets only its
image reference to null, and cleans the now-unreferenced owned copy. Deletion
Cancel leaves the complete frame unchanged. Confirmed local deletion retains the
UUID as a deleted slot at version 6; 21 active records and zero decisions remain.
Exact full-frame checks preserve every other slot and the original store identity.
No server/recipient deletion is implied.

## Native unload/reload

Package Management's Loaded status → Unload Plugin changes TMM to **Not loaded**.
The committed frame remains byte-identical. The normal compatible-plugin Load
confirmation returns it to **Loaded**; opening TMM shows 21 active records without
another upgrade prompt, and the committed frame remains identical, including its
deleted slot. The document-provider original PNG is also byte-identical after the
image operations. This verifies normal unload/reload, not arbitrary process-death
or power-loss timing.

## Isolation, checks and remaining gates

Only one owned headless emulator ran; no Docker/server or phone was used. The
emulator used `-net-socket listen=127.0.0.1:5599` with no peer instead of normal NAT.
Guest airplane mode was enabled, Wi-Fi/mobile data disabled and the route check
was empty before stock-host launch. Location was set to a synthetic fixed point.
The ending route check and airplane setting were recorded. No server profile,
account, certificate or operational data was introduced. Private fixture files,
UI captures and logs stay outside Git. The guest was stopped after verification;
its synthetic state is retained privately.

The concrete native checks above pass on this one standard 5.6.0.25 emulator and
this exact signed v20 APK. Documentation whitespace and relative file links were
checked. No product code, domain/data contract, signing input or golden fixture
changed; no build, core/image suite or CI wait was required/rerun for this evidence
PR. No new signing submission is needed for the graphics-marker correction.

Still unverified here: physical 5.6.0.12/OEM/provider behavior for v20, fresh/missing
or damaged storage activation, capacity limits, gesture/orientation/low-memory
stress, arbitrary failure timing, signed connected two-client privacy and trusted
feed ingestion/import application. Previous bounded evidence remains separate.
This result does not authorize production sharing, claim a supported Data Sync
Android API, implement image transport, or close the complete compatibility issue.
The next runtime slice can use this recovered isolated setup for the explicitly
scoped signed connected-client gates; a phone regression remains necessary before
operational upgrade approval.
