# Signed version 15 connected-private verification — 2026-10-23

Tracking: #4 / #7; A01 / N03. This records a bounded standard-binary CoT trial
under [CONNECTED_LOCAL_TRIAL.md](CONNECTED_LOCAL_TRIAL.md) and the first gate in
[NEXT_STEPS.md](NEXT_STEPS.md). No product code, transport, schema, permissions,
signing settings or release artifact was changed. Human acceptance remains required.

## Exact candidate and environment

| Component | Verified value |
|---|---|
| Candidate source | Reviewed main `b643ee633cee2b9d16c4333fa7f14b7a0931622b` (PR #73) |
| Documentation baseline | Main `9a527bbd886e27a40f9ee73073ba5fe3597999a2` (PR #75) |
| TMM package, on both clients | `se.tmm.sensorplanner.plugin`, versionCode **15**, versionName **0.1.0-device-details.1** |
| Signed APK SHA-256 | `221017dd9f2eeb8d5c46ab2fd1db235f2644059f480fe7874865dc4e8fdd7645` |
| Signing certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| Standard host, on both clients | ATAK-CIV **5.6.0.25 (238484ee)**, `com.atakmap.app.civ`, versionCode `1792077359` |
| Host APK SHA-256 | `5bada2d865f937bbcb295bcf08760a8bd4765902c91dc5511738d480c0b6b8c6` |
| Android, on both clients | **15 / API 35**, AOSP arm64-v8a image revision 2, security patch **2024-09-05** |
| Android full build | `Android/sdk_phone64_arm64/emu64a:15/AE3A.240806.019/12368160:userdebug/test-keys` |
| Emulator | Android Emulator **37.2.12.0**, build **16428233**; two new, separate 1920 × 1080 / density 160 guests, 2 GiB RAM each |
| TAK Server | Existing isolated **5.8-RELEASE-92** lab; actual protocol announcement **5.8-RELEASE-92-HEAD**, API version 3 |
| Running server WAR SHA-256 | `9f03a3c21e5ed09350fd92a2c16fed0dca246122dfb8f266e4179f3ca4134523` |
| Database / container engine | PostgreSQL **18.6**, arm64; Docker client/engine **29.7.2**, Linux arm64 |

The APK checker passed against the supplied signed artifact: signature, discovery,
API 5.6.0.CIV, absence of requested plugin permissions and packaging. Installed
host/plugin APK hashes were checked on each guest against the table. Both standard
hosts reported TMM **Loaded**, without modifying signature validation. These are
standard hosts, distinct from SDK developer 5.6.0.24 and the reported phone 5.6.0.12.

The earlier temporary AVD storage was unavailable, so two empty task-only guests
were created. One host installation exceeded the harness's 60-second wait; its
completed installation was verified before continuing. An initial external VIEW
file-import attempt did not resolve; only the subsequent stock Local SD import of
the inspected synthetic profiles was used. These setup attempts are not passing
test observations. No operational phone, profile, location, image or server was used.

## Isolation and observation method

Both guests had real Android-reported connected simulated Wi-Fi, airplane mode off,
and IPv4/IPv6 OUTPUT rules accepting loopback first and dropping all other output.
Rules remained in place through the test. Only guest loopback TCP 8089 was reversed
to the existing host loopback relay. Each imported synthetic TLS profile pointed to
`127.0.0.1:8089:ssl`. Mutual TLS and IP hostname validation were retained.
The database/server stayed on their existing internal Docker network, with no
published database/server ports. Only the existing fixed-destination relay used
loopback host bindings. No operational feed or external server connection was added.

A separate authenticated TLS subscriber recorded decrypted CoT throughout the
operation window. Independent SQL queries read the lab server's new CoT records.
Positive controls were unique ordinary synthetic contacts at 0/0, sent by a separate
TLS source held connected for 20 seconds, with fresh event UIDs. Actual recipient
object-list/search entries established delivery; a send return or green icon alone
was insufficient. See the controls and final counts below.

Server and stream scans accumulated both draft UUIDs, IDs, owner/comment values,
owned image filename/UUID, synthetic source filename and private storage-path
canaries, including the deleted record. Plain and Base64 forms were searched.
Both actual draft positions were compared with decoded XML/server geometry, using
1e-6 degree tolerance to include ordinary coordinate rounding. Control positions
were distinct from both private positions. Final XML position parsing had no errors.

The recipient's global object index was queried for 16 UUID/label/owner/comment/
image/path/MGRS canaries, with zero results; each captured query field was checked
against the intended string. This is an index/UI check, not a forensic inspection
of every recipient database or every possible field encoding. Final private-label
and identity searches were repeated after reconnection and positive-control receipt.
Raw UI XML, screenshots, APKs, certificates, profiles, keys, captures and database
extracts remain ignored under `private-data/connected-signed15/`, outside Git.

## Observed local behavior

| Check | Actual observation |
|---|---|
| Consent cancellation | Cancel closed the planner; opening it again still required explicit connected-local consent |
| Explicit opt-in | Empty list opened with `testläge`; a newly saved synthetic Mini remained Planned / Local |
| Create and edit | Saved a canary ID/owner/comment through the actual form; changed the comment and verified persisted UUID/revision/fields |
| Image | Selected a generated 256 × 256 PNG through DocumentsUI, inspected normalized preview, explicitly saved and reopened it; original source bytes remained unchanged |
| Graphical move | Panned under the actual map sight, chose Use position, then separately saved the form; storage readback confirmed changed coordinates and retained image |
| Background/return | Home then normal ATAK return preserved the open image viewer; it closed normally |
| Deletion | Created a second uniquely identified draft, then confirmed its deletion; one retained draft remained, byte-identical to its pre-second-draft snapshot |
| Sharing restrictions | Expanded saved review omitted metadata sharing; the separate CoT lab did not open |
| Offline/connected transition | Returning offline in the opted-in session retained test mode and both lab restrictions; reconnecting simulated Wi-Fi left snapshot bytes unchanged |
| Normal Unload/reload | Manager reported Not loaded; both TMM tools disappeared. Normal reload required fresh consent; one saved draft and its image reopened |
| Cold restart and reconnection | Force-stopped only A, removed its task tunnel, cold-started with connected Wi-Fi, then restored the tunnel before renewed consent; no delayed canary publication was observed |
| Connected startup rendering | After cancellation, the stock map was zoomed around the synthetic area. The same camera showed no private symbol before consent and one yellow Mini after consent; no duplicate symbol was visible |
| Cold-start consent/data | New consent was required; list restored one retained draft, both tools appeared once, and the saved image reopened |
| Storage/lifecycle | Retained snapshot SHA-256 stayed `1d91f5b20b8a80859fec950cc0b56851d09b7c1ccf2eabea7443287c302cbfed` after deletion, connectivity transitions, Unload/reload and cold restart; UUID, Planned status, revision 4 and image handle were unchanged |
| Crashes | Final captured Android crash buffers on both guests contained zero `FATAL EXCEPTION` entries; this does not exclude caught errors |

The map comparison is a visual sample, not exhaustive layer enumeration or dense-map
performance testing. No data import/update/conflict, manual metadata transmission,
image delivery or companion workflow was exercised. Data Sync was not installed;
its optional automatic-publication setting and reconnect queues were not tested.

## Traffic window and controls

Private-operation observation window: **2026-10-23 14:30:16–14:50:37 UTC**
(**16:30:16–16:50:37 Europe/Stockholm**). SQL baseline: last pre-window row **754**;
final scan: **82 new rows**, ending at row **836**. Decrypted subscriber baseline:
byte offset **3,753**; final preserved window: **14,697 bytes**, **22 complete XML
events**, zero XML position-parse errors. The full retained stream also includes
the earlier before-control. Counts exclude subsequent cleanup traffic.

| Distinct ordinary control | Server rows and UTC receipt | Independent observation |
|---|---|---|
| Before operations: `TMM-S15-CONTROL-BEFORE-20261023` | 749 / 14:28:50.396; 750 / 14:29:05.363 | Decrypted observer and B's actual Cyan Team object list |
| During operations: `TMM-S15-CONTROL-MIDDLE-20261023` | 782 / 14:37:15.330; 783 / 14:37:30.302 | Decrypted observer; additional transport-liveness check |
| After cold opt-in: `TMM-S15-CONTROL-AFTER-20261023` | 818 / 14:46:31.686; 821 / 14:46:46.572 | Decrypted observer and B's actual global object search |
| After all lifecycle/image checks: `TMM-S15-CONTROL-AFTER-LIFECYCLE-20261023` | 830 / 14:49:09.031; 832 / 14:49:23.934 | Decrypted observer and B's actual global object search |

Final cumulative scan: **15 draft/image/path canaries**, **two private positions**,
**zero matches** in new server rows or the decrypted stream. Four fresh recipient
queries after final control receipt (private-label prefix, both draft UUIDs and
owned-image filename) each returned zero rows. Deleted-record identifiers remained
in the scan set. Retained lifecycle readbacks kept the same UUID, Planned status,
revision, coordinates and image reference; no connection-induced publication or
state transition was observed.

**Result:** no private draft exposure was observed in this bounded standard-binary
CoT trial, with working routed controls before and after it. This is test evidence
for reviewer acceptance of the stated setup, not a universal no-transmission claim.

## Scope, remaining gates and cleanup

No unresolved environment blocker prevented this bounded standard-client CoT run.
Its absence result applies to the tested signed APK, hosts, synthetic operations,
isolation and observed channels. Egress restrictions intentionally prevent arbitrary
Internet/HTTP traffic; this does not prove unrestricted-network privacy, Data Sync
or resource-upload behavior. The server's config/messaging/API roles ran, but only
the explicit CoT route was exercised. The existing adapted lab is not a newly
verified hardened deployment; see [TWO_CLIENT_LAB.md](TWO_CLIENT_LAB.md).

The physical OnePlus was not connected or re-inventoried. Its earlier owner-reported
offline acceptance remains separate in [PHONE_ACCEPTANCE.md](PHONE_ACCEPTANCE.md).
A separately authorized isolated phone trial, exact phone-host reconfirmation,
companion report/feed/voice workflows, broader security/provenance/distribution
review, operational-network approval and release review remain outstanding.
Keep #4/#7 open for human assessment and their residual gates; this report does not
authorize automatic sharing or close full A08.

Both task emulators and the observer were stopped. Task ADB reverse tunnels were
removed and guest radios disabled before shutdown. Only the task lab server, relay
and database containers were stopped; their database volume and private evidence
were retained. Other containers, the phone and operational infrastructure were not
started, stopped or modified by the trial. The container wrappers initially ended
with Docker exit 137. As a separate cleanup check, the synthetic database alone
was restarted, SQL readiness/recovery confirmed, PostgreSQL stopped cleanly with
`pg_ctl -m fast -w stop`, and its container stopped again. No post-cleanup traffic
is included in the trial result.

No domain/product change was made, so core/SDK builds and unit suites were not
rerun for this documentation-only branch. The APK checker and the runtime/readback/
traffic checks described above were actually run. Documentation links and patch
whitespace are checked separately before opening the PR.
