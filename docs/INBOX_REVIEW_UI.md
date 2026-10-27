# Read-only inbox review UI component — #15

Based on merged main `52a9ec5` (#105/#106). The owner assigned this component on
2026-10-27 alongside agent 2's independent import-decision preparation work.
`InboxReviewUi` browses the existing immutable `InboxReview` result. It does not
read a store, initialize an inbox, receive content, prepare a consent command,
apply metadata, edit an image, export or acknowledge anything.

## Integration boundary

The constructor takes an Android Context and a pure position-to-MGRS formatter.
`show(review)` runs on the UI thread and requires a nonnull, already assembled
review. The future caller must successfully load/validate both stores, assemble
stable snapshots and apply its own access/source policy first. Missing/corrupt/
unreadable storage must not become a fabricated successful empty review. This
component has no directory selection, initialization fallback or storage callback.
A valid supplied empty review has a distinct empty-snapshot message.

**No toolbar/planner entry or production loader is added in this PR.** There is no
approved live ingress/source-context adapter to connect here, and installing a
button that implicitly creates a new inbox or only fails on missing storage would
change that boundary. The component is directly exercised in Android UI tests;
real loader/entry/lifecycle integration is a later slice. It adds no version bump
or separate signing request. It does not change the pending signed v19 map-copy
gate or make that new caller verified.

The caller supplies actual MGRS formatting; no ATAK API or coordinate conversion
is invented here. Unsupported formatting shows a generic MGRS-unavailable label
plus exact latitude/longitude. The formatter must not open files, navigate or
perform writes. Images are never resolved. Context identifiers are displayed as
registered context IDs, not invented authenticated feed names.

## Browsing and comparison

- Group by device UUID using the existing model. Equal visible labels do not merge
  different UUIDs. Short list previews are limited by Unicode code points; full
  values remain available in details. Preview formatting does not define equality.
- Retain every supplied receipt variant, including equal metadata from different
  contexts. Show registration time and context, keeping insertion order without
  ranking or automatically selecting a winner.
- Compare one explicitly opened variant with **local state in the supplied
  assessment**. Describe the entire view as a snapshot, not a live/staleness-aware
  representation. The view cannot know whether the stores changed afterward.
- Show `Samma metadata`, `Avvikande metadata · granskning behövs` or `Saknas lokalt
  i underlaget`. Differences do not prove concurrent edits. Absence does not prove
  the device never existed, represent a known-deletion token or authorize re-add.
- Prioritize changed fields; unchanged metadata can be expanded. Show local and
  incoming values with textual change headings, without relying on color.
  ID, type, status, owner, created/updated/deployed instants, comment, position and
  bearing use the existing comparison semantics. Creation-time differences have
  a separate identity-review warning.
- Display MGRS and 6400-mil true-north bearing while retaining canonical numeric
  detail below them. Tiny stored differences must not disappear behind identical
  rounded display values. UTC Instants retain nanoseconds; absent and empty values
  have explicit labels. Positive/negative zero still compare numerically equal.
- Local revision and image association are local-only information outside metadata
  equality. No sender revision is inferred from InboxStore's revision-1 carrier.
  A present local association is not a claim that its image file exists or was
  checked. Incoming metadata contains no image; absence of incoming image data
  does not clear or prove delivery of the local image.

The component uses plain TextViews, lists and scrollable dialogs with Swedish
labels. Expanded-field buttons have a 48 dp minimum height and text uses sp.
Receipt ID, registered context and first local registration time remain visible;
owner text is not authenticated sender identity. The ten metadata-field row
comparisons are presentation only, not a replacement for ImportReview or any
future prepared-command/expected-state API.

## Historical observations and navigation

History is a separate list/view, labeled **Historik · inte nuläge**. Each supplied
local observation remains available, including explicit observed absence. A
historical draft keeps its observed local revision/metadata; its stripped imageRef
must not be presented as proof of no image. There is no separately recorded time
for each historical observation, so the view does not invent one from receipt
registration or metadata timestamps. History is never substituted for current
local state, combined with another receipt or written back.

Android Back returns through observation → history → comparison → variants →
groups; Back at the root closes the browser. Explicit navigation and Close perform
no data operation. A new `show(newReview)` replaces the supplied snapshot and
invalidates old dialog callbacks. The caller must explicitly reload/reassess to
obtain that new review; the browser does not refresh itself or cache a source.
`stop()` invalidates callbacks before later UI-thread `close()` disposal. No
pending selection resumes after close. These are component lifecycle checks, not
actual plugin Unload/rotation evidence.

## Verification — 2026-10-27

Targeted `InboxReviewUiTest` has **22 passing cases across Robolectric API 26 and
35**. They cover grouping, equal labels/different UUIDs, separate equal-metadata
variants from different contexts, differing/identical/absent-local states, field expansion, identity warning,
current versus historical present/absent observations, image/reference and foreign
revision wording, navigation, refresh after local deletion, stale callbacks,
stop/close, null rejection and a genuinely initialized empty snapshot.

The UI tests use actual temporary InboxStore/DraftStore fixtures and synthetic
image bytes. Navigation/Close leaves inbox, draft and image bytes unchanged and
reopening retains all four receipts and the local image association.

Four `InboxReviewTextTest` cases additionally exercise every compared field against
the existing classifier, signed-zero/null rules, image/revision exclusion, exact
Unicode/text/nanosecond/numeric values, safe formatter failure, bounded emoji list
previews and all status labels. Total: **26 targeted passing tests**, not the full
image-library suite. The synthetic MGRS formatter in these tests does not establish
SDK coordinate-conversion behavior.

`sh scripts/test.sh` passed existing core/inbox/local-v2 codec/publication and
separate-process/100-device checks. `sh scripts/check-sdk.sh` passed SDK probes,
CivDebug assembly/lint, the existing three app unit tests and static APK checks.
`./gradlew --no-daemon :app:assembleCivRelease` passed including shrinking. The first lint run caught missing
API-level annotation on the private formatter; its boundary now matches the
API-26+ component instead of changing minSdk or weakening the lint rule.

No core model, storage/wire schema, source-context policy or existing app flow is
changed. No emulator, Docker, signing submission, operational data, full image
suite or CI wait was used. Component tests/builds do not prove standard ATAK-host
layout, phone usability, source authenticity, durable receipt, real-time freshness
or draft-application behavior. Those remain explicit integration/acceptance work;
agent 2's decision engine is not invoked by this view.
