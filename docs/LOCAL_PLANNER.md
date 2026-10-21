# Local planner prototype (Issues #12 / #4)

The toolbar now opens a local draft list instead of the disposable single-point
map experiment. This is a **local test prototype**, not a field release. Use
synthetic data only. No domain record is published, exported or added as a normal
ATAK Marker/MapItem. A separate [manual metadata-sharing lab](METADATA_SHARE_LAB.md) now adds explicit
single-recipient sharing from saved review; ordinary local operations do not send.
Offline remains the default. The explicit, session-only
[connected local trial](CONNECTED_LOCAL_TRIAL.md) permits synthetic-data planning
after consent while disabling TMM lab sharing for that session; see ADR 0009.

## Behavior

Open Tools → TMM Sensor Planner. Choose **Skapa enhet**, with the map centered on
the desired test position. The form starts with that center in decimal degrees;
latitude/longitude can also be entered manually. Select Mini, Scout, PIR or Gateway,
enter the actual device label manually and a group/role owner. New drafts default
to Planerad; all five statuses can be selected manually. No field is derived from
status. Required fields and invalid coordinates have adjacent Swedish errors.

Coordinates/bearing accept a dot or comma decimal separator, without grouping or
exponent notation. The Android key listener preserves both separators independently
of host keyboard locale; prefilled small values use plain decimals, not exponents. Optional deployment time uses explicit date/time selectors in the device local
timezone, with the zone and UTC offset visible; see [form entry](FORM_ENTRY.md).
Opening a selector initializes an empty value to now without assigning it to the
draft. Only confirming both date and time assigns a value; Cancel preserves the
previous value and **Rensa utplaceringstid** explicitly clears it. Existing values
retain their exact Instant until changed. The review displays timestamps locally. PIR/Scout support optional decimal bearing clockwise from **true
north**. Other types hide an empty bearing field; a retained nonempty bearing must
be explicitly cleared before saving a type change. Text and decimal fields offer **Done/Klar** to hide the keyboard without moving
focus or saving. Labels scroll with their active field, and landscape input remains
in the form rather than a separate fullscreen editor. Touch, hardware Tab and
accessibility navigation remain available. Comments retain the 2,000
UTF-16 code-unit limit. Duplicate visible IDs warn and offer **Spara ändå**, without
merging UUIDs or changing labels. Edits preserve UUID, creation time and image handle.

**Spara lokalt** commits on one background executor. Review shows the saved fields,
local status, dates/revision, nearest eligible other device and a sorted gateway list.
Distances use the accepted core rules, rounded to whole meters, with no coverage claim.
Saved-device review provides local image add/preview/view/replace/remove actions;
see [LOCAL_IMAGES.md](LOCAL_IMAGES.md). **Ta bort lokalt utkast** requires confirmation,
commits metadata and then cleans an image only after a fresh reference check.
Cancellation saves nothing. **Läs om** reloads the snapshot; storage errors never reset it.
An uncertain save keeps entered text and disables repeat saving until the user reloads.

The map uses one plugin-owned in-memory database/FeatureLayer3 per planner lifetime.
Updates replace feature sets under the SDK bulk modification lock; unload queues
database disposal after GL renderer release, so its query worker cannot access a
closed database. The layer retains its original render queue for late host shutdown,
rather than dereferencing a GL surface that ATAK has already removed; see
[the shutdown reproduction and correction](MARKER_RESTORE.md#shutdown-correction-and-final-combined-artifact). The first emulator run exposed a crash from replacing/discarding
the database on each refresh; that approach was removed before review readiness. The owner-approved [device pictograms](DEVICE_SYMBOLS.md) replace the provisional
M/S/P/G disc artwork in four 64 px PNG point icons, so type shape remains visible
even when the SDK declutters labels. The vector masters follow the private reference
silhouettes; no extracted manual bitmap is packaged. Numeric `android.resource` URIs
load the APK drawables through the SDK bitmap loader. Map labels contain only the full manual ID and `Lokal`, for example
`PIR 134 · Lokal` (#53); device pictograms identify type without an extra type
letter. Lifecycle-status words are kept in the list, details and manual edit form.
`Lokal` means a draft on this device, separate from lifecycle status. Connecting
ATAK to a server neither changes that label nor proves synchronization or delivery;
the details explain this distinction. IDs are not truncated or renamed in storage.
SDK decluttering may still hide overlapping or very long labels; use map-tap review
or the list to read the complete ID and status. Status colors remain a secondary cue: yellow Planned, green Deployed/Active,
gray Retrieved/Cancelled, as approved on 2026-10-18.
A PIR/Scout arrow is a fixed **100 m direction cue**, not sensor range. Tapping within
32 dp of a point opens review when another planner dialog is not already open; nearest
point wins if labels overlap. The list remains an alternative for overlapping points.
**Visa på kartan** centers the saved point. A successful load rebuilds the layer; unload
removes the layer/listener and dialogs. A rendering failure removes the stale view,
retains saved data and reports an error. On permitted API-26+ offline plugin startup,
the [saved-marker restoration path](MARKER_RESTORE.md) now reads and renders saved
drafts automatically without opening TMM or centering the map. The toolbar still
provides explicit list/reload controls.

## Runtime, storage and privacy boundaries

- The entry point checks Android API 26 **before constructing** the separate planner
  class. Existing minSdk 21 is unchanged; below Android 8, local planning is unavailable
  with an explicit message. No API 21–25 planner support is claimed. The image library adds AndroidX ExifInterface; the host also supplies its own version,
  so actual ATAK codec behavior requires runtime checks. Native NIO/Instant avoids unverified desugaring/classloader behavior.
  `app/lint.xml` excludes NewApi only for the SDK-independent core source directory;
  core consumers are isolated in API-26-annotated classes behind that runtime guard.
  NewApi remains enabled for all app sources; future callers must keep this boundary.
- Session access is checked before local actions. Without connected-trial consent,
  the fail-closed offline restriction remains: airplane mode, Wi-Fi off, no active
  connected/connecting network and known host network-state permission. Explicit
  connected consent permits local actions until Unload/process restart and keeps
  both lab transports disabled even if connectivity changes again. This is a trial
  restriction, not approval of production network privacy.
- The plugin executes as ATAK's UID. Its namespaced storage directory is under the
  **host context's no-backup files directory**, not the separately installed plugin's
  inaccessible app directory, shared storage or a feed. Android no-backup storage is
  excluded from normal Android Auto Backup/device transfer; this must still be checked
  against the actual host/device's management tools before field use. Same-UID plugins
  are not isolated from each other. Host uninstall/data clearing deletes these drafts;
  uninstalling the plugin alone does not necessarily delete host-owned files.
- Store bytes are plaintext sensitive local data. No field/position/file exception is
  sent to a logger or notification error object. Form autofill is disabled and the IME
  is asked not to learn input; external keyboards and host/device policy remain outside
  that request. There is no backup/export/recovery feature in this prototype.
- `DraftStore` format 1 is unchanged. New core form parsing has no storage side effects;
  all existing fixture/reopen/failure tests still apply. Actual image bytes and future
  schema migrations are separate gates.

Primary platform references: [Files](https://developer.android.com/reference/java/nio/file/Files),
[Instant](https://developer.android.com/reference/java/time/Instant),
[no-backup storage](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()),
[NIO desugaring limits](https://developer.android.com/studio/write/java11-nio-support-table).
ATAK signatures are checked against the supplied 5.6.0.24 SDK with `javap`, not inferred
from an online example: FeatureSetDatabase2/FeatureLayer3, styles, Point/LineString,
MapEventDispatcher MAP_CLICK registration/removal, event point, map projection and
MapView.getPoint().get(). They do not establish standard 5.6.0.12 compatibility.

## Verification procedure

Use only the isolated headless SDK developer emulator described in EMULATOR_TEST.md;
keep its guest firewall, airplane mode and networking off. Do not install this developer
host over a phone's standard ATAK app or put the APK on any feed.

1. Build/lint, run core tests and the APK packaging/signature checker. Install with an
   explicit emulator serial. Open the tool and create two synthetic devices near 0/0.
2. Try blank ID/owner, invalid coordinates, comma decimals and unsupported bearing.
   Expect adjacent errors and unchanged saved records. Cancel another edit.
3. Create Mini/Scout/PIR/Gateway, select statuses, and check distinct device pictograms and
   status text across all five statuses. Test zero/north/east bearing with north-up map, then rotate the map.
   Compare coordinate direction, not a magnetic-north display assumption.
4. Review and tap points, move via form coordinates, check recalculated neighbors and
   gateway list. Try a duplicate label, cancel its warning, then explicitly save it.
5. Force-stop/relaunch ATAK offline. Before opening TMM, verify restored markers
   in the existing viewport and absence of planner dialogs. Then reopen the toolbar
   to verify saved fields/UUIDs/revision. Unload/reload without opening TMM and verify
   layer/listener cleanup/restoration; follow MARKER_RESTORE.md for failure cases.
6. Retain a synthetic v1 snapshot and install a subsequent build with `adb install -r`;
   verify it reopens unchanged. Separately corrupt a copy/change schema version, verify
   visible load failure without rewriting it, and restore the valid test copy afterward.
7. Check small/large portrait and landscape layouts, keyboard/scrolling and 48 dp form
   controls. Record exact pixel/density sizes and builds; emulator usability is not
   physical-phone field acceptance. Never publish raw snapshots/screenshots/logs.

## Recorded evidence — 2026-10-17

Environment: AOSP API 35 arm64-v8a, official Emulator 37.2.12, SDK developer ATAK-CIV
5.6.0.24, Android Debug APK `0.1.0-local-planner.1` / versionCode 5. Missing temporary
SDK/cache files were restored from official packages before testing; the empty test
client was reinstalled under the same previously approved TAK agreement/permissions.
Guest IPv4/IPv6 egress remained blocked with loopback only, airplane mode on and
Wi-Fi/data off. No operational server/phone or feed was used.

| Check | Observed result |
|---|---|
| Build / lint | assembleCivDebug and lintCivDebug passed; zero errors, seven warnings (including localization/backup/tooling warnings); core-only API lint boundary recorded above |
| Static APK | Signature/discovery/no-permission/SDK-key packaging checks passed; Android Debug signing is not standard-host approval |
| Core / form / storage | `sh scripts/test.sh` passed, including field errors, dot/comma parsing, manual IDs, optional fields, clock/revision, duplicate warning, plain-decimal prefilling, fixed v1 fixture and separate-process reopen |
| Form creation | Two drafts created through actual UI: PIR with manual ID `PIR 134`, owner, manual deployment time, comment and north bearing; Gateway with no bearing field |
| Validation / editing | Out-of-range latitude left form unsaved; corrected value saved. Decimal comma `0,001` remained visible and saved longitude 0.001, revision advanced and nearest distance became 111 m. The original keyboard filtering bug was corrected before this result |
| Deletion | Confirmed removal of the synthetic Mini updated the list to four records and refreshed the layer; other UUIDs remained separate |
| Duplicate ID | Warning cancellation retained the editor; explicit save kept two separate records and review warned about the same visible ID |
| Restart / install-in-place | Two-record snapshot remained byte-identical after force-stop, `adb install -r` of a changed development build and host restart; both records reappeared through the toolbar. This is same-schema development-build replacement, not a historical production migration |
| Map selection / distances | Tapping the saved Gateway point opened its revision-2 review. A five-record fixture additionally exercised Mini, Scout, PIR and two Gateways; gateway list showed the other Gateway at 111 m |
| Bearing / original symbols | North-up PIR 0° arrow pointed north; fixture Scout 90° arrow pointed east. Actual resource-backed M/S/P/G icon rendering was checked, including different status colors and longer labels |
| Unload / reload | Plugin Manager changed to Not loaded, then Loaded; host remained alive without another closed-datastore fatal error. The per-refresh disposal bug was fixed before this result |
| Offline guard | Clearing only the airplane-mode flag rejected tool opening; radios remained disabled and guest IPv4/IPv6 egress blocking stayed installed; the flag was restored |
| Smaller layout | List, form, required-field rejection and cancellation passed at 1280×720 landscape / 320 dpi; the normal test view was 2340×1080 landscape / 440 dpi. Physical portrait/landscape field acceptance remains open |
| Future schema | A checksummed synthetic version-2 snapshot produced an explicit load-error dialog and remained byte-identical; valid synthetic bytes were restored afterward |

The synthetic five-record fixture was generated with the core store; only the first
PIR/Gateway were created through forms. Screenshots, snapshots, APKs and emulator
logs remain ignored/private. No map transmission/two-client observation was performed
for this production-shaped adapter. Prior experiment results do not automatically
approve it. Rotated-map direction, magnetic settings, API 21–25 planning, actual image
bytes, physical screen/field use, standard 5.6.0.12 signing/coexistence, OEM backup
behavior, a 100-object phone performance trial and future schema migrations remain
unverified. Issues #11/#12 stay open for the relevant actual-client acceptance gates.

Final private APK SHA-256:
`733657c0e3675a47a07a4d054e60988219190c3d914b3d8ef5fbab969faee189`.
Green public core CI is not Android runtime, connected privacy or signing evidence.

## Connected SDK-lab follow-up

On 2026-10-17 the merged saved-draft/image adapter was exercised with both SDK
hosts connected to the isolated CoT lab through scoped ADB tunnels, while Android
continued to report offline. The test covered real form creation, a local image,
position/bearing edit, plugin unload/reload, persisted host restart and deletion.
See [the exact protocol and limits](TWO_CLIENT_LAB.md#actual-planner-follow-up--2026-10-17).
No draft publishing implementation or relaxation of the offline restriction is
introduced by this evidence. Phone/standard-client, companion-plugin and other
transport/privacy acceptance remain separate gates.

## Simplified map labels (#53) — 2026-10-21

The owner-approved map text is `<manual ID> · Lokal`, without a redundant type
letter or appended lifecycle-status name. Status remains explicit in the list,
details and the five-option manual editor. The saved-review introduction explains
that a local draft is on this device and that server connectivity proves neither
synchronization nor delivery. No ID/status, snapshot-v1, wire, image, publishing,
access-policy, icon, tint, label-font or map-selection behavior is changed.

Verification uses only synthetic data in a dedicated API-35 arm64 SDK-host guest
at 720×1280 and 1280×720 px / 320 dpi. The 22-record fixture covers all four types ×
five statuses plus a long Unicode ID and a nearby overlapping point. Raw fixtures,
APKs, logs and screenshots stay in ignored private storage. First-run host graphics
failed before plugin loading with `No config chosen`; the existing
[generic EGL emulator workaround](EMULATOR_TEST.md#graphics-workaround) resolved it.
No plugin trust/signature setting was bypassed. Airplane mode is enabled, Wi-Fi,
data and location are disabled, and IPv4/IPv6 output is blocked. No server or feed
is configured. This is SDK-emulator evidence, not physical-phone or service-signed
standard-client acceptance, nor a new connected/privacy test.

The runtime candidate is source `99a5b26`, based on main `c0e8343`, with Android
Debug APK SHA-256
`bddfcf364bb8e6d5f243958492c50e63667769d44109189e7540db714d533a50`.
`sh scripts/test.sh` and `sh scripts/check-sdk.sh` passed with Temurin
17.0.20.1+1 and the supplied ATAK-CIV 5.6.0.24 SDK (build, lint, existing app tests
and static APK checks). No ATAK API was added or guessed.

- Actual phone-size landscape and portrait rendering retained the four pictograms,
  yellow Planned, green Deployed/Active and gray Retrieved/Cancelled. Map labels
  showed IDs and `Lokal`, without appended lifecycle words. Existing bearing cues
  remained in their planning colors.
- A real map tap opened PIR 141 with Active status and the new local-state explanation.
- The long ID remained complete in review and on the centered portrait map. The
  nearby pair demonstrated SDK label suppression: both labels are not guaranteed
  to appear simultaneously. At dense grid spacing labels can still touch/overlap;
  zooming and the list/review remain necessary. This change does not implement a
  new collision engine or promise every ID is always visible.

- Tapping each of the nearby icon centers opened its correct full ID/status:
  the long PIR remained Active and Scout NEAR remained Retrieved.
- The editor still exposed all five status choices. Cancelling the edit and
  returning to the map left the complete snapshot byte-identical to the fixture.
- The post-workaround runtime log contained no AndroidRuntime fatal exception.
Physical-phone readability, diverse map backgrounds, TalkBack and a newly signed
standard-client artifact remain unverified for this change. Human review/merge
and the existing release gates still apply.
