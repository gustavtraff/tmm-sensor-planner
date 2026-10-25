# Publication metadata format candidate 1 — #14

**Status: proposed for explicit owner review; not an accepted production schema.**
Date: 2026-10-25. Final baseline: main `f6ce51a`, including the signed SDK-path trial
and agent 2's merged read-only inbox review (#97).
This PR supplies independently encoded fixtures and a test-only inspector. It
adds no production encoder/decoder, artifact writer, Android action or transport.
Agent 2's #15 review assembly and the approved inbox format remain unchanged.

## Decision proposed

Approve a small standalone **metadata XML profile**, `tmmPublication` version 1,
as the next codec's input/output contract. Use the reviewed eleven-field metadata
projection; retain the device UUID and exact domain values, with explicit units and
image exclusion. Do not turn the experimental `tmmShareLab` payload into production
by changing its name in place. Both lab codecs/checkers continue to behave as before.

This format is deliberately not a CoT event or a Data Sync request. A private
preparation cannot be handed directly to ATAK's generic XML importer or advertised
as a selected feed item. A future separately reviewed adapter may carry the same
metadata element inside a CoT envelope; tag preservation and actual routing need
new tests. The old tag's lab result is not verification of the new tag/adapter.

## Shape, bounds and fields

UTF-8 without BOM, XML 1.0; an optional XML declaration may specify UTF-8.
Exactly one unnamespaced `tmmPublication` root and the following **15 attributes**
are required. No other attributes/namespaces, child elements, non-whitespace text,
comments or processing instructions are allowed. Attribute order, quoting and
formatting whitespace are not identity. DOCTYPE/external entities, schemas and
XInclude are forbidden. A reader must not fetch external content or print parser
diagnostics/input values in a user-visible error or log.

Bound the complete input to **262,144 bytes before parsing**. The profile has
element depth one and fifteen attributes; apply bounds before decoding/allocating
large values. Each `*Utf8` attribute is at most **87,384 ASCII characters**; its
decoded UTF-8 is at most **65,536 bytes**. Other attributes are at most **128
characters**. Domain limits also apply, notably the 2,000 UTF-16-code-unit comment
limit. This is a metadata-only profile, not an image/package allocation budget.

| Attribute | Required interpretation |
|---|---|
| `schemaVersion` | Exactly `1`; independent of snapshot-v1, inbox-v1 and lab schema numbers. Unknown versions fail closed. |
| `deviceUid` | Canonical lowercase 36-character UUID of the reviewed device. No new UUID on preparation, resend, receive or manual-ID change. |
| `displayIdUtf8` | Canonical padded standard Base64 of strict UTF-8 for the manually entered display ID, e.g. synthetic `PIR 134`. |
| `type` | Exact enum name: `MINI`, `SCOUT`, `PIR` or `GATEWAY`. |
| `status` | Exact enum name: `PLANNED`, `DEPLOYED`, `ACTIVE`, `RETRIEVED` or `CANCELLED`. |
| `ownerUtf8` | Same Base64/UTF-8 rule for the saved owner text; this is editable metadata, not authenticated authorship. |
| `createdAt` | Exact canonical `Instant.toString()` UTC value; retain nanoseconds. |
| `updatedAt` | Same instant rule; must not precede createdAt. Neither a causal version nor recipient acknowledgment. |
| `deployedAt` | Empty string for absent, otherwise the exact canonical UTC instant. Never derive it from status. |
| `commentUtf8` | Same Base64/UTF-8 rule, including empty string; preserve tabs, line breaks, Unicode and punctuation. |
| `latitude` | Canonical finite `Double.toString()` degrees in [-90, 90], retaining the saved value. |
| `longitude` | Canonical finite `Double.toString()` degrees in [-180, 180]. |
| `bearingDeg` | Empty for absent, otherwise canonical finite `Double.toString()` in [0, 360), only for PIR/Scout. No conversion to rounded display mils. |
| `bearingReference` | Exactly `true-north`, including when bearing is absent. |
| `imageIncluded` | Exactly `false`. No image, reference, path, URI, thumbnail or image-delivery state accompanies these bytes. |

Required ID/owner must already equal their Java `trim()` result and be nonempty,
matching the validated domain. Do not trim, normalize or substitute received values
to make invalid input pass. Base64 is encoding, not encryption or redaction; it
prevents XML attribute normalization from changing text. Reject malformed UTF-8,
noncanonical Base64/padding, unknown enums and scalar spellings, nonfinite/out-of-range
numbers, mismatched optional/type rules, duplicate/missing attributes or extra roots.

Negative zero is retained by the scalar encoding. Metadata comparison still treats
positive and negative zero as equal under ImportReview. Reordered XML and other
allowed syntax can also express the same metadata. A file hash or raw XML string
must therefore never replace domain equality for duplicate/conflict decisions.

## Identity, source trust and missing fields

`deviceUid` identifies the subject, not the sender. Different devices may have the
same visible ID; equal labels never authorize a merge. The exact incoming metadata
may match an existing inbox receipt or be a distinct preserved variant according
to its separately approved context + UUID + metadata key.

**No source/feed context is asserted by this file.** A future ingress adapter must
obtain and validate the source boundary, then map it to InboxStore's stable local
context ID. An owner/callsign, mutable feed label, arbitrary URI, supplied `sourceId`
or this file's UUID cannot authenticate that mapping. The current contract does
not specify or implement that adapter. Missing/unverifiable context must fail
before a receive operation; it must not be guessed from these attributes.

There is no local revision, remote revision, receipt ID, publication/message ID,
vector clock, origin account, destination, Applied/Shared flag, tombstone or
attachment field. In particular, a local file name must not become remote object
identity or a trusted version. The metadata format cannot prove causality or
choose the newest variant; the inbox preserves differing variants for review.
Any future causal/withdrawal/attachment extension needs its own versioned contract.

`imageIncluded=false` means only that this payload contains no image. It does not
assert that the sender has no image, authorize clearing the receiver's attachment,
pin an older local image or claim its bytes arrived. The existing preparation UI
must continue to derive **Bilden delas inte** from the local saved association.

## Preparation and future envelope boundary

The future encoder should accept only the explicit metadata projection from a
successful one-use preparation, never serialize a TmmDraft/review object wholesale.
Validate size/Unicode/domain suitability without modifying source metadata. Saved
image associations and revision remain private freshness inputs even though they
are absent from this format. A too-large ID/owner or malformed saved Unicode must
produce a clear preparation failure; do not truncate text, renumber IDs or silently
remove fields. DraftStore already accepts some values that cannot fit this bounded
publication profile; existing drafts must remain readable and unchanged.

Only claim a private artifact exists after its actual owned write/readback succeeds.
This PR chooses byte semantics, not Android directory/filename/retention/atomic-write
or lifecycle implementation. Those must preserve private no-backup ownership,
revalidate on the serialized edit/delete boundary and distinguish failed versus
ambiguous writes. A file checksum would not authenticate its sender. No private
XML should be auto-exported, auto-imported or broadcast as a side effect of saving.

For any future CoT adapter, require outer event UID = `deviceUid`, exact inner
coordinates, and the tested SDK-rounded outer point. Reject disagreement rather
than letting native map edits silently replace exact inner values. CoT envelope
times are envelope times, never substitutes for createdAt/updatedAt. The lab
sensor type is a candidate envelope, not a new domain sensor type. All-type/tag
runtime acceptance is still required before operational use.

The manual-native-handoff decision in [PUBLICATION_PATH](PUBLICATION_PATH.md) is
unchanged: no verified native Done callback revalidates a draft at a later click.
Approving this metadata profile would authorize a codec PR, not a destination API,
real-draft host-copy action, release, deployment or weakened freshness guarantee.

## Independent evidence and approval scope

Four [synthetic XML fixtures](../test-fixtures/synthetic/publication/README.md) were
encoded with Python standard-library ElementTree/Base64, without a Java encoder
or ShareLabFields. Their exact values/hashes are pinned independently in
`PublicationFormatFixtureTest`, which contains a **test-only inspector**. It uses
the existing validated domain and ImportReview equality; it writes no files and
does not add a production parser or call InboxStore. Its temporary TmmDraft carrier
has revision 1 and no image handle solely for validation, not draft application.

Checks cover all four types, optional values, manual IDs, Unicode/control text,
nanoseconds/exact degrees, signed zero/domain equality, reordered attributes,
size/UTF-8 byte/comment boundaries, wrong versions/enums/units, forbidden local/image
fields, namespaces, entities/DOCTYPE, child content, malformed UTF-8/Base64/XML,
duplicate attributes and extra roots. Passing inspection is not proof of a future
encoder, Android XML parser, file writer, Data Sync tag preservation or source trust.

Local verification on 2026-10-25: `sh scripts/test.sh` passed on Temurin
17.0.20.1+1 with `--release 8`, including the new inspector and the existing
inbox/core/snapshot/separate-process/100-device tests. The suite passed again after
rebasing onto #97, including its read-only inbox-review tests. Local document links and
`git diff --check` passed. No SDK build, Android/image suite, emulator, Docker,
signing submission or CI wait was performed; no new APK or ZIP is needed.

Owner review should explicitly adopt or amend **this profile and its identity/image
semantics** before a production codec/writer is implemented. This is separate from
the already approved inbox A–C; it does not reopen that format or approve D.
