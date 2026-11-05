# Signed v23 and native import-source verification — #161 / #25

Date: 2026-11-05. This records the owner's returned stage-foundation candidate
on an isolated synthetic lab. The returned archive/APK were not modified.

## Artifact and source association

| Field | Verified value |
|---|---|
| Package | `se.tmm.sensorplanner.plugin` |
| Version | 23 / `0.1.0-stage-foundation.1` |
| APK bytes | 603,743 |
| APK SHA-256 | `0cde5066cccd2aa703f3aeb0e49a89f9e6ebdf81e73568a8b15b4408759ffb9b` |
| Signing certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| Submitted source | `4e0ae4347df33ac006c8c967099029c9f48f11eb` |
| Submitted ZIP SHA-256 | `88cb24581a9262a749d81644d68ed78d555b94beaf62cb89675298843d7d3289` |

The leaf signer matches the accepted v21. `apksigner verify` and the current APK
boundary checker pass: expected discovery/API, no requested plugin permissions,
no packaged SDK/key artifacts. Existing warnings about unprotected META-INF
metadata entries remain. The service Gradle log reports `BUILD SUCCESSFUL in
3m 3s` and 106 task lines. These are owner/service artifact associations, not a
reproducible-build attestation or an operational release approval. The misleading
`unsigned.apk` filename does not override actual signature verification.

## Environment and preserved update

The retained synthetic A/B profiles use standard ATAK-CIV 5.6.0.25 (238484ee),
Data Sync 3.7.4 (58746181) [5.6.0], Android 15/API35 arm64 and the retained TAK
Server 5.8-RELEASE-92 lab. No phone or operational profile/server/data is involved.
The documented generic-EGL fallback is retained. A new empty profile was first
booted, then stopped after retained profiles were discovered; it is not the update
baseline reported below.

Both retained guests use a socket network with no peer, airplane mode, radios off,
first-rule guest IPv4/IPv6 non-loopback OUTPUT drops and dedicated ADB tunnels to
host-loopback TLS/HTTPS relay ports. The database/server have no published host
ports. Diagnostic Mission API GET retains CA/hostname validation and mutual TLS;
it is not a product REST publishing route. The service has no operational access.

A accepts `adb install -r` from signed v21 to v23 without uninstalling. Its actual
3,594-byte local frame remains byte-identical before update and after host startup:
SHA-256 `ceb0f9f8a726655aa74e8eaff84e64abe330182285ce913df36b4e566220f14d`.
Its actual 10,615-byte owned PNG also remains identical:
`4c3ee82aeb4f1cf19bc117bde915c8b4abdabdeafbac5de73f06230f102e9876`.
The map displays restored local TMM markers before subsequently opening the planner.
B accepts the same signed v23 as an update from v20; native Package Management
shows compatible, and explicit **Load** succeeds. A is already **Loaded**.

## Bounded source observations

The [explicit observer protocol](INGRESS_OBSERVER_PROBE.md) remains the authority
for scope: four fixed PUB18 UUIDs, bounded structural enums/counts only, no payload
or arbitrary key/value logging, no inbox/source writer and no source authentication.
The ordinary native importers remain in control.

- A's explicit Mini SDK import reports SUCCESS, one host UUID and an exact
  `CotEventFactory` metadata re-export. One observer callback has `extras=true`,
  `from=STRING_OTHER`, `serverFrom=ABSENT`, `endpointFrom=ABSENT`, two other keys,
  zero drops. A fresh-generation identical repeat gives the same result.
- B's **Download once** initially loads the selected feed listing with Mini still
  **Available**. Zero callbacks at that point are not receipt evidence.
- B then explicitly chooses the selected feed's Mini leaf and confirms its native
  **Download** dialog. Its row becomes a native Mission item. The active observer
  records one Mini callback: `extras=true`, `from=STRING_OTHER`,
  `serverFrom=STRING_TEST_ROUTE`, `endpointFrom=ABSENT`, two other keys, zero drops.
- A separate validating TLS harness sends the exact fixed Mini production envelope
  with fresh outer times. The server records the announcing ordinary control,
  but the eligible Mini callback is not observed. This is an unsuccessful positive
  control, not proof that live production-envelope ingress is absent or complete.

The selected feed independently contains one Mini event that passes the fixed
envelope verifier. A fresh native recipient **Export**, whose output did not exist
beforehand, produces a 1,528-byte ZIP. Its one actual CoT member passes the same
independent UUID/type/point/all-field verifier; member SHA-256:
`f224ab29aa22245ce2d33d85bd187cea3986a8dd2ed7905b535c07e1fe6f6d25`.
Its ZIP member time is 2026-11-05 14:31:24 in the guest's local time. It is not an
expected input fixture used as a substitute for receipt.

The observer's `serverFrom` equality is a route hint, not the
selected feed identity. The two uninspected keys remain unknown; their count does
not establish that no useful provenance exists. Identical callbacks/UUIDs cannot
bind original input variants to a feed or to each other.

## Lifecycle and incomplete controls

- With B stopped at one received callback, an explicit local Mini SDK import still
  succeeds and the observer's count/received-route classification stays unchanged.
- Normal native **Unload Plugin** while B's observer is active shows **Not loaded**.
  Compatible **Load** succeeds. Before any new activation, its observer is stopped
  with zero observations. A newly explicit start/local Mini import/stop again gives
  one local callback, no server/endpoint hint and zero drops.
- A starts an observation, opens its summary, then loses the airplane flag while
  the no-peer socket backend/radios/firewall remain isolated. **Stoppa och visa**
  refuses continuation; after restoring the flag, a fresh opening shows an invalid
  run, zero observations. The test uses an open control: the outer lab menu also
  correctly refuses entry while offline access is unavailable. A quiet guard
  loss/return between checks was not forced or claimed detected.
- A cold host restart while observation is active restores a stopped, empty probe;
  no observation is automatically resumed. Prior local/received observations are
  memory only.
- A checks only Mini in native Overlays and presses **Done** for the existing
  selected feed. An independent GET verifies one fresh event at
  `2026-11-05T13:33:09.234Z`; its metadata remains exact, and both other checked
  feeds still contain no Mini. B's cached/pending-download workflow produces no
  new eligible callback. This does not prove acceptance/coverage of that update.
- A separate explicit same-UUID second-feed attempt returns to the dashboard,
  but independent feed metadata and event GETs still show zero members/events.
  That native attempt is unsuccessful; no two-feed source comparison, original
  losing-variant coverage or conflict acceptance is inferred from a Done click.

## Disposition and next boundary

The exact signed candidate loads, preserves the tested current v2 data/image, and
supports the concrete local/native-receive and stop/reload/restart controls above.
This does **not** establish a supported complete Data Sync ingress API. Keep the
trusted selected-feed adapter blocked: the current probe cannot bind authenticated
feed identity and every original metadata variant. Route/classification counts
cannot fill that contract. Do not infer a source from UUID, owner, endpoint, order
or a current MapItem, and do not replace the ordinary importers to force callbacks.

The next #25 integration decision needs an actual supported native contract that
supplies selected-feed context plus original payloads, or a separately reviewed
server-Mission-API architecture. The latter has not been approved or implemented
by this diagnostic GET. Request version-applicable integration documentation and
test its original/conflict/recovery behavior before enabling the existing inbox.
Meanwhile #162 separately tests the documented native image-feed workflow; that
feasibility result cannot supply missing source/association authority.

Full same-UUID/two-feed conflict coverage, original
losing variants, arbitrary listener stacking/timing and physical 5.6.0.12/OEM
behavior remain separate gates. No trusted ingress adapter, automatic draft
application, image association/adoption, Data Sync image transfer or receipt follows
from these structural observations. Core/standalone image tests do not establish
these native compatibility claims.

At this slice's end, A's pulled frame and owned PNG still match the pre-update
bytes exactly. Both checked native crash buffers contain zero Java fatal entries.
Both probe sessions are stopped; the same bounded lab continues for #162 before
final service shutdown. Raw UI, frames, exports and logs stay private. No app/data
contract, build input or version changes, so no new signing ZIP is needed.

Actual checks here: signature/APK boundary verification, submitted-ZIP hash,
native actions and independent state/export comparisons, patch whitespace and
relative document links. No local core/image suite or SDK build is rerun for this
evidence-only change. Required PR CI remains a separate merge check.
