#!/usr/bin/env sh
# Standalone synthetic probe on an explicitly selected, already running emulator. No ATAK or server operation.
set -eu
: "${ANDROID_HOME:?Set the inspected Android SDK directory}"
if [ "$#" -ne 1 ]; then
    echo "Usage: sh scripts/test-publication-android.sh EMULATOR_SERIAL" >&2
    exit 1
fi
serial=$1
adb="$ANDROID_HOME/platform-tools/adb"
test "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test "$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" -ge 26
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
./gradlew -p images --no-daemon assembleDebugAndroidTest
log=$(mktemp "${TMPDIR:-/tmp}/tmm-publication-android.XXXXXX")
installed=false
cleanup() {
    if [ "$installed" = true ]; then
        "$adb" -s "$serial" uninstall se.tmm.sensorplanner.images.test > /dev/null 2>&1 || true
    fi
    rm -f "$log"
}
trap cleanup EXIT HUP INT TERM
"$adb" -s "$serial" install -r -t images/build/outputs/apk/androidTest/debug/TMM-Local-Images-debug-androidTest.apk
installed=true
"$adb" -s "$serial" shell am instrument -w \
    se.tmm.sensorplanner.images.test/se.tmm.sensorplanner.core.PublicationCodecInstrumentation > "$log"
cat "$log"
grep -q '^INSTRUMENTATION_RESULT: result=PASS' "$log"
grep -q '^INSTRUMENTATION_CODE: -1' "$log"
