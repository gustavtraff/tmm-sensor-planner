# Isolated two-client lab (Issue #4)

The laboratory uses two separate AOSP API 35 arm64 Android emulators, SDK developer
ATAK-CIV 5.6.0.24, and the owner's locally supplied TAK Server Docker package
`takserver-docker-hardened-5.8-RELEASE-92`. Server package metadata reads
`5.8-RELEASE-92`; it is a separate version from the plugin API `5.6.0.CIV`.

## Local boundaries

Persistent package copies, certificates, credentials, profiles, raw logs and captures
are kept under ignored `private-data/two-client-lab/`; temporary diagnostic files
also remain outside Git. They are not GitHub artifacts or feeds.
The operational phone, existing containers and operational TAK environment are not
part of the lab. Credentials and certificates are generated only for synthetic clients.

The database and TAK Server use a task-specific internal Docker network with no
published database port. A fixed-destination TCP relay forwards encrypted bytes
between localhost ports 19089/19443 and the server's TLS/API ports 8089/8443. Host
bindings are explicitly `127.0.0.1`. The relay is required because the inspected
Docker Desktop 29.7.2 setup did not publish ports for a container attached only to
an internal network. It is a local test harness, not a production server component.

Each guest has airplane mode enabled and Wi-Fi/data disabled. A guest-only
IPv4/IPv6 OUTPUT chain allows guest loopback and drops other output. Rules must be
reapplied after cold boot; they do not change the Mac firewall. An explicit ADB
reverse tunnel maps the guest's loopback TCP 8089 to the host's loopback TCP 19089.
Synthetic connection profiles point only to `127.0.0.1:8089:ssl`. The server certificate
must validate that IP; do not disable hostname or CA verification to work around a mismatch.

An ADB tunnel can carry traffic while Android reports offline. The map probe's
airplane-mode/network-state checks therefore do not prove that no transport exists.
This lab deliberately exercises the feature-layer candidate while its host has a
working, tightly scoped TLS connection through that tunnel.

## Package/build adaptations

The supplied hardened server recipe encountered OpenSSL/FIPS RPM dependency/file
conflicts with current repositories on this machine. The local test server instead
uses the successfully built package CA/JDK base and the unchanged server WAR.
This is not a verified hardened installation or a production deployment recipe.

The packaged database image builds natively for arm64. Database defaults were
reduced for two clients: 256 MB shared buffers and 100 maximum connections. Server
JVM heaps are 512 MB per role; the lab runs only config, messaging and API roles
in a 4 GB container with two active processors. TAKIgniteConfig.xml sets the cache
initial size to 128 MB, maximum size to 512 MB and pool size to eight, using
attributes supported by the package XSD. A trial 64 MB cache caused eviction
starvation; it is unsuitable here. After the increase, mutual TLS, observer event
recording and reception of both clients’ synthetic situational-awareness events
were observed. These checks alone do not establish sustained control-point delivery.

TAK updates CoreConfig during startup. Use a writable container-local copy of the
private source configuration, rather than a read-only single-file bind. Keep raw
initialization logs private: the packaged database setup can print password hashes.

Package WAR SHA-256:
`9f03a3c21e5ed09350fd92a2c16fed0dca246122dfb8f266e4179f3ca4134523`.
This fingerprints the inspected file; no publisher checksum was compared.

## Test protocol

1. Verify server listeners with mutual TLS, including CA and hostname validation.
2. Import each synthetic profile locally; confirm both ATAK clients connect.
3. Send an ordinary ATAK control point at synthetic 0/0 through the lab. Require
   receipt on the other client and visibility in server/application observations.
4. Start a baseline observation window. Capture decrypted events through a test
   subscription and query the lab server's event records by synthetic UID.
5. On client A, create, move and delete the map probe point; repeat creation,
   unload/reload and host restart. Readback verifies the feature-store mutations.
6. Check client B, observation events and server records for the probe UID/prefix.
7. Repeat a positive control after the negative checks to verify that observation
   and transport function at both ends of the window, including after host recovery.
8. Record exact versions, windows, results, configured channels and remaining limits.
   Stop task-owned clients/services after testing; preserve private evidence locally.

Ethernet captures from the emulator do not include guest loopback/ADB-tunnel traffic.
TLS ciphertext also cannot be searched for CoT UIDs. Use authenticated application
observations and server records, with working controls, before drawing an absence
conclusion. Do not treat an empty packet file as proof of N03.

## Recorded result

Test date: **2026-10-14 Europe/Stockholm**. Observation window:
**2026-10-13 22:33:44–22:42:21 UTC**. Source baseline: `5945681` (merged PR #21).
Client A loaded `0.1.0-map-probe` (versionCode 2); B had no TMM plugin.
APK SHA-256: `e86cef68996cefa0d3c4613006f57882fa03ffd683bf8ef3c3b8809adb163660`.
Both hosts were the supplied SDK developer build **5.6.0.24**, not the owner's
standard **5.6.0.12** phone distribution.

| Check | Observed result |
|---|---|
| Mutual TLS | CA and IP hostname validation passed; both clients connected to the isolated server |
| Initial targeted control | B used stock Send to PLANT (A). Waypoint `TMM-LAB-CONTROL-B-WAYPOINT` at 0/0 appeared in A’s fresh map pick-list and server record 127 at 22:32:16 UTC |
| Initial broadcast control | The same stock waypoint reached the decrypted observer and server record 128 at 22:33:24 UTC |
| Create and duplicate create | Create succeeded; a second create reported the point already existed |
| Move | Store geometry readback passed and the cyan feature rendered at longitude 0.001 on A |
| Delete and repeated delete | Both completed; feature-store count readback was zero |
| Unload/reload with a point | Unload removed the layer. After reload, move requested creation first; new create/move succeeded |
| Host restart with a point | After force-stop/relaunch, move requested creation first; create/move/delete succeeded and the TLS indicator was green |
| Client B observations | Fresh map pick-lists and screenshots during and near the end of the operations showed ordinary controls/clients, with no `tmm-probe-` item |
| Server/application absence checks | Zero `cot_router` rows matched `uid LIKE 'tmm-probe-%'`; no matching prefix occurred in the decrypted observation stream |
| Final broadcast control | Fresh control appeared after the saved observer offset, in server record 138 at 22:40:53 UTC, and on A after restart. Inspection entered stock move mode and was cancelled without changing position |

The final stream snapshot contained 13,879 bytes, including positive controls.
Feature-store mutation checks also required that the point UID had no ordinary
root MapItem. The after-restart rendered UID was
`tmm-probe-3bbe1b1b-e4ec-4ac0-852f-8175bc3bdf7d`; absence checks used the prefix
across all probe instances, rather than only that one UID.

Private evidence includes the decrypted stream and offset/result JSON files,
server records, B’s during/final XML and screenshots, and A’s rendering, unload,
restart and control-receipt evidence. Raw evidence stays local and is not attached
to the PR. Empty emulator Ethernet captures were not used as absence evidence.

## Conclusion and remaining gate

**The disposable feature-layer candidate passed this bounded, connected CoT lab
experiment:** its tested mutations did not appear on B or in the observed server
and subscription channels, while ordinary controls did pass before and afterward.
This is evidence for the candidate, not acceptance of a complete production draft
implementation or a guarantee across every host integration and transport.

Both emulators and all task-owned lab services were stopped after preserving evidence.
Local images, the lab database volume and private configuration remain for repeat testing.

Issue #4 remains open pending human review and the production representation decision.
F04 persistence is not implemented: points intentionally disappear on unload/restart.
Data Sync was not installed, export/share integrations were not tested, and standard
phone signing, full network privacy, companion-plugin coexistence and operational
server compatibility remain unverified. Keep automatic publishing unimplemented.

## Follow-up: Unicode transport prerequisites

The Issue #5 metadata experiment exposed a US-ASCII default in the custom lab
JVM image, which replaced the synthetic comment’s Unicode characters. Its private
startup was corrected to use `LANG=C.UTF-8` and `-Dfile.encoding=UTF-8`. PostgreSQL
was already UTF8. The earlier ASCII control/feature-UID privacy observations are
unchanged; they did not establish Unicode preservation. See
[COT_ROUND_TRIP.md](COT_ROUND_TRIP.md) for the separate metadata experiment.

## Actual planner follow-up — 2026-10-17

The original experiment above used a disposable point. This follow-up tests the
merged **saved-draft planner**, including its labels, bearing geometry, private
snapshot and local image integration. It adds no publishing code and does not
relax the API-26/offline restriction.

### Tested inputs and channels

- Source: `3be458f13988cb69f78beaa13191585add6ecfc2` (main after PRs #36/#37).
  Client A: `0.1.0-local-images.1`, versionCode 6. APK SHA-256:
  `547b7f054d00bbc31f5bcd4d9dade5e36772199f26849d7eca5a5538673a0491`.
- Both hosts: SDK developer ATAK-CIV **5.6.0.24**, AOSP API 35 arm64. B retained
  `0.1.0-cot-probe.1`, versionCode 4, as the fixed-fixture control receiver;
  no production receiver or draft synchronization exists on B.
- Existing isolated TAK Server **5.8-RELEASE-92**, task-owned internal Docker
  network and localhost relay. Only config/messaging roles were needed; the unused
  API role was stopped to reduce memory pressure. No HTTP resource-upload test is
  inferred from this run.
- Both guests retained airplane mode, disabled radios, IPv4/IPv6 egress blocking
  and the explicit loopback ADB reverse tunnel. Android's offline flag therefore
  coexisted with an actual authenticated CoT connection. This does not exercise a
  normal connected phone network or establish that the offline check is a firewall.
- A fresh authenticated subscriber recorded decrypted broadcast traffic. Independent
  SQL queries checked server CoT records in the observation window. Checks searched
  all five draft UUIDs, visible IDs/owner/comment canaries, the owned image handle,
  source filenames/private directory names, and the moved `POINT(0.001 0)` position.
  Feature names do not contain the draft UUID, so UUID-only searching is insufficient.
  Historical rows before the observation window were excluded from negative checks.

### Procedure and observed behavior

1. Verify a targeted B → A fixed-fixture transfer, then A → B. On each recipient,
   the existing test tool found exactly one ordinary host marker for that control
   UUID and successfully re-exported its fixed metadata.
2. Verify a separate broadcast control in both the subscriber and B's actual marker
   export. The control socket first announced a synthetic source identity. An earlier
   unannounced control was recorded by the server without observed forwarding; it
   was excluded from positive-control evidence.
3. Open the actual planner on A, reload its four pre-existing synthetic records and
   create `TMM-LOCAL-CANARY-4`: a PIR at 0/0 with unique owner/comment text and
   359.125° bearing. Save through the real form; the list grows to five records.
4. Attach the synthetic rotated PNG through the real picker/normalized preview and
   explicit save. Move the record to longitude 0.001 with comma input and change
   its bearing to 90°. The saved review shows revision 3 and retains the image.
5. Unload/reload the plugin with the point/image present. Reload all five saved
   records, then force-stop/restart ATAK, reopen the planner and view the owned image.
6. Confirm deletion of only the new canary draft. The list returns to four records
   and the owned image directory is empty. The original records' inspected UUIDs,
   labels, revisions, coordinates, owners, comments and image references are unchanged.
7. Inspect B's stock item-to-move pick-list at the synthetic control cluster, including
   its scrollable entries. Ordinary controls are visible; the unique local draft is
   absent. Cancel without selecting or moving any item. This is a bounded UI check,
   not an exhaustive recipient database audit.
8. Require a fresh A → B control after restart and a final subscriber/B broadcast
   control, then repeat the complete negative check. This brackets the absence
   observations with working transport and recipient imports.

### Result and verification

Observation: **2026-10-17 12:48:52–13:08:57 UTC**. Mutation window started at
**12:54:28 UTC**; checks include the earlier planner load within the broader window.
The final scan covered **33 server CoT rows**, **18,854 subscriber bytes** and
**19 draft/image/metadata canaries**. It found **zero matches**, and no server row
had the moved `POINT(0.001 0)` position. This is an observed absence in those channels.

| Positive control | Verified server record | Independent receipt |
|---|---|---|
| Initial B → A | 494 | One actual A marker, fixed metadata re-exported |
| Initial A → B | 496 | One actual B marker, fixed metadata re-exported |
| Initial announced broadcast | 500 | Subscriber bytes and one actual B marker |
| Final A → B after restart | 512 | One actual B marker, fixed metadata re-exported |
| Final announced broadcast | 518 | Subscriber bytes and one actual B marker |

The final broadcast sender stayed connected until the independent subscriber had
observed its source announcement and control. A premature-close attempt was not
counted as a passing control. Neither empty ciphertext captures nor API send returns
were used as delivery/absence evidence.

`sh scripts/check-sdk.sh` passed on the tested source: APK build, Android lint and
static signature/discovery/permission/packaging checks. No app, schema, dependency,
permission or runtime guard changes are part of this follow-up. `sh scripts/test.sh` also passed (core, form, import review, storage and separate-process
reopen). Repository checks are separate from the runtime observations above.

### Scope of the result

Runtime evidence is retained privately under `private-data/planner-privacy-4/`:
control reviews/recipient exports, server summaries, decrypted subscriber bytes,
per-operation negative results, saved-record readbacks and B's pick-list XML.
Raw traffic, profiles, certificates, APKs and screenshots remain outside Git.

Static review found no normal Marker/MapItem creation, root-group insertion,
CoT dispatch or export-handler registration on the draft path. The actual adapter
uses `FeatureSetDatabase2(null)` / `FeatureLayer3`, with private no-backup metadata
and image files. The test did not add or invoke a domain-draft sharing action.

This remains a bounded SDK-lab CoT result. Keep #4 open for human assessment of the
production privacy gate and actual-client acceptance. Standard 5.6.0.12 signing,
physical phones, companion plugins/Data Sync, arbitrary providers, explicit stock
exports, all other transports and operational server configuration are not covered.
Same-UID host plugins are not isolated, and the host can internally forward a picker
URI. Local image sharing and the broader information-handling policy remain separate.
