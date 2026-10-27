# Inbox decision coverage in review UI (#15)

`InboxReviewUi` now accepts `show(review, coverage)` in addition to the existing
`show(review)`. It renders [InboxDecisionCoverage](INBOX_DECISION_COVERAGE.md)
without changing InboxReview, core coverage logic, LocalV2Store, import preparation/
confirmation/writer, storage formats or transport. It remains a supplied-snapshot
browser and does not open files, reload stores, initialize anything or commit a
choice while browsing. Existing optional transition to ImportDecisionUi is unchanged.

## Inputs and compatibility

The existing constructors and `show(InboxReview)` stay compatible. That legacy
call displays **coverage unavailable in the supplied data**, rather than silently
claiming no decisions or keeping a previous snapshot's current status.

For coverage-aware rendering, the authorized caller must successfully load both
stores and assemble the two immutable models from the same stable snapshots, off
the UI thread. For example, after the caller's source/access and lifecycle checks:

```java
InboxReview review = InboxReview.assess(inboxSnapshot, localSnapshot.activeDrafts());
InboxDecisionCoverage coverage = InboxDecisionCoverage.assess(inboxSnapshot, localSnapshot);
// On the UI thread, only while this caller/session is still active:
browser.show(review, coverage);
```

The UI joins by exact receipt identity, not grouped row index, visible name or
metadata alone. Before replacing a displayed snapshot it rejects mismatched inbox
IDs, source contexts, arrival times, incoming UUID/metadata, missing/extra/duplicate
rows and different full local fields/revision/image association. Full-state
fingerprints use the coverage state version; the read model itself does not claim
to carry a mutation version. A failed pair check leaves the prior dialog unchanged.
Do not fabricate a new inbox, empty successful history or v2 identities on a failed
load. This component adds no loader, source adapter or new planner/toolbar entry.

## Variant list and current state

Every group and received variant remains visible in its existing order/count,
including covered variants and equal metadata from another context. Each row shows
its original registration/context plus one of these explicit labels:

- Current coverage: `Granskat mot aktuellt lokalt tillstånd`.
- Older exact-receipt decisions: `Äldre beslut · ny granskning av nuläget behövs`.
- No exact match: `Inget matchande granskningsbeslut`.
- No coverage model supplied: `Beslutstäckning saknas i underlaget`.

For a currently covered metadata difference, the comparison label says it was
already reviewed against the supplied state instead of simultaneously demanding
another review. Older decisions retain their warning after an edit, image change,
deletion/re-add or old field values restored at a later state version. Details show
local store ID, actual supplied state version/revision and active/deleted/unknown
local-slot presence. Known deletion is explicitly described rather than contradicting
it with the legacy unknown-deletion message. No automatic re-add is suggested.

SAME_METADATA remains a separate comparison result, not proof a decision is current.
The older-state warning explains that a new import choice needs a fresh review;
identical metadata alone does not require another operator choice. No button
changes field-selection/confirmation policy or writes merely to clear a warning.
Current coverage is an assessment of this snapshot, not a new save confirmation,
source authentication, image-byte proof or authorization to bypass fresh commit checks.

## Decision history and observations

Variant details provide `Granskningsbeslut (N)` separately from the existing
`Historik (N)` observation navigation. The decision list keeps all matching choices
and labels each as current or older; it does not select the latest arrival as a
winner. Decision details show operation/source/receipt identity, action/outcome,
before/result versions and revisions, explicit selected fields (or whole metadata
for Add), exact incoming metadata and retained before/result metadata. The saved-result heading is explicitly historical, not an
assertion that those fields are still live. Raw local image handles are never rendered.

The group screen's `Alla granskningsbeslut (N)` exposes the complete decision
history, including when the inbox is valid but empty. Records without an exact
current receipt are explicitly historical/unmatched; they do not gain current
coverage by label, context approximation or target UUID alone. Thus resetting or
losing a receipt cannot hide an already retained decision in the supplied history.
Observations remain labeled historical metadata, without invented observation
times, image handles or source authority. Existing observation navigation is intact.

## Refresh, close and later confirmation

A new `show` replaces the supplied snapshots and invalidates old callbacks;
legacy refresh clears old coverage. Close/stop prevents old history/preview actions
from reopening a selection. There is no automatic live refresh or storage callback.
The caller must reload after changes; static labels do not guarantee later state.
The existing import-choice transition passes the inbox/receipt selection and uses
its own fresh-load/prepare/confirm/save checks. Coverage never replaces that flow.

## Verification — 2026-10-27

Targeted Robolectric host-JVM tests passed **106 cases**, including **22 new
coverage UI cases**, at Android API 26/35 without an emulator.
The new InboxCoverageUiTest covers visible current/different-context variants,
image-only changes/restored values, metadata equality after later writes,
delete/re-add with restarted revision, exact Add/update selection/result history,
old/current decision history versus
observations, retained unmatched history in an empty inbox, incompatible input
pairs, legacy refresh/stale callbacks and unchanged inbox/local/image bytes on
navigation/Close. Fixtures use real isolated synthetic stores for setup only;
all browsing under test performs no writes.

Regression runs include InboxReviewUiTest, InboxReviewTextTest, ImportDecisionUiTest
and ImportCommitUiTest. `sh scripts/test.sh` also passes existing core/coverage/
codec/store/migration/preparation/publication/100-device/envelope checks. Exact
counts/commands are recorded in [TESTING.md](TESTING.md). No Docker, emulator,
ATAK SDK app/APK build, physical-device or server/transport trial was run. These
tests do not establish signed ATAK runtime behavior, phone layout or live ingress.
