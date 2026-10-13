# Headless emulator loading test

The first test environment was prepared on 2026-10-13 using command-line tools.
No Docker container or desktop computer-use automation is needed.

| Component | Local setup |
|---|---|
| Host | Apple Silicon / arm64; Hypervisor.Framework acceleration available |
| Emulator | Official Android Emulator 37.2.12 |
| Android guest | API 35, default AOSP arm64-v8a image, revision 2 |
| Virtual device | TMM_ATAK_56, 2 GB RAM, 4 GB data partition |
| Android SDK tools | Local staging under /private/tmp/tmm-android-sdk |
| AVD state | Separate /private/tmp/tmm-emulator-avd and /private/tmp/tmm-emulator-user |
| Test host app | SDK developer ATAK-CIV 5.6.0.24 |
| Plugin | Android Debug loading probe 0.1.0-dev |

Both APK installations passed. The emulator booted with native Mac acceleration.
Airplane mode is enabled, Wi-Fi and mobile data are disabled, and the guest route
table was empty before ATAK started. No server profile, account, feed or certificate
was provisioned. The operational phone is untouched.

## Observed results — 2026-10-13

The tested APK was built from merged main `c55709d`, package
`se.tmm.sensorplanner.plugin`, version `0.1.0-dev`, plugin API `5.6.0.CIV`.
Its SHA-256 is `221e37832331a6980994a6ba9cd19f5add6d4bbcb1f0fbf8f0f546eb9426cc29`.

| Check | Observation |
|---|---|
| Discovery | Plugin Manager listed TMM Sensor Planner as compatible and not loaded |
| Load | Manager changed to Loaded; registry instantiated SensorPlannerPlugin |
| Toolbar registration | One TMM Sensor Planner entry appeared in Tools; icon conversion completed without exception |
| Click | Android's notification service recorded a toast with text `Lokal prototyp` |
| Unload | Manager changed to Not loaded; the Tools entry disappeared |
| Reload | Manager returned to Loaded |
| Host restart | After force-stop/relaunch, registry instantiated the plugin, one Tools entry appeared, and clicking it again produced `Lokal prototyp` |
| Isolation | Airplane mode remained enabled; guest route table was empty before the host launch and after restart |

The SDK host explicitly logged that it **skipped the signature check**. This proves
loading only in the developer host; it does not validate production signing.
No server profile, feed, account or certificate was configured. The emulator was
stopped after the test; its state was retained locally. No plugin code change was needed.

The owner explicitly approved both the TAK license and ATAK's required **manage all
files** permission for this separate emulator. Camera, foreground location,
microphone, notifications and audio access were granted inside the synthetic guest.
Photo/video, phone and nearby-device access were denied; ATAK's built-in permission
override was selected. Background location and unrestricted background operation
were not granted. These choices are test setup, not recommendations for field use.

## Graphics workaround

Both initial `swiftshader` and `host` runs failed in the host's GLThread with
`IllegalArgumentException: No config chosen`, before plugin loading was tested.
The official [TAK emulator guidance](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/README.md#using-an-emulator)
describes a generic EGL configuration fallback. Creating the following marker inside
the synthetic guest resolved startup with `-gpu host`:

```sh
adb -s emulator-5580 shell touch /sdcard/atak/opengl.broken
```

The app then logged its fallback to generic EGL configuration. This is a local host
app/emulator workaround, not a plugin fix. Map rendering was sufficient for this
loading probe; rendering quality and performance were not evaluated.

## Remaining gates

This is a development loading probe, not the completed sensor planner. Standard
ATAK-CIV 5.6.0.12 installation/signing, physical-phone use, companion plugins, local
map privacy, storage and sharing remain unverified. Issue #3 remains open for the
physical-client gate; an emulator does not replace the project plan's release tests.

## Repeat the local setup

The staged paths are temporary, not a permanent development installation.
Use your own durable SDK/JDK paths for long-term use. To start the existing staged
test device after stopping it:

```sh
ANDROID_AVD_HOME=/private/tmp/tmm-emulator-avd \
ANDROID_USER_HOME=/private/tmp/tmm-emulator-user \
ANDROID_EMULATOR_HOME=/private/tmp/tmm-emulator-user \
ANDROID_HOME=/private/tmp/tmm-android-sdk \
/private/tmp/tmm-android-sdk/emulator/emulator \
  -avd TMM_ATAK_56 -port 5580 -no-window -no-audio -no-snapshot \
  -camera-back none -camera-front none -gpu host -accel on
```

Always target the synthetic emulator explicitly; do not use an unqualified adb
install command that could select a physical phone:

```sh
adb -s emulator-5580 shell getprop sys.boot_completed
adb -s emulator-5580 shell cmd connectivity airplane-mode enable
adb -s emulator-5580 shell svc wifi disable
adb -s emulator-5580 shell svc data disable
adb -s emulator-5580 shell ip route
```

Do not launch ATAK until offline status has been checked. See
[LOCAL_TEST.md](LOCAL_TEST.md) for the complete loading checklist and the distinction
between developer and standard host apps.

To stop the existing test emulator without deleting its state:

```sh
adb -s emulator-5580 emu kill
```

Screenshots and device logs are local diagnostic material. Keep them outside Git;
publish only sanitized results. This environment does not establish compatibility
with the owner's standard 5.6.0.12 app or companion plugins.

Sources: [Android command-line emulator documentation](https://developer.android.com/studio/run/emulator-commandline),
[hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration).
