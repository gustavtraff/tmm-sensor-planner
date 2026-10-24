# ADR 0011: Test a separate sensor-point envelope through Data Sync's own UI

Status: proposed for human review; lab candidate only.
Date: 2026-10-24. Tracking: #83 / #25, with #14/#15.

## Context

PR #84 established that Data Sync 3.7.4 rejected selection of the experimental
`b-m-p-w` waypoint. A distinct friendly-marker diagnostic preserved all custom
fields, but was not an acceptable silent replacement for the original envelope.
The supplied 5.6.0.24 SDK registers `b-m-p-s-p-loc` as a built-in Mission marker
and supplies its sensor icon. The public pinned importer source agrees. This
establishes a host representation to test, not a private Data Sync API contract.

## Proposed decision

Keep the existing waypoint fixtures/checker unchanged. Add explicit `sensor-*`
fixture cases using `b-m-p-s-p-loc`, host archive detail and a separate synthetic
UUID namespace. Reuse the same experimental ShareLabFields metadata; change no
production wire format, storage, device catalogue, icons or APK.

Use only the documented manual workflow: generate a reviewed fixed synthetic file,
ATAK Local SD import, then Data Sync's selected-feed Add Items → Overlays → Mission
checkbox → Done. This is a lab workflow, not an in-app feed picker or automated
plugin adapter. Never create a private draft as an ordinary MapItem to make it
selectable. An integrated adapter still requires explicit review before copy
creation, destination revalidation and evidence for its exact SDK creation path.

[The trial](../DATA_SYNC_SENSOR_TRIAL.md) passed this specific PIR file-import and
manual publication path with a second subscribed feed's automatic publication on
and content prompt off. It does not approve direct Marker creation, arbitrary
source tags, every host version or other incoming/outgoing paths. Do not fake a
stock importer identity to reproduce the result. ADR 0005's production deferral
and ADR 0010's investigation boundary remain in force.

## Consequences

A source/server/recipient event must match the declared `sensor-*` case exactly;
the old waypoint case must continue to reject the different type/UUID. The lab
UUID namespace is disposable and is not an object-identity migration. Other device
cases and the same-UUID update have offline tests, not runtime Data Sync acceptance.

Review the evidence before continuing #25 permission, duplicate/update, recovery,
receiver restart and conflict/removal tests. Durable TmmDraft import remains #15;
production review/publishing remains #14 and images #26. A recipient feed export
is not a live MapItem re-export, durable import or delivery acknowledgment.

## Sources

- Supplied official 5.6.0.24 `main.jar`: CotImporterManager, MarkerImporter,
  ArchiveDetailHandler, Marker's Exportable/MissionPackageExportWrapper methods;
  signatures/behavior inspected privately with `javap`.
- [Pinned official importer source](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/CotImporterManager.java): Mission marker registration.
- Data Sync 3.7.4 bundled user guide, pages 6–7: manual selection and map-item
  families. Its APK/manual hashes are in [the investigation](../DATA_SYNC_INTEGRATION.md).
  No supported callable third-party Data Sync Android API was identified.
