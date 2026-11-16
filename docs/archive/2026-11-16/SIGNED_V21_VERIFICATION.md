# Signed v21 update and activation recovery verification — #128 / #7

Date: 2026-10-29. The owner supplied a new service result in the local TAK resources
collection. It contains the activation-recovery candidate, not the later image-content
implementation from PR #127. The supplied APK and submitted source ZIP were left
unchanged.

## Returned artifact and update identity

| Check | Observed result |
|---|---|
| Package | `se.tmm.sensorplanner.plugin` |
| Version | **21**, `0.1.0-activation-recovery.1` |
| APK bytes / SHA-256 | 587,359 / `281f53f0e8ec15c3a7e0d449c7537ca8811fdd45e3a5a8c0ca681e2de8d3c2d5` |
| Signing certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| Signature/discovery/permissions/packaging | Existing APK checker passes: expected 5.6.0.CIV entry, no requested plugin permissions or packaged SDK/private-key artifacts |
| Installed baseline | Signed v20, `0.1.0-versioned-storage.1`; APK SHA-256 `ae02b633e1db626ad7bc7d62bbf7b44bba04dd24220a71298f469c768c3e7d83` |
| Android update | Same leaf signing certificate; `adb install -r` succeeds without uninstalling; PackageManager reports version 21/name |

The returned `app-civ-release-unsigned.apk` filename is misleading: signature
verification passes. Existing unprotected META-INF metadata-entry warnings remain.
The submitted source is main `ad89af4668c4b4fe003d73ed589b17c36a92bbc6`, ZIP SHA-256
`16d57b4eaae7991857dce9206251fdc638d7a1eb530be72e50cfac6f7b380942`, rechecked unchanged.
The owner/service submission associates this result with that source; these hashes
are not reproducible-build attestation or proof of operational compatibility.

## Standard-host setup and preserved update

One retained synthetic Android API 35 arm64 emulator used standard ATAK-CIV
**5.6.0.25 (238484ee)**, the already documented [graphics fallback](EMULATOR_RECOVERY_V20.md),
2 GiB RAM and two virtual cores. It ran headless with an isolated socket-network
backend, no network peer, Wi-Fi/data off and first-rule IPv4/IPv6 OUTPUT drops.
Airplane mode was normally enabled; the access-loss cases explicitly toggled only
that setting while egress remained blocked. No server, Docker or phone was used.

Before the update, stop ATAK and pull its actual planner state. The baseline has
21 active records, two deleted slots, zero decisions and no active image references.
After the update and host launch, the actual 3,550-byte frame is byte-identical:
SHA-256 `73f41d3b69762d1efc83619e249f5dcfd3977fc0394ce683892a68f71ce90f55`.
ATAK renders restored local markers before opening TMM, and TMM opens the 21-record
list without migration/initialization consent. This tests the retained v2 dataset;
it does not claim v20-to-v21 update preservation of an image absent from that baseline.

## Local image and cold restart

Using the actual native document picker, select the existing generated 1920×1080
red/blue PNG for the first active Scout. Cancel the preview first: the complete
snapshot remains byte-identical. Repeat and explicitly save the image locally.
An evidence comparator reconstructs the entire expected v2 frame using only that
image change and its actual update time: one state-version/revision increment,
new opaque image reference; all unrelated fields/slots, deleted slots, store identity
and history remain exact.

The actual owned PNG is 1920×1080, 10,615 bytes, SHA-256
`4c3ee82aeb4f1cf19bc117bde915c8b4abdabdeafbac5de73f06230f102e9876`.
The original document remains byte-identical to the generated input. The native
large viewer renders the expected red/blue image and accepts Zoom in, Zoom out and
Fit. After force-stop/relaunch, the complete saved snapshot is unchanged and the
Scout's image reopens in the viewer. This is synthetic local provider/viewer evidence,
not a physical OEM camera test or any image-sharing result.

## Access withdrawal during explicit activation

The test harness preserves the complete current synthetic v21 dataset/images,
then changes only disposable test input while ATAK is stopped. It later restores
that exact saved dataset. This is controlled fixture seeding, not a product recovery,
automatic rollback, downgrade or migration of operational data.

1. Temporarily withhold the snapshot. Launch/open TMM: the explicit missing-history
   warning appears and no snapshot is created automatically.
2. While that confirmation is pending, disable airplane mode with Wi-Fi/data still
   off and the egress firewall unchanged. Press **Starta utan historik**: local
   access is invalid, no snapshot is created, and the old choice is not committed.
3. Restore airplane mode and explicitly open TMM again. A fresh warning appears;
   the planner is not permanently busy. Only a new positive confirmation creates
   a valid empty v2 dataset (zero active/deleted slots and decisions).
4. With the host stopped, seed the previously verified 3,482-byte synthetic v1
   fixture and its 70-byte 1×1 PNG with correct guest ownership. TMM shows the
   upgrade warning. **Avbryt** preserves the exact v1 frame.
5. Reopen, withdraw access while upgrade confirmation is pending and press
   **Uppgradera och öppna**. The actual v1 file remains byte-identical. Restore
   offline access and explicitly reopen: a fresh upgrade warning appears.
6. Only a fresh positive choice migrates it. The actual 3,700-byte v2 output matches
   the complete expected migration after substituting only the newly random store
   identity: all 22 records/fields/order/revisions/image references preserved,
   versions start at 1 and decision history is empty. Owned PNG bytes are unchanged.
7. Stop ATAK and restore the preserved current v21 frame and active owned image.
   Readback matches both byte for byte before continuing lifecycle checks.

These cases verify access withdrawal at the **pending confirmation** boundary and
successful fresh re-entry. They do not force the smaller queued-worker/read or
access-return-before-abort-callback timing windows. The earlier 30 targeted
Robolectric cases remain separate deterministic evidence; they were not rerun or
relabelled as signed-host timing acceptance.

## Native unload/reload and cleanup

Native Package Management shows TMM **Loaded**. Choose **Unload Plugin**: it becomes
**Not loaded**, and pulled snapshot bytes remain identical. The normal compatible
plugin **Load** confirmation returns it to **Loaded**. Reopening TMM shows the
21-record list without initialization/upgrade; final frame and owned-image readback
match the preserved v21 dataset byte for byte. The checked crash buffer contains
no Java fatal exception.

Airplane mode and Wi-Fi-off settings plus first-rule IPv4/IPv6 OUTPUT drops were
rechecked. The only owned emulator was stopped after the run; its tracked process
exited. All pulled snapshots, source/owned images, raw UI captures and logs remain
private. No Docker/server or background observer was started.

## Remaining gates

Physical ATAK-CIV 5.6.0.12/OEM camera/provider behavior, arbitrary queued-worker
and callback timing, power loss/rollback, capacity/low-memory stress and signed-v21
connected two-client privacy are not verified here. Previous v20 connected results
remain attributed to v20. Trusted selected-feed ingestion and Data Sync image
transport/association/adoption remain separate work. No operational-sharing approval,
new signing submission or release follows from this evidence.

This evidence-only change runs the existing static APK checker, actual native
runtime actions and independent comparisons of pulled state/images. No product
code/schema changes, SDK build, core/image suite or CI wait is needed/rerun.
