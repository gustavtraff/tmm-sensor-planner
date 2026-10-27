#!/usr/bin/env sh
# Optional local SDK proof; no importer, server, emulator or Android build is started.
set -eu
: "${JAVA_HOME:?Set the documented JDK 17 installation}"
: "${ANDROID_HOME:?Set the inspected Android SDK directory}"
: "${TMM_ATAK_SDK_DIR:?Set the inspected ATAK 5.6.0 SDK directory}"
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir"
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/tmm-publication-sdk.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
sdk_classpath="$TMM_ATAK_SDK_DIR/main.jar:$ANDROID_HOME/platforms/android-36/android.jar"
find core/src/main/java -name '*.java' -print > "$build_dir/sources.txt"
printf '%s\n' scripts/java/se/tmm/sensorplanner/tools/DataSyncMetadataProbe.java \
    scripts/java/se/tmm/sensorplanner/tools/PublicationSdkProbe.java \
    app/src/main/java/se/tmm/sensorplanner/plugin/PublicationProbeFixture.java \
    scripts/java/se/tmm/sensorplanner/plugin/PublicationFixtureCheck.java \
    app/src/main/java/se/tmm/sensorplanner/plugin/publication/PublicationCot.java \
    app/src/main/java/se/tmm/sensorplanner/plugin/publication/PublicationMapCopy.java \
    scripts/java/se/tmm/sensorplanner/plugin/publication/PublicationMapCopyCheck.java \
    app/src/main/java/se/tmm/sensorplanner/plugin/publication/PublicationEnvelopeFixtures.java \
    scripts/java/se/tmm/sensorplanner/tools/PublicationEnvelopeVerifier.java \
    scripts/java/se/tmm/sensorplanner/plugin/publication/PublicationEnvelopeSdkCheck.java >> "$build_dir/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$sdk_classpath" \
    -d "$build_dir/classes" @"$build_dir/sources.txt"
"$JAVA_HOME/bin/java" -Duser.language=en -Duser.country=US \
    -cp "$build_dir/classes:$sdk_classpath" se.tmm.sensorplanner.tools.PublicationSdkProbe
for locale in en sv; do
    "$JAVA_HOME/bin/java" -Duser.language="$locale" -Duser.country=SE \
        -cp "$build_dir/classes:$sdk_classpath" se.tmm.sensorplanner.plugin.publication.PublicationMapCopyCheck
    "$JAVA_HOME/bin/java" -Duser.language="$locale" -Duser.country=SE \
        -cp "$build_dir/classes:$sdk_classpath" se.tmm.sensorplanner.plugin.publication.PublicationEnvelopeSdkCheck
    "$JAVA_HOME/bin/java" -Duser.language="$locale" -Duser.country=SE \
        -cp "$build_dir/classes:$sdk_classpath" se.tmm.sensorplanner.plugin.PublicationFixtureCheck "$build_dir/$locale.cot"
    "$JAVA_HOME/bin/java" -cp "$build_dir/classes" se.tmm.sensorplanner.tools.DataSyncMetadataProbe \
        check sensor-pir "$build_dir/$locale.cot"
done
"$JAVA_HOME/bin/java" -Duser.language=sv -Duser.country=SE \
    -cp "$build_dir/classes:$sdk_classpath" se.tmm.sensorplanner.tools.PublicationSdkProbe
