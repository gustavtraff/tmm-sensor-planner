# Publication metadata codec v1 — #14

The owner authorized codec implementation after the format review in PR #98 and
its merge on 2026-10-25. `PublicationCodec` implements the unchanged
[XML v1 profile](PUBLICATION_FORMAT_PROPOSAL.md). This is an SDK-independent core
codec, not an Android preparation UI, private artifact writer or publishing path.

## API and ownership

- `fields(PublicationPreparationReview.Prepared)` exposes the same canonical
  fifteen attributes as a read-only map, for standalone XML and SDK detail
  construction. It adds no metadata, image path or local revision.
- `encode(PublicationPreparationReview.Prepared)` returns a new deterministic UTF-8
  byte array. The input can only be constructed by a successful one-use preparation.
  Encoding uses its explicit metadata projection, never the original review/draft,
  image association, image-status enum or local revision. `imageIncluded=false`
  describes the payload, not the sender's or receiver's image ownership.
- `decode(InputStream)` returns immutable `ShareReview.Metadata` only. It reads at
  most **262,145 bytes**, rejecting input beyond the 262,144-byte limit, including
  a growing stream. It leaves the caller's stream open on success and failure.
  There is no file/path/URI overload, cached record, source-context guess, inbox
  write, draft application or acknowledgment. The caller owns opening/closing any
  private file and validating the actual source boundary before using the result.

Encoding malformed saved Unicode or over-limit ID/owner text throws a fixed
`IllegalArgumentException`. Missing/invalid/unreadable input or unavailable parser
configuration throws a fixed `IOException`. Neither error retains the original
cause, path, data, provider name or parser diagnostic. Neither operation logs input
or rewrites source data. A failure returns no partially decoded metadata.

Prepared values may be encoded repeatedly into identical bytes. That is not a new
sharing confirmation or evidence of current saved state. The future owner must
still serialize fresh-record review/preparation with edits/deletes, invalidate its
lifecycle state and govern any later artifact/host/feed side effect. Returning
bytes alone never means a file was saved, a feed selected or a recipient updated.

## Parser and validation

The byte cap and strict UTF-8/no-BOM checks precede parsing. A bounded XML 1.0/UTF-8
declaration check makes the allowed prolog independent of SAX providers' differing
version/encoding reporting. The canonical encoder writes the declaration; a reader
also accepts a missing declaration and valid standalone syntax.

The production parser uses standard SAX with a required lexical handler. A literal
DOCTYPE is rejected before the parser is created; lexical DTD callbacks and both
entity-resolver signatures also reject. Comments, CDATA, processing instructions,
namespace mappings, extra elements and non-whitespace text reject. The handler
retains only one fifteen-attribute root and enforces each value's bound before
Base64/domain conversion. Parser errors are handled without console diagnostics.
Unsupported required parser configuration fails closed; there is no permissive
fallback or external resource resolution.

The required lexical-handler property is present in the inspected
[Android ExpatReader source](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/org/apache/harmony/xml/ExpatReader.java).
The [SAX LexicalHandler contract](https://docs.oracle.com/javase/7/docs/api/org/xml/sax/ext/LexicalHandler.html)
defines DTD notifications before declarations. This inspection and compilation
support the API choice; they do not establish actual Android parser execution.
The independent fixture oracle continues to use DOM and its separate configuration.
Its parser is not reused by production code.

After framing validation, exact schema/UUID/enum/Base64/UTF-8/instant/double rules
and existing domain invariants are checked. Required ID/owner values must already
be canonical. A temporary TmmDraft with revision 1 and no image handle is only a
domain-validation carrier; it is discarded and never exposed as a received draft.
The returned allowlisted metadata has no revision/image field. Snapshot v1, inbox
v1, comparison rules and existing lab codecs/checkers are unchanged.

## Verification — 2026-10-25

`sh scripts/test.sh` passed on Temurin 17.0.20.1+1 / `javac --release 8`, including:

- All four independently Python-encoded fixture hashes remain unchanged. The
  production decoder agrees with the separate literal/DOM oracle; encoding the
  corresponding prepared values matches each fixture byte for byte.
- Every existing malformed fixture case is rejected independently by both parsers;
  extra empty/unused namespace declarations and CDATA are covered as well.
- Actual maximum-size valid input, over-limit/growing streams, short/zero-length
  reads, retained stream ownership, failed I/O/parser-provider setup and fixed
  errors without original causes.
- All four types/five statuses, exact optional values/Unicode/nanoseconds/extreme
  instants/signed zero, deterministic repeated encoding, malformed/oversized saved
  text, concurrent calls and byte-identical existing DraftStore/image association.
- Existing inbox review/store, snapshot-v1 fixture, restart/locking and 100-device
  suites remain passing. No stored schema or migration is changed.

`sh scripts/check-sdk.sh` also passed: SDK MGRS/serialization probes, CivDebug
assembly, Android lint, the existing app unit tests and static APK checks. These
check compilation/packaging, not Android execution of this codec. No emulator,
Docker, image suite, signing submission or CI wait was run. No APK/version bump
or new source ZIP is requested for this unused core integration step.

Next: Android-owned private preparation/artifact lifecycle and actual parser
execution, followed by separately reviewed new-tag CoT/feed integration. The
approved received-device UX, source trust, D application/commit rules and images
remain separate work. Agent 2's core/application contract is untouched here.

**Follow-up:** [the private preparation UI](PRIVATE_PREPARATION_UI.md) now retains a
candidate only in memory and exercises actual API-35 Android Expat parsing through
a standalone test APK. It adds no artifact writer. Standard ATAK dialog/phone and
new-tag feed integration remain separate gates.
