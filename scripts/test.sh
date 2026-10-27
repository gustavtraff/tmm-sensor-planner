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
printf '%s\n' scripts/java/se/tmm/sensorplanner/tools/PublicationEnvelopeVerifier.java >> "$build_dir/sources.txt"
"$javac_cmd" --release 8 -encoding UTF-8 -d "$build_dir/classes" @"$build_dir/sources.txt"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.CoreTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftFormTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.ImportReviewTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.InboxFormatFixtureTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.InboxStoreTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.InboxReviewTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.LocalV2FormatFixtureTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.LocalV2CodecTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.ImportDecisionPreparationTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.ShareReviewTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.PublicationPreparationReviewTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.PublicationFormatFixtureTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.PublicationCodecTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.ShareLabFieldsTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftStoreTest
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftStoreTest write "$build_dir/restart"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.DraftStoreTest read "$build_dir/restart"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.HundredDeviceScenarioTest write "$build_dir/hundred-devices"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.HundredDeviceScenarioTest verify "$build_dir/hundred-devices"
"$java_cmd" -ea -cp "$build_dir/classes" se.tmm.sensorplanner.core.HundredDeviceScenarioTest exercise "$build_dir/hundred-devices"
if "$java_cmd" -cp "$build_dir/classes" se.tmm.sensorplanner.core.HundredDeviceScenarioTest write "$build_dir/assertions-disabled" > "$build_dir/assertions-disabled.log" 2>&1; then
    echo "Scenario utility unexpectedly accepted disabled assertions" >&2
    exit 1
fi
test ! -e "$build_dir/assertions-disabled"
"$java_cmd" -cp "$build_dir/classes" se.tmm.sensorplanner.tools.PublicationEnvelopeVerifier self-test
