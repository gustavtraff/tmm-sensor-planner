# Safe local mission cleanup (#15)

**Status: owner-approved product principles, 2026-10-25; technical storage layout
and exact activation/format fixtures await review before implementation.**
This complements [D's local application contract](IMPORT_APPLY_CONTRACT_PROPOSAL.md).
No cleanup API, storage writer/migration, Android UI or transport is implemented.

## Scope and user-facing meaning

Suggested UI label: **End mission and clear local data**
(`Avsluta uppdrag och rensa lokala data`). It is an explicit administrative action
on this device's private TMM working data, never an automatic capacity response,
arrival effect, timeout or server/feed deletion command. Until mission identity
exists in the product, display “all private TMM data on this device” instead of
implying that data are already isolated by named mission or feed.

The inventory must include private drafts, retained received metadata/observations,
review decisions and conflict metadata, UUID deletion markers, plugin-owned local
image copies and abandoned files belonging to this working dataset. Counts may
overlap semantically; explain images are owned copies and history will be forgotten.
Show the actual dataset identity/name and fresh counts, or block if scope cannot
be established safely. Do not infer ownership from a filename or scan shared paths.

Explicitly disclose what is **outside** the scope: TAK-server/feed content, other
clients' copies, host ATAK map/import/export copies and downloaded packages outside
the private dataset, original gallery/provider files, external backups, SDK or
signing material, and ATAK connection profiles/accounts/certificates. Do not invoke
native host deletion or any remote withdrawal. This is not a guarantee that every
copy on the device is gone, or secure/forensic erasure. No export/archive is created
automatically; any later export requires its own explicit data-sharing review.

## Three-step confirmation

1. **Scope and inventory.** List the private data to be lost and local storage
   usage, explain that retained history cannot be recovered by this action, and
   let the operator cancel without changing data. Never place the entry point
   beside ordinary Save/Back or substitute it for a capacity warning button.
2. **Consequences.** Clearly state that published/server/recipient/host copies
   remain, images are local owned copies, and shared objects may be received again.
   Require deliberate acknowledgment before moving to the final screen. Distinguish
   historical deduplication/deletion evidence from the visible active-device count.
3. **Explicit final confirmation.** Require typing the displayed mission name
   when one is actually configured, otherwise `RENSA`, then a clearly destructive
   final button. Bind confirmation to the exact inventoried dataset identity/state.
   Text from another mission/session is invalid. No default positive action,
   auto-submit, countdown or restoration of confirmation after process restart.

Cancel before final commit has no side effects. If incoming data/local edits alter
the inventory after review, stop as stale and show a fresh inventory before a new
confirmation. This confirmation cannot be reused to clear later arrivals or a new
dataset. Reachability of the server is not a precondition for local cleanup.

## Recommended storage direction for exact-format review

A sequence of independent deletes of drafts, inbox, history and images cannot
establish one restart boundary. Recommend switching one private **active-dataset
record** atomically to an already initialized empty dataset, then removing retired
owned data. Review its exact layout/encoding and independent interruption fixtures
before a writer. This record is an authoritative dataset selector, not an optional
migration-complete sidecar or a second commit for an individual D apply operation.
The existing inbox frame remains TMMI format 1; local draft v2 remains subject to
its own encoding/migration gate. Neither layout is implemented here.

Requirements on that future design:

- A stable lifecycle lock outside the replaceable dataset coordinates every
  stateful operation. Proposed global lock order: **dataset lifecycle → inbox →
  local drafts**. Never hold a later lock while requesting an earlier one. All
  writers/readers/background tasks must resolve and validate active dataset identity
  under this boundary; an old Store object/path must not bypass it. No mixed legacy
  access may remain at rollout. Do no UI/network work while holding locks.
- Bind final confirmation to the dataset identity and exact inventory state,
  covering both inbox and local changes. Under the lifecycle boundary, revalidate,
  pause local ingress/writers, and invalidate pending local confirmations,
  preparation objects and sessions. Do not reset ATAK/server settings. If busy,
  release locks and report busy without partially clearing the dataset.
- Prepare valid empty local/inbox stores with new identities and a bounded private
  ownership manifest in a separate new dataset directory. Existing committed data
  remains authoritative until atomic activation. A prepared empty dataset is never
  automatically promoted after a crash. No destructive cleanup before activation.
- Atomically activate the new dataset. Old inbox/draft IDs and every old D command
  then fail identity checks. Report the logical working-data reset separately from
  physical removal. Never interpret raw missing/corrupt selector/storage as an
  implicitly fresh installation or revive a retired dataset as an error fallback.
- Retired datasets must become inaccessible to normal review/export/old workers,
  then be removed using bounded validated ownership information. Never follow
  symlinks, delete original/shared/provider files or remove an image still owned
  by the active dataset. Keep the permanent lifecycle lock. No raw fields, paths,
  handles or credentials in public logs/error reports.
- If retired-file removal fails, say **local reset committed; cleanup incomplete**.
  Do not claim all private files were removed. A durable cleanup description/owned
  manifest must permit safe retry after restart; it may not retain raw metadata
  merely to serve as an audit log. Complete success requires confirmed removal
  of all retired private files in scope. Never roll back the new active dataset
  or replay an old Add to handle a cleanup error.

This adds a lifecycle boundary to the earlier D lock proposal only when cleanup
is introduced. It requires a reviewed rollout of all callers. Parent-directory
sync/power-loss protection, backup rollback and OS secure erasure are not inferred
from atomic activation/file sync; claim process-restart recovery only until proven.

## Recovery and resumption

| Interruption | Required behavior |
|---|---|
| Cancel, stale inventory, busy, prepare/write/sync failure before activation | Original active dataset intact; no success message. Ignore unactivated preparations and request new explicit confirmation. |
| Process death before atomic activation | Original dataset remains authoritative; never promote an orphan new dataset. |
| Activation outcome uncertain | Read/validate the active identity before reporting old/new state. Missing/corrupt evidence is UNKNOWN; do not delete either dataset or initialize a new one blindly. |
| Process death after activation or during retired-data removal | New empty dataset is active; resume only validated retired-file cleanup, not the old user command. Show incomplete cleanup until removal is verified. |
| Old worker/command executes after activation | Reject old identity/session; it must not rewrite or reactivate retired storage or silently publish anything. |

Clear local session confirmations and transient feed/recipient selection; require
an explicit new session/selection before resuming ingress. Actual account/server
configuration remains untouched. A feed can offer the same shared objects again;
resumed reception can refill the inbox and use capacity. Their visibility still
does not require per-device acceptance, but Add locally remains explicit. Clearing
local history intentionally forgets earlier keep-local/deletion decisions; never
claim permanent replay suppression or automatically recreate old private drafts.

## Required acceptance before a cleanup implementation can ship

Use synthetic datasets only. Test cancellation at each step, wrong/final text,
keyboard/back/restart behavior, stale inventory after both local edits and receipt
arrival, capacity-full/corrupt/unreadable datasets, offline operation, old store
objects and in-flight image/background tasks. Inject preparation/activation/cleanup
failures and halt separate processes before/after activation and partial removal.
Verify no automatic dataset promotion/fallback, honest incomplete-cleanup reporting,
new identities and expired confirmations, preserved active/shared/original images,
no remote/host deletion or publication, and no local auto-add on later reception.
Exact dataset-selector/manifest fixtures and migration/ownership rules are still
required before any writer; these are future tests, not passing behavior today.
