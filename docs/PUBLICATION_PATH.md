# Reviewed publication path — #14

Status: publication boundary proposed in merged PR #91; core private preparation
implemented for human review, 2026-10-25.
Implementation baseline: main `5dde0b0`. The preceding slice defined the outbound
boundary and verified SDK construction/serialization. Neither slice enables an app action or transport. Agent 2
owns the separate #15 import/storage proposal; no incoming schema is decided here.

## Decision proposed for the next implementation slice

Use the evidenced manual Data Sync workflow as the candidate for a **reviewed
publication-copy pilot**, keeping direct Data Sync integration deferred. The
[operator matrix](DATA_SYNC_ACCEPTANCE_TRIAL.md) supports manual file import and
native feed selection; it does not establish a callable feed picker, confirmation
callback or recipient acknowledgment for TMM.

Implement private preparation first after review. Test the exact SDK path into
ATAK separately before enabling any creation of a normal host MapItem. Keep the
existing private feature layer and draft store as the source of truth. Do not
convert a saved draft into a root MapItem, enable the old one-recipient CoT lab as
production, borrow a stock importer identity, invoke private Data Sync actions or
introduce a direct HTTP publishing fallback.

This is a proposal for a bounded pilot milestone, not acceptance of #14/F05/F12
as complete. The project plan and [ShareReview](SHARE_REVIEW.md) still require a
real destination and freshness at confirmation. A preparation action cannot be
renamed Share to imply that those guarantees are implemented.

## Operator flow and truthful outcomes

| Step | Required behavior | What TMM may claim |
|---|---|---|
| Open a saved draft | Load current saved metadata, retaining private UUID/revision/image association for freshness checks. No host item, file export or network action. | Local draft. |
| Review preparation | Show the complete metadata projection, MGRS, true-north 6400-mil bearing, saved modification time and image exclusion. State that no Data Sync feed has been selected yet. Cancel writes/creates nothing. | Nothing prepared or sent yet. |
| Confirm private preparation | Reload on the same serialized writer boundary as edits/deletes. Changed, deleted, corrupt or unavailable data requires a new review. Encode only the reviewed metadata into a privately owned immutable candidate; retain exact values and stable UUID. Do not copy image handles/paths or the local revision counter into it. | Preparation completed, **only** after the actual private operation succeeds. No send/delivery claim. |
| Enter the host publication surface | Requires its own reviewed, runtime-tested SDK path and explicit disclosure that ordinary ATAK content differs from the private draft. The tested Local SD import must not be assumed equivalent to a programmatic importer or Marker factory. | A host copy exists, if actually observed; no selected-feed or delivery claim. |
| Select/review the native feed | Data Sync must show the real server/feed and its normal permission/confirmation controls. No feed name guessed from text or a generic Data Sync label used as destination identity. | Native operator action; no TMM confirmation callback has been established. |
| Native publication | Explicit native confirmation or native conflict choice governs that copy. Cancellation, read-only denial, offline queuing and server acceptance remain distinct. | The app cannot infer a successful publication from returning to TMM. |
| Actual recipient handling | #15 validates, reviews and durably records incoming metadata/conflicts. Native host/feed retention alone is insufficient. | No TMM receipt or durable-import acknowledgment in this slice. |

Proposed Swedish preparation wording is **Förbered publiceringskopia**, followed
by **Ingen feed vald. Inte skickat av TMM.** A local image must show **Bilden delas
inte**, even if the original file is missing. These are proposed labels, not UI
already implemented. Do not show Delad, Synkad, Levererad or Mottagen from file
creation, an SDK import result, a visible map symbol, a dispatch return or a native
panel being closed. No new persisted status enum or snapshot field is introduced.

If failure occurs before a private preparation side effect, report that nothing
was prepared. If a write/import side effect is ambiguous, do not claim rollback or
silently retry: reconcile the actual owned artifact/host item before presenting an
outcome. Drop unconfirmed reviews on Cancel/Unload/restart. Do not leave asynchronous
work able to create a copy after cancellation. Removal of an owned preparation
artifact is different from deleting a published feed association or every copy.

## Destination, freshness and the manual-handoff limitation

`ShareReview` retains its existing behavior. Its destination ID/label must identify the actual
verified route, not `Data Sync`, a placeholder server/feed, a guessed URI or an
empty selection. A separate preparation review has a different purpose; do not
manufacture a fake destination merely to reuse `ShareReview.isCurrent`.

Revalidation before private preparation does not establish freshness at a later
native Done click. A prepared immutable snapshot can outlive edits/deletion of
the original draft, an unload, restart or a changed connection. Native host copies
may persist and the operator may later publish an older reviewed copy. TMM has no
verified callback that can revalidate the draft and destination at that click or
prevent later native actions.

Before a user-facing handoff is enabled, the reviewer must choose one boundary:

1. Approve an explicitly manual **reviewed snapshot** pilot with those limitations,
   a visible copy version and honest unconfirmed outcome. This is a scoped
   preparation/publication workflow, not a silent weakening of the existing
   ShareReview contract or completion of all production sharing requirements.
2. Require fully integrated destination/freshness/result handling. Obtain and test
   the supported Data Sync contract first, or review a different transport
   architecture separately. Do not imitate a callback or add an undocumented API.

The proposed next step is private preparation plus a synthetic SDK-path experiment;
neither choice above is assumed accepted by this code. Source identity, causal
versions and any production metadata schema must be reviewed with agent 2's #15
contract before either side is wired into transport. The experimental `tmmShareLab`
payload remains a test canary, not an approved production protocol.

## Exact SDK proof in this branch

`sh scripts/test-publication-sdk.sh` uses the inspected external SDK, JDK 17 and
Android API 36 stubs. It is also included in the local `scripts/check-sdk.sh`
procedure, not SDK-free public CI. It compiles core plus the probe, constructs actual
SDK CotEvent/CotDetail/CotPoint/CoordinatedTime objects and serializes them in
memory. The existing hardened checker verifies all six fixed `sensor-*` cases,
including exact 15-field metadata, UUID/type and SDK-rounded outer position.
Archive/custom-detail presence is checked independently. Separate JVMs start under
en-US and sv-SE, so locale-dependent numeric formatting is exercised at SDK class
initialization. No fixture input, draft store, Android context or real data is read.

The shared test metadata factory/checker are package-visible only to reuse the
existing fixtures and validation. Existing probe behavior and wire/data contracts
are unchanged. The new source is under `scripts/`, not app source sets or core CI's
SDK-free compilation. It is not an installed publication adapter.

The same compilation checks the supplied public signature
`CotImporterManager.importData(CotEvent, Bundle) -> ImportResult`. That method is
**never called**. No MapView/importer instance, source tag, dispatcher or callback
is created. The signature proves neither a safe source/group identity nor native
feed behavior, completion or privacy. A successful ImportResult would still not
be feed acceptance or recipient acknowledgment.

Actual local results: all six construction/serialization cases passed in both
locale JVMs. `python3 scripts/test-datasync-metadata-probe.py` and
`sh scripts/test.sh` passed. Inspected SDK main.jar SHA-256:
`5a111497ca5722c5e0dfdca4a7e5852fa68400b6723038eba7fbc657cc5c8d9b`.
Its supplied directory is labeled 5.6.0.24; this is separate from standard 5.6.0.25
lab and reported 5.6.0.12 phone compatibility.

An initial private attempt to run SDK CotEvent.parse on the plain JVM failed at
Android Log with **Stub!**. This is recorded as unverified SDK parser/runtime
behavior, not relaxed by fake Android implementations. The successful proof tests
SDK construction/serialization and our existing checker, not Android parsing.
No emulator, Docker, Gradle/Android build, image suite, network transmission,
signing input, release or CI wait was needed or performed for this slice.

## Private preparation core

`PublicationPreparationReview(savedDraft)` captures the existing eleven-field
`ShareReview.Metadata` projection and image warning without requiring a destination.
`prepare(current)` returns an immutable `Prepared` value in memory only. This is
not an encoded artifact, an export, a host map copy or a publication result.
Its only fields are `metadata` and `imageStatus`; no image handle or local revision
is exposed. The original draft is retained privately only while review is pending.
Neither review objects nor saved drafts may be serialized or logged as payloads.

The caller must reload the saved draft and call `prepare` on the same serialized
boundary as edits/deletes. Preparation and destination-aware sharing use the same
comparison: UUID, local revision, all metadata and saved image association must
match. Missing/unavailable data must be supplied as null or the pending review
closed; a storage failure must never fall back to the captured draft. The helper
does not read storage, acquire its lock or detect edits after the supplied snapshot.

Every attempt consumes the review, including a stale/missing result. Success
returns the reviewed metadata; failure throws a generic exception and requires a
new review. `close()` cancels pending preparation and is idempotent. Synchronized
methods allow at most one successful preparation per review, including racing
confirmations or cancellation. If preparation wins a race with close, its returned
value exists; close cannot revoke copies or securely erase memory. The future app
owner must serialize lifecycle events, discard references on Cancel/Unload/restart
and separately govern any subsequent side effect. No lifecycle integration is
claimed here.

The core tests cover exact projection, optional fields/image exclusion, changed or
missing drafts, one-use failures/successes, cancellation and competing calls. A
real temporary DraftStore is reopened to check saved bytes/revision/image-reference
preservation. Existing ShareReview tests retain their exhaustive metadata and exact
destination checks. Snapshot v1, wire formats and received storage are unchanged.

No Android action, encoder, private file writer, transport, new status or APK is
added. Encoding/ownership/error handling for an actual artifact still need their
own reviewed format and implementation; this class does not decide #15's proposed
inbox format or production source/causal identity.

Validation on 2026-10-25: `sh scripts/test.sh` passed, including existing separate
process reopen/100-device checks and the new preparation tests.
`sh scripts/test-publication-sdk.sh` passed all six construction/serialization cases
in both locale JVMs. No emulator, Docker, Android build, image tests, app runtime
verification or CI wait was performed for this core-only change.

## Remaining gates

A [concrete metadata XML profile](PUBLICATION_FORMAT_PROPOSAL.md) and independent
fixtures were reviewed in #98. The owner authorized the
[core codec](PUBLICATION_CODEC.md), which now encodes/decodes that profile without
a writer or transport. No new source-context authority or native-handoff approval
is inferred. The experimental lab schema and approved inbox remain unchanged.

The v16 [synthetic SDK import candidate](PUBLICATION_IMPORT_PROBE.md) now provides
a guarded executable experiment for gate 3–4. Its
[signed standard-client run](PUBLICATION_SDK_RUNTIME.md) passed the bounded fixed
PIR import, native selection/Cancel/Done, actual B export and repeat/lifecycle
observations. This is actual runtime evidence, separately scoped from production
draft review, all-type compatibility and app ingress/acknowledgment.
The merged #94 inbox implements approved A–C separately, not transport or draft
application. This does not yet approve an artifact encoder or user-facing handoff.

1. Review this outbound boundary and core preparation together with #15's inbound
   contract. Keep agent 2's core/import work separate; agree production artifact
   identity/format before adding an encoder or private writer.
2. Integrate preparation with the app's serialized storage/lifecycle boundary and
   truthful review/error UI after the artifact contract is accepted. No normal
   host MapItem or operational transmission may result from private preparation.
3. Exclusively reserve the existing two-client lab before executing the exact SDK
   import candidate. Start the server/API first; verify real baseline exports,
   subscription/role controls and before/after recipient/observer controls.
4. With an unselected feed's auto-publication on and its prompt off, check copy
   creation, cancellation, repeated UUID and stale review/Unload/restart behavior.
   Verify both selected and comparison resources plus actual returned fields. Never
   infer this path's privacy from the earlier manual importer experiment.
5. Keep normal publication entry points disabled until their relevant path and
   manual/integrated boundary are accepted. Only then plan app integration and a
   new signed two-phone candidate; image delivery remains #26.

Agent 2 needs no emulator/Docker for its SDK-independent proposal/tests. This
branch does not modify ImportReview, DraftStore, snapshot formats, receiver code or
the shared DATA_MODEL/TESTING/NEXT_STEPS files. Both parallel branches add their
own test invocation to `scripts/test.sh`; retain both when merging.

References: [project plan](PROJECT_PLAN.md), [share review](SHARE_REVIEW.md),
[investigation](DATA_SYNC_INTEGRATION.md),
[ADR 0010](decisions/0010-investigate-data-sync-workflows.md),
[ADR 0011](decisions/0011-sensor-point-data-sync-lab.md).
