#!/usr/bin/env sh
# Local-only verification with the external SDK; no SDK files are copied into Git.
set -eu
: "${TMM_ATAK_SDK_DIR:?Set the inspected ATAK SDK directory}"
: "${JAVA_HOME:?Set JDK 17}"
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT HUP INT TERM
"$JAVA_HOME/bin/javac" -cp "$TMM_ATAK_SDK_DIR/main.jar" -d "$classes" \
 core/src/main/java/se/tmm/sensorplanner/core/Position.java \
 app/src/main/java/se/tmm/sensorplanner/plugin/MgrsCoordinates.java \
 app/src/main/java/se/tmm/sensorplanner/plugin/MgrsPositionEdit.java \
 app/src/sdkTest/java/se/tmm/sensorplanner/plugin/MgrsCoordinatesSdkTest.java
"$JAVA_HOME/bin/java" -cp "$classes:$TMM_ATAK_SDK_DIR/main.jar" se.tmm.sensorplanner.plugin.MgrsCoordinatesSdkTest
