#!/bin/sh
# Build an unsigned, fixed-fixture developer-host measurement APK; never sign or install it.
set -eu
: "${TMM_ATAK_SDK_DIR:?Set the inspected ATAK5.6.0.24 SDK directory}"
: "${ANDROID_HOME:?Set the cached Android SDK directory}"
: "${JAVA_HOME:?Set JDK17}"
[ "$#" -eq 1 ] || { echo 'Usage: sh scripts/build-geometry-sdk-probe.sh /private/output.apk' >&2; exit 2; }
repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
output=$(python3 - "$repo" "$1" <<'PYCODE'
from pathlib import Path
import sys
repo=Path(sys.argv[1]).resolve();target=Path(sys.argv[2]).resolve()
if target.exists() or not target.parent.is_dir() or target.suffix!='.apk':
    sys.exit('Use a new APK path in an existing private directory')
if target.is_relative_to(repo) and not target.is_relative_to(repo/'private-data'):
    sys.exit('Measurement artifacts must remain outside tracked source directories')
print(target)
PYCODE
)
work=$(mktemp -d "${TMPDIR:-/tmp}/tmm-geometry-sdk.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
mkdir -p "$work/src/main/java/se/tmm/sensorplanner/tools" "$work/src/main/java/se/tmm/sensorplanner/plugin/publication"
cp -R "$repo/core/src/main/java/." "$work/src/main/java/"
cp "$repo/scripts/java/se/tmm/sensorplanner/tools/GeometrySdkInstrumentation.java" "$work/src/main/java/se/tmm/sensorplanner/tools/"
cp "$repo/app/src/main/java/se/tmm/sensorplanner/plugin/publication/PublicationCot.java" "$work/src/main/java/se/tmm/sensorplanner/plugin/publication/"
cat > "$work/settings.gradle" <<'GRADLE'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
rootProject.name='TMMGeometryProbe'
GRADLE
cat > "$work/build.gradle" <<'GRADLE'
plugins { id 'com.android.application' version '8.13.0' }
android {
 namespace 'se.tmm.sensorplanner.geometryprobe'
 compileSdk 36
 buildToolsVersion '35.0.0'
 defaultConfig { applicationId 'se.tmm.sensorplanner.geometryprobe'; minSdk 26; targetSdk 34; versionCode 1; versionName '0.0.1-sdk-measurement' }
 compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
}
repositories { google(); mavenCentral() }
dependencies { compileOnly files(new File(System.getenv('TMM_ATAK_SDK_DIR'),'main.jar')) }
GRADLE
cat > "$work/src/main/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="TMM geometry SDK measurement" android:allowBackup="false" android:usesCleartextTraffic="false"/><instrumentation android:name="se.tmm.sensorplanner.tools.GeometrySdkInstrumentation" android:targetPackage="com.atakmap.app.civ" android:functionalTest="true"/></manifest>
XML
"$repo/gradlew" -p "$work" --offline --no-daemon assembleRelease
python3 - "$work/build/outputs/apk/release/TMMGeometryProbe-release-unsigned.apk" "$output" <<'PYCODE'
from pathlib import Path
import sys
with Path(sys.argv[2]).open('xb') as target:
    target.write(Path(sys.argv[1]).read_bytes())
PYCODE
printf 'Prepared unsigned measurement APK: %s\nNo installation, signature or delivery is claimed.\n' "$output"
