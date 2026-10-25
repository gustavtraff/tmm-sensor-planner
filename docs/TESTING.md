# Tests and gates

The [#67 device review](DEVICE_DETAILS.md) adds 6400-mil conversion/preservation,
actual cardinal-direction rendering and asynchronous thumbnail-lifetime tests.
Canonical stored/wire degrees stay unchanged; physical/signed-host gates remain.

The local-only `sh scripts/test-mgrs-sdk.sh` checks actual external SDK conversion
classes and exact position-edit preservation. It is part of `scripts/check-sdk.sh`;
public core/image CI cannot run it without the external SDK. See
[MAP_POSITION.md](MAP_POSITION.md) for UI, precision and remaining client gates.

The [#66 Full HD copy policy](IMAGE_QUALITY.md) tests aspect ratios, no upscaling,
orientation/metadata/source preservation, old-copy reads and separate 10 MiB source
versus 16 MiB owned limits. Physical camera and placement usability remain gates.

The [#66 image viewer](IMAGE_VIEWER.md) adds native gesture/render/decode tests.
They do not prove actual ATAK dialog lifecycle, phone gestures or higher-detail
storage; those are separate acceptance gates.

The [#49 primary-still JPEG regression](LOCAL_IMAGES.md#camera-jpeg-primary-still-compatibility-49--2026-10-20)
checks camera auxiliary data, primary-image limits, safe normalization and malformed
framing. The OnePlus photo/provider and a newly signed standard-client candidate
remain distinct acceptance gates.

The [#62 image-copy notice check](LOCAL_IMAGES.md#image-copy-notice-resource-boundary-62--2026-10-18)
verifies the actual SDK-host resource lookup, formatted size disclosure and local
Cancel/Save behavior. Public image tests alone cannot catch a host/plugin resource
ID collision. The returned versionCode 10 candidate now also passes the actual
standard-host notice, preview Cancel/Save and image-reopen checks; see
[the signed candidate record](STANDARD_CLIENT_VERIFICATION.md#latest-signed-candidate--2026-10-18).

Issue #4's [connected local trial](CONNECTED_LOCAL_TRIAL.md) adds a session-policy
test to the independent `images` test source set. It checks explicit opt-in,
connectivity changes, disabled lab access, reset and a fresh session. It does not
prove Android lifecycle behavior or absence of network transmission. The
service-signed versionCode 10 now has bounded standard-client image/lifecycle and
egress-blocked session-UI evidence (#7); its two-client privacy gate remains open.
The linked record now includes a bounded two-client connected SDK trial with
server/decrypted traffic scans and distinct before/after controls observed by the
actual recipient. It records the exact combined APK hash and keeps production and
standard-client gates open.

**Update — 2026-10-18:** The owner-supplied service-signed artifact passed static
APK checks and standard ATAK-CIV 5.6.0.25 emulator load/unload/restart plus synthetic
snapshot preservation. Companion loading and disconnected panels were examined;
see [exact results and limits](STANDARD_CLIENT_VERIFICATION.md). Earlier SDK-host
failures below are historical; full A08 and phone 5.6.0.12 remain open.

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
The later [offline planner test](LOCAL_PLANNER.md) adds bounded API-35 SDK-host
restart/install-in-place and future-schema failure evidence; phone acceptance remains open.

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
| Data Sync / TAK Voice package follow-up | APK/split signatures and installation passed on 2026-10-16; both SDK 5.6.0.24 load attempts failed with missing `gov.tak.api.plugin.a`, including after TMM unload. See [SIGNING_AND_COEXISTENCE.md](SIGNING_AND_COEXISTENCE.md). Later standard-host manual Data Sync evidence is in [DATA_SYNC_SENSOR_TRIAL.md](DATA_SYNC_SENSOR_TRIAL.md); no voice workflow is established by that trial |
| Standard signing / full A08 coexistence | Signed service output and bounded standard 5.6.0.25 emulator checks now verified; physical 5.6.0.12, connected workflows and full A08 remain open; see STANDARD_CLIENT_VERIFICATION.md |

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

## Offline planner adapter — 2026-10-17

[LOCAL_PLANNER.md](LOCAL_PLANNER.md) records actual headless SDK-host results for
forms, comma input, edits, duplicate warning, map selection, directional cues,
private storage, process restart, in-place APK replacement, plugin unload/reload,
future-schema preservation and two emulated landscape sizes. The initial datastore
lifetime crash and decimal-keyboard bug were fixed and retested. Current app builds
and lint pass with the guarded API-26 core boundary; this is not API 21–25 planning
support. Actual image bytes, rotated-map/phone behavior and production connected
privacy remain open; public CI is core-only.

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

## Integrated local images (#35)

[LOCAL_IMAGES.md](LOCAL_IMAGES.md) records guarded saved-device image actions and
bounded SDK 5.6.0.24 emulator evidence for picker/normalized preview cancellation,
attachment, replacement, metadata removal/source preservation and restart viewing.
The standalone suite now includes real DraftStore coordination tests: stale edits,
shared handles, ambiguous atomic commits, fresh-read failures and cleanup warnings.
These are separate from physical-provider, standard-client and sharing acceptance.

## Metadata import-review core (#15)

`sh scripts/test.sh` now also runs ImportReviewTest: UUID matching, repeated metadata,
each compared field, optional values, numeric zero, unrelated duplicate UUIDs, null
inputs, local revision independence and the image-handle boundary. A real stored
snapshot remains byte-identical after assessment. See [IMPORT_REVIEW.md](IMPORT_REVIEW.md)
for the proposed conservative policy, local-image preservation and remaining
source/version, persisted-conflict, transport and UI gates. Passing these tests does
not establish two-client import behavior or ATAK compatibility.

## Metadata sharing-review core (#14)

`sh scripts/test.sh` also runs ShareReviewTest for the metadata allowlist, excluded
image handles/counters, both image states, stale fields/revisions/image associations,
destination changes and invalid text, deletion and read-only snapshot preservation.
[SHARE_REVIEW.md](SHARE_REVIEW.md) documents the preparation and final-adapter gates.
This adds no transport test and does not satisfy actual-client A06/A07.

## Actual saved-planner CoT privacy (#4)

The 2026-10-17 [two-client follow-up](TWO_CLIENT_LAB.md#actual-planner-follow-up--2026-10-17)
uses the merged planner/image build rather than the earlier disposable map probe.
It brackets saved-draft create/edit/image/reload/restart/delete operations with
actual targeted and broadcast controls. Independent server/subscriber checks search
UUID, visible metadata and local image canaries, plus the synthetic moved position;
a stock recipient pick-list is inspected and cancelled. This is bounded SDK-lab
CoT evidence, not a test of HTTP uploads, Data Sync, arbitrary providers, standard
signing, all exports or normal connected phone networks. The offline guard remains.

## Saved-metadata sharing SDK lab (#14)

[METADATA_SHARE_LAB.md](METADATA_SHARE_LAB.md) records the exact experimental wire
contract and bounded two-client evidence. `sh scripts/test.sh` additionally runs
ShareLabFieldsTest for all four types/five statuses, exact coordinates, optional
values, UTF-8/Base64 preservation and malformed/future/extra-field rejection.
These tests leave snapshot-v1 unchanged and confer no transport/import rights.
Actual SDK-host tests separately verify confirmation, cancellation, freshness,
disconnection, restart and real recipient marker re-export. Production A06/A07,
standard signing, phones and companion compatibility remain open.

## Signing-source preparation (#7)

`python3 scripts/test-signing-source.py` uses temporary synthetic Git repositories
to test committed-build-input selection, one archive root, executable wrapper,
uncommitted/unrelated file exclusion, output overwrite refusal, tracked private
file rejection, symlink rejection and required-file presence. The core CI job runs
these SDK-independent tests. Local debug/release build/lint results and the blocked
Maven preflight are recorded in [signing evidence](SIGNING_AND_COEXISTENCE.md).
None of these checks verifies a service build, standard-client trust or A08.

## Approved device symbols (#43)

[DEVICE_SYMBOLS.md](DEVICE_SYMBOLS.md) records owner approval, SVG/PNG provenance,
build/core checks and actual 5.6.0.24 SDK map rendering for all 20 type/status
combinations, zoom samples, north/east bearing cues, map selection and two landscape
size/density samples. Phone/accessibility, dense-label usability and standard-host
acceptance remain open under #12 and the release gates. No domain or transport
contract changes accompany these symbols.

The source-archive tests additionally reject wrong/missing takdev Maven versions
under the [saved service requirements](references/TAK_SOURCE_ARCHIVE_REQUIREMENTS.md).
The [submitted-ZIP audit](SOURCE_REQUIREMENTS_AUDIT.md) distinguishes static
conformance, actual task execution and the still-blocked authorized Maven preflight.

## Phone-camera attachment correction (#49)

[LOCAL_IMAGES.md](LOCAL_IMAGES.md#phone-photo-failure-and-correction-49--2026-10-18)
records the synthetic camera-size failure reproduced against the old encoder,
bounded sampled copies, metadata/source preservation and unchanged legacy PNG
reads. Native tests also cover copy-size boundaries and safe, distinct preparation
messages. This does not establish physical-phone/provider or standard-host
acceptance of the new APK; versionCode 7 signing evidence does not cover version 8.

## Automatic saved-marker restoration (#50)

[MARKER_RESTORE.md](MARKER_RESTORE.md) records the shared startup/toolbar load path,
worker/UI and stop boundaries, offline checks and deterministic SDK fault-injection
procedure. Verify restoration before any TMM list/review/centering action; source
persistence alone does not prove that a map layer was restored. Changed-code SDK
evidence and service-signed standard-client/phone acceptance are separate gates.

## Form completion and deployment selectors (#51)

[FORM_ENTRY.md](FORM_ENTRY.md) specifies Done/focus behavior, optional local-time
selection, explicit clear, cancellation, exact preservation of existing Instants
and daylight-saving handling. The private SDK check now includes app unit tests
for UTC date/year rollover, overlap and gap conversion. These app tests require
the local SDK; public core/images CI does not run them or establish actual
keyboard, small-screen, standard-client or phone acceptance.

## Current evidence and next gate — 2026-10-22

The owner-reported signed version 15 offline phone results and their limits are recorded in [PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md). This supports local physical acceptance; A01 connected privacy, A06/A07 production sharing and full A08 coexistence remain open. The proposed order and exit gates are in [NEXT_STEPS.md](NEXT_STEPS.md).

## Signed version 15 connected-private trial — 2026-10-23

[The standard-binary trial](CONNECTED_SIGNED15_VERIFICATION.md) records the actual
signed APK and installed host hashes, two standard ATAK-CIV 5.6.0.25 clients,
Android 15/API 35, isolated TAK Server 5.8-RELEASE-92, unique routed controls before
and after the operation window, and recipient/server/decrypted canary checks.
Synthetic create/edit/map move/image/delete, background/return, offline/connected
transitions, Unload/reload, cold restart and renewed consent were exercised.
This closes no issue automatically and establishes neither unrestricted-network
privacy nor physical connected acceptance, Data Sync or full companion coexistence.

## Data Sync automatic-publication privacy phase — 2026-10-24

[DATA_SYNC_FEED_VERIFICATION.md](DATA_SYNC_FEED_VERIFICATION.md) records two-client
standard-host stock-feed controls and private-draft checks with Data Sync 3.7.4
auto-publication off/on, including prompt-disabled automatic controls, offline
edits/deletion, reconnect and cold restart. It supplements the earlier CoT-only
trial; it does not prove a custom TMM feed round trip, physical connected acceptance,
full companion coexistence or production sharing. No app/domain changes accompany
the report, and no core/SDK/image suite was rerun for it.

## Data Sync metadata selection trial — 2026-10-24

[DATA_SYNC_METADATA_TRIAL.md](DATA_SYNC_METADATA_TRIAL.md) records a rejected
canonical waypoint selection and a successful, separate native-type diagnostic
with exact 15-field preservation in an independently obtained B export. Cancel
and selected/non-target feed membership were checked for that diagnostic. The
canonical checker was retained and correctly rejects the diagnostic UID/type.
This is no canonical sharing/import pass; permission, update/recovery/restart and
conflict cases remain untested after the selection blocker.

## Explicit Data Sync sensor-point candidate (#83)

[DATA_SYNC_SENSOR_TRIAL.md](DATA_SYNC_SENSOR_TRIAL.md) records the actual PIR
manual-import/selected-feed standard-client pass, B's independent export/strict
checker result, selection cancellation and exclusion from another subscribed
auto-publishing feed with prompt disabled and live native controls before/after.
The offline CLI suite now also tests all five explicit sensor cases, declared
UUID/type separation, identical custom payload attributes, wrong-envelope/update
rejection, archive output and read-only/overwrite preservation. The common XML
input hardening remains unchanged. Core and CLI suites passed locally; no image
suite, full APK build, CI wait or production integration is claimed.

## Data Sync same-UUID update/recovery — 2026-10-25

[DATA_SYNC_UPDATE_TRIAL.md](DATA_SYNC_UPDATE_TRIAL.md) records actual standard-host
repeat publication without duplicate membership, a coherent same-UUID server
update, B's old exported metadata while offline and after reconnect/list refresh,
and exact updated metadata after online ATAK cold start. Automatic recovery remains
an open gate; no root cause or physical-network defect is inferred. Fresh package
times and strict base/update checks distinguish real exports from stale expected
data. This documentation-only change does not alter or rerun core/image/SDK suites,
build an APK or wait for CI. Permission, conflict/removal and durable TmmDraft
import remain separate acceptance work.
