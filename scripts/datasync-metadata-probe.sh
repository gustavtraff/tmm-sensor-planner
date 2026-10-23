#!/usr/bin/env sh
# Offline lab fixtures/checker only. Never imports, publishes or connects to ATAK.
set -eu
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/tmm-datasync-probe.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
javac_cmd=${JAVA_HOME:+$JAVA_HOME/bin/}javac
java_cmd=${JAVA_HOME:+$JAVA_HOME/bin/}java
cd "$repo_dir"
find core/src/main/java -name '*.java' -print > "$build_dir/sources.txt"
printf '%s\n' scripts/java/se/tmm/sensorplanner/tools/DataSyncMetadataProbe.java >> "$build_dir/sources.txt"
"$javac_cmd" --release 8 -encoding UTF-8 -d "$build_dir/classes" @"$build_dir/sources.txt"
"$java_cmd" -cp "$build_dir/classes" se.tmm.sensorplanner.tools.DataSyncMetadataProbe "$@"
