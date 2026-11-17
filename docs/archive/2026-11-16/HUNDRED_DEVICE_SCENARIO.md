# Synthetic 100-device acceptance scenario

Issue [#40](https://github.com/gustavtraff/tmm-sensor-planner/issues/40) prepares the
repeatable input for #12 / N05. It does not establish phone performance, rendering,
field accessibility or ATAK compatibility. No app, import, publishing or schema
behavior changes. The scenario is Java test code, not included in the APK.

## Dataset

`HundredDeviceScenarioTest` creates snapshot v1 through the real `DraftStore` API:

- 100 fixed test UUIDs, 25 each of Mini, Scout, PIR and Gateway.
- 20 each of Planned, Deployed, Active, Retrieved and Cancelled, independently mixed
  with type; 60 initial eligible distance candidates and 15 eligible gateways.
- Manual synthetic labels; records 1 and 51 deliberately share `SYNTHETIC-001` but
  retain different UUIDs. These fixed identities are test inputs only.
- Latitude zero, longitude `index² × 0.00001°` for zero-based indices 0–99, spanning
  0°–0.09801°. All locations are synthetic equatorial inputs.
- Fixed nanosecond timestamps, optional manual deployment time, Unicode/empty
  comments and optional true-north bearing on PIR/Scout. No actual images or local
  image handles are created; photo memory/decoding needs its own trial.

At the equator these distances have the independent oracle
`6371008.8 × π/180 × |longitude difference|` meters. The test checks every source's
nearest eligible UUID, each gateway's complete eligible result count, uniqueness,
order and distance within 0.000001 m of this oracle. This numerical tolerance is a
codec/math check, not geographic accuracy or the UI's rounded distance precision.

All persisted fields are compared after reopening. The mutation phase moves record
11 near record 1 and makes it Active; it becomes the nearest device. Changing it to
Retrieved excludes it, restoring record 2 as nearest. Deleting record 2 leaves 99
records and makes record 3 nearest. Other records retain every field. Stale saves
and refused overwrites leave snapshot bytes unchanged.

## Run automated checks

```sh
JAVA_HOME=/path/to/jdk17 sh scripts/test.sh
```

The existing script compiles Java 8-compatible core/tests and now runs writer,
read-only verifier and mutation checks in separate JVMs. Its temporary dataset is
removed on exit. No Android SDK, emulator, server, new dependency or network is used.
No wall-clock latency threshold is asserted in CI; machine load and filesystem sync
cost differ, and a Java host test is not a trial-phone performance measurement.

## Generate a reusable fresh snapshot

From the repository root, with JDK 17 in JAVA_HOME:

```sh
scenario_build=$(mktemp -d "${TMPDIR:-/tmp}/tmm-scale.XXXXXX")
find core/src/main/java -name '*.java' -print > "$scenario_build/sources.txt"
printf '%s\n' core/src/test/java/se/tmm/sensorplanner/core/HundredDeviceScenarioTest.java >> "$scenario_build/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$scenario_build/classes" @"$scenario_build/sources.txt"
"$JAVA_HOME/bin/java" -ea -cp "$scenario_build/classes" se.tmm.sensorplanner.core.HundredDeviceScenarioTest write "$scenario_build/scenario"
"$JAVA_HOME/bin/java" -ea -cp "$scenario_build/classes" se.tmm.sensorplanner.core.HundredDeviceScenarioTest verify "$scenario_build/scenario"
```

The reusable snapshot is `$scenario_build/scenario/drafts.bin`. Keep it local;
there is no need to commit or upload it. `write` requires a nonexistent target with
an existing parent and refuses existing directories/files/symlinks. There is no
force/overwrite mode. Failed generation may leave a partial new directory; only a
successful verification establishes the complete 100-record baseline. All utility
modes require `-ea` before any filesystem operation. The `exercise` mode intentionally
mutates this synthetic baseline to 99 records; use a separate generated copy for it.

## Later dedicated-client trial

The plugin currently has no supported user-facing snapshot import. This utility
must not replace an existing phone's data. A later trial needs an explicitly reviewed
loading procedure on a dedicated empty developer client, with the same schema and
correct host-private/no-backup directory. Standard-client signing remains #3/#7.

Record exact APK hash, ATAK build, Android version, device/RAM and screen settings.
With the baseline loaded, measure initial load/render, list scrolling, review/map
selection, save/refresh and reopening after restart. Repeat cold and warm runs using
the same timing method; record observed timings and choose an acceptance budget on
the oldest intended phone. Include the mutation/expected-neighbor sequence above,
status/type readability, overlapping points, rotation and plugin unload/reload.
Image decoding, connected privacy and actual sharing are separate tests.

Host-side correctness and safe generation do not close #12 or N05. No phone latency,
100-feature renderer behavior or operational suitability is claimed by these tests.
