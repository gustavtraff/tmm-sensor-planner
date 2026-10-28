# Reviewed image sharing and verified association — proposal (#26)

**Status: proposed for explicit owner review, 2026-10-28. No production
implementation, new product wire format, adapter or transport is introduced.**
Baseline: main `b37a1ef` (#117), including the approved #14 metadata profile and
#15 import/local-state contracts. This work does not use the lab or change signing;
agent 1 retains emulator and signed-v20 verification. F09/F10/F11/F12, N02/N03/N06.

## Adopted rules reused without reopening their approval

- Device UUID is identity; visible label, editable owner, timestamps, filename and
  native package UID cannot substitute for it. Different incoming metadata remains
  a retained variant; neither arrival order nor updatedAt chooses a winner.
- Sharing is an explicit reviewed action with a real destination. Metadata receipt,
  server acceptance, local preparation, native map display and sender SDK booleans
  are distinct from actual recipient evidence. No automatic private-draft sharing.
- [Metadata format 1](PUBLICATION_FORMAT_PROPOSAL.md) remains metadata-only with
  `imageIncluded=false`. That means these XML bytes include no image; it does not
  say there is no image or authorize deletion. The existing product must continue
  saying **Image will not be shared** until a separately reviewed image flow exists.
- Metadata import/Add/use-selected/keep-local follows [D](IMPORT_APPLY_CONTRACT_PROPOSAL.md):
  preserve the current local image; Add starts without one; explicit re-add after
  known deletion; full local store/state-version/revision/image binding on writes.
  Historical metadata intentionally does not pin image files.
- [Local image policy](IMAGE_QUALITY.md): source JPEG/PNG <=10 MiB and <=20 million
  pixels; new clean PNG copies <=16 MiB, longest edge <=1,920, no upscaling or forced
  crop; EXIF/embedded metadata removed, original file unchanged. Existing legacy
  copies remain readable without background rewriting. One active local image/device.
- Local cleanup, feed/server removal, recipient deletion and secure erasure are
  different. [Mission cleanup](LOCAL_MISSION_CLEANUP_PROPOSAL.md) remains an explicitly
  confirmed local action; no automatic reset or remote withdrawal follows from it.

The recommendations below are **new #26 choices**, not already implemented/adopted
rules. Approval checkpoints are collected near the end rather than inferred from
this PR's merge or from earlier metadata/image-policy approval.

## Three independent receipt facts, then a separate local choice

| Evidence | Meaning | Does not imply |
|---|---|---|
| Metadata received | Supported metadata validated and retained with a verified source namespace under #15 | Image sent, downloaded, verified or attached |
| Image bytes received | Complete payload arrived in bounded private staging; validation may still fail | Correct hash, safe PNG, correct device/source or durable association |
| Image bytes verified | Actual complete bytes pass length/hash/PNG/size checks and private stored-byte readback | Correct UUID/metadata linkage or local adoption |
| Association verified for review | Verified bytes and an immutable offer are durably bound to the exact device UUID, retained metadata variant and validated source context | Scene authenticity, operator acceptance, current private image changed, or delivery to everyone |
| Attached locally by operator | A separate explicit, current-state-bound local image choice committed successfully | Remote publication/withdrawal or acknowledgment to the sender |

Do not collapse these into a single Shared/Received/Complete flag. A valid hash
proves equality to expected bytes, not that a picture depicts the correct real
sensor, lacks sensitive visible content, or came from the owner text in metadata.

## Proposed identity and binding

Recommend a separate immutable **image association offer** alongside existing
metadata, not an imageRef/URI/thumbnail added to metadata format 1. The logical
values below do not specify XML/JSON/ZIP field names, bytes or an SDK API.

| Value | Proposed contract |
|---|---|
| associationId | New opaque UUID for one immutable image-to-subject/metadata offer. Scoped to the validated source namespace. Never a causal version or active-local-image pointer. |
| deviceUid | Exact TMM device UUID, matching the bound metadata; never generate a replacement UUID on receipt or merge by label. |
| subject metadata M | Full existing compared metadata projection for the exact reviewed variant, with no imageRef/local revision. Use domain equality, including nanoseconds/optionals/numeric zero, not a hash of XML syntax. |
| Content identity | Algorithm SHA-256 plus 32-byte digest of the **entire exact sanitized PNG payload**, expected byte length, media type image/png, actual width/height. Not a hash of a path, thumbnail, ZIP or decoded pixels. |
| deliveryId | New opaque UUID per explicitly reviewed destination send. Retries of that same frozen request reuse it; another recipient/namespace/content/offer requires new review and delivery identity. |
| Receiver provenance | The receiver's approved adapter maps authenticated route evidence to an opaque context. Never accept a payload's context/feed/callsign as that proof. |

Deduplicate content bytes only within an authorized private namespace/cache boundary;
equal hashes do not grant access, join feeds, identify a device or replace association
records. One image can be explicitly offered for two UUIDs, but each binding remains
separate. Resending an unchanged offer retains associationId/content identity; the
same logical delivery retry must not create a second receipt/application. A changed
binding under the same associationId/deliveryId is rejected as conflicting identity,
not silently overwritten. A replacement uses a **new associationId**, even for the
same UUID. Keep both offers/provenance; no distributed newest-image counter is assumed.

Require a retained exact metadata receipt, matched by context + UUID + M, before
claiming verified association. If an offer carries M before the standalone metadata
arrives, only an already approved ingress path may stage that exact metadata using
#15 rules. Otherwise hold it unassociated. Do not bind to whichever current MapItem
or local draft shares the UUID, to the last arrival, or to a feed guessed from a
server address. Identical M in another feed is still another provenance boundary.
Package entry names/native attachedUID may be route consistency checks after runtime
verification, never the sole binding or a recipient-readable local handle.

The receiver must own all relative extraction names and storage handles. Never
send or follow sender imageRef, absolute/relative local filesystem paths, file:/
content: URIs, gallery identifiers or arbitrary remote retrieval URLs. A fixed
package-relative member role may locate bytes inside an approved bounded container;
it is not a local path or subject identity. A server resource token, if eventually
used, needs a separately verified selected-server/feed authority and access policy.
No fetch based merely on a digest or untrusted descriptor-supplied URL is allowed.

## Proposed byte and preparation profile

Recommend one image/offer/transfer in the first slice:

- Freeze the actual export bytes **after** sanitation, then hash/measure those exact
  immutable bytes. Preview that copy, dimensions, byte count, UUID and destination;
  ask separately to include it. Validate the actual source image bytes, not merely
  that an opaque handle exists. Missing/corrupt local image blocks image inclusion;
  allow an explicitly chosen metadata-only action without silently changing intent.
- Preserve accepted quality policy: a clean static PNG <=16,777,216 bytes and a
  <=1,920 longest edge, no upscaling. Propose a closed transfer profile: 8-bit RGB
  or RGBA, noninterlaced, IHDR then consecutive IDAT chunks then IEND, valid CRCs,
  one complete bounded zlib stream and valid decoded image. No EXIF/text/ICC/GPS,
  unknown/ancillary/APNG chunks, extra images or trailing bytes. Dimensions must
  match the descriptor and bound decompression before decode/allocation.
- This profile is stricter than existing owned reads: `ImageFormat.checkOwnedPng`
  allows some ancillary chunks and is **not** an untrusted-input sanitizer or proof
  that every Android-produced/legacy PNG meets the proposed profile. Verify actual
  encoder output. If needed, prepare a separate clean export copy, disclose any
  resizing and obtain fresh review/hash; never rewrite the active local image.
- On receipt, verify exact length/SHA-256 on bounded actual bytes, strict structure,
  dimensions/decoded content, then durable private byte readback before associating.
  Never silently re-encode received bytes and keep the old content ID. A later
  derivative needs its own identity and explicit mapping; first slice should adopt
  a byte-identical validated copy with a new local-only owned handle.
- Existing `LocalImages.prepare` accepts <=10 MiB original input, while owned PNGs
  may reach 16 MiB. A safe received/export-owned-PNG path is therefore a **new
  reviewed interface**, not permission to loosen original-source limits or pipe
  16 MiB through the current preparer. Preserve current local APIs/old images.

Proposed allocation bounds: one PNG <=16 MiB; association descriptor <=512 KiB
including one existing metadata document <=262,144 bytes; any native manifest
<=64 KiB. For a ZIP candidate, at most descriptor, PNG and required native manifest,
<=18 MiB archive bytes and bounded total expanded members by their individual caps.
Reject duplicate/case-colliding names, extra members, nested archives, traversal,
absolute paths, symlinks, encryption and external references before extraction.
No compression-ratio assumption may replace actual expanded-byte accounting.
These are proposed receiving/container budgets, not changes to current metadata
bounds or claims that a particular native package already satisfies them.

At actual send confirmation revalidate the local store identity, persistent target
state/version, full revision/image association, frozen bytes and real destination
on the owning serialized boundary. Changed image, same-UUID recreation, source/
destination change, unavailable data or closed session requires fresh review.
Cancel before that confirmation must create no shared host object, upload or
recipient effect. After dispatch/native handoff, cancellation cannot promise recall
or no remote copy: report what is known and reconcile ambiguous work before retry.
The #14 native-Done freshness limitation is **not** automatically waived for images.
A route unable to enforce these guarantees needs a separately adopted manual
snapshot-pilot boundary or must remain blocked.

## Receive, replacement, conflict and local adoption

1. Stage offers/partial bytes in private, bounded no-backup storage, isolated from
   the active draft/image directory. Do not auto-import raw PNG into a host map,
   activate a placement tool or create ordinary map content on arrival.
2. Metadata, offer and bytes may arrive independently/in either order. Keep their
   states separate; hash-valid bytes without trusted source/metadata binding remain
   unassociated. A missing transfer, rejected file or failed association cannot
   clear any current local attachment. A receiver may see metadata with “image not
   received/unverified” rather than a fabricated successful image state.
3. Recommend no proactive background image fetch in TMM. A verified offer can be
   listed without per-device acceptance of metadata; explicit download/adoption
   actions remain separate. A native push route may already deliver bytes before
   the plugin can ask: that limitation must be tested/disclosed, not described as
   successful receiver-side pre-download cancellation.
4. A different hash/offer for the same UUID is a retained candidate, not an automatic
   replacement. Preserve local image and pending incoming alternatives; display
   source, exact M binding, size/preview and verification status. Equal bytes in a
   new source/association still retain provenance. No arrival/time-based winner.
5. “Keep local” leaves current pixels/handle intact and records the choice against
   that exact offer and full local state. “Attach/replace with received image” is
   another explicit action: require a matching active UUID/creation identity and
   validated selected offer; bind to current store/version/revision/image and the
   verified content. Metadata Add stays image-free under D; do it first if required,
   then review image adoption against the fresh state. If current metadata differs
   from the offer's M, show that conflict explicitly; accepting pixels neither
   accepts/rebinds the offered metadata nor makes an old photo current by timestamp.
   No automatic resurrection.
6. Prepare/sync the new private byte-identical owned copy before a local association
   commit; preserve old and candidate copies if save/readback is ambiguous. A real
   image change advances local stateVersion/revision with a previewed valid local
   updatedAt; preserve UUID/createdAt and unrelated metadata. Revalidate atomically;
   any metadata/image/delete/re-add change since review rejects the old choice.
7. Declare local replacement only after a known successful commit/readback. Cleanup
   old owned bytes only after a fresh same-dataset unreferenced check; cleanup
   failure is not rollback. A later image-only change invalidates older coverage,
   even if metadata still compares equal. Missing active bytes remain an explicit
   image error; keep metadata/handle and show the loss, never silently detach it.

The current inbox/v2 decision formats do **not** encode image offers, content hashes,
received blobs, image-choice history or acknowledgments. Do not squeeze them into
comment, owner, imageRef, metadata revision or an existing metadata-only decision.
A future private image registry/transaction contract must define those records,
references, recovery and ownership, with exact fixtures before its writer. The
new offer/choice and active association must have recoverable commit semantics;
two unrelated successful saves are insufficient. This PR selects no file layout.
Existing metadata import must continue preserving local images throughout.

## Acknowledgment: precise, scoped and optional outbound feedback

Recommend one positive **association stored for review** receipt, only after exact
bytes, PNG safety, metadata/source association and their durable record pass
readback/restart requirements. It is not an acceptance-of-image or attached-locally
acknowledgment. A server store/API boolean, 100% progress, stock notification or
metadata receipt cannot substitute for it.

The logical receipt binds deliveryId + associationId + deviceUid + expected digest/
length + this specific stage. The immutable offer supplies M; return-route evidence
must identify the intended authorized receiving peer/channel and match the send
record. Do not send receiver store IDs, image handles/paths, private revisions or
unrelated local draft state. Unauthenticated/wrong-recipient/hash/association receipts
cannot advance sender status. Checksums do not authenticate a sender or receiver.

Outbound receipts themselves disclose UUID/content/delivery information. Propose
**explicit session-scoped consent** for feedback to a verified sender/channel,
default off, no feed-wide broadcast or per-device popup merely to view metadata.
If disabled or the return route/source cannot be authenticated, keep local verified
state but send no positive receipt. No “attached locally” feedback by default; it
would disclose a private operator decision and needs separate approval.

Duplicate receipt/delivery retries are idempotent and bound to the same immutable
request. Lost receipt/timeout means **no verified acknowledgment**, not proof of
failed delivery; the receiver may already have stored the association. A route may
resend the same verified receipt after authorized retry/readback, never silently
reapply an image. After restart discard unconfirmed send choices; restore only
historical outbox/receipt evidence, not standing consent to retransmit payloads.

An acknowledgment is historical evidence for that recipient/offer, not an assertion
that bytes still exist now. If sender changed H1→H2, a late H1 receipt confirms only
H1. If receiver later detaches/cleans files, that does not retract the old receipt
or imply server withdrawal. Dynamic feed membership is not a known all-recipient
roster: show individually verified receipts, never “everyone received the image”.

## Retention, capacity and failures

Propose separate #26 quotas initially: <=256 MiB total private transfer/staging
storage (including partial/quarantined/verified copies and required duplicate temp
space), <=16 pending image offers and <=256 retained association/delivery/receipt
records. Existing active local images remain under their existing ownership policy,
not retroactively evicted by these quotas. Account shared blob references explicitly;
never discard a pending conflict to make a transfer appear successful.

Warn near capacity; reject new transfer/adoption explicitly if it cannot safely fit,
preserving existing state. An oversize/unsupported/corrupt image is rejected, not
automatically resized on receipt. Cancel can remove only an uncommitted owned stage
with no live/reference ambiguity. No silent expiry or age-based loss of deduplication/
conflict evidence in this first slice; bounded state can stop new work until an
explicit reviewed cleanup/retention decision. No unlimited historical image pinning
is claimed for the existing metadata ledger.

Verified association state requires durable bytes **and** recoverable binding
records. On crash between them, retain bounded orphan/quarantined data and reconcile
by the original IDs; do not infer association/acknowledgment from file existence.
On ambiguous local adoption reload actual active state before retry or cleanup.
Missing/corrupt/unsupported registries fail closed, never reset to an empty success.
Process-restart readback only is the initial durability boundary; directory fsync,
power loss and old-backup rollback are not solved by SHA-256 or atomic rename.

Explicit mission cleanup may delete private received stages/history and owned
images in its confirmed scope, but source/server/recipient/host/gallery/backup
copies remain outside it. It forgets replay history; later reception may display
those offers again but cannot auto-attach. A local detach sends no remote removal.
Recommend no remote image withdrawal/delete protocol in the first slice; if needed,
review separately with source authority, tombstones/history and server/peer evidence.

## Concrete synthetic walkthroughs

All identities below are disposable examples, not live feed/account/device data.
`CONTEXT-A/B` are hypothetical **receiver-validated** namespace mappings, not fields
an incoming descriptor can self-assert. A sender/receiver name is illustrative only.

- U = `00000000-0000-4000-8026-000000000001`, V =
  `00000000-0000-4000-8026-000000000002`; both can display `PIR IMAGE26`.
- M1: U/PIR/PLANNED, owner `SYNTHETIC ROLE`, createdAt `1970-01-01T00:00:00Z`,
  updatedAt `1970-01-01T00:00:10Z`, no deployedAt, comment `Synthetic image offer`,
  latitude 0, longitude 1, bearing 45 degrees true north. Existing XML remains
  imageIncluded=false. M2 changes comment to `Synthetic moved variant`, longitude
  to 1.25 and updatedAt to `1970-01-01T00:00:20Z`; it is not automatically newer.
- A1/A2 associations = `00000000-0000-4000-8026-000000000101` / suffix 102;
  T1/T2 deliveries = `00000000-0000-4000-8026-000000000201` / suffix 202.
- H1: existing 64×64 red/cyan RGB checker, **210 bytes**,
  SHA-256 `d6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5`.
- H2: synthetic 64×64 green/magenta RGB checker, **220 bytes**,
  SHA-256 `8cc05bbabd5fa56beaf58ed6d0d2dbf4c6e497689d295ec8644eda5c4ebb645a`.

**1. Separate metadata, image and local choice.** B already has a private image L.
M1 arrives in CONTEXT-A and is retained by #15. That receipt does not include PNG
bytes and importing metadata preserves L. A1 binds U/M1/H1; without H1 B shows an
unreceived image offer, not verified association. H1 then arrives, checks pass and
private bytes + binding survive readback: B can show association verified for
review, while L remains the active local image. With explicit feedback consent
and a verified return route, a T1/A1/U/H1/210 stored-for-review receipt can confirm
that stage on A. It says nothing about B adopting H1.

If B chooses H1 for the private draft at stateVersion 12 but another local image
change advances to 13 before confirmation, reject the choice; preserve both files
and the offer. Fresh review may later attach a new locally owned copy at version
14, incrementing the local revision once. Its handle stays private. The original
A1/M1 association is retained even if B's metadata is a manually edited variant.

**2. Replacement and late evidence.** A offers H2 for the same U/M1 as new A2/T2.
B keeps A1 and A2, regardless of which payload arrives first, and chooses explicitly
if either should replace L. A late T1/A1/H1 acknowledgment only confirms the earlier
H1 offer for that peer. It must not turn H2's unconfirmed status into Delivered.
An A1 record claiming H2 instead of H1 is an identity conflict, not an update.

**3. Wrong subject or source.** A1/U/M1/H1 cannot attach to V merely because both
show `PIR IMAGE26`. M2 for U is a distinct retained variant; do not bind A1 to M2
just because the latest MapItem displays M2. The same M1/H1 received in CONTEXT-B
needs independent provenance/permission; it cannot satisfy CONTEXT-A's association
or acknowledgment. A raw resource URL or package attachedUID is insufficient proof.

**4. Bytes without proof.** H1 without an offer/trusted M/context can be retained
as bounded unassociated content, but earns no positive association receipt. H2
advertised as H1 fails the expected hash even if it decodes as a valid PNG. If B
later loses H1 after a valid receipt, show the current missing-image condition
alongside the historical receipt; do not clear metadata or claim a fresh copy exists.

**5. Metadata-only or deletion.** A detaches its image locally or sends M1 alone
with imageIncluded=false. B keeps L and both received candidates; there is no
remote deletion instruction. Removing a feed item or performing local mission
cleanup does not revoke files already at A, B, the host or server. Cleared local
history cannot permanently suppress re-offers, but reception never auto-attaches.

## Evidence, transport gaps and safe next steps

| Existing evidence | What is actually supported | Still missing for this proposal |
|---|---|---|
| [Standalone attachment trial](DATA_SYNC_AND_ATTACHMENTS.md), 2026-10-16, SDK hosts 5.6.0.24 / lab server 5.8-RELEASE-92 | One 210-byte PNG/ZIP arrived byte-identically via native contact Data Packages; recipient local removal left sender/server copies | TMM UUID/M binding, standard/phone candidate, final consent freshness, server-side absence on Cancel, persistent app acknowledgment, conflict/restart/image adoption |
| [Metadata publication contracts](PUBLICATION_FORMAT_PROPOSAL.md) and [reviewed path](PUBLICATION_PATH.md) | Metadata-only encoding and bounded host-copy/native-handoff evidence, with stated confirmation limits | Image package/source/return-route API, image destination confirmation, image inclusion/receipt protocol; no inference from metadata success |
| [Ingress source investigation](INGRESS_SOURCE_VERIFICATION.md) | Exact SDK surfaces/signatures and why generic connection hints do not establish selected-feed provenance | Verified selected-feed original-variant source, attachment binding and authenticated receipt return path. No guessed endpoint-as-feed, private broadcast or direct REST fallback |
| [Local image lifecycle](LOCAL_IMAGES.md), [quality](IMAGE_QUALITY.md), [versioned storage](VERSIONED_PLANNER_STORAGE.md) and [import rules](IMPORT_DECISION_PREPARATION.md) | Local image normalization/ownership and persistent local-state guards, metadata-only import preservation | New untrusted-image validation/adoption path, blob/offer/receipt storage and recovery, historical-byte retention, two-client image acceptance |

An individual-contact package is an **experiment candidate**, not a replacement
for a requested selected-feed workflow. Owner must choose the intended initial
scope; a direct contact offer does not inherit selected-feed membership or trust.
No supported callable Data Sync attachment/acknowledgment contract is established.
No named SDK method or native callback is newly verified here. The historical
PNG placement tool is an explicit stop condition until receiver import behavior
is independently proven safe. Keep current product image exclusion unchanged.

Recommended sequence after owner adoption: (1) exact logical/wire and private
recovery-storage specifications with independent synthetic fixtures, (2) bounded
SDK-independent validation/deduplication/state tests, (3) a separately authorized
non-operational SDK/route experiment, (4) actual signed standard-client and physical
acceptance. New container/receipt/registry writers require prior exact-format and
migration/ownership review. This PR does none of those implementation steps and
does not take agent 1's emulator/v20 work or request a signing submission.

## New decisions requiring owner approval before implementation

| Decision | Recommended first slice / consequence |
|---|---|
| A. Association identity | Immutable source-scoped associationId + device UUID + exact M + content digest/length, with destination-bound deliveryId. No causal/newest-image counter and no binding by label. |
| B. Bytes and quality | Exact-byte SHA-256, stricter clean RGB/RGBA PNG profile and accepted 1920/16 MiB quality bounds. Separate export/adoption APIs preserve 10 MiB original input rules; no automatic receive-side transform or active-image rewrite. |
| C. Receive and local adoption | Bounded unassociated/verified-for-review stages, no proactive TMM background fetch, no auto-attach or bundled metadata Add change. Explicit full-state-bound image adoption; keep pending alternatives and warn on M conflicts. |
| D. Receipt privacy and meaning | Positive association-stored-for-review evidence only; authenticated intended-peer binding and explicit session feedback consent, default off. No attached-locally disclosure by default, no “all feed users received” claim. |
| E. Replacement/removal | New offer for replacement, manual choice rather than last-write-wins; no implicit remote deletion/withdrawal in first slice. Metadata-only/null/missing images never clear current attachments. |
| F. Capacity/retention | Proposed 256 MiB staging budget, 16 pending offers, 256 small history/delivery/receipt records; no silent eviction/expiry. Cleanup must disclose loss of replay/history and exactly which private bytes are removed. |
| G. Initial route and evidence | Choose contact experiment versus selected-feed requirement explicitly. Neither path is production-authorized; final destination/freshness, source association, safe import and return receipts still need exact SDK/two-client proof. |
| H. New protocol/storage gates | Keep current metadata/inbox/v2 formats unchanged now. Review exact offer/container/receipt and private registry/recovery bytes/fixtures before their writers; do not disguise these as existing metadata decisions or imageRef values. |

These are the new choices presented for approval. UUID identity, metadata-only image
preservation, explicit confirmation, existing local quality policy and local-versus-
remote deletion boundaries above are already adopted and need no renewed approval.
A future PR must state which of A–H were adopted rather than treating this proposal
or any successful signing/SDK compilation as operational sharing authorization.

## Acceptance matrix — required future evidence, not tests run here

O = offline bounded codec/domain tests; S = private-storage/process/fault tests;
L = two isolated signed standard clients plus independent recipient/server/traffic
observations and positive controls before/after; P = physical-phone/provider checks.
Every row is future acceptance for #26, **not a newly passing result**.

| ID | Scenario | Required observable result | Evidence |
|---|---|---|---|
| I01 | Metadata-only receive/import, no image payload or imageIncluded=false | Local handle/pixels unchanged; metadata receipt does not imply any image bytes/association | O/S/L |
| I02 | Exact U/M1/A1/H1 and trusted namespace | Separate metadata/bytes/verified-content/verified-association states; stored-byte readback before positive receipt; active L unchanged | O/S/L |
| I03 | Same label/different UUID, same UUID/different context, M1 versus M2 | No wrong-device/source/variant association or inherited choice; every candidate remains visible | O/S/L |
| I04 | Wrong hash/length/type/dimensions, malformed/trailing/APNG/metadata chunks, decompression bomb | Bounded rejection before unsafe allocation/association; preserve current image; no positive receipt | O/S |
| I05 | 10 MiB original, 16 MiB clean transfer, 1920 boundary and legacy/ancillary PNG | Distinguish source/owned/wire caps; safe dedicated path; explicit export-copy preview if needed; original remains unchanged | O/S/P |
| I06 | Duplicate delivery/restart, same ID with changed binding | One logical offer/receipt, no duplicate local mutation; conflicting ID rejected without overwriting evidence | O/S/L |
| I07 | Metadata/descriptor/bytes in each arrival order, missing M or trusted context | Pending unassociated state rather than guessed linkage; no premature association receipt or auto-placement | O/S/L |
| I08 | H1/H2 concurrent replacement, equal pixels with different encoded bytes | Separate immutable offers/identities; no arrival/time winner; keep L until explicit choice | O/S/L |
| I09 | Missing/corrupt sender image; missing recipient bytes after prior receipt | No silent fallback to included/empty image; explicit metadata-only choice; historical receipt separate from current missing data | O/S/L/P |
| I10 | Local metadata/image edit or delete/re-add during review; destination changes | Old confirmation rejected, including reused local revision; no implicit rebase or stale replacement | O/S/L |
| I11 | Cancel before final send versus after dispatch/native queue | Before: zero shared copies/uploads/recipient effects with functioning controls; after: honest possibly-sent/unknown status, no recall promise | L |
| I12 | Crash during partial download, hash verification, byte sync, binding record or ack write | No verified association/positive receipt before recoverable bytes+record; bounded orphan handling/readback, no implicit empty reset | S/L |
| I13 | Failure before/after local image association commit or cleanup | Preserve old/new copies when ambiguous, reconcile active state; no false rollback or early old-image deletion | S/L |
| I14 | Receipt lost/duplicated/out of order, wrong peer/association/hash, feedback denied | Only intended authenticated bound receipt advances that one delivery; timeout remains unconfirmed; no private paths/local choices disclosed | O/S/L |
| I15 | Sender changes H1→H2 after sending; receiver later detaches/cleans H1 | Late H1 acknowledgment is historical H1 evidence only, not current H2/receiver-presence proof | O/S/L |
| I16 | Multiple recipients/feeds, offline retry, restart/Unload and permission change | Track exact authorized recipients; never infer all-subscriber delivery; no standing consent or background payload resend after restart | O/S/L |
| I17 | Staging/history capacity reached | Clear warning/rejection, old choices/blobs intact, no success/ack without space; interrupted work accounted within quota | O/S |
| I18 | Local detach, native/server removal, multi-step local mission cleanup | Explicit scope/copy accounting, no automatic remote delete or secure-erasure claim; re-offers never auto-attach | S/L/P |
| I19 | Stock PNG/package receiver import, native background/auto-publication settings | No map-placement tool, unexpected map object, publication into unselected feed or original-byte metadata exposure; stop/reject unsafe route | L |
| I20 | Upgrade/backward/unknown format and old image/metadata contracts | Existing metadata/inbox/v2 bytes and local image behavior preserved; unknown new image registry fails closed; independent migration/failure fixtures before activation | O/S/P |

## Verification actually performed for this proposal

Read the repository contracts/code referenced above and #26's issue/recorded scope.
Computed H1/H2 in memory using only the existing synthetic generator's chunk/image
helpers plus standard-library struct/zlib/hashlib. Confirmed lengths and decoded
RGB row counts; H1 matches the published standalone-fixture hash. No user image,
store, SDK, account or server was read by that example computation. Hashes describe
those exact PNGs, not a promise every compressor produces identical bytes.

The H2 example is reproducible without file writes by replacing the generator's
red/cyan pixels with green/magenta while retaining its format/compression:

```python
import importlib.util, hashlib, struct, zlib, sys
sys.dont_write_bytecode = True
s = importlib.util.spec_from_file_location("synthetic", "scripts/create-attachment-fixture.py")
m = importlib.util.module_from_spec(s); s.loader.exec_module(m)
pixels = b"".join(b"\0" + b"".join(
    b"\0\xff\0" if (x//8 + y//8) % 2 else b"\xff\0\xff"
    for x in range(64)) for y in range(64))
png = (b"\x89PNG\r\n\x1a\n"
       + m.chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
       + m.chunk(b"IDAT", zlib.compress(pixels)) + m.chunk(b"IEND", b""))
print(len(png), hashlib.sha256(png).hexdigest())
```

Only documentation changes. Relative document links and patch whitespace are
checked; no core/UI/image suite, production validator, Android/SDK build, Docker,
emulator, two-client transport, signing change or v20 acceptance was run here.
No new image schema has been put into the product and no operational sharing
permission is implied by this document or by fixture arithmetic.
