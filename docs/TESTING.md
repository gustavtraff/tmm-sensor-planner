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
| ATAK 5.5.0 plugin loading | Not run – SDK/developer app missing |
| Local map privacy / two clients | Not run |
| CoT round trip / image sharing | Not run |
| HV Reports / Data Sync coexistence | Not run |

Core CI is a public check. Android lint, APK and SDK builds will be added
when a permitted build environment exists, as a separate required check. Scanning before
publication must also cover history and artifacts, not just the working directory.
