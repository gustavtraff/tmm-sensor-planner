# Image-offer descriptor bytes — approved version 1 (#134 / #26)

**Status: exact descriptor version 1 explicitly approved by the owner, 2026-10-30.**
The approval applies to PR #135 at `c3a9b21dd66b21cb86bbc0bd407e0b064762b95e`,
including the layout, bounds, UUID constraints, unchanged embedded M and CRC rules
below and both pinned 669-byte fixtures. It permits a separately reviewed production
codec implementation. Logical A–F/H and the Data Sync-feed goal G remain in
[the decision record](IMAGE_SHARING_CONTRACT_PROPOSAL.md). This PR adds only the
specification and test-only inspector in PR #135. The subsequent
[Java-core codec](IMAGE_OFFER_CODEC.md) implements this approved descriptor only;
no registry, adoption, native import or transport is introduced. Container/receipt/registry approval is still separate.

## Scope and recommendation

Use one small, transport-neutral binary descriptor for one immutable image offer
and one reviewed delivery. Embed the existing metadata-format-1 XML unchanged as
M; keep actual PNG bytes separate. This avoids a second spelling of every metadata
field and avoids expanding metadata format 1 or a local imageRef into a remote path.

This is a candidate **descriptor**, not a complete wire/container or storage format.
No filename, MIME registration, native package manifest, Data Sync resource API or
feed authority is chosen here. The verified route may constrain a future container;
that container still needs exact review and two-client evidence. A raw descriptor
must not be passed to a host importer or published by merging this proposal.

## Exact candidate layout

All offsets are zero-based. Integers are unsigned, big-endian, with no alignment,
BOM or padding. UUIDs are their 16 RFC-4122-order bytes (most-significant 64 bits
first, then least-significant); no text representation is stored. Version 1 fixes
PNG, SHA-256 and the [closed content profile](IMAGE_TRANSFER_CONTENT.md): no codec /
algorithm selector or optional extension fields are allowed.

| Offset | Length | Field / constraint |
|---:|---:|---|
| 0 | 8 | Exact magic `54 4d 4d 49 4f 46 52 00` (`TMMIOFR` + NUL) |
| 8 | 4 | Descriptor version, exactly 1 |
| 12 | 16 | associationId; opaque UUID, not all zero |
| 28 | 16 | deliveryId; opaque UUID, not all zero |
| 44 | 16 | deviceUid; must equal the UUID decoded from M |
| 60 | 4 | Exact PNG byte length, 1–16,777,216 inclusive |
| 64 | 4 | PNG width, 1–1920 inclusive |
| 68 | 4 | PNG height, 1–1920 inclusive |
| 72 | 32 | SHA-256 of the entire exact sanitized PNG payload |
| 104 | 4 | N: embedded metadata byte length, 1–262,144 inclusive |
| 108 | N | Existing metadata-format-1 UTF-8 XML bytes, validated by the unchanged profile |
| 108+N | 4 | IEEE CRC-32 of all preceding descriptor bytes, stored as unsigned 32 bits |

The total length is **112+N**, at most **262,256 bytes**, stricter than the approved
logical 512 KiB descriptor ceiling. There are no bytes after the CRC. Association /
delivery UUID nonzero constraints are candidate format choices for this review;
there is no new UUID-version restriction. deviceUid follows the existing metadata
identity contract, not a new version/nil restriction. CRC is a corruption/framing
check, **not authentication**, content verification or a digital signature.

M must satisfy all existing [publication format](PUBLICATION_FORMAT_PROPOSAL.md)
rules, including strict UTF-8/XML, field allowlist, canonical scalar forms, true-north
bearing and `imageIncluded=false`. The latter means the embedded XML contains no
image; the separate offer does not change that metadata-only statement. No local
revision, stateVersion, imageRef, path, URI, recipient list, callsign, source context
or acknowledgment field is added. Owner/comment text remains ordinary metadata;
never interpret it as a retrieval instruction or source authority.

## Required future reader checks

1. Bound actual descriptor input to the exact version-1 cap plus at most one extra
   byte; require EOF for this single completed component. An untrusted claimed N
   cannot authorize unbounded allocation. Deadlines/stream ownership are explicit.
2. Reject unknown magic/version, short headers, unsupported values and lengths
   outside the table. Compute 112+N without overflow and require exact total length.
   Reject trailing/concatenated descriptors, reserved/extra fields or outer padding.
   Whitespace inside the declared M bytes follows the existing XML profile.
3. Check descriptor CRC, then validate embedded M with the existing strict metadata
   codec and require header deviceUid == M.uid. No fallback to parsing a corrupt
   descriptor as standalone metadata and no salvage/partial success.
4. The descriptor only **claims** content identity. Verify actual complete PNG bytes
   separately using the approved exact length/SHA-256/dimensions/closed profile.
   Shape-valid descriptors with wrong digests do not earn verified content status.
5. Require the adapter's independently validated source namespace and exact retained
   metadata receipt before association. No descriptor can authenticate its own feed,
   author or channel; endpoint hints and equal display IDs remain insufficient.
   Durable byte readback and recoverable association records are still required.

The independent test oracle remains separate from the [production codec](IMAGE_OFFER_CODEC.md): it inspects
bounded fixture arrays, reuses only the existing M codec and does not touch stores,
network, source authority or Android decoders. Do not expose/copy it into ingress
before this format and the actual input boundary are reviewed.

## Identity, retries and changed offers

Within an independently validated source namespace, associationId identifies the
immutable binding **deviceUid + M + PNG digest/length/dimensions**. Compare M using
existing domain equality (all compared fields, nanoseconds, optionals and numeric
zero semantics), not XML bytes, its CRC or a hash of its syntax. Supported whitespace /
attribute order can differ; this does not loosen canonical scalar or UTF-8 rules.

- Same associationId and same binding is the same logical offer in that namespace.
  Keep provenance; equal content in another namespace grants no access or shared choice.
- Same associationId with different binding is an identity conflict. A future ledger
  must reject the conflicting identity without overwriting retained evidence; a
  standalone descriptor parser cannot detect historical reuse by itself.
- A reviewed replacement uses a new associationId. Preserve alternatives; neither
  UUID order, timestamp, deliveryId nor arrival order picks the active local image.
- deliveryId identifies one frozen reviewed destination/send. Retry that request
  with the same identity and frozen bytes; a new destination/send needs new review
  and delivery identity. The approved route/ledger must bind the real destination
  outside this payload. A new delivery can carry the same unchanged association.

None of these fields proves delivery, receipt, storage, local adoption or current
image presence. No positive receipt, remote deletion or automatic replacement is
encoded. Feedback remains off by default under the separately approved policy.

## Fixtures and compatibility

[The independent fixtures](../test-fixtures/synthetic/image-offer-proposal/README.md)
embed the existing synthetic PIR publication (UID ending `8014-000000000003`), not
the distinct U/M1 walkthrough from PR #118. H1/H2 retain that proposal's exact PNG
hashes. Python struct/UUID/hashlib/zlib generated the frames; a Java **test-only**
inspector pins their complete bytes and checks field/metadata/content measurements.
No production envelope encoder generated either golden.

The fixtures include replacement alternatives with different association/delivery
IDs, the same M and different PNGs. Test-only mutations exercise identity collisions,
equal M with different supported XML whitespace, mismatched subject UUID, bad CRC /
length/version/dimensions, missing/trailing bytes, malformed metadata and the exact
maximum descriptor size. A descriptor with a false digest remains shape-valid but
fails the independent actual-PNG measurement check; parsing is not verification.

No persisted image registry exists to migrate. The [core codec](IMAGE_OFFER_CODEC.md)
now accepts this descriptor version in memory, with no app/transport activation. Unknown future versions must fail closed without rewriting
metadata/inbox/local-v2 or resetting history; a later encoding change needs independent
compatibility/migration review before activation. Existing formats remain untouched.

## Owner review and remaining gates

The owner explicitly approved **this exact descriptor version** in the conversation
on 2026-10-30. This is an explicit decision, not inferred from merging the proposal.
A production codec can now implement these bytes in its own scoped implementation
PR with the independent fixtures and strict malformed-input tests. That
[implementation is now recorded separately](IMAGE_OFFER_CODEC.md); the original
approval-record PR did not introduce it.

Exact container, receipt and private registry/recovery formats remain **unapproved**;
this proposal does not approve unseen bytes for those writers. Trusted Data Sync-feed
source/recipient evidence, frozen inclusion/destination review, quota accounting,
crash recovery, manual adoption and two-client acceptance also remain. A contact
transfer is not a substitute for the owner's feed goal. Current signed APKs, local
images and the submitted v21 source archive are unchanged.

## Verification actually run

`sh scripts/test.sh` passed, including this test-only fixture inspector and the
unchanged core/storage/publication fixtures. Relative document links and patch
whitespace passed. The proposal also checks its independently generated fixture
hashes and the full maximum descriptor frame. No SDK/image suite/build, emulator,
Docker, phone/server, source signing, production codec or transport was run.
