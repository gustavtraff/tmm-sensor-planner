# Source-observer and stage-foundation candidate v23 (#154 / #25 / #26)

**Returned artifact, 2026-11-05:** [signed v23 verification](SIGNED_V23_SOURCE_VERIFICATION.md)
records signer continuity, preserved update bytes, actual native import observations
and lifecycle controls. Trusted feed/original-variant ingress and image sharing
remain unverified; the preparation record below is not operational acceptance.

| Field | Candidate |
|---|---|
| Package | `se.tmm.sensorplanner.plugin`, unchanged |
| versionCode / name | **23** / `0.1.0-stage-foundation.1` |
| ATAK API | `5.6.0.CIV`, unchanged |
| Active lab addition since signed v21 | Explicit guarded read-only source observer from v22; no startup listener |
| Image storage | Approved private unassociated stage-v1 foundation, with no UI/ingress caller |
| Maintenance | Exact one-shot discard backend for unassociated copies; no cleanup UI/automatic deletion |
| Existing state | Local/inbox/v2 and active-image formats unchanged; no automatic stage initialization |

This combines the [unchanged source-observer preparation](INGRESS_OBSERVER_PROBE.md)
with the implemented [descriptor/payload boundary](RECEIVED_IMAGE_PAYLOAD.md) and
[private stage](IMAGE_STAGE_STORAGE.md), including the inactive
[reviewed discard operation](IMAGE_STAGE_MAINTENANCE.md). These have focused fault/concurrency
tests, actual Android process-kill/guest-reboot and synthetic Full-HD readback
evidence. Those standalone results do not establish loaded-ATAK/OEM behavior.
No trusted source/feed authority, association/receipt registry, cleanup/adoption UI,
background image fetch or Data Sync image transfer is activated.

The distinct version supersedes the prepared v22 source recommendation. Earlier
ZIPs and their hashes remain immutable. This session found no returned signed
v22/v23 APK locally and established no standard-host observer result. Keep SDK/local build,
restricted Maven access, service build/signing and actual runtime proof separate.

## Review and owner handoff

Prepare the exact reviewed merged source with the existing
[archive boundary procedure](SIGNING_SOURCE.md). Record its commit and ZIP SHA-256;
build a clean extraction through the supplied local SDK route before handoff.
That preflight does not establish restricted Maven/service acceptance. The archive
and local APKs stay private; no upload or release is performed by preparation.

1. Submit the exact reviewed source ZIP to the third-party service if desired.
2. Keep the complete returned download under the established TAK resources path.
   Inspect actual package/version/API/signature, discovery, permissions and packaging,
   and compare signer continuity before updating the isolated lab. Stop on mismatch;
   do not uninstall to bypass it or install a local debug APK over a service-signed one.
3. Run [the existing source-observer controls](INGRESS_OBSERVER_PROBE.md) with fixed
   synthetic PUB18 canaries, known loopback route and independent native feed/variant
   observations. A hint classification or matching UUID is not a source namespace.
4. Decide the next #25/#26 source/association step from that evidence. Phone/operational
   online use and actual image sharing require their own acceptance; source signing
   alone activates neither.

Current backlog and the active sequence are summarized in [BACKLOG](BACKLOG.md)
and [NEXT_STEPS](NEXT_STEPS.md). Exact local build/archive results are recorded
separately once those artifacts exist, without rewriting older evidence.


## Local preparation verification — 2026-11-05

Supplied-SDK 5.6.0.24 debug/release assembly, debug lint and all three existing app
unit tests passed (167 executed tasks; lint zero errors/57 warnings). The actual
debug APK's versionCode 23/name, signature/discovery/API/no-permission/packaging
boundaries passed. The source-archive boundary suite passed all five tests. Local
release remains unsigned; no service build, standard-host load, live observer or
image-feed trial was performed. The source archive's clean-extraction preflight
and exact hashes follow separately after reviewed merge.


After integrating the reviewed maintenance and clean-probe build fixes, the complete
v23 candidate again passed debug/release assembly, app lint, three app unit cases
and actual debug APK checks (168 tasks: 36 executed/132 up to date). Actual version
23/name were re-read from the APK. The four standalone probe assets are absent from
the production debug APK; they belong only to the separate instrumentation app.
No standard-host, phone, source/feed or service-signing result is inferred.


## Exact reviewed source archive — 2026-11-05 (#154)

| Field | Recorded result |
|---|---|
| Source commit | `4e0ae4347df33ac006c8c967099029c9f48f11eb` (merged PR #155) |
| ZIP | `TMM-Sensor-Planner-source-main-4e0ae43.zip` |
| ZIP SHA-256 | `88cb24581a9262a749d81644d68ed78d555b94beaf62cb89675298843d7d3289` |
| Archive shape | 206 entries; one `TMM-Sensor-Planner/` root; executable wrapper mode verified |
| Archive boundary tests | Five tests passed; no SDK/configuration/signing material added |
| Exact extracted-source build | Offline supplied-SDK `:app:assembleCivRelease` passed, 81 executed tasks |
| Actual extracted APK | Package/version23/name/API/discovery/no-requested-permission/packaging assertions passed |
| Test separation | Four standalone descriptor/PNG probe assets absent from production APK |
| Signature | Local release actually unsigned; `apksigner verify` rejected missing `META-INF/MANIFEST.MF` |

The archive was prepared from committed reviewed main, then freshly extracted with
no Git metadata. Only the wrapper's executable extraction mode was restored; no
source or local property file was patched/added. JDK17/Gradle8.14.3/AGP8.13 and the
external local 5.6.0.24 SDK route were used. This is not a credentialed Maven/service
preflight. The actual selected-source delta from the prior v22 archive was reviewed:
new stage/maintenance code, synthetic tests/probe inputs and version metadata.
No keys, operational images/locations, server packages or user configuration belong
to this source archive. Boundary checks are bounded checks, not a general secret
scanner. The prior v21 and v22 ZIPs were rehashed and remain byte-identical.

The ZIP and local APKs stay private. **No service upload, signed v23 return, phone /
standard-host observer run, release or operational sharing was performed.** The
owner may submit this exact ZIP/hash for signing, then return the complete result
under the private TAK resources directory for package/signer/runtime inspection.
Local unsigned/debug APKs are not update candidates for a service-signed phone.

A later documentation-only merge does not require another ZIP when the explicit
signing-input files remain byte-identical. Preserve this commit/hash pair instead
of recreating archives solely to follow evidence documentation.
