# First usable shared-image view — approved trial contract (#200 / #26)

**I1–I5 explicitly approved by the owner on 2026-11-12**, against PR #201 /
commit `afa20c435f5a4a48ea07cb4c194df40ed6788c88`, after clarification of the
incremental user flow, snapshot limitations and separate later adoption work.
See [the recorded owner decision](https://github.com/gustavtraff/tmm-sensor-planner/issues/200#issuecomment-6820302379).
Approval authorizes implementation of this synthetic trial; it is not passing
runtime acceptance, release approval or operational-server permission. The earlier
C1–C3 approval and the proposal merge did not grant these route decisions.
No new persisted format is proposed: reuse the approved container, inbox, stage
and association registry. Active local-image adoption/history remains separate.

## User outcome and smaller first slice

A reviews a saved device and its actual sanitized image, exports one frozen
container, then manually publishes that file through native Data Sync to the
dedicated synthetic feed. B explicitly fetches the file through the selected
configured source, verifies its bytes/metadata/source binding and can open the
received image under the matching device UUID. Unknown metadata remains available
for normal inbox review/Add; known-device differences never overwrite local edits.

**Received pictures are a separate read-only section.** The current private image
is never replaced or detached. No received picture is chosen as newest/current by
arrival time or filename. This gives the exercise a useful picture-sharing flow
without first migrating local-v2 for an adoption-history writer. Later explicit
adoption/replacement is still a product goal, not removed from #26.

## I1 — explicit native frozen-snapshot export pilot

Reuse the metadata trial's selected configured SSL profile and synthetic-only
session/operator prerequisites: server-owner permission, one test server/feed,
no operational data/profile and verified native Auto-publish off. TMM still cannot
verify or enforce the final native destination. CoT diagnostics remain unchanged.

After the exact image/metadata/destination preview and fresh one-shot confirmation,
write a new opaque UUID-named `.tmmi` file to **local Downloads**, with forced
write and exact readback. Disclose that it contains the reviewed picture and
metadata, is outside private no-backup storage and remains until explicitly
deleted locally. Never overwrite an export, reuse a sender path or evict files
automatically. Limit outstanding TMM exports to 16 files / 256 MiB, refusing new
exports at capacity. Do not modify a completed export later.

The operator then uses native selected-feed **Add Items → File Select → OK** and
any additional **Publish** confirmation. TMM reports **export prepared**, never
Shared/Delivered. Cancel before export confirmation creates no public export or
native host object/upload; after export, cancelling a native dialog cannot undo
the local file or recall an earlier upload. Local export deletion is not withdrawal.

**Owner-accepted pilot limitation:** TMM cannot revalidate current
private state or destination at the final native Publish action. Later edits or
deletion do not change/revoke the frozen export. This is a historical immutable
offer, not a promise to publish the latest draft. The receiver retains its exact
metadata variant and never applies it automatically. This exception is specific
to the synthetic image trial; the metadata exception does not grant it implicitly.

## I2 — configured selected-feed read adapter and source meaning

Reuse the existing configured SSL source selector, default-provider restriction,
host-managed validating TLS/client credentials, logical service + stable mission
GUID context and explicit request cancellation. No new credential/profile,
payload-supplied URL, private Data Sync broadcast/API or HTTP publisher is added.

For one explicit resource selection/download:

1. GET the selected GUID's overview, validate that GUID/name against the selection
   and identify an eligible resource by its exact 64-hex content hash, declared
   byte length and supported content type. Display labels are not identity.
2. GET only the fixed same-origin **`/Marti/sync/content?hash=<validated hash>`**.
   Never follow a provider URL or redirect, fall back to another server, or treat
   a hash as authorization. Require identity encoding, HTTP 200, bounded actual
   bytes and the complete container's exact length/hash and codec checks.
3. Read the selected GUID overview again; require the same resource claims still
   present and recheck the selected source/session/provider. Other resources may
   change independently. Missing/revoked/ambiguous evidence refuses association.
4. Treat the container's validated metadata M as an **observed claim from that
   authenticated selected feed**, using the existing inbox rules, then persist
   the exact private stage and association record. This permits a file containing
   M to arrive before a standalone CoT snapshot; it never creates an active draft.
   Different M is retained as a variant. Local handles/revisions never enter M.

Source means service + observed selected-feed membership. It does not authenticate
the author/owner text, prove scene truth, provide a continuous permission lease or
complete history. A valid container from an arbitrary file picker remains
unassociated. Previously stored verified images can be viewed offline; a later
disconnect does not rewrite their historical source evidence or publish feedback.

Use one live transfer, the existing 17,039,492-byte container bound, a 60-second
overall attempt deadline and the existing five-second connect/read timeouts.
No automatic fetch/retry/background polling. The approved overview budget is
512 KiB / 256 resource entries; over-limit/partial/malformed input fails honestly,
without silently accepting a partial list or changing existing metadata limits.
Resource selection alone is not evidence of successful download or storage.

## I3 — verified received-image viewer, without adoption

Group verified association records by stable device UUID and retain their source,
exact M and distinct association/delivery identities. In the inbox/device details,
show **Mottagna bilder** separately from the private local-image action. Opening
one rechecks its registry/inbox/stage binding and actual bytes before using the
existing zoom viewer. Show whether M differs from the current private draft; the
picture remains attached to its offered version, never silently rebound to newer M.

Do not resurrect a deleted device, create a map item on file arrival, apply
metadata, replace an image, or send an acknowledgment merely to display pixels.
Missing/corrupt content remains an explicit error with retained history and old
private data. Unload/cancel closes pending UI/work without a late callback opening
a viewer. A normal metadata Add remains image-free; the received section is how
the operator views the offered picture in this first slice.

## I4 — reuse current retention and truthful results

Keep stage-v1's 16-entry / 256 MiB budget and registry's 256-record budget. Explicit
download may refuse at capacity; no historical association or referenced stage is
evicted to make room. Exact repeats should reuse an existing matching association
without allocating another stage entry. If its historical content is missing,
report that fact instead of inventing a new association or silently rebinding it.
Current image, draft, inbox and registry formats remain unchanged.

Outbound receipts remain disabled and are not a prerequisite for viewing an
actually verified local image. Without receiver feedback the sender has no verified
delivery acknowledgment. Native upload success alone never changes that status.

## I5 — verification and activation boundaries

Use the isolated synthetic lab first. Verify export Cancel, frozen-byte consistency,
native confirmation and comparison-feed controls; resource permissions/membership,
profile/access loss, malformed/partial input and bounded cancellation; exact inbox /
stage/registry readback and repeat/restart; actual viewer/UUID association and
preservation of current private state. Independently inspect server and recipient
bytes. Do not infer these from SDK booleans or unit tests.

Then sign the integrated candidate and run standard-client two-client acceptance.
The owner's separately authorized two-phone/server trial still needs server-owner
permission, exact server/API/feed roles and actual phone/plugin builds. The target
TAK Server 5.6 is an untested combination versus the 5.8 lab. No operational data,
image or server connection is authorized by approving this synthetic trial.

## Owner decisions

| ID | Recommendation |
|---|---|
| I1 | Approve the disclosed synthetic native frozen-file pilot and explicit Downloads export |
| I2 | Approve the bounded selected-feed read adapter and exact-M observation/source meaning above |
| I3 | Deliver read-only received-image viewing first; leave active-image replacement/adoption separate |
| I4 | Reuse current quotas/retention and no-feedback status; accept explicit capacity refusal |
| I5 | Require integrated lab, signing/standard clients and separately authorized phones before rollout |

## Current route evidence — 2026-11-12

Static inspection of the supplied 5.8-RELEASE-92 `ContentServlet` identifies
`/sync/content`, the `hash` parameter and resource-group-based reading. Its servlet
route is not `/api/sync/content`. No vendor class or package is committed.

An independent strict-mutual-TLS GET on the retained lab returns the actual prior
6,224,532-byte container byte-identically as `application/octet-stream`, without
content encoding, for both existing permitted lab client contexts. The selected
image feed lists one matching resource; a comparison feed lists none even though
the hash GET remains readable. This is why the byte GET cannot prove feed context
and the before/after selected-membership checks are necessary. This experiment
does not test a denied resource or approve a product adapter.

The prior [native trial](IMAGE_CONTAINER_NATIVE_TRIAL.md) separately verifies
File Select, actual server/recipient bytes, Cancel, restart and local-only deletion /
repeat download. Neither record activates the UI/source bridge approved here; implementation and
the I5 verification gates remain required.
