# ADR 0008: Guarded offline local planner adapter

Date: 2026-10-17. Status: proposed for Issue #12 PR review.

## Decision

Connect the validated Java model/store to a Swedish Android list/form/review UI and
one in-memory SDK feature layer. The toolbar checks API 26 before constructing this
adapter; APK minSdk remains 21 and older clients get an unavailable message. This
prototype does not claim planning support below Android 8. Keep the existing offline
restriction and synthetic-data boundary while production privacy/phone gates remain open.

Use the host context's namespaced no-backup directory because the plugin runs under
ATAK's UID. Do not assume the installed plugin APK's app directory is writable under
that UID. Store I/O uses one background executor; only acknowledged saved data becomes
the displayed snapshot. Raw storage failures/fields are not logged.

Keep one FeatureSetDatabase2/FeatureLayer3 for the adapter's lifetime. Rebuild feature
sets under the supplied SDK bulk-modification lock. Remove the layer, then queue its
database disposal after renderer release on the same GL queue. The initial per-refresh
store replacement crashed in the SDK emulator when its query worker reached a closed
store; this approach was removed. No normal Marker/MapItem, export or CoT handler is
registered for domain drafts. Map clicks are projected proximity selection only.

## Alternatives and consequences

NIO desugaring adds an unverified plugin-classloader runtime and documented behavioral
differences (including missing-file exceptions); it is not introduced just to promise
untested API 21–25 support. Native APIs plus an explicit guard keep current scope small.
The core-only NewApi lint exclusion is necessary because plain Java sources cannot
import Android annotations. All app callers remain API-26-annotated and guarded; app
NewApi checks remain enabled. Adding a new core caller requires preserving this boundary.

The UI defaults to map-center decimal coordinates, explicit true-north bearing and
manual UTC deployment input. Editable coordinates move a device; drag tools are not
added. Type letters/status text avoid reliance on color alone. Original symbols do not
copy manual artwork. Image files, sharing and physical signing/coexistence remain separate.
Full Android 5–7 support and production connected privacy require additional evidence
before release, not a silent change of support promises. See [LOCAL_PLANNER.md](../LOCAL_PLANNER.md).
