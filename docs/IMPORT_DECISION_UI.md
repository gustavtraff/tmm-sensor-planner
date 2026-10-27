# In-memory import-choice and exact-preview UI — #15

Based on main `2592daf`, after #107's preparation engine and #108's read-only
browser. The owner authorized this next UI slice on 2026-10-27. It uses the
existing approved `ImportDecisionPreparation` actions, fields, time/identity rules
and binding. No schema, storage, migration or synchronization policy changes.

## Components and caller contract

`ImportDecisionUi` takes an Android Context, a worker Executor, a pure MGRS
formatter and a Host. The Host supplies a fast **thread-safe** availability/access/
source policy, background loading of successfully validated inbox + genuine
`LocalV2.Snapshot`, UI posting and a local edit-clock value. Missing/corrupt/v1-only
storage must fail or make the component unavailable; no default empty state,
replacement store UUID, revision-derived persistent version or migration is made.
A valid binary/context string by itself does not authenticate or activate a store.

`show(expectedInboxId, selectedReceipt)` binds entry to the actual browser
selection. The initial load checks inbox identity, receipt ID/context/first time,
subject UUID and metadata equality; it does not silently select a different
receipt with the same visible label. Additional historical observations do not
change the selected metadata variant. Source authority belongs to the caller and
must remain available; the component cannot infer it from owner/context text.
The public preparation factory validates both full snapshots before a preview;
LocalV2 is also validated before presenting initial choices. The private inbox
codec is not exposed as a new UI API.

The read-only `InboxReviewUi` retains its original two-argument constructor and
behavior. A new optional constructor takes this preview component and adds
**Förhandsvisa importval · inget sparas** inside an explicitly opened variant.
That transition closes the browser and passes inbox/receipt identity, not the old
v1 local classification as versioned state. Default construction has no import
button, no loader and no preparation action.

**No production toolbar/planner entry, live receiver or activated v2 loader is
added.** A future owner must supply actual storage/access policy and coordinate
both controllers' lifecycle. Preview and commit are separate: the current Host
has no commit method or prepared-result callback, and the private Prepared object
is discarded when the result closes. It is not exposed as durable consent for an
automatic later write. No version bump or separate signing request is needed for
this still-unwired component; v19's saved-map-copy runtime gate is unaffected.

## Choice and exact preview

1. Load on the worker and recheck access. Never replace a failed load with captured
   or fabricated data. Availability is checked before queued reads/builds and
   before result presentation as well as before fresh preparation.
2. For an active differing UUID, offer **Behåll lokalt** or **Använd valda mottagna
   värden**. The latter starts with no checked fields and requires an explicit
   nonempty selection from this single receipt. Position selects both coordinates.
3. The eight labels bind to the existing explicit field masks, not display order.
   Null deployment time/bearing is labeled **Rensa utplaceringstid/Rensa bäring**.
   Type/bearing combinations must validate; choosing Gateway does not silently
   clear an unselected bearing. Invalid choices fail with safe Swedish guidance.
4. For never-seen absence, offer an Add preview without claiming the UUID never
   existed before initialization/migration. Known deletion has distinct wording
   and an unchecked explicit re-add checkbox; no preview can bypass that choice.
5. Fully equal metadata needs no preparation prompt or new operation. Different
   metadata with unchanged selected values previews Keep, with no revision/version/
   timestamp bump. The engine remains authoritative for these classifications.
6. Capture the caller's local clock once for a selected-value preview. Do not clamp
   it against foreign/local times or refresh it at confirmation. The engine rejects
   backward/missing edit time for a real edit and normalizes unused time to null.
   A creation-time mismatch blocks received-value application, with an identity
   review message. Raw exception messages/causes are not forwarded to the UI.
7. Show the complete exact hypothetical result, selected field names, subject and
   source binding, current/proposed persistent versions and proposed local revision.
   Use the existing metadata renderer for status, MGRS, true-north 6400-mil display,
   exact numeric detail, nanosecond UTC times and text. State image exclusion/local
   association preservation without opening an image or showing its handle.

The preview is explicitly **Exakt importförslag · inget sparas**. Add preserves
received creation/update values and does not claim local authorship. Keep leaves
the full local draft unchanged. Selected edits retain UUID/creation/image and bind
the exact previewed edit time. Capacity is not reserved and no decision record is
written merely because a valid hypothetical result exists.

## Fresh memory-only preparation and lifecycle

**Förbered i minnet** consumes the pending review once. It loads fresh snapshots
on the worker and calls the existing `prepare` binding checks; changed store/source/
receipt/state/version/metadata/image information fails without a silent rebase.
The successful dialog says **Inget har sparats eller applicerats. Ingen historik
eller kvittens har skrivits.** Closing releases the private prepared reference.
A future transaction must revalidate again under its own approved locks and source
policy; this in-memory check is not atomic with a later application.

Cancel/Android Back, stop and close invalidate a generation, close pending reviews
and discard results. Old dialog buttons cannot cancel/navigate a newer flow.
Queued worker/UI results cannot resume a cancelled selection. UI result handling
and stop linearize under a small lifecycle lock; reads/codec/clock work stay on
the worker. The UI never waits for that worker. A second positive callback cannot
queue another preparation. No pending decision is serialized or restored.

## Verification — 2026-10-27

`ImportDecisionUiTest`: **30 passing cases across Robolectric API 26 and 35**.
Covers explicit/no-empty/all-eight field choices and masks, Add versus checked
re-add, Keep/equal/unchanged selections, exact preview/time/image/counters, type/
bearing/null removal, identity/clock errors, source/load/unavailable failures,
stale image/version/deletion, appended evidence, old callbacks, Cancel/Back/stop,
queued result suppression and optional/default browser behavior.

Tests use an actual temporary InboxStore and legacy DraftStore/image plus a
separate explicitly identified/encoded synthetic v2 fixture. Its store ID and
persistent version are independent fixture literals, not production migration from
v1. Host loading reads the fixture using the real codecs. Review/prepare/Close
preserves all four byte arrays; clock changes after preview do not change the
confirmed edit time. Tests do not prove that an active v2 store or loader exists.

The existing read-only browser's 22 API-26/35 cases and four presentation cases
also pass: **56 targeted tests total**. `sh scripts/test.sh` passed existing
core/inbox/v2 codec/preparation/publication/restart/100-device checks.
`sh scripts/check-sdk.sh` passed SDK probes, CivDebug build/lint, existing app unit
tests and static APK checks; CivRelease assembly passed including shrinking.
No full image suite, emulator, Docker, signing upload or CI wait was run.

Actual standard ATAK/phone layout/lifecycle, genuine v2 adapter loading, trusted
transport, persistence, migration/activation and a user-facing apply action remain
separate. Agent 2's storage work and the core/read contracts are untouched.
