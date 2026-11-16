# GET-only Mission overview lab candidate v24 — #167 / #164

The owner approved the [bounded prototype](MISSION_READ_PROPOSAL.md) on 2026-11-05.
This implements that lab experiment, not a production HTTP receive architecture.
Package/API are unchanged; candidate version is **24 / 0.1.0-mission-read-lab.1**.
The accepted signed v23/source archive are left unchanged.

## Explicit scope and UI

In **TMM CoT-labb → Mission API · läs labböversikt**, explicitly select either
`tmm-ds25-selected-20261023` or `tmm-image162-20261105`, review, then choose
**Läs labböversikt**. There is no startup request, arbitrary URL/feed input,
subscription change, background retry, publisher, inbox/source registry, draft
mutation, image retrieval/association/adoption or acknowledgment.

Activation requires API26+, the existing offline/lab policy, exactly one connected
enabled stream at `127.0.0.1:8089:ssl`, and the default comms provider without its
alternate MISSION_API feature. The only constructed request is GET to the fixed
`https://127.0.0.1:8443/Marti/api/missions/<selected fixed name>` URI. Any different
SDK URL resolution is rejected. Operational/connected-local mode cannot enable it.

The worker consumes at most 65,537 stream bytes to enforce a **65,536-byte** body
cap, requires a complete UTF-8 JSON overview through EOF, bounds nesting/string /
array sizes, checks exact selected name and canonical nonzero GUID shape, and
returns only UID/resource **array counts**. It retains no returned identifier,
body, owner, path, image or other values. Counts do not validate each member or
establish original metadata, authorization scope, feed identity or a receipt.

## Verified SDK details and conservative controls

Actual SDK 5.6.0.24 public signatures and relevant method bodies were inspected;
raw vendor material remains private. The convenience `TakHttpClient.GetHttpClient`
appends its API port and `/Marti`, uses a cached factory whose default mode allows
all hostnames, and can delegate execution to an alternate comms provider. The
prototype deliberately does **not** use that convenience TLS factory.

It requests a fresh host-managed `CertificateManager.getSockFactory(false,
NetConnectString, false)`: cache disabled, strict-hostname mode, selected lab
profile key managers managed by the host. No key, password, certificate byte or
trust override is copied into plugin state. The supplied factory's inspected
strict socket path calls `STRICT_HOSTNAME_VERIFIER`. This code inspection is not
an observed TLS handshake or invalid-CA/hostname rejection on the standard host.

The explicit SDK client constructor uses that factory, exact API base and 5-second
connection/read settings. Request controls disable redirects and automatic HTTP
authentication, request identity encoding, cap header count/line length and reject
non-200, unsupported content type/encoding or excessive declared length. The body
cap applies independently of a missing/incorrect Content-Length. The Android
optional Apache library is declared non-required; actual host loading is a runtime
gate, not proven by private compile stubs.

One worker with **no queued requests** and one timer service belong to the probe.
At 12 seconds cancellation/abort is requested independently of the UI; monotonic
deadline checks also reject late reads/results. Cancel, Android Back, Unload and
connected-local consent invalidate the attempt; late resource/future registration
is cancelled too. A new request is rejected as busy while the old worker unwinds.
Response/client cleanup runs before a result is posted; failed cleanup is not
reported as a successful overview. No raw exception/provider messages are shown.

Loopback/host guards are also polled through the UI every 250 ms and rechecked
before presenting a result. This is observational, not atomic route/provider
revocation: a stalled UI or dynamic provider replacement is not a verified safety
case. Use the fixed, independently isolated lab with no provider replacement during
the run. Cancelling cannot guarantee instant termination of blocked native code;
the bounded worker remains unavailable rather than accumulating new tasks.

## Checks and remaining native gate

Focused tests pass **17 cases**: 12 API26/35 response-reader cases and five read-
lifetime cases. They cover actual byte/structure limits, malformed/truncated UTF-8 /
JSON, wrong name/GUID/arrays, complete EOF, zero-progress reads, caller-owned input,
generic provider errors, cancellation at EOF, deadline wrap and concurrent late
registration/cancellation, including abort failure and no resource rebinding.
The full image/helper suite passes **377 tests, zero failures/errors, one skip**;
image lint and standalone instrumentation APK compilation pass. The existing
standalone fixture-APK check is separate from loaded-ATAK behavior.

Actual local supplied-SDK debug/release assembly, app lint and three app unit tests
pass. The expanded SDK signature/canary check passes without constructing an HTTP
client/request or making a network call. These checks do not run the new UI,
configured-profile TLS, HTTP headers/redirects or actual cancellation in ATAK.

Before standard-host acceptance, obtain a returned service-signed v24 and verify
package/API/signer continuity. Never install a debug APK over the accepted signed
plugin or uninstall to bypass a mismatch. In isolated synthetic clients check:

1. Review Cancel/Back causes no request; explicit positive overview counts match
   an independent authenticated server read for each selected feed.
2. Bad CA/hostname, denied/redirected/compressed/malformed/oversized responses and
   wrong feed fail without trust overrides, writes, raw errors or false receipts.
3. Slow/trickled reads, Cancel, guard loss, native Unload/reload and restart suppress
   late results and do not automatically resume. Before/after positive controls work.
4. Local draft/image bytes stay exact; only task-owned lab resources are stopped.

These source/build checks alone do not establish native acceptance. Stable
mission GUID scope, original metadata/history, two-feed same-UUID separation and
recovery remain subsequent experiments; no snapshot-import semantics are silently
adopted. Image registry/container/receipt format writers remain separately gated.

## Bounded SDK-developer-host experiment — 2026-11-05

One new headless Android15/API35 arm64 guest uses SDK developer ATAK-CIV 5.6.0.24,
not the standard 5.6.0.25 clients. Retained standard-client profiles are untouched.
The supplied SDK app fingerprint remains the one recorded in DEVELOPMENT.md.
Native compatible-plugin Load accepts the final development-signed v24; tested
debug APK SHA-256 is
`01ec963a411a8487589eb94335364724587007e235c69c4391672ed90d3ca99d`.
This is not a service signature or standard-host installation result.

The guest has a socket network with no peer, radios off/airplane mode, guest
IPv4/IPv6 non-loopback OUTPUT drops and only explicit lab 8089/8443 ADB tunnels.
It imports the existing synthetic profile through native Local SD → Copy, using
the previously approved test-only EULA/permissions. No operational data/account,
phone, private-plugin API or TLS trust override is introduced. Existing isolated
TAK Server 5.8-RELEASE-92 and its private CA/profile are reused.

- Native review Cancel returns without starting the reader in the observed UI;
  no independent per-request server absence window was measured for this Cancel.
- Explicit image-feed read succeeds through the actual configured-host TLS client:
  **0 UID posts / 2 resources**, exact name/GUID-shape checks, count-only result.
  A separate CA/hostname-validating authenticated server GET gives the same counts.
- Explicit metadata-feed read returns the generic unverified result. An independent
  GET returns 9 UIDs / 0 resources and a 2,850-byte overview with canonical GUID,
  shallow structure and bounded strings. This is an **unsuccessful control**;
  transport/status/response handling has not been isolated as its cause. It is not
  evidence that the feed is absent, that access is denied or that its payload is bad.
- A temporary fixed-stage diagnostic build cannot be installed: Android's package
  service returns `Broken pipe (32)`. That diagnostic is not tested or retained in
  this candidate. The reviewed code is restored and exact final debug/release,
  lint and app checks rebuilt. Do not attribute the earlier failed read to that
  later package-service failure without evidence.
- The held TMM-private baseline file remains byte-identical after the controls;
  directory additions were not independently enumerated at shutdown. The reader
  has no draft/image write caller. This fresh profile does
  not test preservation of a populated signed v23 dataset. The final checked crash
  buffer contains zero Java fatal entries; airplane mode is still enabled.

Wrong-host/CA rejection, redirects, authorization denial, live slow-body deadline /
cancel/unload behavior and standard-host v24 loading remain unrun. A synthetic
wrong-host certificate was prepared privately but never served; no negative TLS
pass is inferred from preparation. The host-helper bounds/race tests remain
separate deterministic evidence, not those native timing cases.

The test emulator/dedicated ADB are stopped. Lab Java roles receive TERM and
PostgreSQL completes a clean fast shutdown before container wrappers stop. Raw
profiles, certs, UI, logs and response bodies stay ignored outside Git. A private
owned-session deadline guard is cancelled after verified manual cleanup. #164
remains open for the failed metadata control and complete source/runtime evidence.
