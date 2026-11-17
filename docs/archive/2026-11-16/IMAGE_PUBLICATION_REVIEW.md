# Frozen image publication preparation — #193 / #26

`ImagePublicationReview` joins the existing saved-state selection, destination
review, private owned-image reader, sanitized export and approved descriptor codec.
It has no UI or transport caller. It does not write files, adopt images, register
associations or report publication/delivery.

## Preparation and confirmation

1. Select an active saved device with a local image using `LocalV2.Selection`.
2. Read its actual bounded owned PNG through `LocalImages`. Prepare a separate
   clean export through `ImageTransferPng.prepareExport`; never rewrite the owned
   copy. Freeze its exact bytes, digest, dimensions and descriptor metadata before
   presenting the preview.
3. Review the exact destination ID/label and the exported pixels. Each new review
   creates new association/delivery UUIDs. This component is not an offer-history
   store and does not identify a resend of a prior offer.
4. On explicit confirmation, reload local state and reread the actual owned bytes
   on the caller's serialized edit/delete/image/handoff boundary. The selected
   dataset, UUID, presence, state version, full saved fields, revision, image
   handle, actual original owned bytes and exact destination must still match.
5. Return an immutable in-memory descriptor/PNG pair. Encoding the same confirmed
   value again retains the same identities and bytes; nothing is sent here.

A cancelled, confirmed or failed review is consumed. Restoring the original image
or destination does not revive it. Never silently reread/re-encode a replacement
and substitute that for the preview. Changing another device alone does not
invalidate this selected device. A missing/corrupt/symlink image refuses and never
clears metadata or an existing attachment.

The descriptor contains the approved metadata projection and image identity, never
the local handle, local state version/revision, private path or destination token.
Preview and prepared byte accessors return independent arrays. Closing a pending
review releases its reference to the export; this is not secure memory erasure or
recall of an already returned candidate.

## Caller obligations and remaining gates

Run preparation and confirmation off the UI thread. The future adapter must prove
the destination/source, inclusion consent and session lifetime independently and
own the serialized fresh-check/handoff boundary. This synchronous helper does not
make a future delayed native upload fresh, establish an authenticated feed,
enforce destination routing, limit aggregate concurrent preparations, implement
cancellation of a blocked provider or acknowledge recipient delivery.

The approved [image contract](IMAGE_SHARING_CONTRACT_PROPOSAL.md), exact container
review, supported Data Sync-feed route, source/exact-metadata association, receiver
adoption transaction and two-client acceptance still apply. Metadata-only sharing
continues saying the image is not shared. Current signed v29 does not gain a new
image-sharing action from this inactive backend.

## Verification — 2026-11-10

The focused native Robolectric suite runs on Android API 26 and 35. It exercises
actual owned-file reads, the real encoder/PNG validator, descriptor decoding and
`ReceivedImagePayload` verification, rather than a mocked export. Controls cover
preview/confirmed-byte agreement and defensive ownership; cancellation/one-shot
confirmation; destination change; missing/different dataset; deletion/recreation;
state version, metadata, revision and handle changes; same-handle changed bytes;
missing/corrupt/symlink images; unrelated-device changes; and distinct review IDs.

This is not emulator/phone, native Data Sync transport or delivery evidence.
