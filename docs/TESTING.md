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
