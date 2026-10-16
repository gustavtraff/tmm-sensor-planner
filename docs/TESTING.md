# Tests and gates

For the private first loading test, follow [LOCAL_TEST.md](LOCAL_TEST.md). It distinguishes
the developer host from the standard app and keeps the test disconnected from feeds.

`python3 scripts/test-github-auth.py` tests token verification using synthetic data
and without network requests.

`python3 scripts/create-attachment-fixture.py /path/to/new-fixture.zip` creates and
reopens a fixed synthetic ZIP, checking manifest entries, absence of local references,
PNG CRCs and decompressed size. Public CI runs it in a temporary directory. This is
a fixture check, not an untrusted-file validator or network delivery test.

sh scripts/test.sh checks domain validation and distances using synthetic
coordinates. Tests cover UUID identity, bearing limits, invalid coordinates,
time fields and sorting. The 2026-10-16 domain update additionally tests manual IDs
such as synthetic `PIR 134`, distinct UUIDs for duplicate visible labels, all 25 manual
status transitions, revision/clock failure cases, explicit field clearing and rejected
type/bearing changes. Distance tests cover filtered/empty candidate sets, any source
status, UUID self-exclusion, ties and recalculation after status/position changes.
These domain tests do not prove map privacy or compatibility.

Issue #11 adds storage tests to the same script: all-field reopening, a fixed v1
fixture, a writer/reader in separate JVM processes, stale edits/deletes, lock contention,
failed replacement, input limits and malformed/future schemas with byte-preservation
checks. See [LOCAL_STORAGE.md](LOCAL_STORAGE.md) for the exact scope and format.
The tests passed locally on 2026-10-17 with Temurin 17.0.20.1+1 / --release 8.
No ATAK/phone offline restart or upgrade acceptance is claimed.

Acceptance tests A01–A10 are in project plan §7. A01/A06/A07/A08 require actual
clients, an isolated test server and documented traffic/recipient observation.
The core storage fixture/restart tests support A02/A09; actual APK integration,
offline restart, images (A05) and physical-device upgrade/migration remain open.

[ADR 0006](decisions/0006-local-mvp-product-rules.md) records owner-confirmed product
choices and PR-review implementation defaults. Its manual-ID preservation (for
example `PIR 134`), status and distance-filter domain checks now pass. Duplicate-warning
UI, image-limit/metadata/orientation and replacement/failure cases remain acceptance
work in #11–#13, not passing tests.

| Test | Result |
|---|---|
| Plain Java core | Passed locally on 2026-10-16 with Temurin 17.0.20.1+1 / --release 8, including the accepted edit and status-filter rules |
| Android development build / lint | Passed locally with SDK 5.6.0.24; zero lint errors |
| APK static contract / signature | Passed; scripts/check-plugin-apk.py; Android Debug signature |
| ATAK API 5.6.0 plugin loading | Passed in isolated API 35 arm64 emulator with SDK developer host 5.6.0.24 on 2026-10-13: load, click, unload, reload and host restart. Standard 5.6.0.12 phone/signing remains unverified; see [EMULATOR_TEST.md](EMULATOR_TEST.md) |
| Offline feature-layer experiment | Create/move/delete and normal unload tested in SDK developer emulator; see [LOCAL_MAP_PROBE.md](LOCAL_MAP_PROBE.md). Not a production map or privacy result |
| Local map privacy / two clients | Bounded disposable feature-layer test passed on 2026-10-14: two SDK developer 5.6.0.24 clients / lab server 5.8-RELEASE-92, controls before/after, no observed probe events. Production privacy gate remains open; see [TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md) |
| Synthetic CoT round trip / manual target | Passed in the bounded SDK lab on 2026-10-15–16: all four types, actual marker import/re-export, same UUID, all fixture fields, repeated UID, cancellation, disconnect and recovery. See [COT_ROUND_TRIP.md](COT_ROUND_TRIP.md); production sharing is not implemented |
| Standalone synthetic image package | Passed on 2026-10-16 in the isolated two-client SDK lab: recipient cancellation check, byte-identical PNG/ZIP/server resource, remote manifest without local references, viewing and local deletion. Stock PNG import started a placement tool, cancelled without choosing a position. See [DATA_SYNC_AND_ATTACHMENTS.md](DATA_SYNC_AND_ATTACHMENTS.md) |
| Production TMM image sharing / Data Sync | Deferred; no TMM image association, acknowledgment, EXIF/size policy or applicable Data Sync plugin API verified. A05 and production image A07 remain open |
| HV Reports SDK-host loading attempt | Failed on 2026-10-16: provided 1.1.9 [5.5.0] installed but could not construct its plugin extension in SDK 5.6.0.24; same missing-class failure after unloading TMM. No reporting workflow passed. See [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md) |
| Data Sync / TAK Voice package follow-up | APK/split signatures and installation passed on 2026-10-16; both SDK 5.6.0.24 load attempts failed with missing `gov.tak.api.plugin.a`, including after TMM unload. See [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md); no feed or voice workflow passed |
| Standard signing / full A08 coexistence | Open: standard 5.6.0.12 installer, supported companion/host combination, appropriately signed TMM artifact and physical-client test still required |

Core CI is a public check. Android lint, APK and SDK builds will be added
when a permitted build environment exists, as a separate required check. Scanning before
publication must also cover history and artifacts, not just the working directory.

The accepted API target is 5.6.0 ([ADR 0002](decisions/0002-atak-5.6-target.md), 2026-10-12).
Record exact SDK and client full build numbers independently of the API version.
The operational server version is unknown. The isolated local lab uses the supplied
TAK Server 5.8-RELEASE-92 package; this does not establish operational compatibility.
Reported installation versions are not test evidence.

The APK check is a static artifact check, not a runtime or network observation test.
For Issue #3, install on a dedicated SDK developer client, load the plugin, verify
that the toolbar item appears once and shows Lokal prototyp, stop/unload the plugin
and verify removal, then restart and repeat. Record exact versions. Do not treat
passing compilation or absence of APK permissions as proof of local map privacy.

## CI enforcement and separate SDK evidence

The public `core` job also checks patch whitespace. `sh scripts/check-sdk.sh`
provides the separate local Android build/lint/APK procedure with private external
inputs; it is not an automated required GitHub check. See
[repository governance](REPOSITORY_GOVERNANCE.md) for main-protection evidence,
the failing-PR experiment and the remaining #9 acceptance gaps. No SDK/device
verification is implied by repository-check results.

## Independent local image tests (#13)

`./gradlew -p images --no-daemon testDebugUnitTest lintDebug` tests and lints the
standalone image library without the ATAK SDK or shared emulator. Native graphics
Robolectric tests use synthetic JPEG/PNG and EXIF at API 26/35; they cover rotations/
reflections, metadata removal, input/output and pixel limits, animation/corruption,
source preservation, cancel/replace, ambiguous saves, restart reopening, missing
files and invalid paths/symlinks. See [LOCAL_IMAGES.md](LOCAL_IMAGES.md) for the API,
remaining UI/phone gates and decoder limitations. The public `images` job is separate
from required `core`; it does not establish full A05 or ATAK acceptance.
