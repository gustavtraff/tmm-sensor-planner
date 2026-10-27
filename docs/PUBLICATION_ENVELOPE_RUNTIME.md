# Signed production-format envelope trial — 2026-10-26

Tracking: #14. **Bounded result: all four fixed production-format canaries passed
actual signed SDK import, native feed selection and independent recipient export.**
No canary entered either feed before native Done. After Done the selected feed
held each UUID once, with exact metadata; the automatic comparison feed held none.
This is evidence for the synthetic `tmmPublication` route, not an enabled
saved-draft publisher, trusted receiver or application acknowledgment.

## Candidate and environment

The owner supplied the service result for merged source main
`f1b8570a1ca2b2d76e251d8a7611d9baaa98ee95` after #102/#103.
Prepared source ZIP SHA-256:
`e5bb1e3ada816e02c767d65f1bee1ba97b828bdbd585a6eed738efda4694b477`.
Returned APK SHA-256:
`2c04dffeb19ef771e34a91ce79262eabe7e59bb65d1f056951fbc84d0f21d0cf`.
Despite its unsigned filename, apksigner verifies the service signature, whose
certificate SHA-256 matches the earlier installed trial:
`f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17`.
Static discovery, permission, registration and packaging checks passed.

Both existing clients accepted the update and actually ran:

| Component | Exact tested version |
|---|---|
| TMM | `se.tmm.sensorplanner.plugin`, versionCode 18, `0.1.0-publication-envelope.1` |
| Standard ATAK-CIV | `5.6.0.25 (238484ee)`, versionCode 1792077359 |
| Data Sync | `3.7.4 (58746181) - [5.6.0]`, versionCode 1769093904 |
| Guests | Android 15 / API 35, arm64-v8a |
| Isolated server | Existing TAK Server 5.8-RELEASE-92 |

Only the two owned headless guests and existing server/database/relay containers
ran. Guest IPv4/IPv6 output allowed loopback only; Wi-Fi/mobile data were disabled
and airplane mode enabled. Task reverses connected the existing synthetic profile
to the local TLS and HTTPS endpoints. No operational connection/profile, real
position/image or physical phone was used. CA/hostname checking and mutual TLS
were retained. No signature bypass, stock importer identity or private Data Sync
API was substituted.

Initial API warm-up EOFs were not counted as readiness. ATAK initially occupied the
guest HTTPS port before its tunnel was bound; both clients were fully stopped,
the correct existing profile mappings were bound and ATAK restarted. The active
cases began only after authenticated API readiness, actual subscriptions and a
fresh native automatic-publication control were verified. These setup attempts
are not passed transport tests or an assertion of complete startup coverage.

The [v18 candidate](PUBLICATION_ENVELOPE.md) uses the actual public
`CotImporterManager.importData(CotEvent, Bundle)` with its own
`se.tmm.sensorplanner.plugin.publicationlab` source tag. The approved fifteen-field
payload is not the old `tmmShareLab` experiment. Independent fixed expectations
are in [the canary fixtures](../test-fixtures/synthetic/publication-envelope/README.md).
All four use Planned status; this is not an exhaustive runtime test of every
status, optional field, coordinate boundary or host/OEM combination.

## Controls and actual observations

The first baseline at **20:37:01 UTC** showed zero members/events for every new
UUID in both feeds. Observations used fresh API reads, not expected fixture files.
The selected feed's auto-publication was verified OFF. Comparison auto-publication
was ON and Publish Prompt OFF. Both clients were subscribed. A fresh native
`DS18-ENV-BEFORE 1` marker reached the comparison automatically before the cases;
`DS18-ENV-AFTER 1` independently did so after the repeats. B's comparison-only
view showed the first native control and no new canaries.

Each case exercised TMM review Cancel, Android Back and a new explicit import.
The actual app reported SDK SUCCESS, one matching root UUID and an exact
CotEventFactory re-export for every type. Feed readbacks after the imports remained
zero for all four. The Mini Cancel had its own separate zero readback; do not
interpret the other post-import readbacks as individual measurements after every
intermediate Cancel/Back action.

| Check (UTC, 2026-10-26) | Actual result |
|---|---|
| Mini Cancel, 20:43:30 | All four absent from both feeds. |
| Mini import, 20:44:14 | One exact host copy; all four still absent from both feeds. |
| Scout import, 20:45:13 | One exact host copy; all four still absent from both feeds. |
| PIR import, 20:46:16 | One exact host copy; all four still absent from both feeds. |
| Gateway import, 20:47:18 | One exact host copy; all four still absent from both feeds. |
| Pending Mini review, cold restart; ready readback 20:48:03 | No pending review resumed in the observed UI; all four remained absent from both feeds after startup completed. |
| Pending Mini review, normal Unload; 20:48:54 | TMM actually Not loaded; both feeds remained empty of the four. Normal compatible-plugin Load then succeeded without resuming a review. |
| Four checked native leaves, selection cancelled; 20:50:41 | Both feeds still empty of the four. Custom native checkbox images were visually checked; generic accessibility checked flags were not used as proof. |
| Same four selected, explicit native Done; 20:51:52 | Each UUID appeared once in the selected feed, with one exact returned event. Comparison still zero. The older experimental PIR was left unchecked. |
| Repeat each SDK import; 20:56:43 | Each reported SUCCESS, one UUID and exact host re-export; each selected-feed membership/event count remained one, comparison zero. Stored server envelope times also remained unchanged for these identical repeats. |
| Final restored readback, 20:58:44 | Selected feed nine total members including the four new canaries; comparison six native controls and zero new canary members/events. |

The native selection list after cold restart/Unload retained all four copies.
Neither operation revokes ordinary host or published feed content. Identical-repeat
results do not establish changed-metadata update/conflict behavior or guarantee
that a previously associated host copy will require a fresh native Done.

The independent CLI passed on **all four actual server events** after Done.
It checks outer UUID/type/SDK-rounded point plus every exact metadata attribute,
including Unicode/newlines, nanoseconds and unrounded domain position/bearing.
No source authentication, source-context mapping or recipient acknowledgment is
inferred from those values.

## Fresh recipient-side exports

B's actual selected-feed list received all four items. Each row's native **Export**
action generated a new ZIP containing one CoT event. A pre-export directory check
showed no `TMM-PUB18*` outputs. ZIP-entry/outer times are fresh and raw ZIPs/CoT
were captured privately; no expected payload was substituted for recipient data.
A private helper initially misreported Mini's existence check; its already-created
fresh export was captured, the helper corrected, and the remaining exports checked
with direct process exit status. No existing file was overwritten to manufacture
a pass.

`sh scripts/publication-envelope-probe.sh check CASE FILE` passed on every B event:

| Type | B event time (UTC) | Actual B event SHA-256 |
|---|---|---|
| Gateway | 20:53:18.587 | `dbf291b715c3998e1bf8d42032fd603b03b7b12d94e5990bbfb93ab8e3184ed6` |
| Mini | 20:53:55.327 | `f5aa97197c5f34d8b2f94e044397d06c50850fd5357470a0e8ce70ceb5c87180` |
| Scout | 20:54:28.712 | `2d1f12710104d756e525410b2c09f94ac0a2e3252c7d45f1dbac4af10a57d26a` |
| PIR | 20:54:34.356 | `84f3e17c1b9a16bbb7650c9fce14f491154e864a7791263f46d1fd7634f8cf70` |

This proves B-held native feed content, not a TmmDraft/inbox import or a TMM-level
receipt. The common native sensor/camera icon is still a transport representation;
it does not implement type-specific native presentation. Private TMM symbols are
unchanged. This trial does not test image delivery.

## Signed private preparation and privacy

The signed standard host also exercised **Förbered metadatakopia** on an existing
synthetic private draft with a saved local image. Review displayed image exclusion
and no selected feed. Cancel returned to the device. A fresh **Förbered lokalt**
succeeded with the actual **Metadatakopia förberedd**, memory-only/no-send result;
closing returned to the underlying device/list. This is a bounded native-host UI
check, not repetition of every Robolectric failure/race case or phone acceptance.

Both saved snapshot files were byte-identical before install and after the entire
trial, including their image associations:

- A: `4c75346b8ad3e412c4da96c49021b8e49b48649160f9a9b39a149fd42a69f89e`.
- B: `ca334be7239ebd2138db9571b3799168bbb0708e955b79e9b1d7c422dc5baf8f`.

The final bounded scan at **20:58:45 UTC** checked 108 router rows after baseline
3082, 25,082 observer bytes / 38 complete events, 25 private canaries / five private
positions, and the recorded selected/comparison/read-only/unused feed and recent
change resources. It found **zero canary/position matches and zero position-parse
errors**. Independently injected before/after TLS controls were present in the
observer. The complete retained stream contained no new-tag payload or new-canary
UID prefix. Native feed transfer is a different channel from routed CoT broadcast.
These checks do not inspect every client database, all possible encodings or an
unbounded history, and they do not establish production-network privacy.

## Cleanup and disposition

Comparison auto-publication OFF, Publish Prompt ON and B's original non-feed view
preference were restored with readback. Final client crash buffers had zero
FATAL EXCEPTION entries. Only task reverses were removed; both owned guests and
the observer were verified stopped. The TAK JVMs received TERM and the server
container exited 0. PostgreSQL completed a verified `pg_ctl -m fast -w stop` before
stopping its wrapper. Relay/database wrappers exited 137; this does not mean the
PostgreSQL engine was killed before its clean shutdown. All three owned containers
were verified stopped. Synthetic content/volumes and raw evidence are retained
privately; no resource or data from another task was removed.

This evidence-only change modifies no app, schema, build input or version and
requires **no new signing ZIP**. No full image suite, Gradle build or CI wait was
needed for it. Source/build tests were already recorded for #103 and repeated core/
SDK/archive checks on merged main before signing; they are separate from these
actual runtime results.

The new-tag/all-type synthetic SDK/native-feed gate now has evidence for human
review. **#14 remains open** for a user-facing real-draft handoff and its explicit
manual-snapshot destination/freshness/result limitations. Native Done has no verified
TMM callback. Do not silently turn successful synthetic imports into an operational
Share action or infer a changed-host-copy policy from identical repeats. #15 still
owns trusted ingress, local application/concurrency and durable history; #26 owns
images. The reported standard 5.6.0.12/Android-14 physical phone and the owner's
actual server/companion combination remain separate acceptance targets.
