# Read-only received-image review (#202 / #26)

`ReceivedImageReview.assess` joins already loaded association-registry, inbox and
local-v2 snapshots for one device UUID. `ReceivedImageContent.open` then reopens a
selected record's actual retained content. These are inactive backend components:
no HTTP, native export, source adapter, UI, automatic application or receipt is
enabled. The separate [first shared-image trial proposal](https://github.com/gustavtraff/tmm-sensor-planner/pull/201)
still needs explicit route/handoff approval before its implementation/activation.

## Review and opening guarantees

- UUID selects the device; matching visible labels never merge devices. Every
  source/association/delivery record is retained, without a newest/current winner.
- Registry and inbox namespaces must match. Missing receipt links remain visible
  as unavailable metadata, rather than disappearing or being rebuilt from an offer.
  Duplicate receipt IDs reject the review. Missing stores are errors, not emptiness.
- Current metadata equality is separate from active/deleted/never-seen presence.
  Private image handles and revisions do not become incoming metadata. A historical
  picture remains historical after local edits or deletion, even if it can be read.
- Opening accepts only an item from that review, reloads the exact inbox namespace
  and receipt, and verifies that the exact association still exists in the same
  registry namespace. The registry lock covers the stage read, in the existing
  registry-before-stage order used for association and guarded discard.
- The existing stage reader verifies the actual descriptor and strict PNG bytes.
  The result must also match the retained receipt metadata, association/delivery,
  device UUID, digest, length and dimensions. A coherent replacement descriptor
  with different metadata cannot reuse a previously reviewed association.
- Missing/corrupt content fails without recreating it, removing history, replacing
  a private image or choosing another record. Returned image bytes are defensive.

The domain join performs no I/O. The opener reads the approved stores and may
create their existing empty lock files; it does not write data frames, stage
contents, drafts or active attachments. No persisted or wire format changes.
`ImageAssociationRegistry.withRecord` uses its existing exclusive OS lock and
executes only the caller's read-only stage action. Missing/changed namespace or
binding refuses the action; the action's own I/O failure is preserved internally.

## Caller obligations and limits

Run on the owning serialized worker, with bounded live image memory. The future
UI must reject callbacks after cancellation/unload and refresh current local state
when describing a picture as matching that state. Snapshot equality here is not a
continuous local-state lease. Inbox receipts and registry records are append-only
under the existing cooperative-store contract; arbitrary backup rollback or a
malicious same-UID peer is outside that contract.

An existing association records historical source evidence supplied by an approved
adapter. This reader does not establish new feed authority, current permission,
authorship, scene authenticity, delivery acknowledgment or local image adoption.
Previously verified local content can be opened without a network permission check;
it is not proof that the server still serves it. Source ingestion and actual viewer
integration remain separate work. No signing/version bump is needed for this
inactive backend alone.

## Verification — 2026-11-12

Core tests use independent descriptor fixtures to exercise UUID/source/receipt
linkage, all local presence states, metadata changes, image-only local differences,
broken links, offer substitutions, immutable output, registry locking and unchanged
stored frames. Their association proof is a test stub, not real image evidence.

The Android-library tests instead read the independent actual container and PNG,
write/read the approved stage and registry, and exercise the complete local opener
on Robolectric API 26/35. They cover exact bytes, namespace/selection refusal,
missing receipts, corrupt/missing stage files, coherent descriptor substitution,
local edits/deletion, defensive bytes and complete before/after file preservation.
`sh scripts/test.sh` passed. The full standalone image/UI suite passed **524
cases, zero failures/errors, one existing skip**, including 12 actual local
content-opening cases on API 26/35; standalone image lint passed. These tests
do not prove an ATAK runtime viewer, authenticated feed adapter or physical
two-phone behavior. `sh scripts/check-sdk.sh` also passed the supplied ATAK
5.6.0.24 SDK probes, CivDebug assembly/lint/app unit tests and static APK checks.
No emulator, native viewer, signed-client test, new HTTP call, signing upload or
physical-phone test was run for this inactive slice.
