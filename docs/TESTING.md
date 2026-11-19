# Tests and gates

[Current status](NEXT_STEPS.md) distinguishes implementation, dated evidence and
open gates. [Earlier test records](archive/2026-11-16/TESTING.md) are preserved
unchanged. Record new test results in the relevant PR, not in this procedure.

## Public checks

With JDK 17, `sh scripts/test.sh` runs the plain Java domain, store, migration,
conflict, codec and review suites using synthetic fixtures. `.github/workflows/ci.yml`
also runs offline auth/signing-source/attachment and image-format/guard checks.

With the public Android SDK configured as in [development](DEVELOPMENT.md):

```sh
./gradlew -p images --no-daemon testDebugUnitTest lintDebug assembleDebugAndroidTest
python3 scripts/check-image-stage-apk.py
python3 scripts/test-image-stage-restart.py
```

These checks compile the helper sources selected by `images/build.gradle`.
They do not compile the whole ATAK plugin module.

## Private SDK check

Set external `JAVA_HOME`, `ANDROID_HOME` and `TMM_ATAK_SDK_DIR`, then run:

```sh
sh scripts/check-sdk.sh
```

The script checks SDK signatures, assembles `app`'s CIV debug variant, runs lint
and unit tests, then inspects the APK contract. Keep SDK inputs, signing material,
build outputs and raw private logs outside public artifacts. #221 tracks the
owner-selected self-hosted required check; runner registration and enforced
failure testing remain necessary before claiming merge protection for `app/`.

## Manual acceptance and release gates

Use [project-plan A01–A10](PROJECT_PLAN.md#7-acceptance-tests-and-release-gate).
Record exact SDK, client, companion-plugin, Android and server builds separately.
Core/image CI and successful compilation do not establish runtime compatibility.

- Local physical checks: #231 and [phone evidence](PHONE_ACCEPTANCE.md).
- Metadata publication/Fetch/conflicts: #25/#171/#178 and their approved contracts.
- Image export/receive/view: #26/#200/#222 and [the viewing-trial contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md).
- Exercise trial on the real server (about 15 minutes of checks, then go/no-go): [exercise trial protocol](EXERCISE_TRIAL_PROTOCOL.md) (#252).
- Signing, coexistence, information review, migration/upgrade and owner release
  decisions remain applicable to the exact candidate being released.

Server/recipient observation must establish cancellation, privacy, actual bytes,
UUID association and recovery; a sender boolean is not recipient acknowledgment.
Target-server and physical tests require separate authorization. Use synthetic
fixtures; never put raw operational data or credentials in public evidence.

## Candidate 31 pending acceptance

Issue #252 tracks the integrated `versionCode 31` candidate. Signing/package
verification is complete; native two-client workflow acceptance is open. Use the
approved [automatic-sync decisions U1–U8](AUTOMATIC_FEED_SYNC_PROPOSAL.md) and
[metadata-route decision M7](PUBLISH_METADATA_ROUTE_PROPOSAL.md#m7--verification-before-a-candidate)
as the acceptance source, with [the exercise protocol](EXERCISE_TRIAL_PROTOCOL.md)
as the UI sequence. Record each check as pass, fail or not run, with the exact build.

- Native initial **Add Items → Done**; automatic receipt without a manual import.
- The other client updates position, status, bearing and photo on the same UUID;
  the first receives the update automatically without duplication.
- Native **Nej** leaves the change unpublished and starts no photo upload; cancelling
  or interrupting an attempt must not start later publication requests.
- Status-only updates preserve the photo; photo-only and no-photo cases work.
- Conflicts retain both versions; both **Behåll min** and **Använd inkommande** work.
- Restart preserves following and resumes fetch; reconnect catches up; interrupted
  fetch/photo work loses no committed data. In the loopback lab, remove the owned
  ADB reverse tunnel to simulate loss of transport: airplane mode alone leaves it live.
- Observe intended requests and no automatic outbound publication. Include a second
  subscribed feed as M7's negative control and configured-publication controls 2, 6,
  7 and 8. Agent server access remains loopback-only.
- Owner repeats the real-server 5.6 exercise checks and records go/no-go. Keep the
  physical/coexistence, upgrade and release gates above in scope for that candidate.

## Resource-bounded lab runs

Do not run two Android emulators plus the TAK Docker server on the 8 GiB development
laptop. Guest `-memory` is not a host process limit. Use a larger-memory lab host for
the full concurrent run; do not substitute sequential single-client tests for it.
An alternative arrangement needs its isolation and server authorization reviewed
before use; this procedure does not authorize non-loopback server access.

When the owner is ready to resume, first measure **one** foreground emulator with
the server off, using a disposable copy of its AVD. Reduce its screen resolution
and guest memory in that copy, then verify readability and stability. Change one
setting at a time. Software-rendering and graphics overhead must be measured along
with guest RAM; RSS alone misses compressed memory. Do not change the signed APK.

Use the macOS guard for that one-client measurement:

```sh
python3 scripts/lab-memory-guard.py --seconds 300 -- /absolute/path/to/emulator [isolated lab arguments]
```

The placeholder command above must be replaced with the reviewed loopback-isolated
foreground emulator invocation. Do not start a daemon, use a detached launcher, or
run `docker start` under this wrapper: the command must own its process group for
its entire lifetime and must not leave workers after exit. The guard stops only
that group; it does not control Docker's separate VM or unrelated applications.

The default host budget requires normal OS memory pressure, no more than 1024 MiB
of compressed memory and 512 MiB of used swap, checked before launch and every two
seconds. Sampling failure or the ten-minute default deadline also stops the test.
TERM is followed by KILL for the owned group. These conservative thresholds are a
preflight budget, not a guarantee against a sudden allocation or host failure. If
preflight refuses, defer the run; do not raise limits to force it onto a busy laptop.

Before a full lab run elsewhere, set a total host budget including Docker, cap the
owned containers and VM, and arrange independent deadline/memory supervision of
those services too. Record owned process identities and verify cleanup. A launcher
that detaches from the guard is not covered. Keep measurement logs private and put
only sanitized numeric results in the PR.

Verify the guard without a lab or real memory pressure:

```sh
python3 scripts/test-lab-memory-guard.py
```
