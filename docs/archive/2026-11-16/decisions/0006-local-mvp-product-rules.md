# ADR 0006: Product rules for the local MVP

Status: accepted by human merge of PR #28. Domain edits/filtering and core storage implemented; guarded offline forms/map integration added; images and physical acceptance pending.
Date: 2026-10-16. Tracking: [Issue #8](https://github.com/gustavtraff/tmm-sensor-planner/issues/8).

## Owner decisions recorded on 2026-10-16

| Topic | Confirmed choice |
|---|---|
| Status and neighbors | Planerad, Utplacerad, Aktiv, Intagen and Avbruten; manual status changes. Count only PLANNED/DEPLOYED/ACTIVE candidates in distance results. |
| Visible ID | Manually enter the actual device's ID, for example synthetic `PIR 134`. No automatic numbering or enforced prefix/format. |
| Local image | One JPEG/PNG per device, at most 10 MiB input; save a separate copy without EXIF/embedded metadata. Image sharing remains deferred. |

Owner text and duplicate handling retain the existing free-input approach as an
MVP default adopted through PR #28; no organization-specific owner standard was supplied.
The additional decoding, timestamp, bearing and artwork details below are
implementation defaults adopted in that review, not claims that the owner separately
approved a pixel limit, automatic date behavior or a particular symbol design.

## Existing decisions

The owner selected Mini, Scout, PIR and Gateway. Only PIR and Scout support bearing.
Users save local drafts and explicitly review before any later sharing operation.
Images are local in the MVP; Data Sync and production image transport remain deferred.
See [DEVICE_CATALOGUE.md](../DEVICE_CATALOGUE.md) and [ADR 0005](0005-defer-data-sync-and-image-sharing.md).

## Rules and implementation defaults

### Status and deployment time

| Stored value | Swedish label | Meaning |
|---|---|---|
| `PLANNED` | Planerad | Intended placement; position may be approximate |
| `DEPLOYED` | Utplacerad | Manually recorded physical deployment |
| `ACTIVE` | Aktiv | Manually recorded active state; not measured telemetry or an alarm |
| `RETRIEVED` | Intagen | Device recorded as retrieved; retain its record |
| `CANCELLED` | Avbruten | Plan recorded as cancelled; retain its record |

New drafts start as PLANNED. Allow explicit manual changes between any of the five
statuses, including correction or reopening. No automatic status changes, deletion,
sharing or field clearing occurs as a consequence. A cancelled edit saves nothing.

Keep `deployedAt` optional and manually editable. A status change does not stamp,
overwrite or erase it: entering data later must not invent a deployment time.
`createdAt` and `updatedAt` remain automatic UTC instants, displayed locally.

### Identity and owner

Keep the random stable UUID as identity across edits. Visible ID and owner are
required, trimmed free text. The user enters the actual device label, such as
synthetic `PIR 134`, preserving its internal spaces and spelling. Do not generate,
renumber, derive from UUID or enforce a type prefix/regular expression. Changing
the visible label does not change the record's UUID. Owner remains a descriptive
group/role field, not access control; no organizational directory is introduced.

Warn about duplicate visible IDs in the local collection after trimming and
case-insensitive comparison; retain the entered spelling. Do not block saving or
merge records by visible ID. UUID is authoritative. Cross-client uniqueness and a
future working-area scope are outside the local MVP.

Retain the existing comment limit of 2000 UTF-16 code units. Keep this unit explicit
in validation documentation; do not claim it counts user-perceived characters.

### Bearing and distances

Store optional decimal degrees clockwise from **true north**, finite and in
`[0, 360)`, only for PIR and Scout. Label the reference explicitly. No implicit
magnetic conversion or inference from the host's display settings is allowed.
The Android adapter must verify rendered direction against the actual ATAK settings.

For nearest-device and gateway lists, use PLANNED, DEPLOYED and ACTIVE
**candidates** only. RETRIEVED and CANCELLED records remain available for editing
but do not count as neighbors. The selected source record may have any status;
always exclude its UUID, even if a second object instance carries that UUID.
The gateway list additionally restricts candidates to Gateway, sorted by distance
then UUID for ties. No eligible candidate means an explicit empty result.
Keep great-circle horizontal meters; these distances do not estimate radio coverage.

### Local image lifecycle

One optional static JPEG or PNG per record. Reject input over **10 MiB
(10,485,760 bytes)** before decoding. Check decoded dimensions before allocating a
full bitmap and reject over **20,000,000 pixels**; a small compressed file can still
be too large to decode safely. Reject corrupt, unsupported or animated inputs
without replacing an existing image. Do not trust extensions or a supplied MIME type.

Decode and save a new plugin-owned image from pixels only, without EXIF, GPS,
embedded text or other source metadata. Never copy the original file into draft
storage. The original implementation enforced the same byte/pixel limits on the normalized result;
the #66 amendment below separates source and owned-copy byte limits. Do not silently
downscale; the initial implementation rejected images that could not fit. The
explicitly disclosed resize-copy amendment below supersedes this rejection default
for new attachments after human review of #49.
Apply source orientation to pixels before discarding its metadata; verify with
synthetic rotated images. This does not remove information visibly drawn in an image.

Keep originals untouched. Preview the new copy before committing replacement;
cancel/failure retains the previous record and image. Remove the old owned file
only after a successful replacement commit. Removing the attachment clears only
the local association/owned copy; no server or recipient deletion is implied.
A missing owned file produces a visible error and does not silently drop metadata.
`imageRef` remains an internal local handle, never a shared file path or CoT field.

### Symbols and sharing boundary

Use original plugin symbols with type letters M/S/P/G and explicit type/status
text for the first local UI. Do not copy manual graphics or map alarm colors to
planning statuses. This does not identify Mini with an unconfirmed manual symbol.
Exact manual symbol matching remains outside this initial artwork choice.

No feed selection, edit rights over received objects, recipient-without-plugin
representation or production wire schema is fixed here. These belong to #14/#15,
#25 and #26. Metadata-only sharing must explicitly state that an image is not shared.

## Consequences and implementation gates

The domain implementation now includes validated immutable edits and the accepted
candidate filter, with manual-ID preservation and status/boundary tests. It still
now has an offline duplicate-warning/form/map adapter on API 26+; image processing
and production connected/physical acceptance remain pending. The first [persisted format](../LOCAL_STORAGE.md) now has a fixed v1 fixture and
separate-process reopen tests. See [LOCAL_PLANNER.md](../LOCAL_PLANNER.md) for bounded APK integration results.

- #10: domain rules and candidate filtering implemented with synthetic validation,
  boundary, status, self-exclusion and tie-order tests.
- #11: core versioned storage and failure tests implemented; Android restart/upgrade
  evidence remains open. Image association stores an opaque handle only; actual
  image-file replacement/failure ordering remains #13.
- #12: manual status/time edits, duplicate warnings, explicit bearing reference and original symbols.
- #13: normalized image copies, decode limits, orientation, preview/replace/remove,
  cancellation, missing-file and failure tests. Use synthetic metadata/rotation fixtures.

The three owner answers are recorded above. Human merge of PR #28 adopted the remaining
MVP defaults and closed #8. #11–#13 retain Android integration/acceptance work.
ADR 0007 introduces the first persisted format; there was no older stored format
to migrate. Later storage/schema changes still require migration tests. The original core
assumptions in ADR 0001 remain provisional until a decision is accepted and implemented.

## Symbol follow-up — 2026-10-18

The owner confirmed Mini's seismic/acoustic association in #43 and approved the
revised reference-based four-device pictograms plus planning colors: yellow Planned,
green Deployed/Active, gray Retrieved/Cancelled. This supersedes the initial
letter-disc artwork choice above. [DEVICE_SYMBOLS.md](../DEVICE_SYMBOLS.md) records
source/PNG assets, acceptance evidence and remaining physical-client checks. No
alarm/health status or domain/snapshot/wire field is added.

## Phone-photo copy amendment (#49) — 2026-10-18

Status: implemented for PR review; acceptance is by human merge, not a claim of
separate owner approval of a particular pixel value. The owner requested working
ordinary phone-photo attachments after a valid-size camera JPG failed.

Retain static JPEG/PNG input, 10 MiB / 20,000,000 source pixels, EXIF orientation,
metadata removal and immutable originals. Prepare new PNG copies at a maximum
1,600 pixels on the longest side, preserving aspect ratio and never upscaling.
Sample the decode and draw orientation/resize into a fresh bounded bitmap to
avoid full-resolution PNG expansion and multiple full-size allocations.
The normalized preview must disclose the reduction before Save locally; Cancel
leaves the previous attachment intact. This is explicit review of a reduced copy,
not silent replacement of the original. The tradeoff is reduced local image detail.

Keep existing owned PNGs readable without rewriting them. No new handle format,
snapshot/wire schema, transport or image-sharing behavior is introduced. Use
specific safe preparation errors and preserve existing metadata commit ordering.
See [LOCAL_IMAGES.md](../LOCAL_IMAGES.md) for reproduction and validation scope.

## Form entry follow-up (#51) — 2026-10-18

The owner requested keyboard completion without focus jumps and simpler deployment
time entry. [FORM_ENTRY.md](../FORM_ENTRY.md) defines explicit local date/time
selectors, initialized to now only when opening an empty value, cancellation and
clear behavior, minute precision for new selections and exact preservation of
existing Instants. No deployment timestamp is derived from status or merely saving
a draft. UTC storage and snapshot v1 remain unchanged.

## Camera JPEG primary-still amendment (#49) — 2026-10-20

Status: proposed through this PR for human review. Allow bounded JPEG inputs with
auxiliary camera data, but save only the validated primary still. Discard extra
pictures, HDR recovery data, video/audio and trailing bytes; explicitly disclose
primary-still-only saving in the confirmation preview. This supersedes the earlier
blanket MPF/trailing-JPEG rejection, not the requirement for one normalized still.
PNG/APNG rules, original immutability, EXIF orientation/removal, complete-source
10 MiB cap, pixel/output limits and safe commit ordering stay unchanged. No handle,
snapshot or wire migration is introduced. See [the image investigation](../LOCAL_IMAGES.md#camera-jpeg-primary-still-compatibility-49--2026-10-20).
The specific OnePlus photo/provider still needs physical acceptance after signing.


## Full HD detail amendment (#66) — 2026-10-21

The owner explicitly requests at least Full HD detail where the source permits.
This reviewable implementation supersedes the 1,600-edge policy with a **1,920-pixel
longest edge**, aspect ratio retained, no upscaling/cropping. A 16:9 image becomes
1920 × 1080; the reported 3072 × 4096 photo becomes 1440 × 1920.

Keep the complete source limit at **10 MiB / 20,000,000 pixels**. Separate the owned
PNG byte cap at **16 MiB**, including read/reopen validation. A 1920-square ARGB
bitmap has 14,745,600 raw pixel bytes before PNG overhead; keeping a shared 10 MiB
cap would unnecessarily reject sufficiently detailed copies. PNG preserves the
resized pixels without introducing a new lossy compression setting or handle type.
The display decoder remains independently bounded (preview 1024, inspection 2048).

The earlier synthetic PNG-expansion regression did not prove 1600 necessary or
diagnose the OnePlus camera failure. The separate primary-JPEG validator correction
and physical camera acceptance stay in #49. Preserve EXIF orientation/removal,
originals, old handles/images, snapshot v1 and commit/Cancel ordering. Old 1600-edge
copies do not regain detail; reselect their originals to replace them. No background
rewrite, sharing or transport is added. Older APKs with a 10 MiB owned-read limit
may not display new copies above that size; downgrade compatibility is not promised.
See [IMAGE_QUALITY.md](../IMAGE_QUALITY.md) for bounds and actual test evidence.


## MGRS and graphical placement amendment (#52) — 2026-10-21

Use WGS84 UTM-based MGRS in the local form and saved review. Initial manual input
supports one-meter references (zone/band, square, five easting plus five northing
digits), not decimal degree fields or UPS polar grids. This is a display/input
precision, not a claim of positioning accuracy. Numeric canonical storage/wire
values remain unchanged. Existing values outside 80°S–84°N remain intact on an
unrelated edit, with an explicit unavailable-MGRS indication in review.

Graphical selection uses a temporary screen sight: pan/zoom under it, explicitly
accept or cancel, then Save locally separately. Keep the same editor instance and
all unsaved fields while selecting. No normal ATAK Marker/MapItem, storage write,
publishing or new sharing route is introduced. Untouched displayed MGRS retains
exact original/map numeric coordinates instead of quantizing every unrelated save.
Actual SDK conversion, cell/boundary handling and acceptance limits are documented
in [MAP_POSITION.md](../MAP_POSITION.md).


## Review and 6400-mil display amendment (#67) — 2026-10-21

Implement the owner's requested review priority: visible ID/type/status, MGRS and
map actions, local image, PIR/Scout bearing, then other fields. Keep local draft
state separate from lifecycle status. Group technical timestamps/revision and
less frequent actions under Fler uppgifter; rename the list reload to Uppdatera.

Use optional whole mils on a 6400 circle, clockwise from true north. Accept 6400
as zero; keep domain/storage/wire/map geometry in degrees [0,360). Preserve exact
old degree values on an unchanged rounded field, including unrelated edits. A
read-only north-up cue is included; the optional draggable compass is deferred.
No type/status, schema, sharing or image-quality change. See
[DEVICE_DETAILS.md](../DEVICE_DETAILS.md) for precision, ownership and test gates.
