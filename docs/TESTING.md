# Tests and gates

`python3 scripts/test-github-auth.py` tests token verification using synthetic data
and without network requests.

sh scripts/test.sh checks domain validation and distances using synthetic
coordinates. Tests cover UUID identity, bearing limits, invalid coordinates,
time fields and sorting. They do not prove storage, map privacy or compatibility.

Acceptance tests A01–A10 are in project plan §7. A01/A06/A07/A08 require actual
clients, an isolated test server and documented traffic/recipient observation.
A02/A05/A09 will be introduced with storage, images and migrations.

| Test | Result |
|---|---|
| Plain Java core | Passed locally on 2026-10-12 with Temurin 17.0.20.1+1 / --release 8 |
| ATAK API 5.6.0 plugin loading | Not run – SDK directory 5.6.0.24 supplied; app/phone compatibility with reported build 5.6.0.12 not verified |
| Local map privacy / two clients | Not run |
| CoT round trip / image sharing | Not run |
| HV Reports / Data Sync / TAK Voice coexistence | Not run – reported versions: HV Reports 1.1.9 [5.5.0], Data Sync 3.7.4 [5.6.0], TAK Voice 2.1.0 [5.6.0] |

Core CI is a public check. Android lint, APK and SDK builds will be added
when a permitted build environment exists, as a separate required check. Scanning before
publication must also cover history and artifacts, not just the working directory.

The accepted API target is 5.6.0 ([ADR 0002](decisions/0002-atak-5.6-target.md), 2026-10-12).
Record exact SDK and client full build numbers independently of the API version.
The server version is unknown; it does not block a local build, but must be recorded
before server-dependent sharing tests. Reported installation versions are not test evidence.
