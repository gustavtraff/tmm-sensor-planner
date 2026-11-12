# Synthetic shared-image trial UI — candidate 30 (#208 / #200 / #26)

Candidate 30 (`0.1.0-shared-image-view-trial.1`) connects the approved I1–I5 trial
through the existing planner. It is an incremental **synthetic-only test** flow,
not release/operational acceptance or automatic synchronization. No data formats,
private active-image binding or metadata-only publication schema change.

## Operator flow

Open **Bilddelning · test** in the device list or a saved device's **Fler uppgifter**.
Select the already connected TLS profile and HTTPS API port. Accept the four
synthetic-only/operator/profile/destination-limit statements. Exactly one enabled,
connected selected stream and the default provider are required; disconnect or
Unload invalidates pending source work. No credentials are copied.

**Sender:** from a saved device, choose **Förbered bild för Data Sync**, then the
intended test feed. Review the actual sanitized picture, metadata and destination.
Only **Spara export i Hämtningar** may write a public file. The controller rereads
available feeds and freshly confirms saved local state, source image and destination
before producing a new immutable UUID-named container. Find it under
`Download/TMM-Sensor-Planner/`. In native Data Sync, select the feed and use
**Add Items → File Select → OK**, then any **Publish** confirmation. TMM reports
only **Export förberedd**, not Shared/Delivered. Later edits/deletion cannot update
or recall the frozen file; it remains outside private no-backup storage until
explicitly removed. The final native feed/destination remains operator-controlled.

Exports are limited to 16 files / 256 MiB, counting existing completed files. Writes
use an exclusive cooperative lock, new opaque pending file, force/readback, atomic
same-directory rename and final readback. No overwrite, copy fallback, automatic
retry, cleanup or eviction occurs. Partial `.pending` exports block admission and
must be explicitly reviewed/removed locally; failures after rename may already
have produced a complete `.tmmi`. Public files may be changed by external actors
later; recipients still verify actual bytes. These observations are not a perpetual
integrity/permission lease or power-loss durability guarantee.

**Receiver:** choose **Hämta bilder från feed**, select feed/resource, review the
claims and explicitly confirm download. Missing stores require a separate explicit
initialization decision, with no reset of corrupt/unsupported existing history.
Known registry links to a missing inbox/stage refuse instead of silently starting
empty. The approved bounded reader checks current selected-feed membership before
and after the same-origin content read, actual file hash/length and strict bytes.
The existing inbox/stage/registry retain the exact observed metadata and picture;
source context is the configured service/GUID observation, not author identity.

The result offers **Visa mottagen bild** or **Granska metadata**. The latter reuses
the existing versioned import preview and explicit local confirmation, bound to the
fresh received receipt/source session. Merely viewing/importing metadata never
attaches the received picture to the private active image. A previously unknown
UUID is not created automatically; explicit metadata Add remains image-free.

**Later/offline viewing:** **Mottagna bilder** in device details opens all retained
variants for that UUID. The global image menu can list unknown-device variants.
The controller rechecks actual registry/inbox/stage binding before using the
existing zoom view. **Uppgifter** shows the offered metadata, source, association
and delivery variant. No arrival order selects a current picture, and a locally
deleted device is not resurrected. Missing/corrupt content stays an explicit error.

## Lifecycle and outcomes

One bounded network worker, a 60-second actual abort timer, local guard polling and
the SDK connection-loss observer cancel pending source work. Local store/export
operations use the planner's serialized worker and generation/source checks.
Unload cancels dialogs, timer, network, previews and viewer pixels; stale callbacks
cannot export, open a viewer or grant source authority. A physical store commit may
already have happened when cancellation/error is reported: inspect saved history /
Downloads rather than assuming rollback. Registry/stage limits stay 16 / 256 MiB and
256 associations, without automatic historical eviction or feedback.

## Verification boundary

Focused API 26/35 Robolectric tests use real independent container/PNG bytes and
actual local stores, with a controlled fake transport. They cover export Cancel,
actual frozen readback, fresh feed/local state, stale callbacks/source loss, quotas,
unknown-device retention, explicit metadata Add/Cancel, repeat, offline viewer and
Unload before queued intake. This is not an actual SDK HTTP/native publisher,
standard-client signed update or two-phone/server acceptance. The full image/UI suite and lint passed; the final focused controller/export
suite passed 28 API 26/35 cases without skips/errors. External SDK/debug/lint/app/
APK checks passed; release assembly and bounded runtime results are recorded
in the PR as actually completed. I5's integrated
lab, signer/standard clients and separately authorized physical trials remain
required. Current signed v29 has none of these new UI actions.


## Actual isolated SDK-host controls — 2026-11-13

One retained SDK developer host **5.6.0.24 (7f2055b0)** on Android API 35 loaded
the candidate 30 debug plugin. Both IPv4/IPv6 guest egress were verified loopback-only;
only owned local tunnels reached the retained 5.8-RELEASE-92 lab. No operational
server/phone or new credentials/permissions were used.

| Control | Actual result |
|---|---|
| Real configured source and image feed | Native UI listed the real accessible feeds and the eligible 6,224,532-byte `.tmmi` resource through the production SDK adapter. |
| Actual HTTP/intake | Explicit download completed; independently read-back descriptor and 6,223,843-byte 1920×1080 PNG exactly equal the original prior native-uploaded server container. The two existing private devices were not changed or supplemented automatically. |
| Viewer | The real image displayed; Zoom in/out, Fit and offered metadata details worked. Screenshot inspection confirmed image and controls visible. |
| Repeat | A new explicit download of the same resource succeeded; every private/inbox/registry/stage file remained byte-identical. |
| Offline/cold start | After force-stop/cold start with both lab tunnels removed, the retained picture opened without source consent or a new download. |
| Local import for sender control | A synthetic 6,223,843-byte FullHD PNG was selected through Android's normal local document picker and explicitly saved on one existing private Mini. This deliberate local attachment is the expected private-data change, not an intake/export effect. |
| Export Cancel | Real image/destination review opened; Cancel created no public export directory or file. |
| Export confirmation | Fresh feed check and local confirmation produced one 7,172,600-byte completed container, including 7,171,952-byte 1920×1080 PNG. Independent frame/descriptor-CRC/PNG-digest/dimension/selected-label inspection passed; no pending file remained. Every private file matched the post-local-import baseline. No native Publish was requested. |
| Actual pending-read Cancel | The owned relay was briefly paused; Cancel was pressed at `Hämtar och verifierar bildpaket`, then relay was restored in a finally block. All private/history/stage/image files remained unchanged; no late association appeared. |

The offline restart allowed an SDK-local listener to occupy 8443, so the operator
explicitly selected an owned alternate API tunnel 18443 for subsequent sender and
cancellation controls. There was no automatic product fallback or credential
change; the source service/port remains part of the reviewed identity.

Data Sync 3.7.4 was installed but not loaded/available in this developer-host tool
list. Native publication was therefore **not** repeated or forced through a private
API/security bypass. The earlier native container transfer remains separate
evidence. New signed-candidate standard-client A→feed→B, permission/lifecycle
matrix, target 5.6 server and physical-phone acceptance remain open under #200/#26.
These actual SDK controls do not close those gates.
