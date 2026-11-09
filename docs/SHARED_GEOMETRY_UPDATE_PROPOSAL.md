# Reviewed position and bearing updates — proposal

**Status: proposal awaiting owner decisions G1–G5. Merging it approves nothing.** No
code changes with this document. The existing update path still refuses any change
to position, bearing or type.

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
without the other would publish a device in two different places, depending on who
reads which field. Bearing exists only in the payload, so it carries much less risk.

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
| 6 | Rounding | The outer point's SDK rounding is measured, and the comparison tolerance comes from that measurement |
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
