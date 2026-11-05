# Native Data Sync image-feed feasibility — #162 / #26

Date: 2026-11-05. **One generated Full HD PNG reaches the explicitly selected
Data Sync feed, actual server storage and recipient B byte-identically.** This
is native workflow evidence, not a TMM image-sharing feature or verified device /
metadata/source association. No product API, wire/container/receipt format or
background image fetch is introduced.

## Inputs and documented route

The retained isolated standard-client lab uses the exact signed v23 candidate
recorded in [its verification](SIGNED_V23_SOURCE_VERIFICATION.md), standard
ATAK-CIV 5.6.0.25, Data Sync 3.7.4 (58746181) [5.6.0], Android 15/API35 arm64
and TAK Server 5.8-RELEASE-92. Only task-owned clients and the synthetic server /
database/host-loopback relay run. Airplane/radios/guest egress blocking and validating
mutual TLS remain as documented there. No operational profile, location, image,
phone, contact transfer, private plugin broadcast or product REST publisher is used.

The bundled `ATAK_Data Sync User Guide.pdf` is labeled version 3.5, 18 July 2024,
despite being shipped in the tested 3.7.4 APK. Its pages 4/6/7 document selected-feed
Add Items → File Select, native OK confirmation and JPG/PNG Photos support. The
[input hashes and manual references](DATA_SYNC_INTEGRATION.md) identify the exact
bundle. The runtime below verifies this bounded workflow on 3.7.4; the manual is
not a third-party developer API or a version-independent compatibility promise.

A creates a dedicated synthetic feed, `tmm-image162-20261105`, through native
**Create New Feed**. Its per-feed **Auto-publish new content** checkbox is actually
unchecked; it remains off. B joins through native **Download & sync** before the
positive publication. The selected destination is the lab's verified loopback
server, not a default or operational account.

## Independent synthetic input

Python's standard library generates a new red/blue RGB PNG, without reading any
phone image, draft or active-image handle. It has exactly IHDR/IDAT/IEND chunks,
valid CRCs, no embedded metadata and no location or device association:

| Property | Exact value |
|---|---|
| Dimensions | 1920 × 1080 |
| Bytes | 9,403 |
| SHA-256 | `4ef47006a33bb1fa89f243ea51a59e5f20af6936946ab7d82dc6595f40975db7` |
| Native selection name | `TMM-IMAGE162-20261105.png` |

It is copied only to A's Download directory. The input is small despite its Full HD
dimensions because the generated solid colors compress well. This does not test
high-entropy 16 MiB payloads, a real camera decoder or throughput/large-file prompts.

## Cancel, publication and actual stored bytes

Before selection, the target has zero UIDs and zero resources. Three other existing
synthetic feeds have zero image/file resources. B has no matching test file.

1. In A's native target feed, choose Add Items → File Select, select only the
   generated PNG, then **Cancel** before OK. Independent authenticated GET shows
   the target still empty. A direct server resource query by exact hash/name /
   filename finds zero rows; B's checked Data Sync/Download directories have no
   matching file. These bounded observations establish no matching stored upload
   or recipient file in this case, not universal absence across every encoding.
2. Repeat the same single-file selection and explicitly press native **OK**.
   A's dashboard shows one Photo, zero Map Items and zero Files. Independent GET
   shows exactly one target resource, zero target UIDs, and no resource in any
   of the three comparison feeds. The resource has the expected name, image/png,
   9,403 bytes and content hash. No private TMM record was selected or converted.
3. Independently read the actual resource row's bytea content from the isolated
   server, rather than trusting its hash column. Its complete bytes exactly equal
   the generated PNG and pass the SHA-256 comparison. Server acceptance alone is
   not recipient delivery.

## Actual recipient and native viewer

B's already-open dashboard initially remains at zero Photos. A native feed-list
**Refresh**, then reopening the target, shows the new Photo. Manual refresh and
explicit content download are the observed route for this first case. A later
larger-image case does produce native **Content Available**, described below;
do not infer automatic complete delivery from that notification.

B's Photos tile displays the generated filename as **New**. Selecting it downloads
the file and opens ATAK's native image viewer. Pull the actual file from Data Sync's
managed directory: it is 9,403 bytes, SHA-256 exactly as above, with actual PNG
dimensions 1920 × 1080. Source, server and recipient bytes all match, with no
recompression or downscaling in this one File Select case.

The viewer displays the red/blue image; date/location are absent placeholders.
No map-placement tool appears in the observed flow. The Mini already visible on
the map predates this photo and does not establish photo-to-device linkage. This
is different from the older [Data Packages PNG import](DATA_SYNC_AND_ATTACHMENTS.md),
which started a placement tool. Do not generalize either result to another route,
geotagged input or an unreviewed container.

## Local removal and larger-file confirmation

B chooses native Photos multi-select → Delete, selects only the generated photo,
then confirms **Remove 1 files?**. The downloaded file disappears. A second,
distinct **Remove Content From Feeds** dialog names the one image/feed. Choose
**No**, leaving the standing **Never Remove / Don't ask again** option untouched.
The gallery row disappears locally; this is not remote withdrawal. Independent
GET still shows one feed resource, actual server bytes stay identical, and A's
source stays byte-identical. A later native Pending Downloads list offers this
retained image again. Do not promise that local Delete automatically reopens it
from the now-empty gallery or securely erases other copies/cached pixels.

A second deterministic RGB noise image uses the same closed metadata-free PNG
profile and 1920 × 1080 dimensions, but actual bytes are **6,223,843**; SHA-256:
`d908e4982680dce8f21004b77648ae3d892e753579ca99ac8dcac752f2d16add`.
Native File Select → OK adds **Confirm Large File**, identifying the image, size
and exact destination. Android Back here leaves no matching server resource.
A fresh selection/OK plus explicit **Publish** uploads it. The actual stored
server bytes match the complete generated input; target membership is now two
resources and zero UIDs. Large-file handling is therefore a separate native
confirmation, not an assumption that the first picker OK always publishes.

B subsequently displays **Content Available — 1 change available** for this
specific feed. **OK** leads to Pending Downloads containing the new large image
and the retained locally removed small image. This establishes notification and
pending availability in this bounded case, not verified complete bytes or TMM
association. It does not erase the manual-refresh limitation of the first case.

## Bounded broken transfer and explicit retry

Close B's host first, removing its existing HTTP connection pool. Replace only
its guest HTTPS reverse with a temporary host-loopback TCP relay to the same
validating TLS service. The relay forwards encrypted bytes unchanged, never
decrypts or records payloads, and closes after **262,144 cumulative server bytes**.
This count includes startup/metadata/TLS traffic across 15 accepted connections;
it is not an assertion of an exact PNG-byte interruption offset. CoT's separate
lab tunnel, A and the original server/CA/hostname policy are unchanged.

In native Pending Downloads choose the large image's actual **download icon**,
then confirm **Download … 5.9 MB**. The relay reaches its bounded cut. No final
noise-image filename exists in B's checked Data Sync directory; the item remains
pending and subsequently offers Download again. No successful viewer or complete
receipt is inferred. An earlier metadata-only harness attempt timed out without
a cut before the actual download control was identified; it is not a passed
interrupted-transfer case.

Restore B's ordinary TLS reverse. A presence check before retry still finds no
completed noise image. A fresh explicit Download/OK succeeds: actual B bytes are
**6,223,843**, SHA-256 exactly as above and actual dimensions 1920 × 1080. The
native Pending Downloads list no longer includes that image. This is bounded
transport failure/retry evidence, not a TMM stage transaction, arbitrary mid-file
failure timing, power loss or a claim that every private temporary fragment was
securely erased. Future TMM verification must still reject incomplete/mismatched
bytes independently of native download labels.

After a cold ATAK stop/start, the actual large-image file remains byte-identical
and native Photos reopens it in the image viewer. This is host-restart persistence,
not guest power loss or TMM draft/stage adoption. Final reads show two resources /
zero UIDs in the image feed, and zero resources in each of the three comparison
feeds. A's actual private draft frame and active owned image remain byte-identical
to their pre-v23-update baselines. Both native crash buffers contain zero Java
fatal entries at shutdown; both guests still report airplane mode enabled.

## Shutdown and handoff

The task-owned guests, dedicated ADB server and fault relay are stopped. The three
lab Java roles receive TERM; PostgreSQL completes its installed `pg_ctl -m fast
-w stop` before its wrapper is stopped. Lab resources and evidence remain private
for repeat tests. No other container, phone or checkout is modified.

The [source-observer result](SIGNED_V23_SOURCE_VERIFICATION.md) supplies the
companion source/lifecycle evidence. The next receive-route proposal is tracked
separately in #164 and has not adopted a product HTTP architecture.

## Product consequence and remaining work

The documented native feed can carry a clean Full HD PNG intact. A product must
still freeze/review its sanitized export bytes, exact destination and the separately
approved image descriptor. A filename, native author, content hash or directory
name is not authenticated TMM UUID/metadata/feed association or a recipient receipt.
Nothing here attaches the image to a TMM draft or changes an existing local image.

Keep #26 open for an approved exact container/association/receipt registry, trusted
source and exact-M binding, manual current-state-bound adoption and user-facing
failure/retry/replacement controls. [The source-observer limits](INGRESS_OBSERVER_PROBE.md)
also remain. A read-only diagnostic Mission GET is not authorization for a new
product HTTP adapter. Contact transfer does not replace the owner's feed target.

This evidence-only work changes no APK source/build input/version or data format;
the returned v23 and submitted ZIP remain unchanged. No new signing ZIP is needed.
Raw images, client UI, resource bytes, private profiles and server logs stay outside
Git. Required core/images CI is separate from this native result; no local domain /
SDK/image-suite rerun is implied by it.
