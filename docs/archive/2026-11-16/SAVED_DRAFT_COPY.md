# Reviewed saved-draft map-copy pilot — #14

Version **19**, `0.1.0-saved-draft-copy.1`, based on main `2c85c0d` after the
[signed four-type envelope result](PUBLICATION_ENVELOPE_RUNTIME.md). The owner
approved this bounded implementation on 2026-10-27. It connects saved-device review
to the evidenced SDK path, retaining the isolated-lab restriction. It is not an
operational Share action or completion of F05/F12.

## Operator flow and boundary

Under a saved device's **Fler uppgifter**, choose **Skapa granskad kartkopia · labb**.
The action is visible only under the existing lab session policy. It requires
flight mode and exactly the connected loopback lab stream; a missing route gives
an explanatory toast. A connected-local trial still disables lab actions. Use only
synthetic saved drafts in the isolated lab; the guard is not a semantic detector
of operational data and does not authorize sharing the user's actual devices.

1. Read the device from the saved store on the planner's normal single worker.
   A cached list item is not used as fallback. Review complete metadata, MGRS,
   true-north display mils, exact UTC times, UUID and image exclusion.
2. Show the host label **ID · rN** and the manual snapshot boundary before a new,
   explicit **Skapa kartkopia i labbet** confirmation. The local revision is a
   visible copy label, not a distributed/causal version, message ID or extra wire
   field. No UUID is regenerated and payload displayId is unchanged. ID plus suffix
   must satisfy the existing SDK label bound of 128 UTF-16 code units without
   controls; unsupported labels fail without truncation/renumbering or import.
3. Reload the saved record, consume the existing one-use preparation check against
   UUID/revision/all metadata/image association, and encode/decode the approved
   profile in memory. Changed/deleted/corrupt/unavailable data requires a new review.
   A saved image association always warns **Bilden delas inte**, even if missing.
   No image file is opened and no image reference/revision enters the payload.
4. Execute the actual SDK handoff on the UI thread, checking lab/session access
   again. The planner worker remains occupied until this callback completes so
   regular local edits/deletes/image updates cannot overtake the fresh check.
5. Report the actual SDK/host verification result or a refusal/uncertain outcome.
   The source draft remains local. No persisted Shared/Synced/Applied state, native
   feed selection, transport dispatch or delivery acknowledgment is added.

The review warns that ordinary ATAK copies can participate in host auto-publication,
persist after Unload/restart and outlive edits/deletion of the private draft.
There is **no feed selected by TMM**. Native Data Sync selection/Done is a separate
operator action, with no verified callback to refresh the draft or destination.
The result must not be interpreted as no network activity by the host or a verified
feed delivery. The native sensor/camera icon remains a common transport symbol,
not the private planner's type/status artwork.

**Förbered metadatakopia** remains the separate memory-only action; its result
cannot silently authorize an import. Existing fixed-canary experiments and the
old one-recipient lab are unchanged. This slice changes no incoming/inbox contract,
wire fields, local snapshot format, migration or #15 application rules.

## Existing host copies and uncertain effects

`PublicationMapCopy` uses the already inspected public MapView/group, CotEventFactory
and CotImporterManager APIs. The source remains the plugin's own
`se.tmm.sensorplanner.plugin.publicationlab`, as tested in v18; it does not borrow
StateSaver/MissionPackage identities or use private Data Sync actions.

Before any importer call, count root-group matches and export the existing UID:

- Zero matches and no existing object: construct/validate the approved envelope,
  recheck access, then invoke the importer once.
- Exactly one object with exact reviewed metadata **and** expected callsign: verify
  and reuse it without an importer call. No label, timestamp, field, feed association
  or object is changed/revoked by this branch.
- Different/unverifiable metadata or label, duplicate/inconsistent matches: refuse
  without an importer call or overwriting/removing anything. A new local revision
  can therefore require a separately reviewed update workflow; this pilot does not
  implement editing an already published copy.

After an attempted import, count again and compare the actual host re-export,
including label. SDK return and verified host copy are separate observations;
neither means feed acceptance or receipt. Failure after the importer boundary is
reported as attempted/uncertain. There is no retry, rollback, deletion or fallback
export. A failing SDK result does not authorize claiming the host was unchanged.

## Concurrency, cancellation and limits

The controller keeps normal planner actions busy-blocked for the entire modal flow
and uses the same worker as saved edits. It waits at most ten seconds for the UI
handoff; an unstarted expired/interrupted callback loses its permit. Cancel/Back,
connected-trial transition and Unload invalidate the generation and pending core
review and release a waiting worker. Stop invalidates synchronously before later
UI disposal. A queued callback cannot import after cancellation/stop wins.

The short SDK operation and cancellation linearize under one lifecycle lock. Once
an import has begun, closing/stop cannot revoke its side effects. Cancellation
then suppresses only later result presentation; the code does not claim erasure.
The main UI never waits for the worker. Metadata reads/codec work stay off the UI
thread. A second positive-button callback cannot enqueue another handoff.

This serializes **this planner's** worker, not every process or a later native Done
click. Snapshot v1 does not distinguish deletion/recreation with identical saved
UUID/revision/values, and another process can still write after the final saved
read. No cross-process transaction/anti-ABA guarantee is claimed. Agent 2's approved
v2 codec work remains separate and is not activated by this pilot.

## Verification — 2026-10-27

- `sh scripts/test.sh`: passed existing domain, inbox, v1 storage/restart/100-device,
  publication and proposed v2 fixture checks. No domain or stored format changed.
- Targeted Robolectric `SavedDraftCopyUiTest`: **20 cases across API 26 and 35**.
  Covers exact saved metadata/label/image exclusion, unchanged snapshot, stale
  metadata/image-only edit/deletion, corrupt input, Cancel/Back, duplicate clicks,
  queued handoff cancellation/stop, interrupted worker, lost route, ambiguous host
  failure and cancellation after an already completed host side effect.
- Existing `PublicationPreparationUiTest`: **12 cases across API 26 and 35** pass,
  retaining the separate private memory-only behavior.
- `sh scripts/test-publication-sdk.sh`: both locale JVMs pass the new counted host
  boundary tests for all four real SDK envelopes, independent serialized-wire checks
  with the version-label character, exact reuse, metadata/label/UID/type/count
  conflicts, unavailable/lost access and post-attempt uncertainty. Its host port is
  a test fake: it does **not** call a real MapView/importer or establish transport.
  Existing fixed envelope and legacy checks remain passing.
- `sh scripts/check-sdk.sh`: passed SDK probes, CivDebug assembly/lint, existing
  app unit tests and static APK checks. Initial compilation caught a controller
  name shadowed by the MGRS copy button; it was renamed before the passing run.
- `./gradlew --no-daemon :app:assembleCivRelease`: passed including shrinking.
- Signing-source boundary tests (five cases), shell syntax and patch checks passed.

No emulator, Docker, full image suite, signing submission or CI wait was performed.
Actual v18 SDK/feed evidence does not automatically verify this new saved-record
caller, UI dispatch, labels or lifecycle. After human review/merge, a **new signed
v19** must exercise synthetic saved-device creation, review/cancel/stale handling,
new/reused/conflicting host UUIDs, private bytes/images and two-client feed privacy
before and after native Done. The ten-second unstarted-callback timeout branch is
not a measured device timing guarantee; cancellation/interruption permit revocation
is tested. Actual device/SDK dispatch timing and external writers remain explicit
verification limits. No production-network activation or physical phone approval
is implied by a successful core/Robolectric/local build.
