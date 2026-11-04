# Private unassociated image-pair storage (#146 / #26)

The owner explicitly approved [stage-v1's exact layout](IMAGE_STAGE_LAYOUT_PROPOSAL.md)
after PR #143. `ImageStageStore` implements that format in the app image adapter,
reusing `ReceivedImagePayload` and the approved descriptor codec. It has no UI,
ingress or transport caller. It never opens drafts, an inbox, active attachments,
gallery resources or a host map item. No persisted existing format changes.

## Caller contract

Use a trusted, dedicated app no-backup `Path`, proposed under
`se.tmm.sensorplanner/received-image-stage-v1`, with an existing trusted parent.
All calls are synchronous worker operations. The caller bounds worker concurrency
and live image memory; a successful bounded test is not a heap guarantee. Shared
ATAK/plugin UID access is not a security boundary against a malicious peer.

- `initializeNew()` explicitly creates only an absent root, empty OS lock file and
  exact synced/read-back format marker. It never initializes on construction/read,
  adopts an existing directory or resets damaged storage. Initialization failure
  can leave an incomplete root or an actually valid empty root; inspect actual state.
- `prepare(payload)` freezes canonical descriptor/exact verified PNG bytes and a
  fresh receiver-generated local UUID before writing. Incoming identities never
  select a path. Equal incoming offers can occupy separate unassociated stages.
- `stage(prepared)` permits one attempt on that handle. Under an exclusive OS lock,
  it validates all existing state and quota, creates the pending directory/files
  without replacement, syncs each file, reopens/verifies the complete pair, then
  atomically renames pending to ready and verifies actual ready bytes again.
- `inspect()` returns immutable sorted ready/pending UUID lists and actual logical
  file bytes. It validates every ready pair, caps enumeration and accounts partial
  files. Pending blocks admission, even if its contents are complete. Missing,
  corrupt, foreign, unsupported or ambiguous state fails without resetting it.
- `read(localId)` validates the stage before returning one verified ready pair.
  A pending or unknown ID cannot be read as an image. Read-only inspection/read of
  valid ready records is possible alongside retained pending records.
- `reconcile(prepared)` checks the original operation's actual outcome. The overload
  accepting the **original local ID and exact frozen payload** supports a caller
  that retained both across restart. A ready result requires byte equality of the
  entire canonical descriptor and PNG, not just UUID, pixels or digest. Pending
  remains pending; absent means absent now. No new identity/retry is generated.

Failures have bounded categories without filesystem paths/provider messages or
causes. Unsupported or busy locking never falls back to unlocked access. The lock
also covers reads, reservation, publication and readback. An already-attempted handle
cannot retry after any result; retain/reconcile it. A lock/capacity error before a
write can leave no candidate. Failure during a write leaves pending; failure after
rename may leave ready. A readback failure never reports known completion and
never deletes either candidate file. Synthetic allocation failures use the existing
safe low-memory category. There is no deletion, cleanup, auto-resume or eviction API.

## Exact limits and remaining guarantees

The approved marker/names, 16 total candidate directories, 256 MiB logical-byte
budget, 262256-byte descriptor and 16777216-byte PNG caps are enforced. Sparse
partial files count logical length. Filesystem overhead/free space are outside
that quota. Symlinks, nonregular files, unknown entries and duplicate local IDs
across pending/ready fail closed. Cooperative writers share the same exclusive OS
lock; arbitrary concurrent replacement by another host/plugin is outside scope.

File sync, atomic rename and byte readback do not prove directory durability under
power loss or backup rollback. No receipt/index history is stored: whole-directory
disappearance cannot be detected by enumeration. An empty valid stage does not
prove nothing was previously received/lost. Local UUIDs and matching bytes cannot
restore source/feed authority after restart. Any future association registry must
retain its own expected content IDs, source/metadata evidence and missing-data state.

No positive association acknowledgment, metadata application, active-image change,
Data Sync delivery or operational sharing follows from a READY result. This is only
known unassociated stage readback. A concrete cleanup/recovery operation and its
review still precede end-user ingress activation.

## Verification

Focused API 26/35 Robolectric tests use actual native PNG decoding and the independent
stage fixtures. They cover initialization and every injected write milestone,
unknown outcomes before/after rename, exact descriptor/content match, immutable
inspection, same incoming pair with separate local IDs, partial/complete pending,
bad/unknown/missing files, symlinks, canonical spelling, entry/actual-byte limits,
no eviction and synthetic allocation/readback faults. Recreated store instances
exercise reopened filesystem state. A separate plain JVM provides actual OS-lock
exclusion, while two concurrent in-process instances exercise blocked admission/read.

These tests use the development host filesystem under Robolectric, not an Android
device filesystem or a killed/restarted ATAK process. Native process/restart,
device storage denial/full-disk, unsupported-filesystem atomic move and power-loss
acceptance remain unverified. No emulator, Docker, server, phone, signing submission
or transport was used for this slice. The focused suite passed **30 API 26/35 cases**, with no failures/errors/skips.
Standalone image lint reported zero errors and six warnings. Local supplied-SDK
CivDebug/CivRelease assembly, CivDebug lint and all three existing app unit tests
passed (167 executed tasks). App lint had zero errors and 57 warnings. The debug
APK passed actual signature/discovery/API/permission/packaging checks. Local release
remains unsigned; no SDK method was introduced. These results do not establish
standard-host runtime compatibility or a native Android filesystem/restart result.


## Abrupt worker restart — 2026-11-04 (#148)

`python3 scripts/test-image-stage-restart.py` passed locally using two separate
API-26 native-graphics Robolectric workers and the actual development filesystem.
The first committed H1, synced H2 into pending, recorded the test-only original
local IDs, then abruptly halted its own JVM with exit 73 while the OS lock was held.
The controller observed that expected failure; it did not treat it as a passed test.
A fresh worker then verified exact H1 ready bytes, exact H2 pending bytes, the
1776-byte total, known READY/PENDING reconciliation and blocked fresh admission.
No pending pair was promoted or deleted. The OS released the dead worker's lock.

The harness owns only a fresh temporary synthetic namespace and its child processes;
phase logs and controls are private and removed afterward. Normal image CI skips
this single opt-in scenario because its deliberate worker halt is unsuitable for an
ordinary suite run. Run the script explicitly with the pinned JDK/toolchain and
cached image-test dependencies; it has bounded worker timeouts and checks the final
one-case XML result. It needs no SDK host, emulator, Docker, server or computer use.

This strengthens development-host process recovery evidence, not Android-filesystem,
ATAK process, full-disk, unsupported-filesystem, power-loss or rollback acceptance.
It changes no production code or format and provides no source/association/receipt
or Data Sync result.


## Standalone native Android acceptance — 2026-11-05 (#150)

A new dedicated headless AOSP **Android 15 / API 35 / arm64-v8a** emulator ran one
standalone debug instrumentation APK, package `se.tmm.sensorplanner.images.test`.
It had no requested permissions, ATAK, accounts, server/client credentials or server. Airplane
mode was on, Wi-Fi/data off and the route table empty before each probe. Only one
1.5-GiB/two-core emulator was used; no Docker or computer-use automation was needed.
APK SHA-256: `2e0edfe0e5dd5348cc6adbf0ba1e4792e9658881bae9703f1be816a96b94cc0a`.

The harness compiled the **actual app storage/payload classes** into the standalone
instrumentation APK and used the independent H1/H2 descriptor/PNG fixtures. It did
not substitute a fake filesystem, codec or PNG decoder. Each run first committed
H1; during H2's real stage attempt the selected milestone signalled a positive
control and killed its own Android process while it held the stage lock. A new
process verified the original receiver-generated IDs and actual on-disk bytes.

| Interruption point | Fresh-process result | Repeat after Android guest reboot |
|---|---|---|
| Pending directory created | Ready H1; empty pending H2; new writes blocked | PASS |
| Descriptor synced | Ready H1; descriptor-only pending H2; new writes blocked | PASS |
| PNG synced | Ready H1; complete pending H2; new writes blocked | PASS |
| Pending pair independently verified | Ready H1; complete pending H2; no automatic promotion | PASS |
| Atomic directory rename completed | Both exact pairs ready; original H2 outcome reconciled READY | PASS |
| Ready readback completed, before caller response | Both exact pairs ready; original H2 outcome reconciled READY | PASS |

Every row passed both immediately and after **`adb reboot` of the Android guest**.
The latter pass used only the original run ID/read path: no APK reinstall, new
stage write, root reset or pending cleanup. Actual file locking, file sync,
NOFOLLOW checks and atomic directory rename worked for these native filesystem
operations; the killed process released its lock. An explicit held-lock control
also returned BUSY. Each complete file matched its fixture; incomplete files were
absent as expected and never rendered as a received image.

Additional native controls passed once, then were checked read-only after reboot:

- Sixteen sparse pending PNG files plus marker at exactly 256 MiB logical size
  were counted; adding one byte failed with CAPACITY while retaining the files.
- A symlink replacing a ready PNG failed with INVALID_STATE; the link and separate
  synthetic target were retained. An unsupported `TMMISTG2` marker was not reset.
- A deterministic high-entropy **1920×1080** image was exported into the closed PNG
  profile, paired with a new coherent descriptor and staged. Its output exceeded
  1 MiB. Actual readback and the reboot pass retained dimensions, length and exact
  SHA-256 against a separate test-only frozen control. This is a synthetic standalone
  memory/filesystem check, not phone-camera/provider or loaded-ATAK heap acceptance.
- The existing native publication parser probe still passed: four independent
  metadata fixtures, exact re-encoding and its malformed controls on Android Expat.

### Repeat with explicit synthetic target

With the pinned public Android toolchain and an explicitly isolated offline emulator:

```sh
./gradlew -p images --no-daemon assembleDebugAndroidTest lintDebug
python3 scripts/test-image-stage-android-guards.py
python3 scripts/test-image-stage-android.py --serial emulator-5584 --adb-port 5039   --kill-after PNG_SYNCED --check-filesystem
```

The controller rejects physical serials, non-emulator runtime, API <26, non-airplane
state, a nonempty route table, wrong APK package or requested permissions before
installation. Its offline guard tests pass without Android tools or any device.
Select each of the six fixed `--kill-after` values for the matrix. Save its synthetic
run ID, reboot the guest, recheck isolation, then repeat without installing/writing:

```sh
python3 scripts/test-image-stage-android.py --serial emulator-5584 --adb-port 5039   --run-id ORIGINAL_SYNTHETIC_RUN_UUID --read-only
# For the run that created the filesystem/Full-HD controls, also pass --verify-filesystem.
```

Native build and image lint passed (zero lint errors/six warnings). The final run
used only synthetic app-owned namespaces. After readback, the standalone test APK
was uninstalled and the owned emulator/dedicated ADB server stopped. Private logs,
run IDs and the six-row result matrix remain outside Git; no screenshot/raw log or
image was published. The plugin version and production runtime implementation were unchanged. Build/test
inputs changed, so this is not a byte-identical signing archive; no signing submission
was performed.

This proves the bounded **standalone API-35** filesystem/process cases, not API-26
native runtime, ATAK's shared UID/lifecycle, physical/OEM storage pressure, a full
disk, unsupported atomic-move filesystem, power failure or old-backup rollback.
It provides no trusted source/feed association, receipt, image adoption or Data Sync
image-delivery evidence. Those gates remain separate.


## Clean native-probe packaging correction — 2026-11-05 (#157)

A subsequent fresh worktree exposed a build-graph defect: registering a mapped
Sync task provider as a legacy asset source did not reliably schedule the four-file
producer. The earlier native APK contained the correct generated fixtures and its
actual runtime results remain valid, but prior incremental output hid the missing
clean-build dependency. A new clean APK omitted those assets and failed the probe
before its first pair could be read; this was not a camera or production-stage fault.

The pinned AGP merge/lint consumers now explicitly depend on the selected producer.
`clean assembleDebugAndroidTest lintDebug` passed, and a separate bounded ZIP check
verified all four expected asset paths, unique entries, lengths and exact independent
fixture bytes. Offline synthetic controls reject missing, changed, duplicated and
oversized entries. Required core/images CI now runs those controls and the actual
APK byte check. No default test-data download, cached asset folder or manual copy
is needed. Recheck the pinned consumer names when changing AGP; the packaging check
must keep failing if fixture production breaks. These are test assets only and
remain outside the production app source set.
