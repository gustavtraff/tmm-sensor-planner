# Production-format SDK envelope experiment — #14

Version **18**, `0.1.0-publication-envelope.1`, based on main `510558f`.
This implements the next synthetic SDK-path candidate, not a saved-draft publisher.
The approved [metadata XML v1](PUBLICATION_FORMAT_PROPOSAL.md) is carried unchanged
inside a CoT envelope. The old `tmmShareLab` experiment remains available unchanged;
its signed runtime evidence does **not** establish preservation of the new tag.

## Boundary and representation

`PublicationCot.create(prepared, label, now)` constructs an SDK CotEvent in memory.
It neither creates a MapItem nor imports, dispatches, stores or selects a feed.
It reuses `PublicationCodec.fields(prepared)`, the same read-only fifteen-field
projection used by the standalone encoder. No field or storage schema is added.

The envelope UID must equal the inner `deviceUid`. The outer `b-m-p-s-p-loc`
sensor-point type is the already tested transport representation for **all four**
TMM types. The exact Mini/Scout/PIR/Gateway identity remains in the payload; the
native symbol is not the private planner's type/status artwork. This experiment
does not decide the final native icon policy. `archive` and `contact` accompany
the production detail. The bounded, separately supplied host callsign is a display
label, never the device identity, source authority or a rewritten display ID.
Unrepresentable labels fail rather than being truncated or silently renamed.

SDK outer point serialization uses seven fractional decimal places. The adapter
sets that rounded point explicitly and requires it to agree with the exact
latitude/longitude in the metadata. It never rounds the metadata. CoT time/start
and one-hour stale are envelope values supplied at construction; they do not
replace creation/update/deployment instants or establish durable feed retention.

`read(event)` accepts only a valid sensor-point event, one immediate new-tag
payload, the exact inner UUID/outer point correspondence and a successful bounded
core decode. It rejects mixed immediate old/new tags, duplicate payloads, children,
extra attributes and unsupported profile values. Immediate detail children are
capped at 64; each attribute is bounded before XML construction, and both temporary
XML and decoded metadata are bounded. Errors have fixed wording and no raw cause.
`matches(event, prepared)` then compares the exact reviewed wire values. It is a
lab verification check, **not** InboxStore's metadata equality/deduplication rule.

The adapter receives an already constructed SDK object. It does not authenticate
its source, validate original raw XML framing, recover namespace declarations the
SDK has discarded, infer feed membership, establish freshness or write an inbox.
It must not be wired as a trusted production receiver without a separate ingress
contract. An independent raw-XML checker covers the fixed lab canaries below.

## Executable synthetic candidate

Choose **TMM CoT-Labb → Produktionsformat · syntetiskt prov**, then one of the four
types. The existing loopback/airplane-mode lab guards and access policy must pass
both before review and immediately before confirmation. Connected-local-trial
consent continues to disable all TMM sharing experiments.

Only `PublicationEnvelopeFixtures` is read. No saved draft, image, file, account,
feed context or inbox input is accepted. Cancel/Back creates nothing; switching
lab modes, the connected-local-trial transition and Unload close pending reviews.
Confirmation consumes the pending review before the SDK operation, preventing a
second pending callback from importing it again.

After explicit **Importera syntetisk kartkopia**, the experiment calls the same
verified public `CotImporterManager.importData(CotEvent, Bundle)` path as v16,
using its own `se.tmm.sensorplanner.plugin.publicationlab` source tag. It refuses
to replace a different existing object with the canary UUID. It counts actual host
UUIDs and checks the actual CotEventFactory re-export against the reviewed values.
The reported SDK result and exact-host-copy check are distinct from feed acceptance
or recipient acknowledgment. No direct send, guessed destination, private Data Sync
action, fake stock identity, rollback or automatic retry is introduced.

Normal host copies may persist after Unload/restart or be published by other host
components. The review warns about that before confirmation. An ambiguous import
requires inspection of the map and both feeds before another attempt. The normal
saved-device **Förbered metadatakopia** action remains private and memory-only;
it is not connected to this experiment or to the adapter's import operation.

## Independent checks

[Four separately encoded fixtures](../test-fixtures/synthetic/publication-envelope/README.md)
pin exact expected values and hashes. All positions/text are synthetic. The raw
checker uses standard JDK DOM rather than the production SAX decoder, validates
outer UUID/type/rounded point and every metadata attribute, rejects DTD/external
resources and bounds its returned input to 1 MiB with depth 32. It only checks
these fixed cases; it does not import or serve as a general-purpose receiver.

```sh
sh scripts/publication-envelope-probe.sh self-test
sh scripts/publication-envelope-probe.sh check mini /private/path/actual-export.cot
# Cases: mini, scout, pir, gateway. JAVA_HOME must name the documented JDK.
sh scripts/test-publication-sdk.sh
```

The offline checker self-test also runs in `sh scripts/test.sh` and thus public
core CI. It uses in-memory independently assembled envelopes and rejection controls.
The SDK check constructs the actual app canaries in both en and sv locale JVMs,
serializes actual SDK objects and passes their bytes to that independent checker.
It additionally rejects UID/type/tiny-point drift, duplicate/mixed tags, children,
extra image fields, bad schema/units/UTF-8, excessive values/detail count and invalid
host labels. Valid changed metadata fails the exact-match check; changed host
label/envelope time does not falsely change metadata identity.

## Local verification — 2026-10-26

- `sh scripts/test.sh`: passed, including existing snapshot/inbox/restart/100-device
  suites, byte-identical production codec fixtures and immutable field projection.
- `sh scripts/publication-envelope-probe.sh self-test`: passed.
- `sh scripts/test-publication-sdk.sh`: passed both locale runs with the actual
  SDK objects and independent four-canary checks; legacy six-case checks still pass.
- `sh scripts/check-sdk.sh`: SDK MGRS/publication checks, CivDebug assembly/lint,
  existing app unit tests and static APK packaging checks passed.
- `./gradlew --no-daemon :app:assembleCivRelease`: passed, including release
  shrinking. This is a local build, not a service-signed APK.

SDK main.jar SHA-256:
`5a111497ca5722c5e0dfdca4a7e5852fa68400b6723038eba7fbc657cc5c8d9b`
(supplied directory labeled 5.6.0.24). Compilation uses the documented JDK 17,
Android API 36 stubs and existing Gradle configuration. No SDK CotEvent.parse
execution is claimed on the plain JVM; the known Android Log stub limitation is
unchanged. No emulator, Docker, image suite, service upload or CI wait was run.

## Required signed runtime gate

After human merge, prepare a new source ZIP and obtain a signed v18 APK. Keep the
existing two-client lab exclusive to agent 1. Agent 2's #15 storage/format work
needs neither emulator nor server and changes no file owned by this slice.

1. Inspect the returned version/signature/package, install as an update on both
   standard 5.6.0 lab clients and confirm the baseline/export/observer controls.
   Check private preparation's newly signed dialogs separately; no host object
   may result from that saved-device action.
2. Use separate selected/comparison feeds. Comparison auto-publication must be ON
   with its prompt OFF and an independent native control must prove it works.
3. For **each** fixed type, Cancel/Back first, then create the host copy. Inspect
   the actual host export and both feeds/recipient before native Done. Stop if
   the unselected comparison feed receives it; do not rename the importer source.
4. Exercise actual native selection/Cancel/Done. Check actual fresh client-B
   re-exports with the corresponding offline checker, not an expected local file.
   Inspect comparison feed absence and repeat UUID behavior on both clients.
5. Repeat pending-review Unload/reload/restart controls and inspect private snapshot
   preservation and canary leaks. Record exact versions, observations and limitations.
   Stop the owned lab cleanly after the run.

A passing compile/checker result is not this runtime test. #14 remains open for
real-draft handoff, the accepted manual-snapshot destination/freshness limitations
and truthful result handling. #15 ingress/application and #26 image transport are
separate. This PR requests review of a synthetic experiment, not operational sharing.
