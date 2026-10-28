# Versioned planner storage and explicit upgrade — #15

Candidate versionCode **20**, `0.1.0-versioned-storage.1`, builds on the isolated
[v2 store](LOCAL_V2_STORAGE.md) and [v1 migration](LOCAL_V2_MIGRATION.md) from #110/#111.
The planner now uses v2 for all its metadata and attachment mutations. Application
ID, no-backup directory paths, image files, domain schema and outgoing/inbox byte
formats stay unchanged. The historical directory name `local-drafts-v1` remains
intentional: moving to a fresh directory would hide the existing collection.

This is a reviewable integration candidate. It still needs signing and real ATAK/
phone acceptance before operational upgrade approval. No inbox receiver, automatic
import, applied metadata choice, mission cleanup/reset or sharing permission is
introduced. Existing publication/lab guards remain in force.

## Startup and user choice

`LocalStorageUi` performs bounded read-only `LocalV2Store.inspect()` on the planner's
existing serialized worker, then posts UI results. Inspection holds the permanent
lock, validates complete v1 or v2 data and returns its actual format and frame size.
It never constructs a v2 identity from v1 revisions or interprets invalid files as
missing. Only valid v2 inspection contains a live v2 snapshot.

- Valid v2: load active drafts and restore the private map layer. Retained deleted
  slots and decisions remain in storage but are not rendered as active devices.
- Valid v1 during background restore: no migration or empty map collection is
  saved. Tell the user to open TMM. An explicit opening offers **Uppgradera och
  öppna**, explains preserved fields/images, absent older history, and that older
  binaries cannot read the upgraded data. Cancel/Back leaves v1 untouched.
- Missing committed file: no automatic empty initialization. An explicit opening
  offers **Starta utan historik** with a warning that absence cannot distinguish a
  new install from lost data. Users with previous drafts must cancel and investigate.
  Confirmation calls initializeNew, which refuses any file created in the meantime.
- Corrupt/future/unreadable/ambiguous files: show a safe error and clear stale map
  state. No upgrade, initialization, reset or fallback to an old copy is attempted.

Explicit initialization/upgrade confirmations are one-use. Old callbacks cannot
activate storage or cancel a later flow. Reads/confirmations queued before Cancel,
stop or loss of session access cannot start a write. A file operation already
started may finish atomically after stop; its late UI result is ignored. An operation skipped while access is unavailable releases its pending UI state
even if access returns before the abort callback runs; the next opening starts
a fresh read/choice. Nothing is serialized as consent or resumed automatically after restart. After uncertain
upgrade, reopening inspects whichever complete format actually committed.
There is no second activation record: the validated committed frame is authoritative.

## Complete caller inventory

`LocalV2.Snapshot.select(uid)` returns an immutable `Selection` with the original
store identity, presence/version, complete draft/image/revision state. New form
UUIDs bind absence against the snapshot captured when the form opened. A selection
is never rebuilt from fresh state just to make a failed old edit succeed.

| Caller | Bound operation |
|---|---|
| LocalPlanner create/form/MGRS/map edit | Save with captured storeId and exact expected state; preserve input on errors and require a fresh review after stale/unconfirmed saves. |
| LocalPlanner delete | Pass the captured selection into DraftAttachments; retain a deleted slot at its advanced version. |
| LocalImageUi picker/preview/attach/replace | Carry the same selection through picker/provider work and explicit Save, not just visible ID/revision. |
| LocalImageUi detach | Save removal with captured expected state; no stale revision-only path. |
| DraftAttachments post-save cleanup | Fresh valid same-dataset v2 inspection; consult all current active references before removing an old copy. Unknown/different-dataset outcomes preserve copies. |
| PublicationPreparationUi / SavedDraftCopyUi | Read actual v2 snapshots, bind the review to dataset/persistent state and reject byte-identical delete/re-add or dataset replacement before preparation/handoff. |
| LocalShareUi lab review | Same additional dataset/state check before the existing guarded dispatch. No route, destination or wire changes. |

No production app caller constructs DraftStore or calls revision-only save/delete.
The legacy core store remains for compatibility/migration tests. Current attachment
ownership comes only from active drafts, never metadata history. Image locations
and image normalization policy are unchanged. A successful save followed by an
unreadable/different-identity reload is reported as unconfirmed; no rollback or
cleanup is guessed. All paths remain on API 26+ behind the existing runtime gate.

## Capacity and limits

Valid inspections report committed frame bytes and the real v2 snapshot. At 80%
of **any** slot/history/byte budget, a persistent nonmodal warning appears in the
list, review and form. It separates active/deleted slots, decision count and bytes,
explains that deletion frees no slot and that history is not silently evicted.
The warning explains that reads remain possible and writes must fit the projected
limits. There is no reset button disguised as recovery. Explicit mission cleanup
and its separately approved physical format remain future work.

Unreadable usage is unavailable, never 0%. Metadata and image saves still validate
projected counts/bytes in the core store before temp creation. Errors preserve the
current editor or explain that a fresh read is needed; unknown outcomes never
claim a guaranteed rollback. Deleting a device removes it from the active list
and image ownership scan but retains its versioned tombstone.

## Verification — 2026-10-27

- `sh scripts/test.sh` passes, including read-only format inspection, immutable
  active-list/selection behavior, unrelated edits, stale image association and
  dataset identity checks, plus existing transaction/migration fault/process tests.
- **76 distinct targeted cases** across Robolectric API 26/35 exercise actual v2
  files and real attachment
  normalization: activation/cancel/Back/stale callbacks/queued stop/source loss,
  missing/corrupt/concurrently created storage, explicit migration preservation,
  slot/byte warnings, attach/replace/detach/delete, shared images, uncertain saves,
  image cleanup and fresh reads (22 activation, 18 attachment, 14 private preparation
  and 22 saved-copy cases, across the targeted commands). Byte-identical re-add and dataset replacement
  invalidate attachment and metadata review actions.
- SDK checks cover the installed external 5.6.0 SDK probes, CivDebug build/lint/app
  unit tests and static APK checks. CivRelease assembly including shrinking passes.

No full image-library suite, CI wait, emulator, Docker, signing upload, phone/server
trial or actual dispatch was run. These are bounded local/core/SDK tests. The new
activation dialogs and complete planner/map/provider lifecycle still require a
signed ATAK runtime/phone test; core/host tests do not establish operational safety.

## Next acceptance and work

After prerequisites and this candidate are human-reviewed/merged, build a source
archive from main for signing. Test upgrade on a synthetic existing v1 collection:
Cancel first, then explicit upgrade; verify fields/images, edit/map move, replace/
remove image, delete, restart and markers. Do not downgrade after migration or
restore an old copy over live v2. Test an explicitly new dataset separately.

Then connect the optional inbox/import-choice UI to the existing prepared atomic
transaction with trusted source/lifecycle/result handling. Trusted feed ingestion,
signed two-client acceptance, image sharing and production sharing approval remain
separate; this PR does not complete #15 or enable Data Sync ingestion.

## Returned candidate follow-up — 2026-10-27

[Signed v20 verification](SIGNED_V20_UPGRADE_VERIFICATION.md) records passing static
checks and an actual signed v18-to-v20 update install. Native migration acceptance
remains blocked: the resumed fresh-profile control reproduces a stock-host EGL
startup failure even with TMM disabled. No migration/marker/image runtime pass is
inferred from the existing core/host tests. Owned emulators were stopped.

## Signed runtime follow-up — 2026-10-28

[The resumed acceptance](EMULATOR_RECOVERY_V20.md) now passes native v1 Cancel and
explicit migration, full-field/image preservation, versioned form/map/image edits,
local deletion, cold-host automatic marker restore and unload/reload in one
isolated standard 5.6.0.25 emulator. The missing graphics fallback was corrected
without changing the candidate. Fresh/missing/corrupt activation, physical-phone
and connected-client gates were not exercised in that slice.
