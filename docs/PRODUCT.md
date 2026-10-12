# Product

Planners create a local TMM device, review it and share it manually. Deployers
update the same stable identity. The target version is ATAK-CIV 5.5.0.

Requirements F01–F12 and N01–N08 are defined in the [project plan](PROJECT_PLAN.md#3-requirements).
The MVP includes drafts, storage, a map view, forms, bearing, distances, a local image and
review. Sharing may only be added after an approved technical investigation.
Hardware control, telemetry, optimization, radio/coverage modeling and a dedicated server
are outside the first version. Distance says nothing about radio links or terrain.

The initial core implements parts of F01/F02/F03/F07/F08, without a user interface.
Minimum catalog: gateway, pir, scout. Proposed status catalog: planned, deployed,
active, retrieved, cancelled. Status transitions have not been decided.

Open decisions: catalog, ID/owner standards, status transitions, automatic deployment time,
bearing reference/display, which statuses count when finding the nearest device,
image size limit/EXIF, target channel and editing other users' objects.
See [provisional core choices](decisions/0001-sdk-independent-core.md).
