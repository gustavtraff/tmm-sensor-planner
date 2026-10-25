# Synthetic SDK publication-import candidate — #14

Status: implemented test candidate, **runtime and feed privacy unverified**.
Date: 2026-10-25. Baseline: main `adc7d24` (including the #94 review inbox).
Version 16 / `0.1.0-publication-lab.1` requires new service signing for standard ATAK.
The package ID is unchanged. This is a lab candidate, not an operational release.

## Purpose and boundaries

The old signed v15 has no callable experiment for
`CotImporterManager.getInstance().importData(CotEvent, Bundle)`. A manual file
import or a plain-JVM constructor test cannot prove this path. This candidate
adds a separate **Syntetiskt SDK-importprov** choice under **TMM CoT-labb**.
The earlier CoT lab remains a separate choice. Switching closes pending reviews
and the earlier waypoint-only metadata handler before the sensor experiment.

The new probe accepts no user record, URI, file or external event. It constructs
only the fixed `sensor-pir` fixture already specified by the independent offline
checker: UUID `00000000-0000-4000-8083-000000000003`, type `b-m-p-s-p-loc`,
`archive`, and the exact 15 experimental `tmmShareLab` fields. All coordinates and
text are synthetic. No draft/inbox/image store is read or written.

Android 8+, the existing offline/airplane-mode policy, no connected-local-trial
consent and exactly the enabled connected loopback lab stream are required at
entry and confirmation. Those checks are guardrails, not proof of network isolation;
the operator must still firewall the guests and use the isolated test server.
There is no exported probe activity/broadcast, background import or native feed API.

A one-use PublicationPreparationReview precedes the synchronous import. Cancel,
back, switching labs and Unload invalidate pending preparation. The confirmation
states that an ordinary host object may trigger automatic publication in other
feeds. It must be used only with controlled synthetic feeds. No destination is
claimed, and the result reports import return/count/re-export separately from
publication or recipient acceptance. There is no automatic rollback/retry/delete.
Unload cannot revoke a completed import, native retained copy or queued publication.

The Bundle contains only our own `from` identifier,
`se.tmm.sensorplanner.plugin.publicationlab`. It does not impersonate
`MissionPackage`/`StateSaver`, set `serverFrom`, or invoke a dispatcher. The pinned
[public importer source](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/CotImporterManager.java)
and [MapItemImporter source](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java)
were inspected; signatures were checked against the supplied SDK main.jar.
That source distinguishes stock local-import identities and dispatches host import
events; an own source string is not a claim of local-only behavior.

An existing same-UUID map item must match the fixed metadata/type/rounded point
before an import is attempted; other variants are refused. After the attempt the
probe counts actual root-group UUID matches and checks CotEventFactory's actual
re-export. Unknown-detail preservation is deliberately tested as host behavior;
no new handler is installed to make the result pass. Lack of preservation is a
failed candidate, not permission to claim recipient compatibility.

## Verification before signing

`sh scripts/test.sh` passed, including the merged inbox/store tests. The expanded
`sh scripts/test-publication-sdk.sh` compiles the installed fixture itself, exercises
construction/mismatch rejection in en/sv JVMs and validates its serialized bytes
through the unchanged independent `check sensor-pir` command. Existing six-case
SDK serialization checks also pass. No importer is invoked by these JVM checks.

`sh scripts/check-sdk.sh` passed: MGRS/SDK checks, CivDebug build, Android lint,
app unit tests and APK signature/discovery/permission/packaging checks. The initial
new fixture API-level lint error was corrected with its API-26 annotation and
the full check rerun successfully. No image test suite or CI wait was performed.
The signing-service target `./gradlew --no-daemon :app:assembleCivRelease` also
passed locally using the inspected external SDK. This is not verification of the
remote Maven environment or the service signature.

No emulator/Docker, runtime import, recipient export or actual wrong-feed absence
check has run for v16. Signing and the following lab protocol remain prerequisites
to any runtime/privacy claim. Existing production sharing stays disabled.

## Standard-client lab protocol after signing

1. Inventory/hash the signed returned APK; verify package, v16, API target and
   signer before installing in the two existing standard lab clients. Retain the
   v15 artifact and private snapshots. Reconfirm exact host/Data Sync versions.
2. Start only the owned lab server/API, task observer and two guests. Check actual
   authenticated readiness before opening ATAK. Keep loopback-only egress, validated
   mutual TLS and the normal signature checks; never use an operational profile.
3. Read actual baseline membership/exports for the intended and comparison feeds.
   Resolve the reused synthetic sensor UUID from earlier trials explicitly; a
   residual server/feed/history copy must not masquerade as a new transfer.
4. Subscribe A/B appropriately. Keep the intended feed's auto-publication off;
   turn the comparison feed's auto-publication on and its prompt off. Verify both
   settings by readback and a fresh native control reaching B/comparison resources.
5. Open the new review, then Cancel/back. Search map, both feeds, observer and B
   for the fixture UUID. Repeat with Unload/restart while review is pending.
   A result is valid only relative to the recorded baseline; cancel must add nothing.
6. Confirm the SDK import once. Record the actual return/count/re-export report,
   both feed/API memberships, observer and B before any native Add Items/Done.
   If the comparison feed publishes it, stop: this creation path fails the intended
   pre-feed privacy boundary. Do not suppress the evidence by adopting a stock tag.
7. Only after that boundary passes, select the single sensor via the normal native
   feed UI. Cancel first and measure; then explicitly confirm. Export from B itself
   and run `sh scripts/datasync-metadata-probe.sh check sensor-pir RETURNED_COT`.
   Confirm intended membership once, comparison absence and unchanged private stores.
8. Repeat the same candidate and inspect actual UUID counts/resources. Test pending
   review cancellation and completed-copy behavior separately across Unload/restart.
   Do not treat import success, host persistence or native panel closure as delivery.
9. Run after-controls, restore feed settings, scan private canaries/snapshots, then
   stop only the owned lab resources cleanly. Record failed/unavailable checks too.

The probe cannot validate stale *saved-draft* reload or real destination confirmation:
it has no store/feed selection and uses a fixed synthetic value. Those app integration
gates and the manual-snapshot decision in [PUBLICATION_PATH](PUBLICATION_PATH.md)
remain pending. #15's durable inbox is not connected to this experiment; its source
trust/ingress and D (draft application) remain separate work.
