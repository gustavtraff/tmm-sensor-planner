#!/usr/bin/env sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/tmm-core-test.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    javac_cmd="$JAVA_HOME/bin/javac"
    java_cmd="$JAVA_HOME/bin/java"
else
    javac_cmd=javac
    java_cmd=java
fi

cd "$repo_dir"
find core/src/main/java core/src/test/java -name '*.java' -print > "$build_dir/sources.txt"
"$javac_cmd" --release 8 -encoding UTF-8 -d "$build_dir/classes" @"$build_dir/sources.txt"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.CoreTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftFormTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftStoreTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftStoreTest write "$build_dir/restart"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftStoreTest read "$build_dir/restart"
