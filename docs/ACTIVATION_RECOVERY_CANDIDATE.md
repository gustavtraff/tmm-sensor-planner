# Activation recovery candidate v21 — #123/#7

Prepared 2026-10-29 from main `2387427c72f9bcb5395d081eeeb745d660b11b58`,
which includes the [#120 lifecycle fix](https://github.com/gustavtraff/tmm-sensor-planner/pull/121).
This candidate only gives that merged source a distinct Android update identity:

| Field | Candidate |
|---|---|
| Package | `se.tmm.sensorplanner.plugin` |
| versionCode | **21** |
| versionName | `0.1.0-activation-recovery.1` |
| ATAK target | API `5.6.0.CIV`; specific standard-host/device compatibility still needs verification |

The fix releases a pending local-storage operation when session access disappears,
including when access returns before the abort callback reaches the UI. Reopening
requires a fresh read/choice; aborted consent is not resumed. All 30 targeted
Robolectric API 26/35 activation cases passed for the merged fix, including the
previously failing queued read/migration cases. See [the storage contract](VERSIONED_PLANNER_STORAGE.md).

The [signed-v20 offline acceptance](EMULATOR_RECOVERY_V20.md) applies to the earlier
APK associated with submitted source `f9845c89c408d9c3bb941d00adda2c6f1c5c553d`.
It does **not** test this fix or make a v21 APK signed/verified. Existing v20
[connected-runtime results](CONNECTED_SIGNED20_VERIFICATION.md) likewise cannot be attributed to v21. Application ID,
permissions, storage/wire formats, transport and signing configuration are unchanged.
This is preparation for review and signing, not a release or sharing approval.

## Preparation checks

The existing five source-archive boundary tests pass in their isolated synthetic
Git repository (`python3 scripts/test-signing-source.py`). They test the archive
tool, not a final archive of this candidate. The actual submitted commit/archive
hash, local SDK build/static APK checks, signing-service result and returned-APK
checks must be recorded separately when performed. No final source ZIP, full
app/SDK build, emulator, Docker, phone test, signing upload or release was produced
in this version-preparation slice.

## Remaining human signing and install gates

1. Review/merge the preparation PR after required checks. Build and inspect a source
   ZIP from the exact selected merged commit; record its commit and SHA-256. The
   owner chooses whether to submit it to the signing service. Never include local
   SDKs, profiles, certificates, credentials or operational data in the ZIP.
2. Inspect the returned APK: signature, package, versionCode/name, API/discovery,
   permissions and packaging. Record its APK/certificate hashes and verify update
   signing compatibility with the installed candidate; filename alone proves none
   of these properties.
3. Install as an **update** in a controlled synthetic-data environment. Do not
   uninstall existing drafts or downgrade after v2 migration. Existing valid v2
   data should reopen with the same fields, images, UUIDs, revisions and store
   identity. Test v1 Cancel/explicit upgrade separately, preserving exact v1 on
   Cancel and all supported data on approved upgrade.
4. Exercise loss/restoration of local-session access during pending opening and
   confirmation: no automatic write/resumed consent, no permanently busy planner,
   and a new explicit opening remains possible. Deterministic queue tests do not
   replace signed-host/device verification; record any timing case not reproduced.
5. Recheck local edit/map/image and restart/unload flows on the returned candidate.
   Physical standard ATAK-CIV 5.6.0.12/OEM acceptance and controlled connected
   privacy remain distinct gates. No operational server sharing is authorized by
   this candidate, and no callable Data Sync API or image transport is introduced.
