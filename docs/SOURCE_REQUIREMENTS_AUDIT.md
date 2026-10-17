# Third-party source archive requirements audit (#7)

Date: 2026-10-18. Baseline: the owner's repeated
[full requirements snapshot](references/TAK_SOURCE_ARCHIVE_REQUIREMENTS.md).
Before this audit, the project retained summaries and links, not the complete
supplied text in a dedicated file.

## Submitted artifact

- Source commit: `971a9ab56a55e6e60876d7300fdb1d8bf7d4d754`.
- Filename: `TMM-Sensor-Planner-source-main-971a9ab.zip`.
- Expected SHA-256: `b484de885ec605c56eee514f4b5a04675f55ea8165e53838a8e075dccec11d27`.
- The owner reports a failed submission and only `Build failed` from Download.
  No job-stage log exists locally. Submitted bytes were not independently hashed.

## Requirement-by-requirement result

| Supplied requirement | Actual evidence | Submitted ZIP result / correction |
|---|---|---|
| ZIP with one root folder; root names output APKs | ZIP CRC passes; all entries under `TMM-Sensor-Planner/`; 57 files | Pass for structure. Root name is deliberate; actual service APK naming remains unobserved. |
| Gradle build scripts and associated folder inside that root | Root `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, wrapper JAR/properties, app/image build scripts are present; wrapper executable mode was preserved | Pass. Extracted source previously built/linted outside Git. |
| Define `assembleCivRelease` | Local SDK execution of `:app:assembleCivRelease` passed; Gradle task help identifies the task | Pass for task definition. Not proof of a remote service build. |
| Resolve all ATAK SDK references through TPC takdev, including SDK-dependent libraries | App applies `atak-takdev-plugin`; remote mode uses TPC Maven and forced Maven takes precedence. Source scan finds 10 Java files with ATAK namespace references, all in app. Core/images have none | Pass for inspected source configuration. Actual remote dependency resolution is blocked. No local SDK/JAR is included in the ZIP. |
| For target >=4.2 use latest 2.x (`2.+` allowed) | Target is API 5.6.0; submitted app build selects `3.+` | **Fail.** PR #46's SDK-aligned candidate did not meet this explicit service requirement. This branch restores `2.+`; successful resolution/latest available 2.x remains unverified. |
| For target <4.2 use latest 1.x | This project targets API 5.6.0 | Not applicable. Do not switch to 1.x. |
| Successful forced Maven build with authorized artifacts credentials | Previous online attempts with empty credentials failed parsing metadata; HTTP inspection found an Appgate SDP HTML redirect. No authorized account/access was used | **Blocked / not verified.** Local SDK success is not a substitute. The text restricts artifact access to USG personnel; do not claim this preflight passed or service success is guaranteed. |
| Replace template ProGuard repackaging with a plugin descriptor | Configured `app/src/main/proguard-rules.pro` contains `-repackageclasses atakplugin.tmm.sensorplanner`; `PluginTemplate` is absent | Pass for the configured rule. The supplied text describes replacing an entry; it does not explicitly require renaming the rule file to `proguard-gradle.txt`. Standard-host mapping/runtime remains unverified. |
| ATAK discovery activity, MissingClass ignore, labelled intent filter and action | Parsed the archived XML and checked all four entries, including `@string/app_name` | Pass. Extra `android:exported="true"` is retained; it does not remove the required registration. |

## Correction and verification boundaries

The 3.x SDK/public examples explain the earlier selection, but they do not prove
that the supplied source-service requirement can be waived. Treat the 3.x trial
as noncompliant with that explicit requirement. Its failure cause is still unknown;
there is no evidence that changing to 2.x alone will make the service build pass.
No claim of 2.x compatibility with AGP 8.13.0 / Gradle 8.14.3 is made without a
successful build using that Maven artifact.

The branch restores the service candidate to `2.+` and adds a bounded source-ZIP
guard for this project's literal Maven coordinate. Five offline tests include
rejection of wrong/missing versions and the existing packaging boundaries. A
one-off check also confirmed that this guard rejects the actual submitted source
commit. This is a static check of this repository's known build-script form, not
a general Gradle parser, dependency resolver or service acceptance test.

Record local build/test results and the corrected archive's exact source commit
and checksum in the PR and #7. Do not silently replace the previous archive or
resubmit. A reviewed corrected archive can be used for a new owner-approved trial,
but authorized Maven/service verification, returned signature, standard-client
loading and A08 remain open. The full snapshot must be updated only when new
service requirements are supplied or independently verified; changing the checker
alone does not change the external contract.

## Corrected-branch checks actually run

- `python3 scripts/test-signing-source.py`: five tests passed, including four
  wrong/missing-version subcases and the existing boundary tests.
- The new guard rejected the actual noncompliant `971a9ab` source snapshot.
- Gradle `help --task assembleCivRelease` identified `:app:assembleCivRelease`.
- Local SDK `:app:assembleCivRelease :app:lintCivRelease` passed (27 seconds),
  using SDK tooling 3.6.0, not a Maven-resolved 2.x artifact.
- Patch-whitespace checks passed. Authorized Maven preflight remains blocked;
  these local results do not change that status.
