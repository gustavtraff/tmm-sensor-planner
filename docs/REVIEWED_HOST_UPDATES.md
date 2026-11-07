# Reviewed metadata-only host updates — #173

Candidate **27 / `0.1.0-reviewed-update-lab.1`** extends the saved-draft map-copy
pilot with a separate in-memory existing-copy review. It is still restricted to
the connected isolated loopback lab and synthetic data. It is not an operational
publisher or completion of #173/#171/#25. No private or outgoing byte format changes.

## Operator flow

Open a saved device, expand **Fler uppgifter**, choose **Skapa granskad kartkopia ·
labb**, then **Granska uppdatering**. That second action only reloads/validates the
saved selection and inspects the existing host copy. It performs no host write.
The separate update review shows the previous host metadata/label and proposed
saved metadata/label. Its confirmation starts disabled. The operator must explicitly
acknowledge checking every affected lab feed and possible native publication.
Unknown destinations are not thereby verified or approved. Do not use real data.

TMM does not select a Data Sync feed or validate all native membership/preferences.
The host may publish changes immediately, later or after its own user action.
The checkbox is a manual synthetic-lab safeguard, **not** a verified destination
API, continuous permission lease or operational sharing authorization. Do not
promise a single destination or a native Done boundary from this UI.

Only ID, status, owner, deployed time, comment and updated time may differ. UUID,
creation time, device type, exact position and bearing must match the previous
valid host metadata. Geometry/type changes need their own SDK/native verification;
refusal does not authorize deleting the old host object or changing UUIDs.
SDK-reported host attachments must be absent at review and commit. Private TMM
images are neither opened nor transmitted by the update code; their association
and bytes are untouched. Native resource/destination behavior remains a separate gate.

## Freshness, ownership and commit

The existing planner worker, Selection/store identity and persistent per-UUID
version protect both private reads. Before commit, the saved selection is loaded
again; deletion/recreation, dataset replacement, image-only changes, missing/corrupt
storage and normal edits invalidate the review. The worker stays occupied during
the bounded UI handoff. This is not an interprocess transaction spanning a private
file, a host object and a remote server.

The one-use host review retains the actual MapItem identity and a bounded deep
copy of its export. The SDK's CotEvent/CotDetail copy constructors retain nested
child references, so every detail is rebuilt independently. Limits are depth 16,
1,024 detail nodes, 64 attributes/children per node and 65,536 aggregate UTF-16
units of detail names/values/text. Oversized/unsupported input fails without
truncation. It is not a new wire/storage profile.

Before writing, require the same single host instance, valid original UUID/profile,
no SDK-reported attachments, unchanged exported metadata, callsign, details and
non-delivery headers/point. Export time/start/stale are excluded from freshness:
they are not causal host-edit versions. Changes/replacement, duplicate UUIDs,
missing/unsupported copies, Cancel/Unload/access loss and reused handles refuse.
The policy does not detect every hidden native membership or concurrent server
change and never labels timestamps or local revisions as distributed versions.

The update does **not** call the importer, delete or replace the MapItem. The
verified public SDK path is OpaqueHandler.toMarkerMetadata for the single existing
`tmmPublication` payload, the SDK callsign update helper, then MapItem.refresh and
MapItem.persist with this plugin's own source class. Its default persistence extras
are retained; no private Data Sync action or invented suppression flag is used.
This preserves the original object's other properties instead of reimporting them.
Native listeners may react once the confirmed mutation starts; no globally atomic
host/transport transaction is claimed.

The result `METADATA_PERSIST_REQUESTED` describes the SDK request only. Readback
checks exact proposed wire values, same host identity/count, no reported attachments,
label and retained exported content against an independently owned expected copy.
It is not a disk-flush, delivery, remote acceptance, receipt or complete-membership
confirmation. A failure after mutation may have effects: report uncertainty, do not
roll back, blindly retry or claim nothing changed. Cancellation after the boundary
only suppresses stale presentation. Unchanged content is reused without a write.

## Verification

The actual supplied 5.6.0.24 SDK was inspected and used to compile the public
OpaqueHandler, MapItem/MapView, AttachmentManager, PlacePointTool and CoT APIs.
`sh scripts/test-publication-sdk.sh` exercises all four device types against real
SDK envelopes and a counted fake write boundary, in English and Swedish locales.
It covers ownership, preserved nested details/non-delivery headers, exact reuse,
private geometry refusal, duplicate/missing/changed/replaced host objects, reported
attachments, cancellation, one-use reviews and ambiguous/changed post-write results.
It does not run a native host or establish Data Sync behavior.

The full helper/image run reports **429 cases, zero failures/errors, one intentional
skip**, including 12 additional API26/35 controller cases. These cover separate
consent, disabled confirmation, double activation, cancellation/access loss,
changed image/draft, recreated record/dataset, safe inspection failure and post-write
cancellation. Core tests and actual SDK debug/release assembly, lint and app unit
checks pass. Native evidence and exact candidate identifiers are recorded below.
