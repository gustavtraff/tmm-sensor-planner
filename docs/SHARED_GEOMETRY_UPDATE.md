# Reviewed position and bearing updates — proposal

For current implementation and remaining gates, use [NEXT_STEPS.md](NEXT_STEPS.md).
Approval and implementation notes below retain their stated scope and date.

**G1–G5 approved by the owner on 2026-11-14.** See the
[recorded decision](https://github.com/gustavtraff/tmm-sensor-planner/issues/189#issuecomment-6878986721).
The measurement tool below does not enable production updates. The existing update
path still refuses changes to position, bearing or type.

## Problem

An operator can move a device or change its bearing locally, but the reviewed update
of an existing map copy refuses those changes. The shared copy then goes stale, and
the only way to correct it would be a new object, which the product forbids because
the UUID identifies the device. [The current rules](REVIEWED_HOST_UPDATES.md) say
geometry changes "need their own SDK/native verification". This document defines it.

## Proposed first slice

Allow one reviewed update to change **position, bearing, or both**, on the same map
item and UUID. Everything else in the existing path stays: separate review and
confirmation, freshness checks, no attachments, no retry after an uncertain write,
and the wording that makes no delivery claim.

The review must show old and new MGRS and bearing side by side. A position change
gets its own acknowledgement, because a wrong one misplaces a device for everyone
on the feed.

### Why position is harder than the metadata fields

Position exists in **two places** on a published item: the outer point of the map
item and the exact values inside the `tmmPublication` payload. The current path
writes only the payload and requires the outer point to be unchanged. Updating one
without the other would show native ATAK users one place while the payload says
another, and `PublicationCot.read` would then refuse the event, because it requires
the outer point to equal the rounded payload position. Bearing exists only in the
payload, so it carries much less risk.

## Static SDK check — 2026-11-09

Checked with `javap` against the supplied 5.6.0.24 `main.jar`. Nothing was executed.

| Need | Public API found |
|---|---|
| Read the outer point | `PointMapItem.getPoint()` and `getGeoPointMetaData()` |
| Write the outer point | `PointMapItem.setPoint(GeoPoint)` and `setPoint(GeoPointMetaData)` |
| Refresh and persist | `MapItem.refresh` and `MapItem.persist`, already used by the metadata path |

Method presence says nothing about behavior. In particular it does not show whether
`setPoint` before `persist` produces a native Data Sync update of the point.

## What must be measured before code is enabled

Isolated lab, synthetic data, two standard clients, one emulator at a time.

| # | Question | Pass condition |
|---|---|---|
| 1 | Does a position update reach the server? | Server geometry and payload both show the new position, and agree |
| 2 | Native prompt behavior | No leaves the server unchanged and local state honest; retry and OK behave as for metadata |
| 3 | Receiver behavior | The other client's inbox shows a new variant. Position changes only after an explicit selected-field choice |
| 4 | Partial write | A failure between the point and payload writes is reported as uncertain, with no rollback claim |
| 5 | Native drag | If a user drags the published marker in ATAK, the mismatch with the payload is detected and refused |
| 6 | Rounding | `setPoint` followed by export yields exactly the rounded value `PublicationCot.read` requires |
| 7 | Bearing | A bearing-only update changes the payload and leaves the outer point unchanged |
| 8 | Server version | Controls 1–3 are repeated on the target 5.6 server, or the gap is stated |

The receive side already has the fields it needs: the
[import decision](IMPORT_DECISION_PREPARATION.md) has selectable POSITION and BEARING
fields, so no change to that format is expected. Case 3 must confirm this in practice.

## Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| G1 | Scope of the first slice | Position and bearing. Keep refusing type changes, because a type change alters the CoT type and has no evidence at all |
| G2 | Route | Update the same map item with the point written first, then the payload, then `persist`. Never delete and reimport |
| G3 | Review | Side-by-side old and new values, plus a separate acknowledgement for a position change |
| G4 | Native drag | Treat a marker moved in ATAK as a conflict and refuse the update until the operator reviews it. Do not adopt it silently |
| G5 | Lab experiment | Authorize the two-client lab measurement above, on isolated synthetic clients only |

After approval, the order is: lab measurement, a separately reviewed implementation
PR, a signed candidate, then standard-client controls.

## Verification actually run

Reading of the update code and existing documents, plus the `javap` inspection above.
No build, emulator, server, phone or signing step was run for this proposal.

## Actual SDK geometry measurement — 2026-11-14

A separate permission-free instrumentation APK, signed locally with the inspected
SDK’s existing developer certificate, ran against the retained **5.6.0.24/API35**
developer host. Only public version-matched SDK methods were used. It created one
fixed synthetic marker in the isolated lab and removed that same live object.
No private draft, inbox, stage, registry or image file changed.

The actual observations were:

- A bearing-only payload update plus `refresh`/`persist` preserved the outer point.
- Moving only the point left old metadata; the existing decoder rejected that
  intermediate mismatch. This is an observed partial state, not an atomic-update
  or future error-UI guarantee. A manual native drag was not performed.
- Passing raw private-coordinate precision to `setPoint` left the actual in-memory
  CoT object inconsistent with `PublicationCot.read`. XML serialization rounded
  its point and hid that difference. A valid XML file alone would miss this case.
- Setting the point from the candidate event’s existing seven-decimal outgoing
  projection, then retaining the exact metadata and requesting persistence, passed
  immediate readback with the same MapItem instance and UUID.
- Independent XML checks preserved every exact offered coordinate/bearing value
  and verified the expected outer-point rounding, including the deliberately
  inconsistent intermediate export.

The future writer should therefore use the candidate event’s point coordinates,
not raw private draft coordinates. No persisted/wire format change is needed.
These are actual developer-host SDK observations, not merely method inspection.
They do **not** prove native Data Sync prompt/server behavior, durable persistence,
standard-client compatibility or the receiving client’s selected-field workflow.
The two-client/native/target-server matrix above remains open under #189.

## Reproduce the bounded measurement

`sh scripts/build-geometry-sdk-probe.sh /private/output.apk` builds an **unsigned**
APK using the cached toolchain and inspected external `TMM_ATAK_SDK_DIR`. It
packages the core, existing envelope adapter and measurement source only; it does
not sign, install, request permissions or include the SDK/key. Local deployment
requires the matching inspected developer-host certificate; no signing bypass is
supported. Never submit this measurement APK as the ordinary TMM plugin.

On an explicitly owned isolated developer host with existing approvals, after
independent runtime ownership/deadline/loopback checks, the instrumentation command
is `am instrument -w -e confirmSyntheticLab G1-G5-20261114
se.tmm.sensorplanner.geometryprobe/se.tmm.sensorplanner.tools.GeometrySdkInstrumentation`.
It requires an empty host external-files `tmm-geometry189` directory and refuses a
pre-existing fixture UUID. Proof and fixed synthetic CoT exports are written there.
Keep actual captures private; independently compare the exports and private-file
baseline, then clean only the owned APK/runtime. The live-map removal result is
not a promise of remote withdrawal or deletion of every historical copy.

## Standard-client native file handoff attempt — 2026-11-15

An owned retained ATAK-CIV **5.6.0.25**, TMM **29**, Data Sync **3.7.4**
client attempted the ordinary Import → Local SD → Copy route with the actual
SDK measurement's synthetic base CoT export. Only the outer delivery time/start/
stale headers were refreshed; the fixed UUID, all exact metadata and projected
point were unchanged. The selected feed and four known comparison feeds had
Auto-publish new content disabled; no setting or role was changed.

ATAK logged the fixed UUID's CoT import attempt, but its native Data Sync overlay
selector did not expose that fixture, including a root search by its exact callsign.
No Add Items/Done publication was confirmed. The selected feed and an independently
read comparison feed both remained without the fixture UUID, and every private
TMM file remained byte-identical. This does **not** establish the importer failure's
root cause, successful native map-item creation, geometry transport, native update
prompt behavior or the other client's selected-field result. No second client was
started and the production geometry restriction remains in place.

The owned hash-verified Downloads fixture was removed, and the emulator, dedicated
ADB listener and three lab containers were independently verified stopped. Actual
captures and ownership records remain private. There was no signing submission,
APK installation, new permission grant or external server access.

The next measurement must exercise the actual existing host-item update route in
a compatible synthetic standard-client candidate; importing an exported CoT file
cannot substitute for the direct SDK/persist route. Retain the existing SDK rounding
result rather than rerunning it, and keep activation behind the separately reviewed
implementation and remaining two-client controls.
