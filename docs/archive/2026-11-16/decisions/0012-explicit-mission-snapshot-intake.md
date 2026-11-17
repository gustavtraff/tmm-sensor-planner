# ADR 0012: Explicit configured Mission snapshots for reviewed metadata intake

Status: accepted for the bounded metadata receive trial; not operational rollout.
Decision authority: the owner explicitly approved snapshot-first delivery on
2026-11-05 and subsequent configured-source implementation. Recorded 2026-11-08.
Tracking: #164; writer/user-flow acceptance #171 and parent Data Sync work #25.

## Context and decision

Native ITEM_IMPORTED, importer extras and logger hints did not establish both an
authenticated selected feed and every original incoming variant. Do not turn those
hints into a trusted inbox writer. The owner's accepted first useful version uses
explicit current snapshots, preserving observed variants and requiring review of
differences. Intermediate server changes between Fetches may never be observed.
Complete history is neither implemented nor promised.

Use the supplied ATAK public SDK's host-managed strict TLS client for explicit
GET-only Mission API reads. The operator chooses an existing connected SSL profile,
confirms the separate HTTPS API port, selects a server-returned mission GUID and
reviews before Fetch. No automatic polling, subscription or publisher is introduced.
Native Data Sync remains the user-mediated publication surface. This is a deliberate
version-matched receive adapter, not a claim of a callable Android Data Sync SDK.

The actual 5.6.0.24 SDK APIs, fresh factory/redirect/authentication policy and v28
operator contract are recorded in [CONFIGURED_SNAPSHOT_SOURCE](../CONFIGURED_SNAPSHOT_SOURCE.md).
[Full metadata lab evidence](../MISSION_METADATA_LAB.md) records the pinned official
MissionApi source, supplied 5.8-RELEASE-92 binary behavior and strict-membership
controls. [Signed v28](../SIGNED_V28_METADATA_ROUND_TRIP.md) adds actual independent
standard-host Fetch/review/Add and same-UUID native republication/return evidence.
Source pins corroborate the actual binary tests; later server builds are not assumed
compatible just because the endpoint names match.

## Source meaning and trust boundary

The configured canonical HTTPS logical service plus stable mission GUID supplies
`mission-api-v1|<service>|<guid>` inside the already approved opaque inbox context.
Names are display labels. Payloads cannot select URLs, profiles or authority.
The legacy loopback context is unchanged. No new source-registry file, stored
permission token, inbox frame, metadata profile or migration is adopted here.

On the tested 5.8 server, mission membership is scoped by GUID but latest accessible
CoT is obtained by UID. Do not invent independent per-feed versions for one UID or
claim a globally atomic remote snapshot. Fetch requires matching before/after
membership and complete validated events; membership ABA and in-between payload
changes cannot be eliminated by these reads.

Successful host-managed TLS/GET and observed membership grant session application
access only to that inbox identity, logical source and observed members. A stored
context alone grants nothing. Grant loss on Cancel, restart, Unload or observed
profile/provider loss requires a new Fetch. This sampled guard is not a continuous
remote permission lease. Owner text is editable metadata, not authenticated author
identity; receipt order/timestamps do not select an automatic winner.

## Private data and failure behavior

Reuse the existing bounded codec, atomic InboxStore batch, variant/decision review
and local-v2 transaction. Validate the complete snapshot before intake. Missing
members delete nothing locally; repeated identical metadata deduplicates. Current
local drafts/images remain unchanged at Fetch. Add, Keep Local and selected-field
application require separate fresh preview/Save. Selected-field edits use the
approved local edit timestamp and retain the original remote variant.

No private image handle, image bytes, source credentials or raw provider error is
exported or logged. Unsupported provider/auth/API/format cases fail without fallback,
partial-prefix acceptance or silent history reset. An uncertain file replacement
may already have committed; report uncertainty, inspect genuine state and require
an explicit retry rather than rollback or automatic replay.

## Consequences and remaining work

This completes #164's receive-route decision, with actual owner approval and
version-specific runtime evidence rather than proposal-merge inference. It does
not complete #171's user/phone/server rollout or #25's broader integration work.
Publication still uses the separately reviewed SDK/native workflow and retains
its isolated-loopback gate. Operational publication, actual server permissions and
physical phone compatibility require their own reviewed trial and implementation.

Images remain #26. Valid hashes/native file delivery do not establish source-image
association, adoption or acknowledgment. Future source registries, attachment
containers/receipts and their exact stored formats still need separate approval
before writers. ADR 0010's caution against private broadcasts and a silent direct
REST publishing workaround remains in force; this decision changes receive only.
