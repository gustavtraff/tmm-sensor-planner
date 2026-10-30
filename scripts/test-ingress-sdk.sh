#!/usr/bin/env sh
# Optional SDK signature proof only; never registers callbacks or starts a host/network.
set -eu
: "${JAVA_HOME:?Set the documented JDK 17 installation}"
: "${ANDROID_HOME:?Set the inspected Android SDK directory}"
: "${TMM_ATAK_SDK_DIR:?Set the inspected ATAK 5.6.0 SDK directory}"
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/tmm-ingress-sdk.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
sdk_classpath="$TMM_ATAK_SDK_DIR/main.jar:$ANDROID_HOME/platforms/android-36/android.jar"
find "$repo_dir/core/src/main/java" -name '*.java' -print > "$build_dir/sources.txt"
printf '%s\n' "$repo_dir/scripts/java/se/tmm/sensorplanner/tools/IngressSdkCheck.java" \
    "$repo_dir/app/src/main/java/se/tmm/sensorplanner/plugin/ingress/IngressProbeState.java" \
    "$repo_dir/app/src/main/java/se/tmm/sensorplanner/plugin/publication/PublicationEnvelopeFixtures.java" >> "$build_dir/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$sdk_classpath" -d "$build_dir" @"$build_dir/sources.txt"
"$JAVA_HOME/bin/java" -cp "$build_dir:$sdk_classpath" se.tmm.sensorplanner.tools.IngressSdkCheck
