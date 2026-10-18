#!/usr/bin/env sh
# Run only in an approved local environment; never publish SDK inputs or build outputs.
set -eu

: "${JAVA_HOME:?Set JAVA_HOME to the documented JDK 17 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the documented Android SDK installation}"
: "${TMM_ATAK_SDK_DIR:?Set TMM_ATAK_SDK_DIR to the inspected external ATAK SDK}"
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
./gradlew --no-daemon :app:assembleCivDebug :app:lintCivDebug :app:testCivDebugUnitTest
python3 scripts/check-plugin-apk.py app/build/outputs/apk/civ/debug/app-civ-debug.apk
