# Signed v28 standard-client metadata trial — #173 / #171

Date: 2026-11-08. This records the owner's returned v28 candidate in the retained
isolated lab. Individual completed observations below are not a claim that all
round-trip, phone or operational-server gates are complete.

## Artifact and environment

| Field | Observed value |
|---|---|
| Package | `se.tmm.sensorplanner.plugin` |
| Version | 28 / `0.1.0-configured-receive-trial.1` |
| APK bytes | 632,415 |
| APK SHA-256 | `35e2727b493b0d6273aa5449a1120c86e96ce198ea41a0d60d21b6f0090c2cd6` |
| Signing certificate SHA-256 | `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17` |
| Expected submitted source | main `0e4f1e7e69d0b08dafeabfa2e6ba4583dbc3ead8` |
| Source ZIP SHA-256 | `12a67aa414c9eb83828523844db4e4d59f91d0b1f6998cd010eb26b3ec4f4fe6` |
| Standard host | ATAK-CIV 5.6.0.25 (238484ee) |
| Native Data Sync | 3.7.4 (58746181) — [5.6.0] |
| Android | 15 / API 35, arm64 emulator |
| Server | Retained isolated TAK Server 5.8-RELEASE-92 |

The actual signature verifies and matches the accepted v21/v23 signer. Discovery,
ATAK API, permissions and packaging checks pass. The service log reports a
successful 2m24s build and assembleCivRelease. Returned R8 mapping contains the
configured-source, endpoint/chooser and reviewed-host-update classes. These are
owner/result/version associations, not a reproducible-build attestation. Existing
unprotected META-INF metadata-entry warnings remain. The misleading unsigned APK
filename does not replace actual signature verification.

Only one emulator is active at a time. Guests use a socket network without NAT,
first-rule IPv4/IPv6 loopback-only egress, no default route, airplane mode and
only the owned TLS/HTTPS tunnels. Strict mutual TLS, hostname checks and normal
standard-host/plugin signing remain enabled. No operational profile, server,
position, image or account is used. Raw app state, APKs, server payloads and private
captures remain outside Git.

## Initial publisher B controls

B updates in place from signed v23 to v28 without uninstalling. Both existing TMM
frame/lock files remain byte-identical. The one previous saved draft reopens.
Native Data Sync's tool opens and reports the actual selected lab feed, rather
than merely being present as an installed package.

A new synthetic Mini is created through TMM's real form and explicit local Save.
Its fresh UUID is absent from all six known accessible lab feeds. The selected
feed and comparison feed are both subscribed with auto-publication off. A cancelled
copy review preserves every owned TMM file and leaves the canary absent from all
six feeds. A separately confirmed SDK copy reports SUCCESS, one host UUID and
verified exact map copy. All owned private files remain unchanged, and the canary
still has no feed membership before native Data Sync selection/confirmation.

At the native selected-feed Add Items → Overlays → Markers → Mission selector,
only the new canary's checkbox was selected before Done. The selector's canvas
controls are not fully exposed in its compressed accessibility tree; a cropped
synthetic-row-only view verified the single checkmark. No map image was retained.
After Done, independent strict-TLS reads find exactly one canary in the selected
feed and zero in the other five known accessible feeds. All 15 attributes exactly
match the canonical projection generated from B's actual saved v2 record, including
UUID, creation/update times and exact numeric position. Both private TMM files
remain byte-identical to the post-local-create baseline. This is a positive native
publication result, not merely an SDK persist/readback claim.

## Independent signed receiver A

B is gracefully stopped before A starts. A updates from signed v23 to the same
v28 without uninstalling. Its 21 active drafts, two tombstones and generated owned
PNG survive; all three existing owned files are byte-identical. The plugin opens
its retained local list.

The configured-source selector presents the actual connected loopback SSL profile.
Cancelling the missing-history warning preserves every existing file and creates
no inbox frame. The normal storage read does leave its empty infrastructure lock;
that is not initialized history or a source grant. Explicit Start creates the
empty history, and the real authenticated feed list appears. Cancel at selected-
feed review leaves all five current state/image files byte-identical.

A separately confirmed Fetch validates all three TMM members and stores them in
the genuine inbox. An independent JVM reads the captured InboxStore and verifies
one canary receipt in the exact logical-service/mission-GUID context, all 15 fields
matching B's actual saved-record projection, and no incoming image handles. Every
pre-existing draft/frame and owned image remains byte-identical. Fetch itself has
not added a local canary or changed the previous drafts.

A cancelled exact Add preview leaves all five owned files unchanged. A fresh
preview and explicit Save then reports **Enheten tillagd lokalt**. Independent v2
readback finds exactly one added UUID with B's complete source metadata; all 21
previous active states, two tombstones and owned PNG are unchanged. One local
review decision is added. The server still holds the original Planned variant,
and the canary remains absent from the other five feeds.

A separately creates and verifies its baseline SDK host copy (SUCCESS / one UUID).
This is not claimed as an automatic native Data Sync download. Private files and
all six checked feed destinations remain unchanged. A was not yet subscribed in
native Data Sync: the snapshot Fetch did not create a subscription. The operator
then explicitly joins the target using native **Download & sync** for the later
publish-back workflow.

A edits only the canary's local status to Deployed through the real TMM form.
Independent readback confirms local revision 2 and unchanged other active states.
The server remains exactly equal to B's initial Planned payload, with no additional
feed membership. A local save has not updated the shared host/server copy.

## Existing-host update and native publication boundary

A's native selected and comparison feeds are both subscribed with **Auto-publish
new content** off; the actual preferences were read after scrolling the native
large-font settings rather than treating off-screen controls as false.

The separate TMM update review shows old Planned/r1 and proposed Deployed/r2.
Its actual confirmation is disabled before affected-feed consent. Cancel preserves
all five owned files and the original server payload. A fresh review still shows
the old host state, so Cancel has not silently applied the proposed host update.

After fresh explicit affected-feed consent and confirmation, the standard host
performs the update and verifies its readback. Native Data Sync then opens
**Publish Content to Feeds** for exactly the canary r2 and the selected feed,
covering the underlying TMM result dialog. This is why a test expecting the TMM
result immediately did not see it; it was not counted as a failure or retried.

The operator deliberately chooses native **No**. The revealed TMM result reports
METADATA_PERSIST_REQUESTED and **Uppdaterad kartkopia verifierad: Ja**. The server
still has B's original Planned metadata and no other feed gains the canary. All
five private files remain byte-identical to the post-local-edit baseline. Native
No declines publication; it does not undo the already reviewed host-copy update.
Auto-publishing *new* content being off does not imply that an existing-item update
cannot prompt for publication. TMM does not promise a native Done boundary.

For the explicit retry after native No, A uses native selected-feed Add Items →
Overlays → Markers → Mission, selects only the existing r2 canary and confirms
Done. The native feed log records an update. Independent server readback now
matches all 15 fields of A's actual saved Deployed/r2 record. Membership remains
three total items with exactly one canary in the selected feed, zero in the other
five. All five private TMM files remain unchanged by native republication.

These observations establish the requested same-UUID handoff/republication path
under the recorded prompt/preferences. They do not prove an exclusive Done boundary
for every preference or future asynchronous action. Native publication confirmation
is separate from TMM's verified SDK mutation and cannot be inferred from it.

## Returned update on original publisher B

A stops before B restarts. B's private state remains byte-identical throughout
A's remote work and restart: the canary is still locally Planned/r1. A separately
confirmed configured-source Fetch creates three genuine receipts. Independent
InboxStore readback verifies the exact A Deployed metadata and source context.
All B draft files stay byte-identical to their initial post-create state. Thus the
complete standard-client path is B native publish → A Fetch/review/Add → A local
edit/reviewed host update → A native republication → B Fetch/review, with one stable
UUID and exact incoming metadata. There is no automatic private overwrite.

B explicitly saves **Keep Local**. Independent v2 readback confirms neither draft,
revision nor state version changes; one review decision is recorded. B then makes
a new explicit proposal selecting only the received Status field and saves it.
The canary becomes locally Deployed, state version/revision 2, with both decisions
retained. UUID, createdAt, other business fields and the unrelated local record
are unchanged. The local edit timestamp advances by the approved selected-field
import contract; it is not relabelled as the remote author's timestamp. The exact
remote variant remains intact in the inbox. No private application republishes:
server metadata still matches A's earlier Deployed payload exactly.

## Second native update and repeat intake

B then makes a genuine new local Active/r3 edit. Its existing host copy is observed
as Deployed/r2 before the new review, corroborating the received shared-object
state without treating that native observation as the trusted inbox source.
After fresh affected-feed consent, the SDK update again opens native Publish
Content to Feeds for only the selected feed. Before native OK the server still
matches A's earlier Deployed variant; after OK it matches all 15 fields of B's
actual saved Active record. One stable UUID remains in the selected feed, zero in
the other five. Private files are unchanged by host update/native publication.

B Fetches the new server snapshot and repeats it identically. Independent inbox
readback verifies both exact Deployed and Active variants for the target, unchanged
logical source/inbox identity, and retention of all original receipt IDs. No previous
variant is erased. The identical repeat leaves all four current private/inbox files
byte-identical; it adds neither a duplicate receipt nor a new observation.

## Source loss, explicit recovery and final artifact

Stopping only the owned relay causes the actual standard client to display source
access cancelled. All four private/inbox files remain byte-identical. After the
relay restarts, receipt history remains readable but actual import preparation
refuses without source access; reconnection does not silently authorize the old
review. A new explicit profile/feed Fetch succeeds and deduplicates with every
file still byte-identical.

The actually installed B APK is read back and matches the returned service APK
SHA-256 above. A final normal ATAK cold start preserves all four files, including
the Active private state, both incoming variants and both local decisions. No
uninstall, data clear, signer bypass or transport workaround is used.

## Scope and checks

This is a bounded completed metadata-only update-path trial on the recorded
standard combination. Type/position/bearing updates and reported host attachments
still refuse; no image sharing, physical-phone test or operational-server sharing
is enabled. #171/#25 and the concrete configured-publication gate #178 remain.
Native prompt behavior depends on the recorded preferences; TMM does not claim to
validate all future native destinations or return a remote delivery acknowledgment.

No product/domain/build input changed for this evidence. The current APK boundary
checker and actual signature checks were run; independent current-core helpers
were compiled to verify captured frames, receipt identity/variants and every exact
metadata field. Full unchanged core/image/SDK build suites were not redundantly
rerun or relabelled as new acceptance. Required CI on the documentation PR remains
mandatory. No raw profile, key, account, server package, image or capture is committed.
