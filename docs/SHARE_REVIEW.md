# Metadata sharing review preparation

Status: core preparation adopted by human merge of PR #38 for
[Issue #14](https://github.com/gustavtraff/tmm-sensor-planner/issues/14), F05/F12.
Date: 2026-10-17. Human PR review may adopt this preparation; it does not approve
production sharing or a wire schema. The production privacy work in #4 and the
other project-plan SDK/two-client gates remain prerequisites for transport integration.

## Review contents

`new ShareReview(savedDraft, destinationId, destinationLabel)` captures immutable
metadata, a selected destination and one of two explicit image states:

| ImageStatus | Required meaning in a later UI |
|---|---|
| `NO_LOCAL_IMAGE` | No image is attached; no image will be sent. |
| `LOCAL_IMAGE_NOT_SHARED` | A local image is attached; **Image will not be shared** (Swedish UI: `Bilden delas inte`). |

Image status is based on the saved association, even if its file is missing. The
helper never opens images, resolves a file handle or claims delivery/removal.

The `metadata` projection exposes only UUID, visible ID, type, status, owner,
createdAt, updatedAt, optional deployedAt, comment, position and optional bearing.
It contains no imageRef, file/URI/path field, image bytes, local revision counter
or shared state. Free text and position are retained for explicit review, not redacted. Every value is copied from the validated immutable domain value without
rounding coordinates/bearing, changing manual IDs, deriving timestamps or selecting
defaults. Optional values remain optional. Bearing retains the true-north convention;
a later UI must label it. A local revision is retained privately for freshness only,
not advertised as a cross-client version or acknowledgment.

This is an allowlist for review preparation, not a serializer. `ShareReview` also
privately retains the original draft (including its local image handle) for freshness;
never serialize/log the review object or original draft. Only use the explicit
metadata projection when designing the later reviewed encoder. Schema version,
CoT representation, source/causal identity and any additional protocol fields still
require their own contract and round-trip tests. The synthetic lab fixture does not
freeze that contract. This helper changes no snapshot-v1 bytes or migration rules.

## Destination and freshness

Destination ID and display label are required exact strings. Neither is trimmed,
case-folded or otherwise normalized; surrounding Unicode whitespace, control/format
characters, line/paragraph separators and malformed surrogates are rejected. This
avoids silently retargeting a selection or presenting multiline/bidirectional control
text. These text checks do not authenticate or authorize a destination.

A future adapter must obtain both from its actual user-selected verified route.
The opaque ID must identify the full destination/routing context, not an ambiguous
label reused on different servers/feeds. No allowed route, recipient lookup, contact
API, server URL or Data Sync integration is invented by this core helper.

`isCurrent(currentDraft, selectedId, selectedLabel)` requires exact destination
ID/label, UUID, local revision, metadata and local image handle to still match.
Missing/deleted current records or missing selections return false. Changed IDs,
status, owner, comment, coordinates, bearing, times or image association need new
review, as does a revision-only edit even at the same timestamp. A changed field at
the same revision also fails. An unchanged separately loaded instance matches.
Numeric signed zero follows ImportReview's existing numeric comparison; there is
no approximate coordinate/bearing tolerance. This shares its internal metadata
comparison without changing ImportReview's public API or classification policy.

The caller must freshly load the selected UUID and resolve the selected route at
actual confirmation. False means discard/rebuild and show the updated review for
new explicit confirmation; never silently confirm the refreshed values. Deleting
then exactly recreating all identity/state values cannot be distinguished here:
new local devices must use new UUIDs, as required by the existing model/workflow.

True means only that the supplied values match. It does not lock storage, prove
route availability, authenticate a recipient, consume a one-use confirmation or
claim that the UI displayed the review. A later adapter must coordinate final
revalidation and sending with local edits/selection changes, handle disconnection,
and report actual failures without marking drafts/images shared. Do not cache this
boolean as permission to send. Drop the review on cancel, unload or session changes;
there is no send, storage-write or confirmation operation to invoke here.

## Initial core verification and remaining scope

On 2026-10-17, `JAVA_HOME=<Temurin-17> sh scripts/test.sh` passed with Temurin
17.0.20.1+1 / `--release 8`. ShareReviewTest checks the projection allowlist/values,
optional fields, image states, each compared metadata field at an unchanged revision,
revision-only and image-only edits, changed/missing destinations, invalid destination
text, deletion and new review. Creating/checking a review from real saved data leaves
the snapshot byte-identical. Existing import-review, core/forms/storage tests,
the v1 fixture and separate-process snapshot reopen also pass.

The core-preparation PR changed no APK/UI/permission, SDK API, CoT encoder, transport,
send status, image library or shared test environment. No SDK build, emulator, phone
or two-client test was run for that preparation. CI compiles/tests it as Java core;
it does not prove ATAK compatibility. Keep #14 open for reviewed UI, actual routing,
explicit confirmation/cancellation/failure behavior and verified two-client delivery.

The later [manual metadata lab](METADATA_SHARE_LAB.md) connects this review to an
explicit, isolated one-recipient experiment. It does not approve production sharing
or remove the offline/physical-client gates.

## Publication-copy path proposal — 2026-10-25

[PUBLICATION_PATH.md](PUBLICATION_PATH.md) separates private metadata preparation,
host-copy creation, native Data Sync feed confirmation and durable TMM receipt.
It preserves this helper's destination/freshness contract: a generic handoff label
must not be passed as though it identifies an actual verified feed. Preparation
cannot claim Shared or receipt, and its freshness check cannot be represented as
a callback at native Done. The manual-snapshot versus fully integrated boundary
requires human review before a user-facing handoff is enabled.

An optional local SDK probe constructs/serializes all six fixed sensor cases and
checks their exact metadata under en-US/sv-SE. The importer signature is compile-only
and never executed. No runtime adapter, production schema, private-draft conversion
or network path is enabled. Agent 2 owns the separate #15 import/storage proposal.
