# 0001 – start with an SDK-independent core

Date: 2026-10-12. Status: initial technical choice; product assumptions are provisional.
Responsible for final approval: repository owner/product owner, not yet formally appointed.

At the initial decision, the original target SDK 5.5.0 was not available locally. Start with a plain Java 8-compatible core so validation
and map distances can be tested without inventing ATAK APIs. No Android
toolchain will be pinned until the correct SDK is reviewed. Alternatives: documentation only
or copying a later HelloWorld; the latter does not satisfy the SDK gate.

Provisional testable choices: gateway/pir/scout, the plan's five proposed statuses,
bearing from true north, required owner, a 2000-character comment and all
statuses in distance calculations. No automatic status transitions or dates.
These choices must be finalized before freezing the data model/building the UI. No persistence/synchronization
has been introduced, so the current core requires no storage migration.

Consequence: a testable start, but no APK, map integration or storage yet.

Update, 2026-10-12: the user approved API 5.6.0 as the active target in
[ADR 0002](0002-atak-5.6-target.md). A supplied SDK directory labeled full build
5.6.0.24 now contains an app, guide and JARs; the phone is reported as full build
5.6.0.12. This supersedes the SDK availability/target assumption above, while
the SDK-independent core decision and verification gates remain in effect.

The original provisional type list is superseded by the owner-selected Mini, Scout,
PIR and Gateway catalogue in [DEVICE_CATALOGUE.md](../DEVICE_CATALOGUE.md).
