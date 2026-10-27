# Optional local import confirmation and recovery UI — #15

Based on main `cf49067` after the versioned planner integration (#112). The owner
assigned this slice while agent 2 independently works on read-only decision
coverage. Existing approved field/time/identity, TMMD v2 and TMMI formats remain
unchanged. This component invokes the real local atomic transaction; it introduces
no ingress route, source registry, schema, image transport or cleanup policy.

## Explicit opt-in and caller boundary

The existing four-argument `ImportDecisionUi` constructor remains memory-only.
An optional fifth argument, `ImportCommitUi`, enables **Spara val lokalt** in the
exact preview. Default construction still prepares in memory without exposing a
command or writing a decision. The read-only browser distinguishes its optional
commit-capable entry from the default preview-only entry in its button wording.

Construct ImportCommitUi with actual LocalV2Store and InboxStore instances, the
same serialized worker used by normal planner/image writes, and a Host providing:

- UI posting and a fast thread-safe current session/local-access policy.
- A fast thread-safe trusted-source policy for the exact inbox ID and receipt.
  This is checked immediately before every new attempt/retry. Context/owner text,
  valid bytes or a Prepared instance do not authenticate provenance. Policy failure
  or an exception means no new attempt; do not implement this as unconditional
  true for live traffic. External source permissions are not locked by file locks.
- A fast UI-thread `changed(target)` callback that enqueues a fresh storage/map
  refresh after a known local commit. It must not publish, infer delivery, perform
  synchronous file/network work or interpret the historical result as current.

**No production toolbar/receiver or live trusted-source owner is wired here.**
The real planner now has v2, but a validated selected-feed/context adapter is still
needed before a live received-item entry can use this endpoint. No fabricated inbox,
missing-file initialization, v1 migration fallback, fake source mapping, automatic
arrival apply or per-item prompt for merely viewing the shared picture is added.
No version bump or signing archive accompanies this still-optional component.

## Preview → confirmation → local transaction

The existing explicit Add/re-add/Keep/selected-field choices and exact complete
preview remain authoritative. The final preview's **Spara val lokalt** is the
user's save confirmation; there is no extra round of permission questions. The
chosen local edit Instant, fields, receipt and full local/image state remain bound.

The flow loads fresh real snapshots on the worker, consumes the pending review
once using `prepare`, then transfers the returned memory-only Prepared to the
optional endpoint. During that handoff/commit the parent remains busy. Duplicate
old callbacks cannot queue another prepare/apply. Cancel or stop before a queued
attempt prevents it; access/source policy is checked again before actual file I/O.
Core commit repeats exact receipt/state checks under inbox-then-drafts locks.

After successful commit the UI distinguishes:

- Enheten tillagd lokalt.
- Valda värden sparade lokalt.
- Lokalt val registrerat (Keep; no draft mutation).
- Samma metadata (no write/history/callback).
- Tidigare lokalt beslut verifierat (exact operation already committed).

Result details show the decision's historical version/revision separately from
the current state at inspection, including later deletion, ID/status or newer
version. They never restore an old result, claim live data, feed publication,
global conflict resolution, source authentication or a transport acknowledgment.
No raw image handles or raw filesystem errors are displayed. Current image
association and bytes remain under existing local ownership; import performs no
image write or cleanup. A refresh callback exception cannot undo a known commit;
the UI explains that the view must be reopened. Reentrant close cannot resurrect
an old result dialog.

## Safe failures and uncertain outcome

Busy, stale and invalid/capacity outcomes have separate safe guidance. Stale
requires fresh review; no silent rebase or automatic retry is offered. Count/size/
validation rejection does not evict evidence or reset storage. The exact preview's
selection remains in the in-memory command until closing; no partial result is
substituted for the confirmed fields/time.

An I/O outcome may already have committed. The UI reports **Sparandet är
obekräftat** and offers an explicit **Kontrollera sparat beslut** operation:

1. Read only the local ledger, using the same dataset/operation ID **and exact
   Prepared binding**. `LocalV2Store.readback(prepared)` adds this binding check
   using the existing typed comparison. A different binding under the same ID is
   UNKNOWN rather than proof that this choice succeeded. The older identity-only
   readback API remains available; no bytes/contracts change.
2. COMMITTED shows historical evidence and current slot separately and queues a
   storage refresh once. Inbox loss or revoked source access cannot erase this
   local proof; global/local session access still applies.
3. NOT_COMMITTED is reported only after a complete valid same-dataset read. If
   source access is still valid, offer **Försök samma val igen**. This is a one-use
   explicit same-command/same-ID retry, with all fresh core guards; a changed target
   fails rather than rebasing. No retry under a new operation ID is invented.
4. UNKNOWN for missing/corrupt/future/different-identity/unreadable/busy evidence
   remains uncertain. Offer inspection again or close, never initialization,
   rollback, restoration or a blind new save.

Close/Back discards the private command. Restart does not restore or execute it as
standing consent. A transaction already started can finish after stop; generation
checks suppress late UI/refresh callbacks, without pretending the write rolled
back. Normal source/access checks are not atomic authorization against a remote
service and do not prove remote feed membership.

## Verification — 2026-10-27

`sh scripts/test.sh` passes, including exact-binding readback rejection and the
existing transaction/migration/process/format tests. **84 targeted cases pass**
across Robolectric API 26/35: 28 new commit/recovery cases, 30 existing preparation
cases, 22 browser cases and four text-rendering cases.

New tests use real temporary inbox/v2/image files and the production transaction:
exact preview Save/one-use handoff, unchanged image/inbox bytes and exact edit time;
Cancel/queued stop/access/source loss; image-state staleness; Keep/equal/Add/replay;
historical-versus-current deletion; injected failed rename and post-replacement
error; local-only readback after inbox/source loss; explicit same-ID retry and
stale retry rejection; missing/corrupt/different dataset uncertainty; busy/capacity
preservation; old callbacks and reentrant-close suppression. No snapshots or image
handles are printed. Fixtures are synthetic and existing golden bytes unchanged.

SDK probes, CivDebug build/lint/app unit tests/static APK checks and CivRelease
assembly/shrinking pass locally. No full image-library suite, CI wait, emulator,
Docker, signing upload, physical-phone/server test or dispatch was run. These tests
do not establish actual ATAK layout/lifecycle or trusted Data Sync ingestion.

Next: integrate agent 2's read-only coverage when reviewed, establish the actual
trusted ingress/context adapter and owner, wire the received-item entry and refresh
lifecycle, and complete signed two-client/phone acceptance. The versionCode 20
storage-upgrade runtime gate remains independent. #15 stays open.


## Source-path follow-up — 2026-10-27

[The verified SDK ingress surfaces](INGRESS_SOURCE_VERIFICATION.md) identify usable
signatures and concrete provenance/coverage limits. Connection hints do not yet
establish selected-feed identity; a native-import runtime observation gate remains
before a live source owner may inject this endpoint. The existing logger-based
lab inspector is not promoted to product ingress.
