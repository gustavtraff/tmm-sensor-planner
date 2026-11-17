# Returned signed v20: static checks and blocked runtime acceptance — #7/#15

Date: 2026-10-27. The owner supplied the signing-service result associated with
main `f9845c89c408d9c3bb941d00adda2c6f1c5c553d`. This records actual checks, including
the interrupted trial and its resumed graphics controls. It does **not** mark the
[v20 migration/activation gate](VERSIONED_PLANNER_STORAGE.md) passed.

## Returned artifact

| Check | Observed result |
|---|---|
| Android package | `se.tmm.sensorplanner.plugin` |
| Version | versionCode 20, `0.1.0-versioned-storage.1` |
| Host API/discovery | Expected 5.6.0.CIV metadata and plugin entry pass the static checker |
| Signature | APK verification passes; leaf signing identity matches the earlier service-signed candidate |
| Signing certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| APK bytes / SHA-256 | 583,263 / `ae02b633e1db626ad7bc7d62bbf7b44bba04dd24220a71298f469c768c3e7d83` |
| Service build log | One BUILD SUCCESSFUL marker, no BUILD FAILED marker |
| Permissions/packaging | Existing static APK checks pass: no requested plugin permissions, expected registration, no packaged SDK/key artifacts |

The returned filename retains `unsigned` despite having a valid signature. The
static checker reports the existing META-INF metadata-entry signature warnings;
it does not establish runtime host trust or source reproducibility. Source archive
SHA-256 is `4016e783eb01504378b2750613386a9461d1a774431ec685f1e282cbda37d2c1`.
Association with the source submission comes from the owner/service result, not a
claim that these hashes constitute a build attestation.

## First bounded standard-client update trial

Used one disposable overlay of an existing **synthetic** Android 35 arm64 test
AVD, with standard ATAK-CIV 5.6.0.25 (`238484ee`). The original lab profile was kept.
The old SDK host's encrypted shared test databases required removal in the overlay
during standard-host bootstrap; this occurred before the candidate update and
was not migration/cleanup by TMM. No production/phone data was involved.

The standard host was configured with test-only permissions and synthetic location.
ATAK required manual Package Management → TMM → Load → OK to enable the signed
versionCode 18 baseline (`0.1.0-publication-envelope.1`). Its planner opened the
controlled 22-record v1 collection. The baseline file remained byte-identical after
opening. The fixture covers four types/five statuses plus long/near-overlap labels;
one record was deliberately given an owned synthetic 1×1 PNG reference and advanced
revision. The planned migration comparator preserves every field/bit/order/revision
and expects v2 slot versions 1 with empty decision history.

After force-stopping the host, Android `install -r` of the returned **signed v20**
APK succeeded. PackageManager reported versionCode 20 and the expected versionName.
This establishes an actual same-package/signing-identity update install, not just
signature inspection. It does not establish migration success.

The subsequent host startup could not provide a fresh UI hierarchy or screenshot
reliably. No Java FATAL EXCEPTION appeared in that attempt's captured crash buffer.
The guest also failed to finish a diagnostic reboot within the bounded check.
The disposable overlay was stopped. No explicit v20 storage-upgrade confirmation
was reached, and no post-migration frame/image result was verified. Do not assign
a root cause for that stall from these observations.

## Resumed clean-profile graphics controls

The owner authorized resuming after interruption. Reused a separately created
private writable AVD so renderer changes did not require repeated host installs.
The profile contained standard ATAK 5.6.0.25 and the signed **v18 baseline**, not
v20. Test-only startup permissions were configured; no account/server was added.
All tests used Android emulator **37.2.12.0**, build **16428233**, API 35 arm64.

| Renderer/configuration | Stock-host observation |
|---|---|
| Explicit SwiftShader / ANGLE-SwiftShader, Vulkan override disabled | Host exits with a GLThread `IllegalArgumentException: No config chosen` |
| Host GPU, GPU enabled, modest 720×1280 portrait size | Same exception; emulator also warns that host GLES 3.0 falls below the API-35 GLES-3.1 expectation |
| Emulator-recommended `software` mode | Same exception |
| Final software control with TMM **disabled-user** through Android PackageManager | Same stock-host failure, in `GLSurfaceView$BaseConfigChooser.chooseConfig` |

The final disabled-package control makes the fresh-profile graphics failure
independent of executing TMM. It does not prove the cause of the earlier overlay
stall, or prove an emulator-version/driver regression. No unsupported capability
property was forced and no app code was changed to bypass a graphics requirement.

## Isolation and resource cleanup

- One emulator at a time; no Docker containers/server started.
- Used `-net-socket listen=127.0.0.1:5599` instead of the normal NAT backend, with
  no bridging peer; guest airplane mode and Wi-Fi/mobile-data disablement were
  configured. The initial guest route check was empty.
- Only generated/synthetic planner data and images were used. No phone was targeted;
  every ADB command selected the emulator serial explicitly.
- Raw logs, fixture files, generated snapshots and screenshots stay in ignored
  private evidence, never in Git. No credentials, real sensor data or SDK binaries
  are attached here.
- Each owned emulator received shutdown; final emulator process termination was
  confirmed. Private profiles/evidence are retained for diagnosis. No emulator is
  intentionally left running.

## Acceptance status and next step

**Passed:** returned APK static signature/discovery/permission/packaging checks;
known signing identity; actual signed v18→v20 update installation; pre-update v1
baseline opening/preservation.

**Blocked/unverified:** v20 native loading/upgrade dialog, Cancel preserving v1,
explicit migration preserving fields/images/order/revisions, subsequent edits/map
moves/image replace-detach/delete, automatic marker restore, unload/reload and
cold host restart against migrated v2. Existing core/Robolectric/SDK-build results
remain separate evidence and cannot fill this gap. No online/feed/source-context
trial was performed.

Track the environment prerequisite and runtime gate under existing #7/#15 rather
than creating a duplicate product-bug claim. Next use a verified graphics-capable
isolated host or a controlled physical test phone with synthetic data. Preserve
existing data; install as an update, cancel the first upgrade prompt, reopen and
confirm the intended migration, then inspect all fields/images and restart. Older
plugin binaries reject v2 after migration; there is no automatic downgrade/reset
recovery path. Production sharing/upgrade approval remains with the owner.

No source changes, builds, core/image suite, CI wait, signing upload or release
were performed in this resumed acceptance slice. This is a bounded evidence report,
not a fix for the graphics problem or completion of #15.

## Follow-up — 2026-10-28

[Recovered emulator and signed v20 acceptance](EMULATOR_RECOVERY_V20.md) supplies
the previously omitted documented EGL prerequisite and actual Cancel/migration,
edit/map/image, cold restart and unload/reload evidence. The earlier observations
above are historical; their fresh-profile graphics blocker no longer prevents
this bounded runtime slice. Physical and connected-client gates remain separate.
