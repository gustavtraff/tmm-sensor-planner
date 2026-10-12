# 0001 – start with an SDK-independent core

Date: 2026-10-12. Status: initial technical choice; product assumptions are provisional.
Responsible for final approval: repository owner/product owner, not yet formally appointed.

SDK 5.5.0 is not available locally. Start with a plain Java 8-compatible core so validation
and map distances can be tested without inventing ATAK APIs. No Android
toolchain will be pinned until the correct SDK is reviewed. Alternatives: documentation only
or copying a later HelloWorld; the latter does not satisfy the SDK gate.

Provisional testable choices: gateway/pir/scout, the plan's five proposed statuses,
bearing from true north, required owner, a 2000-character comment and all
statuses in distance calculations. No automatic status transitions or dates.
These choices must be finalized before freezing the data model/building the UI. No persistence/synchronization
has been introduced, so the current core requires no storage migration.

Consequence: a testable start, but no APK, map integration or storage yet.
