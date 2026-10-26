#!/usr/bin/env sh
# Offline synthetic checker only; no SDK, emulator, file import or transport.
set -eu
: "${JAVA_HOME:?Set the documented JDK installation}"
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/tmm-envelope-check.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$build_dir/classes" \
    scripts/java/se/tmm/sensorplanner/tools/PublicationEnvelopeVerifier.java
"$JAVA_HOME/bin/java" -cp "$build_dir/classes" se.tmm.sensorplanner.tools.PublicationEnvelopeVerifier "$@"
