# Configured-server metadata trial contract — proposal (#178)

**Status: approved by the owner on 2026-11-08 (D1–D5).** This document alone enables
nothing: a separate reviewed implementation PR, a new signed candidate and controls
1–8 still follow. No server is contacted. [The gates document](CONFIGURED_PUBLICATION_GATES.md) records
the SDK investigation; this file states the exact policy to approve or change.

**Owner approval recorded 2026-11-08:** the owner explicitly approved all decisions D1–D5 in the conversation, for this document as merged at `fc3b34e`. Approval came from that decision, not from the merge. D5 leaves server permission and details with the owner before any two-phone trial.

## What the trial is

One session-only, synthetic-data mode that lets the **already proven native route**
(reviewed SDK host copy or update, then native Data Sync publication by the
operator, as in [signed v28](SIGNED_V28_METADATA_ROUND_TRIP.md)) run against one
configured TLS server instead of the loopback lab. It is a trial gate for two
phones on a dedicated feed, not a release, not image transport, and not a new sender.

## What the guard does today

`ShareLabRoute.connected` requires `OfflineMode.isOffline`, then exactly one enabled
stream, connected, whose connect string equals the constant `127.0.0.1:8089:ssl`.
Host-copy creation and update run behind it and `labAllowed`. The trial would replace
**only the constant comparison and the offline requirement**, and only while the
session consent below is live. `labAllowed` and all CoT diagnostic tools are unchanged.

## Proposed policy

### Source

| Rule | Proposal |
|---|---|
| Selection | The operator selects one already configured, connected SSL profile. Reuse the receive selector (`MissionSourceChoiceUi`, `ConfiguredMissionSource`). No free-text host, new credential or new profile. |
| Single stream | Exactly one enabled stream may exist, and it must be the selected one. Zero, several, or an unreadable list refuses. This is today's `enabled == 1` rule with the selected connect string in place of the constant. |
| Freshness | Recheck provider, enabled/connected state and connect string at review and again immediately before the host write. Any change refuses and requires a new review. |
| Lifetime | Consent lives in memory only. Cancel, Unload, restart, profile or provider change, and disconnect all end it. It is never stored and never restored. |

### Destination

TMM **cannot select or verify a Data Sync destination** on this route. Native
functions decide publication, and an existing-item update can open the native
prompt even with Auto-publish *new* content off. The proposal therefore does not
pretend to enforce a destination. It makes the operator's test profile the boundary,
and refuses to describe the outcome as more than that:

- Each phone uses a test profile with **one server, one dedicated synthetic feed**
  and no other feed subscribed or cached.
- Auto-publish is off for that feed; the operator reads the actual setting.
- No operational profile, feed, position, image or account is present on the profile.
- UI text states the host copy may be published by native functions, that TMM cannot
  verify the feed, and that **Nej** in the native prompt does not undo the SDK change.
- Results keep today's wording: `METADATA_PERSIST_REQUESTED` and readback only.
  Never `Delad`, `Levererad` or a single-feed claim.

### Operator consent

Before the first host write of a session, a dialog names the selected server's
display label and requires one separate confirmation per statement. All start
unchecked and the action stays disabled until every one is set:

1. Only synthetic data is on this device and in this feed.
2. The server owner has permitted this trial on this server.
3. This profile has one server and one test feed, with Auto-publish off.
4. I understand TMM cannot verify or restrict the destination.

Each host write still goes through the existing per-record review and confirmation.
Consent permits reaching that review. It does not replace it.

### Unchanged refusals

Metadata only. Type, position and bearing changes on existing host copies, SDK-reported
attachments, private images, automatic or background publication, direct REST writes,
general CoT sending, retry after an uncertain write, and new stored formats all stay
refused or absent. The local draft remains the private source of truth.

## Static SDK check for the guard — 2026-11-08

Checked with `javap` against the supplied 5.6.0.24 `main.jar`; nothing was executed.

- `TAKServerListener.getServers()` and the typed `TAKServer.isEnabled`, `isStream`,
  `isConnected` and `getConnectString` are what `ShareLabRoute` already calls. Keep
  this typed route for the stream check.
- `CommsMapComponent.getAllPortsBundle()` builds `streams`, `outputs` and `inputs`
  from `TAKServer.getData()`. `TAKServer` also defines `USERNAME_KEY` and
  `PASSWORD_KEY`, so such a Bundle may carry credential fields. If it is used for the
  `outputs` check, read only named scalar keys (`ENABLED_KEY`, `CONNECTED_KEY`,
  `CONNECT_STRING_KEY`) and never log, copy or dump the Bundle.

**Not established:** whether `outputs` lists every local broadcast route, whether
its entries match these keys at runtime, and which outputs a default phone profile
enables. Until a probe shows this, treat outputs as an operator prerequisite. Do
not present them as a guarded property.

## Evidence required before code is enabled

Run on isolated standard clients, in this order. Each row needs its own recorded result.

| # | Control | Pass condition |
|---|---|---|
| 1 | Consent gating | Any unchecked statement keeps the action disabled; Cancel writes nothing |
| 2 | Stream rule | Zero or two enabled streams, or a non-selected stream, refuse at review and at commit |
| 3 | Consent lifetime | Disconnect, profile change, Unload and restart each end consent; nothing resumes |
| 4 | Happy path, non-loopback | A fresh UUID published natively arrives with all 15 fields, on a lab server reached by a non-loopback address |
| 5 | Destination isolation | The UUID is absent from every other known lab feed, including with a second subscribed feed as a deliberate negative control |
| 6 | Native prompt | No leaves the server unchanged and local state honest; retry and OK behave as in v28 |
| 7 | Uncertain write | A failure after the SDK write reports uncertainty, with no rollback claim and no automatic retry |
| 8 | Outputs | Record which outputs a default profile enables, and observe no TMM metadata on them |

Control 5's negative case is expected to show that a second subscribed feed **can**
receive the update through native functions. That is the reason for the one-feed
profile rule, and a surprising result there should stop the trial.

## Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| D1 | First route: native handoff, or the unproven `sendCoTToServersByMission` sender | Native handoff. Only it has round-trip evidence; the mission sender stays a separate probe |
| D2 | Accept the profile-based destination boundary above, knowing TMM cannot enforce it | Accept for synthetic data only |
| D3 | Allow the offline requirement to be lifted while trial consent is live | Accept, limited to this mode; the local planner's own offline and connected-trial rules are unchanged |
| D4 | Consent statements and their Swedish wording | Accept the four statements; the wording is reviewed in the implementation PR |
| D5 | Who confirms server permission and supplies build, API port and feed roles | Owner, recorded before the two-phone trial. The agent does not contact that server |

Approval should name this document's commit. After approval, implementation is a
separate reviewed PR with a new signed candidate, then controls 1–8, then the
separately authorized two-phone trial.

## Verification actually run

Reading of the guard source and existing evidence documents, plus the `javap`
inspection above. No build, emulator, Docker, phone, server, signing or runtime
check was performed for this proposal.

## Implementation — 2026-11-08

Candidate **29 / `0.1.0-configured-publication-trial.1`** implements this contract.
Open a saved device, expand **Fler uppgifter**, then choose **Publiceringsprov mot
server**.

- `ConfiguredPublicationTrial` (core) holds consent in memory and applies the
  single-stream rule. A failed check ends consent for good.
- `ServerTrialConsentUi` offers connected SSL profiles and the four statements.
  The action stays disabled until all four are set.
- A second `SavedDraftCopyUi` and `PublicationMapCopy` serve the trial. The offline
  lab path, `ShareLabRoute`, `labAllowed` and the CoT diagnostic tools are unchanged.
- `ServerTrialConnectionWatch` subscribes through the SDK's `CotStreamListener`, so a
  disconnect ends consent when it happens. A reconnect before the next check cannot resume it.
- Consent ends on Cancel, disconnect, profile or provider change, loss of local
  access, Unload and restart. Closing a result dialog keeps it for the next review.

Automated evidence: `sh scripts/test.sh`, `sh scripts/check-sdk.sh` (SDK checks, debug
build, lint and APK contract), the image and UI suite (460 cases, no failures, one
existing skip) and `assembleCivRelease`, all on the 5.6.0.24 SDK. **At that point controls 1–8 had not been run.** The bounded signed-client
consent/lifetime results below are newer; the full control matrix remains open.

**Pre-signing smoke, debug build on the 5.6.0.24 SDK emulator with the isolated
loopback lab server (2026-11-09).** The action stayed disabled with three of four
statements set and enabled with all four. Cancel at review ended consent. A fresh
synthetic draft got SDK result SUCCESS and a verified host copy, and an existing
copy with a deviating state was refused unchanged. Closing the result kept consent.
With the connection down the trial reported no connected server. After a disconnect
and reconnect between checks it asked for consent again. No crash was logged. This
also refused the trial with the only stream disabled and with a second enabled
stream. Unload then Load, and a force-stop then cold start, each required consent
again. This does not satisfy any of controls 1–8, which need the signed candidate
on standard clients, and it did not include native publication or a non-loopback
server.

**Server version gap.** The owner's server reports a TAK Server **5.6** release, while
all Data Sync, Mission API and snapshot evidence comes from the lab's 5.8-RELEASE-92.
That evidence does not transfer. No 5.6 server package is available locally, so the
first contact with that server must be treated as an untested combination.


## Partial signed-standard-client controls — 2026-11-12

One retained isolated client ran standard ATAK-CIV **5.6.0.25 (238484ee)**,
Data Sync **3.7.4 (58746181) [5.6.0]**, Android API **35** and the previously signed TMM **versionCode 29 /
`0.1.0-configured-publication-trial.1`**. The client had a verified loopback-only
IPv4/IPv6 output guard and local tunnels to the owned 5.8 lab server. No operational
server/phone was accessed. The sole retained lab stream was restored enabled
after the zero-stream check; no credentials or profile contents were replaced. This does not substitute for the reported 5.6.0.12 phone
or the target 5.6 server. No new candidate/signature was needed for these checks.

| Matrix part | Actual bounded result |
|---|---|
|1: statement gating | Continue stayed disabled after 1, 2 and 3 statements; it enabled only with all 4 checked. |
|1: consent Cancel | Cancel confirmed no SDK write; all 5 existing private plugin files had identical before/after hashes. Reopening required server selection and 4 unchecked statements. |
|2: zero-stream entry | With the sole existing profile disabled, entry refused with `Ingen ansluten TLS-server`; no consent/review proceeded. At-commit and two-stream controls remain open. |
|3: cold restart | Force-stop/cold start during an unconfirmed review did not resume it. Server selection and 4 unchecked statements were required; all 5 private files were unchanged. |
|3: connection cycle | While an unconfirmed review was open, the owned local relay was stopped and restarted, with no trial UI check in between. A subsequent **read-only update inspection**, not a mutation confirmation, was refused. New server selection and 4 unchecked statements were required. |

These are signed-client gating results only. The connection cycle does not prove a
continuous permission lease or every profile/provider transition. No SDK mutation,
HTTP write or native Publish confirmation was requested. The test intentionally
examined only consent/review controls, so it did not authorize a multi-feed profile
for publishing or infer feed isolation from the checkboxes. The positive
non-loopback publish, two-enabled-stream and at-commit stream cases, profile/Unload cases, native
prompt/retry/permission/output controls and target-server/phone acceptance remain
open. **#178 is not complete and operational publishing is not approved.**
