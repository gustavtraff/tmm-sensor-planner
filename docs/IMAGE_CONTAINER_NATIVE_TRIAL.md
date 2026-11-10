# Native Data Sync container transport — #195 / #26

Date: 2026-11-10. **The proposed one-file container reaches the selected feed,
actual server storage and recipient B byte-identically.** This is native file
transport evidence, not a product image-sharing action, trusted association,
local adoption or approval of the proposed container format.

## Setup and artifact

The retained isolated standard-client pair uses ATAK-CIV **5.6.0.25 (238484ee)**,
Data Sync **3.7.4 (58746181) [5.6.0]**, Android 15/API 35 arm64 and TAK Server
**5.8-RELEASE-92**. One emulator runs at a time. Dedicated ADB, guest IPv4/IPv6
egress blocking, disabled radios, socket networking without ordinary NAT and
strict host-managed mutual TLS remain in place. No operational server or phone
is contacted. The container restart required PostgreSQL to be started explicitly;
initial TLS reads failed until that lab prerequisite was ready. TLS was not weakened.

The owner-supplied signing-result directory contains an older APK at its root and
a newer nested result. The actual newer artifact is versionCode **29**,
`0.1.0-configured-publication-trial.1`, **640,607 bytes**, SHA-256
`0fccd965cb716da17e1afdc765197591fc411852c07c7f1914fb8fadf018cb93`.
Its release certificate matches the previously accepted service signer; the
signature, discovery, permissions and packaging checker passes. The returned
service log reports BUILD SUCCESSFUL. This is not a reproducible-source attestation.

Both retained clients update in place without uninstall/data clearing. A's five
and B's four private TMM files remain byte-identical after the update and native
operations. The signed TMM tool actually opens on both clients, retaining A's
22 drafts and B's two drafts. This does **not** complete configured-publication
controls 1–8 in #178; no server-trial consent or SDK host write is exercised here.

## Independent synthetic input

Python's standard library generates a deterministic RGB-noise PNG with no camera,
EXIF, visible operational data or external image input. A test-only reference
generator combines it with the unchanged approved synthetic PIR metadata in the
[proposed container](IMAGE_CONTAINER_FORMAT_PROPOSAL.md), using distinct synthetic
association/delivery identities. This is not a production writer.

| Property | Value |
|---|---|
| PNG dimensions | 1920 × 1080 |
| PNG bytes | 6,223,843 |
| Container bytes | 6,224,532 |
| Container SHA-256 | `0f3d3cfa200224db6d014e4251af95dd3efbdd2ea760a75e2061cd0c231ed5ed` |
| Native file name | `TMM-CONTAINER195-20261110.tmmi` |
| Native MIME | `application/octet-stream` |

The existing synthetic image feed starts with two PNG resources and zero map UIDs.
The new hash is absent from it, all five other known accessible feeds and the
server resource table. B has no matching file in its checked ATAK directory.

## Native confirmation and actual server bytes

1. A uses target feed **Add Items → File Select**, selecting only the generated
   container. **Cancel** before OK leaves zero matching resource rows and no
   matching member in any of the six feeds.
2. Repeating the selection and pressing **OK** opens a separate **Confirm Large
   File** dialog naming the file, size and selected synthetic feed. No matching
   resource is stored before its explicit **Publish** confirmation. File-picker
   OK is not the final publication boundary for this case.
3. After **Publish**, the actual stored `resource.data` bytes match the source's
   complete length and SHA-256. An early reread sees uploaded storage before feed
   membership settles; a later independent overview verifies exactly one matching
   resource in the target. The five comparison feeds retain zero matches.
4. The target now has three resources, still zero map UIDs. Native Data Sync treats
   the container as a **File**, not a Photo or TMM map object. Upload storage,
   feed membership and recipient delivery remain distinct observations.

## Recipient controls, actual bytes and restart

A is stopped before B starts; the launcher checks the recorded owned PIDs. One
additional harness-wide process-name scan falsely matched its own shell text;
direct PID checks confirm A stopped and only B running. That harness diagnostic
is not a product failure or evidence of concurrent-client execution.

- B's native **Content Available** prompt names the target. **Cancel** leaves no
  matching completed file. B then explicitly opens that feed's **Files** view.
- The container is **Available**. Selecting it opens a native Download dialog.
  **Cancel** again leaves no matching file. A fresh selection and **OK** downloads
  the actual 6,224,532-byte file into Data Sync-managed storage.
- Actual B file bytes match source and actual server bytes by complete SHA-256.
  The UI remains in Files; no automatic image viewer or placement tool is observed
  in this download flow. This is not a general native-importer guarantee.
- A normal force-stop/cold-start preserves the actual downloaded bytes. B's four
  private TMM files remain byte-identical; the synthetic existing local image /
  draft/inbox state is not changed by this native file receipt.
- A private test-only fixed-frame inspector passes those **actual B bytes** to
  `ReceivedImagePayload` with the real Android PNG decoder (Robolectric API 35).
  It verifies the descriptor, strict PNG, exact digest and 1920 × 1080 dimensions.
  The new [received-owned-copy API](RECEIVED_OWNED_IMAGE.md) then preserves the
  embedded PNG byte-identically through private write/readback/reopen in that
  test. This is host test execution, not adoption by the signed ATAK plugin.

## Remaining product gates

The exact container still needs owner decisions C1–C3 before a production codec.
No source authority is inferred from a filename, native local path, content hash
or observed UI feed name. A supported selected-source adapter must bind actual
resource evidence and exact metadata M to the approved inbox/stage/registry.

Then implement explicit current-state image choice with the separately reviewed
recoverable adoption transaction. Outbound receipt/acknowledgment remains separate
and disabled; no Delivered or Shared claim follows from native Publish.

The current metadata native-handoff freshness exception does not silently extend
to images. An image-specific manual frozen-snapshot pilot needs separate approval
if native File Select cannot enforce the final freshness/Cancel contract. No direct
REST publisher or private Data Sync API is introduced as a workaround.

This bounded loopback result does not prove target TAK Server 5.6 compatibility,
physical two-phone behavior, arbitrary network failures, the 16 MiB maximum,
native local-delete semantics or remote withdrawal for this container. Existing
[PNG lifecycle evidence](DATA_SYNC_IMAGE_FEED_TRIAL.md) remains separate. Keep #26
open. Generated images, raw UI, exact private state and server data stay outside Git.
