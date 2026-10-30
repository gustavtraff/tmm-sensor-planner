# Tests and gates

## Bounded image stream input — 2026-10-30 (#130 / #26)

[The content boundary](IMAGE_TRANSFER_CONTENT.md#stream-input-verification--2026-10-30-130)
now checks actual streamed bytes before complete-payload allocation, exact length
and EOF, then the existing PNG/hash verifier. All 44 focused API 26/35 content cases
and image lint passed. This adds no source/device association, durable storage, UI
or Data Sync route. Caller-owned read deadlines and stream closure remain explicit.

## Returned signed-v21 candidate — 2026-10-29 (#128 / #7)

[Signed-v21 verification](SIGNED_V21_VERIFICATION.md) records the actual standard-host
update, exact saved-state/image checks and pending initialization/migration access-loss
recovery. Those bounded native cases do not reproduce every queued-worker timing
window or establish phone/connected Data Sync acceptance. No unchanged build/core/image
suite was rerun for this evidence-only change.

## Image-content boundary — 2026-10-29 (#26 / #126)

[Verified image content](IMAGE_TRANSFER_CONTENT.md) records 58 passing focused
native-graphics API 26/35 cases and image lint: exact-byte PNG verification, separate
owned-PNG export and existing local-image regressions. This is in-memory content
only; no new wire/storage writer, association, transport or sharing UI is activated.
Data Sync-feed source/recipient evidence and durable readback remain separate gates.

## Synthetic SDK import candidate — 2026-10-25

[The v16 probe and runtime protocol](PUBLICATION_IMPORT_PROBE.md) separate the
new plugin-side `CotImporterManager` experiment from the earlier manual file import.
Core tests, actual-canary SDK construction/independent CLI verification, CivDebug
build/lint/app unit tests/static APK checks and CivRelease assembly passed locally.
The initial fixture API-26 lint annotation was corrected before the passing rerun.
No emulator, Docker, image suite or CI wait was used for those preparation checks.
At that stage, service signing and actual
standard-client import/re-export, pre-confirmation/other-feed privacy, recipient
content and lifecycle checks were **unverified**. It was not a production path.

**Later signed run:** [PUBLICATION_SDK_RUNTIME](PUBLICATION_SDK_RUNTIME.md) records
actual standard 5.6.0.25/Data Sync 3.7.4 SDK import and re-export, pre-Done/feed
absence, native Cancel/Done, B's fresh export, repeats and pending review
restart/Unload/reload. The fixed PIR passes that bounded lab gate. Private snapshots
and bounded canary scans stayed clean. Production UI/ingress, every device type,
physical 5.6.0.12 and operational-combination acceptance remain separate. This
documentation branch reran three real-event CLI checks/static signed APK checks;
it did not rerun unchanged core/image/build suites or await CI.

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
and exact updated metadata after online ATAK cold start. That run left automatic
recovery open; no root cause or physical-network defect is inferred. Fresh package
times and strict base/update checks distinguish real exports from stale expected
data. This documentation-only change does not alter or rerun core/image/SDK suites,
build an APK or wait for CI. Permission, conflict/removal and durable TmmDraft
import remain separate acceptance work.

## Controlled Data Sync reconnect and history — 2026-10-25

[DATA_SYNC_RECONNECT_TRIAL.md](DATA_SYNC_RECONNECT_TRIAL.md) records a task-port
TCP interruption, zero established sockets/red feed state, verified subscription,
actual before/after recipient controls and exact update receipt before manual sync
or ATAK restart. Full sync preserved the update; local older/latest history
downloads changed B's exported version without changing server content/history.
Sixteen real-capture checks had expected match/rejection outcomes. The earlier
stale interval was not reproduced or assigned a root cause. An operator
uiautomator collision is recorded separately; B's original ATAK process survived.
No core/image/SDK suite, APK build or CI wait accompanied the documentation change.

## Data Sync permissions, conflicts and removal — 2026-10-25

[DATA_SYNC_ACCEPTANCE_TRIAL.md](DATA_SYNC_ACCEPTANCE_TRIAL.md) records actual
read-only subscription/UI publication prevention, distinct same-UUID offline and
online edits, a native conflict prompt and exact metadata after explicit choice.
It separates cancelled/local-only deletion, missing-item re-download and server
association removal, including actual recipient views and later server readback.
Normal UI prevention is not a forged-request/HTTP-denial test; native conflict
handling is not durable TmmDraft conflict storage or a production deletion policy.

The CLI suite passed with six cases per envelope family and mutual rejection of
the three different same-UUID payloads. Core tests (`sh scripts/test.sh`) passed;
16 actual-capture checks had the expected match/rejection outcomes. Initial stale
baseline/role UI, a pending owner confirmation and an unavailable accessibility
dump on the conflict dialog are documented without being relabelled as passes.
The usage-limit pause is included in final observation bounds. No image suite,
full APK build, signing submission or CI wait was run for this change.

## SDK-independent durable review inbox (#15) — 2026-10-25

Following explicit owner approval of A–C and the PR #92 format,
`sh scripts/test.sh` runs InboxFormatFixtureTest and InboxStoreTest. The
[storage evidence](INBOX_STORAGE.md#verification--2026-10-25) records the passing
Temurin 17.0.20.1+1 / `--release 8` run: independent golden-byte compatibility,
duplicate/conflict/local-observation preservation, separate-JVM restart and forced
lock contention, process halts and injected errors before/after atomic replacement,
initialization, corrupt/future/limit rejection and current draft/image preservation.
Existing DraftStore v1 remains unchanged. All data are synthetic; these are plain
Java processes, with no emulator, Docker container or Android build. D (application),
Android/filesystem/ATAK compatibility, source authentication and real ingress remain
separate gates. No power-loss durability or transport acknowledgment is claimed.

## SDK-independent read-only inbox review (#15) — 2026-10-25

`sh scripts/test.sh` also runs InboxReviewTest. The passing Temurin 17.0.20.1+1 /
`--release 8` run covers original fixture receipts/history, UUID grouping and
provenance preservation, existing comparison results, stable insertion order,
immutable results and invalid local snapshots. Real store checks compare against
changed/deleted/recreated drafts and replaced/removed local images while preserving
inbox/draft/image bytes during review. See [the API and limits](IMPORT_REVIEW.md#read-only-inbox-review-15--2026-10-25).
No application, new format/migration, outgoing metadata change, Android adapter or
transport is included. No emulator, Docker container or Android build was run;
SDK/device acceptance and D remain separate gates.

## D application/recovery contract baseline (#15) — 2026-10-25

[The D proposal](IMPORT_APPLY_CONTRACT_PROPOSAL.md) specifies future confirmation,
atomic application/history, deleted-UUID protection, migration and recovery gates;
it introduces no production writer or format. `JAVA_HOME=<Temurin 17.0.20.1+1>
sh scripts/test.sh` passed on macOS with `--release 8` and assertions enabled.
The added DraftStoreTest **v1 characterization** proves byte-identical
local delete/recreate can admit a previously prepared save and revision-based
delete. This exposes why current revisions/full-state comparison cannot implement
D's freshness guarantee; it is not a passing D protection test. Existing core,
import/inbox/review, independent fixture, publication-preparation/format and
100-device checks pass unchanged.

No v2 codec/writer/migration or D commit/recovery test is implemented. The proposal's
failure/migration/concurrency matrix is required future acceptance, not observed
behavior. No emulator, Docker container, Android build, SDK/device, image-library
suite, server or transport test ran. Only synthetic temporary local data were used.


### Capacity and safe-cleanup amendment — 2026-10-25

The owner adopted logical D principles and requested capacity feedback plus
[three-step local mission cleanup](LOCAL_MISSION_CLEANUP_PROPOSAL.md). These are
requirements/technical proposals, not working UI/reset behavior. Future tests must
cover near/full/unknown local usage, preserved edits, stale inventory, cancelled
confirmations, interrupted activation/removal, expired identities/old workers,
original/host/server copy exclusion and honest incomplete-cleanup reporting.

This amendment changes documentation only. Relative Markdown link targets and
`git diff --check` were checked; no test suite/build/emulator/container was rerun.
The passing core run above still describes the unchanged test/source baseline,
not capacity/cleanup or v2 implementation evidence.

## Exact local v2 proposal and migration fixtures (#15/D) — 2026-10-26

`JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh` passed on macOS with
`--release 8` and assertions. LocalV2FormatFixtureTest is test-only: it inspects
[independently encoded Python fixtures](../test-fixtures/synthetic/storage-v2/README.md)
for active/deleted/versioned slots, Add/re-add/selected/keep/no-op history and
current-versus-historical results. Eighteen independent canonical preimage/digest
vectors check signed-zero normalization, optional bearing/images, metadata,
identity/revision/version and absence/deletion distinctions. Re-sealed malformed
frame/domain/history/fingerprint cases are rejected. Existing core/publication/
inbox/storage/100-device checks pass without changing production code.

Expected migration fixtures cover the unchanged existing v1 record and empty v1.
Tests compare every current v1 field/revision/image and double bits with the
expected v2 inspection, and verify the legacy reader rejects v2 unchanged. This is
**not** a migration execution or atomic-commit test: no production v2 codec/writer,
migrator or reset exists. Pre-v2 deletion/application history cannot be recovered.
Future writers must match the golden bytes and add real transition/concurrency,
interrupted-write/recovery and migration tests under the adopted D contract.

Re-running the Python fixture builder reproduced all seven outputs byte-for-byte;
relative document targets and `git diff --check` passed. Only synthetic temporary
files/fixtures were used. No emulator, Docker container, Android/SDK build, server,
transport, reset, image-library suite or CI wait was run. Exact format approval,
Android/filesystem/ATAK compatibility and cleanup activation remain separate gates.

## Approved local v2 codec/fingerprint implementation (#15/D) — 2026-10-27

The owner explicitly approved the exact local v2/TMMR bytes and merged #102's
independent test material. `JAVA_HOME=<Temurin 17.0.20.1+1> sh scripts/test.sh`
passed on macOS with `--release 8` and assertions. The independent oracle is kept:
all production-decoded fields and signed-zero bits match it, re-encoding reproduces
the fixed frames exactly, and all 18 canonical fingerprint vectors match. Both
readers reject the re-sealed malformed frame/domain/history corpus, with production
errors fixed and input bytes preserved.

LocalV2CodecTest additionally verifies immutable list/fingerprint ownership,
invalid model/history/provenance/image/counter rejection, both 1,000-slot and
1,000-decision caps together, exact 65,536-byte ASCII/multibyte UTF-8 boundaries,
malformed text, valid Long.MAX_VALUE stored evidence, and exact 16 MiB encoding/
decoding versus one byte over. Existing core/inbox/review/v1 storage/publication/
preparation/100-device checks and the offline four-type envelope checker pass.
See [the in-memory API and evidence boundary](LOCAL_V2_CODEC.md).

No production v1 DraftStore, domain schema, inbox, outgoing metadata or fixture
bytes changed. No file writer, migration, activation, apply or reset was added.
Codec tests do not establish atomic commit/migration durability or SDK/ATAK behavior.
No emulator, Docker container, Android/SDK build, device/server/transport trial,
image-library suite or CI wait ran. Only synthetic fixtures/temp data were used.


## In-memory import-decision preparation (#15/D) — 2026-10-27

`sh scripts/test.sh` passed with Temurin 17.0.20.1+1 / `--release 8` and assertions.
[The preparation tests](IMPORT_DECISION_PREPARATION.md#verification--2026-10-27)
cover Add/explicit re-add, keep/equal/unchanged and each/all selected fields, exact
counter/time/image previews, invalid type/bearing/identity/time combinations,
stale local/source/receipt state, delete/recreate, exhaustion, Cancel and one-use
concurrent preparation. Additional observations/unrelated edits remain compatible;
all reads/previews leave original v1 draft, inbox and synthetic image bytes intact.
Hypothetical memory-only result/decision frames fit the unchanged v2 codec.

Existing core/v1/inbox/InboxReview/v2/publication/preparation/100-device tests and the
offline envelope checker pass. No schema, fixture, InboxReview or outgoing API was
changed. No actual application, file writer/migration/activation/reset, Android
adapter, emulator, Docker, SDK build, device/server/transport or image-library suite
was run. These are not durable-transaction or ATAK compatibility results.


## Isolated local v2 transactions — 2026-10-27

`sh scripts/test.sh` passed with Temurin 17.0.20.1+1 / `--release 8`.
[The store evidence](LOCAL_V2_STORAGE.md#verification--2026-10-27) covers atomic
result/history, Add/re-add/selected/Keep/equal, stale full state/images/deletions,
concurrency/locks, child-process crash boundaries, injected partial-write/sync/
replacement/cleanup failures, exact-ID replay/readback and unchanged files at bounds.
Existing core/inbox/v1/codec/preparation/publication/restart/100-device suites pass.
No emulator, Docker, Android/SDK build, phone test, image suite or CI wait ran.
No migration/activation, trusted receive adapter or ATAK compatibility is established.


## Isolated v1-to-v2 migration — 2026-10-27

`sh scripts/test.sh` passed with Temurin 17.0.20.1+1 / `--release 8`.
[LocalV2MigrationTest evidence](LOCAL_V2_MIGRATION.md#verification--2026-10-27) covers
actual old-to-new independent fixtures, all-field/image/inbox/lock preservation,
legacy writer rejection, count/byte boundaries, malformed sources, competing
migrations, child-process locks/crashes and injected write/sync/rename/cleanup
failures. Existing core suites pass. No Android/SDK build, emulator, Docker, phone
test, image-library suite, signing upload or CI wait ran. App migration/activation
and trusted receive/runtime verification remain separate.


## VersionCode 20 storage integration — 2026-10-27

[The candidate and evidence boundary](VERSIONED_PLANNER_STORAGE.md) record core
inspection/selection checks, targeted API-26/35 activation/attachment/publication UI
tests, external SDK probes, CivDebug build/lint/app tests/static APK checks and
CivRelease assembly. No full image suite, CI wait, emulator, Docker, signing upload
or phone/server test ran. Signed upgrade/marker/provider/lifecycle acceptance is
still required, and trusted import ingestion remains unimplemented.

## Read-only inbox decision coverage (#15) — 2026-10-27

`sh scripts/test.sh` passed with Temurin 17.0.20.1+1 / `--release 8` and assertions.
[InboxDecisionCoverage](INBOX_DECISION_COVERAGE.md) is tested against the unchanged
independent v2 history fixture for exact receipt/inbox/context/metadata binding,
current versus historical result versions, labels/UUID separation, multiple retained
decisions, unmatched history, equal/no-history and empty cases. Local edits, image
changes, deletion/re-add and old values restored at newer versions lose coverage;
invalid same-version/image/result combinations fail closed. All receipts/decisions,
input bytes and immutable outputs are preserved. Existing core/store/migration/
preparation/publication/100-device and offline envelope checks pass.

This adds no UI, LocalV2Store, format, import-writer or transport changes. No Docker,
emulator, Android/SDK build, device/server/transport trial or image-library suite
was run. Coverage remains a supplied-snapshot read model, never write authorization.

## Inbox coverage UI integration (#15) — 2026-10-27

[The coverage-aware review UI](INBOX_COVERAGE_UI.md) passed 106 targeted host-JVM
Robolectric cases with Temurin 17.0.20.1+1, Android API 26/35 and the public standalone
images test harness. Exact selected classes/counts:

| Class | Passed cases |
|---|---:|
| InboxCoverageUiTest | 22 |
| InboxReviewUiTest | 22 |
| InboxReviewTextTest | 4 |
| ImportDecisionUiTest | 30 |
| ImportCommitUiTest | 28 |

Run with JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME set to the existing local toolchain:

```sh
./gradlew -p images --no-daemon '-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8' testDebugUnitTest \
  --tests se.tmm.sensorplanner.core.InboxCoverageUiTest \
  --tests se.tmm.sensorplanner.plugin.local.InboxReviewUiTest \
  --tests se.tmm.sensorplanner.plugin.local.InboxReviewTextTest \
  --tests se.tmm.sensorplanner.plugin.local.ImportDecisionUiTest \
  --tests se.tmm.sensorplanner.core.ImportCommitUiTest
```

Tests verify current/older/no-match/unavailable coverage, all variants visible,
source separation, image changes/restored values, known deletion/re-add, later
SAME_METADATA without forcing another choice, exact Add/update/keep decision
history versus observations, complete unmatched history with an empty inbox,
strict snapshot pairing, legacy compatibility/stale callbacks and unchanged
inbox/local/image bytes while navigating. No decisions are saved by this browser.

`sh scripts/test.sh` and `git diff --check` also passed. Standard library-module
Gradle deprecation/annotation warnings remain; there were no test failures/errors
or skipped cases. No Docker/emulator, ATAK SDK app/APK build, signing submission,
physical-device/server/transport trial or CI wait was run. This is UI-component
and existing-core verification, not signed ATAK runtime/live-ingress acceptance.


## Image-sharing contract proposal (#26) — 2026-10-28

[The proposal and acceptance matrix](IMAGE_SHARING_CONTRACT_PROPOSAL.md) define
future offline/storage/two-client/physical evidence for content integrity, exact
UUID/metadata/source association, replacement/conflicts, explicit local image
adoption, receipt privacy/authentication and interrupted writes/retries/cleanup.
Every matrix row is required future acceptance, not a passing test in this PR.

Documentation-only verification: read current contracts/code and #26, computed two
synthetic 64×64 RGB PNG examples in memory with standard-library helpers (H1 210
bytes, H2 220 bytes), confirmed their SHA-256 values/decompressed row counts,
checked relative document targets and `git diff --check`. No existing image/store,
SDK/account/server was used by the example calculation. No production protocol or
fixture file was changed. No core/UI/image test suite, Android/SDK build, Docker,
emulator, signing change or v20/runtime verification ran; agent 1 retains that work.

## Local activation transient-access regression — #120, 2026-10-28

The targeted `LocalStorageUiTest` suite now covers availability returning between
a skipped worker operation and delivery of its abort callback: queued reads,
initialization, migration and the confirmation click itself. The aborted flow
releases busy state, preserves storage and cannot revive old consent; reopening
requires a fresh read/choice. All **30 cases** pass on Robolectric API 26/35. The
two original queued-boundary regressions first failed on both APIs against the
unchanged controller. No storage-format, golden fixture or transport change was
made. No full app/SDK build, emulator, Docker, phone test or signing submission
was performed; the targeted standalone Android-library test build is separate.
A future signed APK still needs actual host/device regression verification.
