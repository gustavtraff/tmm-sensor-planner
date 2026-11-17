# Activation recovery candidate v21 — #123/#7

**Returned candidate update — 2026-10-29:** [Signed-v21 verification](SIGNED_V21_VERIFICATION.md)
records actual same-certificate update, preserved v2 state, local image/restart,
explicit v1 migration and pending-confirmation access-loss recovery in the isolated
standard host. Queued-worker timing, physical phone and v21 connected gates remain
separate. The preparation-time statements below are historical.

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
tool, not the final submitted commit or the remote signing pipeline.

Root independently ran `sh scripts/check-sdk.sh` against the existing external
5.6.0 SDK with JDK 17 and two Gradle workers: SDK MGRS/publication/ingress probes,
CivDebug assembly/lint, all three app unit tests and the signed development-APK
static checker passed. No actual dispatch or source/feed trust is inferred from
those SDK probes. `:app:assembleCivRelease` including R8 shrinking also passed.

The locally assembled release is **unsigned**. Invoking the signature-enforcing
checker on it correctly rejected the missing signature; no signature check was
weakened. Separate aapt/ZIP checks passed its actual version 21/name, package,
5.6.0.CIV metadata, discovery component, registration, absence of requested plugin
permissions and private-key/SDK packaging boundaries. Unsigned release SHA-256:
`1720fe9322139df90c13c76eba911d724d4f0e7aa858858339b31db5d9e8c933`.
The development signature and unsigned local release do not establish the future
service signing identity or standard-host loadability.

The actual merged source archive commit/SHA-256 and returned signing result must
be recorded separately when produced. No v21 emulator/device trial, signing upload
or release has occurred. The full standalone image-library suite was not rerun
locally for these version-only changes; required CI remains separate evidence.

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
