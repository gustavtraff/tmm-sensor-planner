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

## Exact reviewed signing source — 2026-10-31

Tracking: [#144](https://github.com/gustavtraff/tmm-sensor-planner/issues/144).
The private source archive was prepared from merged commit
`590bac7b9fe3a579164a60c877fd630cf7b87168` after PR #141. Later documentation-only
merges do not require another archive if the selected signing inputs are identical.

| Artifact | Evidence |
|---|---|
| Filename | `TMM-Sensor-Planner-source-main-590bac7.zip` |
| SHA-256 | `ed7fe5ea8466ce88fe832eab79bbce30866f05cdd895a72196848688fc6f71c8` |
| Contents | 197 ZIP entries, one `TMM-Sensor-Planner/` root, executable wrapper mode |
| Boundary check | Five `scripts/test-signing-source.py` tests passed |
| Extracted-source build | Local SDK 5.6.0.24, offline `:app:assembleCivRelease`: passed, 81 executed tasks |
| Release inspection | Package, version 22/name, ATAK API/discovery, absence of requested permissions and SDK/key packaging assertions passed |
| Signature | Unsigned local release; `apksigner verify` rejected it with missing `META-INF/MANIFEST.MF`, as expected |

The build used JDK 17, Gradle 8.14.3 and the supplied local SDK route, from a fresh
extraction without Git metadata. Only the wrapper's executable filesystem mode
was restored after Python extraction; no source was patched or added. Local SDK
configuration and caches remained outside the ZIP. The private unsigned APK has
SHA-256 `892801bbe4d85db51edaa64fa86ebb4e7dadf97092fdce914a28c99a0e904352`;
it is a preflight artifact, not a phone installation candidate.

This does **not** establish restricted Maven preflight, service acceptance, returned
signature trust, standard-host loading or any new runtime/network result. The
archive contains reviewed build inputs and no SDK or signing keys; the generator's
bounded packaging checks are not a general secret scanner. The archive inventory
and selected-source diff were reviewed against the previous submitted candidate.
No signing upload or release was performed.

### Owner handoff and next verification

1. Review the filename/hash above and submit that ZIP to the third-party service
   if desired. Keep the earlier submitted v21 archive unchanged.
2. Save the complete returned download privately under the established TAK
   resources directory. Inspect the actual APK's package, version, API, signature
   and signer continuity before updating the isolated lab clients. Stop on a signer
   mismatch; do not uninstall to bypass it.
3. Run the explicit [observer protocol](INGRESS_OBSERVER_PROBE.md) on the standard
   host with synthetic data and known loopback context. Check positive controls,
   callback summaries, stop/unload behavior and stacking. Record failures as well
   as observations. No callback hint is feed/source authority.
4. Decide the next source-integration step from that evidence. Image staging,
   association, receipts, actual Data Sync image transport and operational phone
   sharing remain separately gated. This candidate does not activate them.
