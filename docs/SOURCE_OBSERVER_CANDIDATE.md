# Source-observer candidate v22 — #139 / #25

| Field | Candidate |
|---|---|
| Package | `se.tmm.sensorplanner.plugin` |
| versionCode / name | **22** / `0.1.0-source-observer.1` |
| ATAK target | `5.6.0.CIV`, unchanged; actual returned standard-host compatibility pending |
| Activation | Explicit isolated-loopback lab observation only; no startup listener |
| Persistence/transport | No new data fields, registry, source authority, send/import or acknowledgment |

The candidate includes the merged image descriptor/content foundations and the
[read-only observer preparation](INGRESS_OBSERVER_PROBE.md). None activates image
sharing or a production receive adapter. The new version distinguishes it from
[the actually signed/tested v21 candidate](SIGNED_V21_VERIFICATION.md); those runtime
results do not prove the v22 observer works.

Local debug/release assembly, lint, existing app unit tests, SDK signature/canary
checks and debug APK boundaries passed as recorded in the probe document. Local
release is unsigned. Do not install the debug APK over a service-signed candidate
or uninstall to work around a signer mismatch. The owner may submit a reviewed
source ZIP; inspect the returned signature/package/version before an update in
an isolated synthetic-data environment. Actual source commit/archive/APK hashes
are recorded separately when those artifacts exist. No service submission, standard
v22 load, observer callback/stack/unload trial or feed/source proof is claimed here.
