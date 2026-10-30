# Private unassociated image-pair staging — exact layout proposal (#142 / #26)

**Status: proposed, not adopted.** Logical B–F/H and descriptor v1 are approved;
this exact private directory/marker/recovery layout still needs explicit owner
review before a writer. This PR adds synthetic fixtures and a test-only inspector,
not a production store, automatic initialization, association, adoption or cleanup.

## Purpose and deliberately limited guarantee

Retain a bounded descriptor + exact PNG pair privately across ordinary process
restart while it is still **unassociated**. The [coherent-payload boundary](RECEIVED_IMAGE_PAYLOAD.md)
checks claims against actual PNG bytes, but source/feed authority and an exact
retained metadata receipt may still be unavailable. No trusted namespace, choice,
acknowledgment or association history is recorded by this first stage format.

Every candidate gets a fresh receiver-owned local staging UUID. It is not an
association/delivery/device UUID, local imageRef or content hash. Equal images or
IDs are not deduplicated across unknown source contexts. Stage entries never
become active attachments merely because they exist. A later association/receipt
registry requires its own reviewed format and source/retained-M/byte-readback proof.

## Exact private layout v1

Select a trusted app no-backup root separate from local drafts/active images;
proposed relative location is `se.tmm.sensorplanner/received-image-stage-v1`.
The caller owns that root. Never use sender names, paths, URLs, gallery handles or
IDs as filesystem path components. ATAK/plugins share a host UID; this is not
isolation from a malicious host/plugin or arbitrary concurrent filesystem writers.

```text
received-image-stage-v1/
  format                 # exactly 8 ASCII bytes: TMMISTG1 (no newline/BOM)
  stage.lock             # regular empty file, OS lock only; no persistent record bytes
  ready-<local-uuid>/
    offer.bin            # canonical approved descriptor-v1 bytes
    image.png            # byte-identical verified closed-profile PNG
  pending-<local-uuid>/   # incomplete/uncertain operation; never automatically promoted
    offer.bin            # may be absent, empty or partial
    image.png            # may be absent, empty or partial
```

- Local UUID names use canonical lowercase hyphenated UUID spelling, nonzero, with
  no new UUID-version constraint. Root has exactly `format`, `stage.lock` and up to
  16 recognized candidate directories. No other root/file/directory names, nested
  directories, symlinks or nonregular payload/marker/lock files are accepted.
- A ready directory has **exactly both** payload files. The same local UUID cannot
  occur as both pending and ready. Pending permits zero, one or both named files;
  even a fully valid pending pair remains recovery-required, not ready.
- `offer.bin` is exactly `ImageOfferCodec.encode(offer)`, not an opaque copy of
  alternate XML spelling. On read, require canonical descriptor re-encoding equality
  in addition to its strict decoder/CRC/metadata rules. PNG bytes are never rewritten.
  The original wire-envelope spelling/signatures are not retained; any future route
  needing those must review an extended format rather than infer an attestation.
- Files are capped individually at **262256 descriptor bytes / 16777216 PNG bytes**;
  ready files must validate through the actual descriptor/content pair boundary.
  A checksum or filesystem filename alone is insufficient. Pending bytes are not
  decoded or exposed as an image; their sizes still count toward quota.

## Exact quota and concurrency rule

Count **all pending and ready directories together**, at most 16. Sum logical file
lengths for marker/lock and every payload (including partial files), at most
**268435456 bytes (256 MiB)**. Directory/filesystem allocation overhead is outside
this data-byte quota; sparse files count their logical length, not allocated blocks.
Thus 16 maximum-size PNGs plus descriptors do not fit: reject admission, never evict
an old candidate or silently reduce image quality. Actual free space may be lower.

One exclusive OS lock on the empty `stage.lock` covers inspection, quota reservation,
file creation, readback and publication. Reject unavailable/unsupported locking;
never fall back to unlocked writes. A missing/damaged/unsupported root is an error,
not an empty/new store. Any pending directory makes the stage **recovery-required**:
read-only bounded diagnostics may count entries, but normal new writes are blocked.
This strict first policy avoids guessing vanished reservations or silently resuming
work after a crash. No unbounded scan: at most 18 root entries / two files per entry.

## Explicit initialization and publish/recovery protocol

1. Initialize only by an explicit caller action and only when the root is absent.
   Create the root with CREATE_NEW semantics, create/lock the empty lock file, write /
   sync / read back the exact marker. An existing directory, missing/partial marker
   or failed initialization is not silently adopted, retried as empty or overwritten.
   Initialization creates no candidates and clears no drafts/history.
2. Receive/verify both complete components before staging. Freeze the coherent
   candidate and allocate its local operation UUID **before** the first write; keep
   that operation handle through outcome/reconciliation. No sender path enters it.
3. Under lock, re-read/validate the root and quota; require no recovery-required state
   and no existing pending/ready directory with that UUID. Reserve actual canonical
   descriptor + PNG lengths before creating `pending-UUID`. Never overwrite a file.
4. Write the two CREATE_NEW files, sync both, close and independently reopen/check
   actual bytes with the production descriptor/content verifier plus canonical-offer
   equality. Any error leaves the pending directory/copies intact; no success state,
   positive acknowledgment or old-image deletion follows.
5. Publish by one atomic same-filesystem directory rename from pending to ready,
   with no replacement of an existing target. Unsupported atomic move is an error;
   do not copy/delete as a fallback. Reopen/read back ready bytes before reporting
   a known completed **unassociated stage**. File sync/rename do not establish power-
   loss/directory-fsync or old-backup rollback guarantees; initial scope is process
   restart and actual readback, not secure durable-delivery attestation.
6. A crash before rename leaves pending, even if both bytes happen to be complete.
   Never auto-promote or resume it on opening. A crash/error after rename can leave
   an actual ready pair despite an unconfirmed caller outcome. The one-shot operation
   must reconcile **its original local UUID and exact frozen descriptor/PNG** by fresh
   readback before retry/cleanup; do not create duplicate retries based on a timeout.
7. Recovery/deletion remains an explicit reviewed maintenance action. This proposal
   authorizes no cleanup UI, silent pending deletion, eviction, expiry, active-image
   change or remote withdrawal. Keep ambiguous copies. A first storage writer can
   implement inspection/staging while reporting recovery-required; recovery/cleanup
   activation needs a separate concrete operation/review before end-user ingress.

## Failure and history limits

Unknown entries/versions, wrong types/links, partial marker, ready missing/corrupt
files, quota violations or ambiguous duplicate local IDs fail closed without resetting
or skipping records as a successful empty store. Pending cases are retained/countable
but block writes. A missing ready file under a known local ID is a missing-data error.

There is **no persistent receipt/tombstone/index history in this layout**. Enumeration
cannot detect disappearance of an entire ready directory after external deletion,
power loss or restoration of an old backup. An initialized empty directory means
only “no candidates are present now”, never “nothing was received/lost”. After a
future association, its separate registry must retain expected local stage IDs and
report missing content; this layout alone cannot do that. No positive association /
receipt status can be inferred from it. If this tradeoff is unacceptable even for
unassociated temporary staging, review a catalog/journal before implementing a writer.

Existing metadata/inbox/local-v2, active local images, domain versions and signed
artifacts stay unchanged. There is no prior stage-v1 store to migrate; unknown future
markers/layouts must be rejected and any later migration reviewed independently.

## Independent synthetic fixtures and tests

[Fixtures](../test-fixtures/synthetic/image-stage-proposal/README.md) cover explicit
empty initialized root, one/two ready alternatives, partial pending alongside ready,
and a complete-but-unpublished pending pair. They reuse pinned independent descriptor /
PNG goldens without a production writer. `scripts/test-image-stage-proposal.py` is a
stdlib-only **test oracle**, not a store or full Java-metadata/Android decoder. It
checks layout/types/caps, descriptor framing/CRC/UID/canonical XML shape, PNG SHA /
CRC/closed structure and bounded zlib rows. Full metadata scalar/domain acceptance
and actual Android decoding remain mandatory through ReceivedImagePayload.

Six test groups pass locally: unchanged fixtures/expected byte totals; missing /
unsupported/foreign root state; ready corruption/mismatch/extra content; symlinks /
duplicate local identity; exact logical-byte quota including sparse partial files;
and entry count plus explicitly demonstrated whole-record-loss limitation. Root /
file hashes remain unchanged by inspection. Public core CI runs this isolated test.
No production writer/store, source authentication, native host, SDK/image build,
phone/server, signing, association or delivery experiment was performed for this PR.

## Owner decision requested before implementation

Adopt or revise **this exact unassociated stage-v1 layout and recovery limits**:
marker, private names, canonical offer/exact PNG, lock/atomic publish, 16-entry /256 MiB
actual-byte limits, pending blocks writes/no auto recovery, explicit initialization
and the whole-record/history limitation above. Merge is not implicit approval.
Approval would permit a scoped store implementation only, not cleanup UI, source /
association/receipt registry, transport, automatic image adoption or operational use.
