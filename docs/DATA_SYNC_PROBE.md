# Offline metadata probe for the proposed Data Sync experiment

Tracking: #77 / #25, building on [the integration investigation](DATA_SYNC_INTEGRATION.md).
This tool prepares and checks synthetic files **without connecting, importing or
publishing**. It changes no app, SDK adapter, snapshot, production wire schema or
sharing gate. It is not a Data Sync integration or a substitute for the #4/#7 tests.

## What it prepares

With JDK 17 selected, from the repository root:

```sh
sh scripts/datasync-metadata-probe.sh self-test
sh scripts/datasync-metadata-probe.sh create /private/path/to/NEW_DIRECTORY
```

The parent must already exist; the output directory must not exist. Failed creation
may leave partial synthetic output; discard that separate directory before retrying.
Never point the command at a draft store or a phone folder. No existing file is
replaced. The script compiles core and the standalone tool into a temporary directory
and removes compiled files on exit. It does not use Gradle, an AVD, SDK binaries,
server settings, accounts, image files or network tools.

Six .cot files have fixed synthetic UUIDs/metadata: Mini, Scout, PIR, Gateway,
a same-UUID PIR update and a distinct same-UUID `pir-conflict` variant. The update
changes status, position, timestamp, deployment
time, comment and bearing. Unicode/newline/tab and precise numeric values exercise
field preservation. The envelope time is fresh at generation, with one-hour stale;
regenerate a new directory for a later lab run. Metadata is deterministic; envelope
timestamps are not. Expiry is not a TMM retention policy.

The payload intentionally reuses the **existing experimental** ShareLabFields
encoder/decoder (`tmmShareLab`, experiment issue-14). It is a test canary rather than
a proposed production Data Sync schema. No new parallel encoding contract is added.
The metadata comes from ShareReview's projection, with no image handle, bytes or
local revision counter. All coordinates are synthetic and files are visibly lab
labelled. Do not import these files on an operational client/server or use them to
convert private drafts into ordinary MapItems.

## Checking a returned event

Once a separately reviewed isolated experiment supplies one recipient-exported
CoT XML event, select its exact case:

```sh
sh scripts/datasync-metadata-probe.sh check pir /private/path/returned-event.cot
sh scripts/datasync-metadata-probe.sh check pir-update /private/path/returned-update.cot
```

The caller must choose a single CoT XML event. A feed ZIP, Mission API JSON result,
KML, file attachment, wrapper document or collection of events is not accepted.
The checker never extracts an archive, writes to the input, imports to DraftStore
or follows a reference to another file.

MATCH means only that the fixed expected UUID and canonical TMM metadata survived
in this supplied event, with a matching seven-decimal outer waypoint. The existing
ShareLabFields decoder validates canonical text encoding, optional fields, types,
statuses, units and exact embedded coordinates. Lost, changed, extra or ambiguous
payload fields fail; the base PIR case cannot pass as the updated case. Harmless
attribute order, host contact-label and envelope timestamp changes are tolerated.
Other host detail extensions may be present; they are not TMM data.

This is deliberately **not** an exhaustive CoT validator, envelope freshness check,
feed/permission validator, server-origin authenticator, transport implementation,
acknowledgment or storage test. It does not verify arbitrary events, unsigned source
identity, feed membership, global non-recipient absence or image delivery. A local
copy of a source fixture also matches; independent provenance of receiver captures
and server observations must be recorded by the test operator. No successful
Data Sync path is inferred by generating files or feeding them straight back.

## Input and diagnostics boundary

Input is bounded to 256 KiB even if a file grows while being read. JAXP parsing
rejects DOCTYPE, external entities/schema access and XInclude processing; XML depth
is capped at 32. The payload cannot contain nested elements or non-whitespace text.
Errors are generic and nonzero, without parser exception text, field values or
input paths. Keep actual exported events private even when the checker fails.
The core lab decoder remains unchanged. No attacker-supplied metadata is stored.

## Handoff after the parallel privacy run

1. Obtain reviewer-accepted #4/#7 privacy evidence, including Data Sync automatic
   publication and reconnect queues. Coordinate exclusive test-client/server use;
   this branch does not start or reset any shared lab resources.
2. Record exact standard host/Data Sync/plugin/server versions and artifact hashes.
   Establish a normal stock-marker selected-feed baseline with positive controls.
3. Only then test an explicitly reviewed synthetic representation in one selected
   feed. Confirm a second test feed/non-recipient does not receive it. Input files
   alone do not establish how TMM can expose a safe publication representation;
   any adapter or import action remains a separate, reviewed spike.
4. Preserve independently obtained recipient/export evidence privately. Check the
   right baseline case and later same-UUID update. Record feed selection, Cancel,
   permission denial, duplicate receipt, reconnect, restart and conflict behavior
   separately. A check failure needs investigation; do not relax the codec just to
   make a transformed/lossy event pass.
5. Record exact observations and limitations in #25. Durable received TMM records
   and conflict UI are still #15; images remain excluded and deferred to #26.

If the privacy/API gate blocks runtime work, this tool remains preparation only;
no operational substitute or automatic transport activation is implied.

## Verification

`python3 scripts/test-datasync-metadata-probe.py` runs the in-process self-checks
and real CLI cases in a temporary directory, including paths with spaces, all six
fixtures, same-UUID update mismatch, missing input, bounded oversized input,
read-only byte preservation, refusal to overwrite existing output and generic
non-leaking diagnostics. Self-checks exercise envelope tolerance, all missing
payload fields, changed UID/bearing/position, duplicate payload, forbidden image
reference, malformed/empty/oversized XML, namespace/text and external-entity/depth
rejection. Self-checks use explicit checks and do not depend on Java assertions.

Local execution passed with the pinned JDK 17; `sh scripts/test.sh` also passed.
The public core CI job now includes the probe check. No CI result is awaited or
claimed. No Android build, image suite, SDK runtime, emulator, server, phone, signing
submission or actual Data Sync transfer was run for this preparation.

## Standard-host selection result — 2026-10-24

[The runtime trial](DATA_SYNC_METADATA_TRIAL.md) found the canonical PIR waypoint
on the map, but Data Sync rejected its selection with “Cannot select this item.”
Adding the verified host archive detail did not make it selectable. A separate
native-marker diagnostic preserved all 15 payload fields in B's own feed export;
it intentionally fails the canonical checker because its UID/type differ. Do not
interpret that diagnostic as a canonical pass or relax the checker to hide it.

## Explicit sensor-point lab cases (#83)

[ADR 0011](decisions/0011-sensor-point-data-sync-lab.md) proposes a separate
SDK-registered sensor-point envelope for manual Data Sync testing. The original
`create` and `check pir` behavior remains unchanged. With JDK 17:

```sh
sh scripts/datasync-metadata-probe.sh create-sensor /private/path/to/NEW_SENSOR_DIRECTORY
sh scripts/datasync-metadata-probe.sh check sensor-pir /private/path/returned-sensor-event.cot
```

This creates `sensor-mini`, `sensor-scout`, `sensor-pir`, `sensor-gateway` and
`sensor-pir-update` and `sensor-pir-conflict` files with outer type `b-m-p-s-p-loc`, host archive detail and
visible TMM-SENSOR-LAB labels. UUIDs use the distinct synthetic `8083` segment;
All three PIR versions share their case UUID. All 15 custom payload attributes are the
same as the corresponding original cases. The case-specific checker requires
that declared type/UUID and rejects the original waypoint or friendly diagnostic;
the old checker likewise rejects sensor cases. Nothing is silently reclassified.

[The actual PIR trial](DATA_SYNC_SENSOR_TRIAL.md) passed selected-feed delivery and
B's own export check with another subscribed feed's auto-publication on and prompt
off, including checked-selection cancellation and before/after native controls.
The later [same-UUID trial](DATA_SYNC_UPDATE_TRIAL.md) also checked a real server
update and B's exact exported update after online restart, while reconnect/list
refresh still returned old metadata. A later [controlled reconnect](DATA_SYNC_RECONNECT_TRIAL.md)
returned the exact update before manual sync or restart; full sync and local
history downloads also preserved their selected versions. These are bounded lab
results; the earlier stale interval's cause remains unknown.
Mini/Scout/Gateway runtime cases remain untested. This command still only generates
files; it never imports, creates a MapItem, publishes, reads private stores or
approves a production schema/adapter. Its safe bounds/overwrite behavior is shared
with the original command. App integration must verify its exact creation path.

The `pir-conflict` / `sensor-pir-conflict` case adds a third same-UUID version for
concurrent-edit experiments: Deployed status, 90-degree bearing, a distinct comment,
updated time and precise latitude. Base/update/conflict all reject each other's
metadata; the earlier five cases retain their identities and payloads. This is an
offline test variant, not a production conflict policy or schema change.

[The acceptance trial](DATA_SYNC_ACCEPTANCE_TRIAL.md) used the new case for B's
actual offline edit against A's different update; its own exported fields passed
before reconnect and the explicitly selected server version passed afterward.
