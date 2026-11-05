# Reviewed unassociated-stage discard backend (#156 / #26)

The accepted image decision F permits explicit bounded local retention/cleanup.
This makes **one unassociated entry** concrete on the unchanged approved stage-v1
layout. It is an inactive backend API: no cleanup UI, ingress caller, automatic
expiry/eviction, mission-wide deletion, association/history change or remote removal
is activated. End-user/association integration still requires separate review.

## Exact review and confirmation

`ImageStageStore.prepareDiscard(localId)` locks and validates the complete stage,
then freezes that entry's root, ready/pending state, exact file presence and bytes.
The opaque plan exposes only the local ID, pending flag, file count and logical
byte total. For a ready entry, `readyPayload()` returns a verified preview of the
**same frozen bytes**; never base the confirmation on a separate later store read.
For every pending entry it returns null, including a complete pending pair: partial
or uncommitted content is not exposed as a received image.

The worker/caller must bound live plans/preview memory and obtain an explicit choice
for that exact local copy. `discard(plan)` allows one attempt and rejects a different
root, changed state, missing/added files or changed descriptor/PNG bytes. It does
not silently recapture/rebase consent, retry on BUSY or persist a standing choice.
An unrelated new entry does not change this single-entry scope; all current root
state must nevertheless validate before any destructive step.

Under the same exclusive OS lock, a selected ready directory is first atomically
renamed to its matching pending name without replacement. An already-pending entry
stays pending. The method unlinks only the frozen named files, then the empty entry
directory; it verifies a fresh valid inspection with that local ID absent before
returning. Every other candidate stays intact. The same pending grammar and marker
are used; there is no new on-disk field, delete journal or format migration.

## Failure and scope

- Failure before deletion keeps the reviewed copy. After rename/unlink it can leave
  both files, one file or an empty pending directory, or an actually absent entry
  despite an unknown caller result. Pending still blocks new stage writes.
- Old plans are consumed after any attempt. Reopen actual state; if data remains,
  obtain a **new explicit review** of its exact remaining bytes. Opening/restart
  never resumes a deletion. Absence means absent now, not proof of a receipt/history.
- Unknown markers, foreign/nonregular/linked entries, quota violations or corrupt
  ready pairs fail closed. This is not a best-effort repair/reset API for damaged
  roots; a new confirmation cannot bypass those validation failures.
- This store currently contains unassociated data only. Before a future association
  registry/UI can call maintenance, independently prove the selected content has no
  retained association/reference that must be preserved. Matching names, UUIDs or
  image bytes alone do not prove that eligibility.
- Existing active local image handles/files, drafts, inbox metadata, gallery sources,
  server/sender/recipient copies and future association/receipt history are outside
  the operation. Equal image bytes outside this root are not a deletion target.
- File unlink is not secure erasure; in-memory frozen previews and other copies can
  remain. No persistent tombstone is created. The approved stage's history/rollback
  limits and lack of trusted source authority continue to apply.

A future confirmation UI must disclose those copy/history limits and distinguish
an unassociated partial entry from a verified image. It must not call this API as
automatic capacity recovery or treat a discarded copy as a remote withdrawal.

## Verification

Focused native-graphics API26/35 tests passed **44 cases**: the 30 stage regressions
plus 14 discard cases. They cover exact ready preview, partial/complete/empty pending,
byte/presence/state changes, identical entries in different roots, one-shot plans,
every removal milestone, concurrent confirmation, fresh review after partial removal,
invalid/foreign state and preservation of an equal active-image control outside
the stage. Independent stage-layout checks and native target/operation guards pass.
Standalone probe compilation and image lint pass (zero errors/six warnings).

The actual standalone Android evidence below uses only synthetic test-app
namespaces, not ATAK/operational data. The existing
metadata/inbox/local-v2/active-image contracts remain unchanged; no source authority,
image adoption, receipt or Data Sync result follows from this maintenance API.


## Actual standalone Android result — 2026-11-05

A clean, fixture-byte-verified test APK ran on the same isolated Android15/API35 /
arm64 emulator described in the stage evidence. SHA-256:
`dc5407602bc7336bb500f8617eb3aa64ac2c9009de2d39502dd2cce949b11123`.
The ordinary explicit-confirmation control passed, including exact frozen preview,
selected absence, one-shot refusal and preserved other pair. Four controlled kills
while holding the actual OS lock then passed fresh-process and guest-reboot readback:

| Kill point | Selected H2 afterward | Unselected H1 |
|---|---|---|
| Marked pending | Both files pending; new writes blocked | Exact ready bytes retained |
| Descriptor unlinked | Only PNG pending; new writes blocked | Exact ready bytes retained |
| PNG unlinked | Empty pending directory; new writes blocked | Exact ready bytes retained |
| Directory removed, before response | Absent now; no pending state falsely recreated | Exact ready bytes retained |

After `adb reboot`, the same original run IDs were checked read-only, without
reinstall, additional discard or stage writes. No interrupted deletion was resumed.
The parser positive control also passed. The APK was then uninstalled and the
single owned emulator/dedicated ADB server stopped; no Docker/ATAK/server/phone or
computer use was involved. Private matrix/logs stay outside Git.

This is bounded standalone API35 filesystem/process evidence, not physical/OEM or
loaded-ATAK acceptance, API26-native or power-loss/rollback/secure-erasure proof.
The independent wrong-root/stale-preview/concurrent-confirmation cases are host
native-graphics tests, not a claim that those exact races were forced on Android.
The existing native controller supports `--discard --kill-after` with one of the
four fixed DISCARD milestones; its `--read-only --discard --run-id` path only inspects.
UI/association integration, broader cleanup policy and operational sharing remain
separate review/acceptance gates.


Local supplied-SDK debug/release assembly, app lint and all three existing app unit
tests also passed (168 executed tasks). The debug APK passed its actual discovery,
API, no-permission, signature and packaging checker. This compiles the new backend;
it does not prove loaded-standard-host or operational compatibility. No production
caller was added. All deletion tests used only disposable synthetic copies.
