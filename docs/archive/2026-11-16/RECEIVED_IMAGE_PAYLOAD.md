# Coherent descriptor and PNG receive candidate (#138 / #26)

`ReceivedImagePayload.read(descriptorStream, pngStream)` joins the approved
[descriptor codec](IMAGE_OFFER_CODEC.md) with the bounded actual
[PNG verifier](IMAGE_TRANSFER_CONTENT.md). It returns one immutable in-memory
candidate only after both complete components pass. It adds no transport or caller
in the plugin UI and opens no file, inbox, draft store or host map object.

## Actual guarantee

The descriptor's exact metadata UUID/association/delivery and content claims are
retained. Its SHA-256, length and dimensions are passed to the strict PNG reader;
the resulting exact bytes must match those claims and the closed PNG profile.
The returned public `offer` and `image` values are immutable/defensive. No source
image or input array is rewritten, no local handle is generated, and no timestamp
or arrival ordering selects a winner.

- Invalid descriptors fail before any PNG read. Descriptor/content mismatch,
  trailing/truncated content, bad CRC or forbidden PNG metadata returns no candidate.
- Matching SHA-256 alone cannot bypass the PNG structural/decode checks.
- Two offers with the same UUID keep their distinct association/delivery/content
  identities. Identical pixels/bytes or visible labels do not merge different UUIDs.
- Descriptor/provider failures are generic IOExceptions without raw messages/causes;
  safe image-preparation categories are retained. Synthetic allocation failures use
  the existing low-memory category. This is not a guarantee that every device has
  sufficient heap or an actual low-memory stress result.
- Both streams remain caller-owned on success/failure. Each must expose exactly
  one completed component through EOF, with actual upstream container/decompression
  limits. The owning worker sets deadlines/unblocking/cancellation, closes inputs
  and limits concurrency/live candidates. This synchronous API belongs off the UI
  thread and does not implement aggregate staging/history quotas or resume reads
  after failure. It is not a parser for two concatenated streams or a live socket.

## What remains unverified

A coherent pair proves only that actual PNG content agrees with that descriptor's
claims **in memory**. The metadata itself remains a claim. It does not prove who
sent it, which authorized feed it came from, that the pictured scene is authentic,
that an exact metadata variant is retained in the inbox, that bytes/binding survive
readback/restart, or that an operator adopted it. No positive association receipt
or Delivered/Shared state follows. The independent trusted-source, exact-M and
durable-storage gates in [the image contract](IMAGE_SHARING_CONTRACT_PROPOSAL.md)
still precede verified association for review. Current local attachments remain
untouched, and received metadata/image content never applies automatically.

No container, receipt or private registry format is implemented/approved by this
boundary. A supported Data Sync-feed route and two-client recipient/association /
Cancel evidence remain necessary; a direct contact transfer is not a substitute.

## Verification — 2026-10-30

Focused native API 26/35 Robolectric tests plus standalone image lint passed:
**64 cases**, zero failures/errors/skips (18 new pair cases, 46 existing strict PNG /
stream cases). Lint has zero errors and eight existing advisories. Tests use the
same independent descriptor goldens as the core and actual H1/H2 PNG files; they
are test resources only, not production APK assets. Coverage includes immutable
coherent results, same-content/different-UUID separation, wrong digest/length /
dimensions, invalid descriptor with no PNG reads, matching digest over a broken
CRC/metadata-bearing PNG, truncation/trailing bytes, private provider errors,
caller-owned streams and synthetic memory failure.

The app adapter uses only Java/core and existing Android image-library APIs, with
the existing API-26 annotation convention. No ATAK API is introduced. No SDK/app
assembly, full core/image suite, emulator, Docker, phone/server, signing upload,
actual feed transport or durable association was run for this slice. The approved
core codec and its independent tests are separate evidence. The returned signed
v21 artifact and submitted source archive are unchanged.
