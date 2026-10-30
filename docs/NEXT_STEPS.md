# Current implementation and next steps — 2026-10-30

## Completed work versus open gates

- **V21 signing and bounded offline verification are complete.** The returned
  [signed candidate](SIGNED_V21_VERIFICATION.md) passed same-certificate update,
  exact v2 preservation, image/restart, pending-confirmation access-loss recovery,
  explicit v1 migration and native Unload/Load on standard ATAK 5.6.0.25. Preparation
  #123 and minimal developer-plugin #3 are completed; SDK provenance remains #2.
  Do not ask for a new signing ZIP merely to repeat this evidence.
- **The reported camera, startup-marker and notice bugs have acceptance evidence.**
  The table below supports closing #49/#50/#62 as narrow fixes. It does not mark all
  UI/physical compatibility work complete or imply a newly tested phone candidate.
- **Image sharing is still implementation work under #26.** The owner approved
  logical A–F/H and the Data Sync-feed target G. [Content verification/export](IMAGE_TRANSFER_CONTENT.md)
  is merged; #130 / PR #131 adds bounded stream input and focused tests for review.
  Neither means image delivery, durable association or a working feed adapter.
- **Metadata sharing/import components exist, but trusted feed ingress remains a
  gate under #25.** Closed implementation issues #14/#15 are not evidence that an
  authenticated selected-feed receive path has been integrated. Follow the
  [source investigation](INGRESS_SOURCE_VERIFICATION.md), not private broadcasts,
  guessed endpoint/feed equivalence or a silent REST replacement.

## Narrow bug acceptance and closure scope

| Issue | Existing implementation and actual acceptance | What closure does not claim |
|---|---|---|
| #49 camera-photo attachment | Primary-JPEG/auxiliary-content and bounded normalization regressions in [LOCAL_IMAGES](LOCAL_IMAGES.md); merged corrections; [owner's signed-v15 phone result](PHONE_ACCEPTANCE.md) reports the previously failing photo scenario and other photos now work | Exact diagnosis of the uncollected historical file/container; every future phone/version/provider |
| #50 saved markers after restart | [Startup restoration](MARKER_RESTORE.md), signed standard-host restart/unload/error-state evidence and owner's v15 cold-start confirmation; [v21](SIGNED_V21_VERIFICATION.md) also rendered restored local markers before opening TMM | Operational sharing, arbitrary power loss or every lifecycle race |
| #62 wrong image-copy resource text | Plugin-context resource fix; actual SDK and signed-standard v10 notice/Cancel/Save in [standard-client verification](STANDARD_CLIENT_VERIFICATION.md#latest-signed-candidate--2026-10-18) | A fresh phone quotation of the notice. Its historical 1600 limit is superseded by the approved 1920 policy, not a resource regression |

The old issue bodies/dated records describe earlier failures and pending tests.
Closing these narrow bugs uses the later merged evidence; it does not silently
reinterpret earlier failures as passes. Keep explicit remaining checks below rather
than treating all candidate/device verification as a reason to reopen fixed bugs.

## Next implementation order

1. **Finish the bounded input PR #131, then the exact #26 formats.** Before new
   offer/container/receipt or private-registry writers, produce concrete byte formats
   and independent synthetic fixtures for owner review as required by H. Reuse exact
   metadata/domain equality, immutable image identity and the approved content profile.
   This format review is still required; approval of A–F/H is not approval of unseen
   bytes. Keep local images and current metadata/inbox/v2 formats unchanged.
2. **Verify trusted selected-feed provenance under #25.** Observe documented native
   download/conflict/restart ingress on the isolated supported test combination.
   Prove which original metadata variant and authenticated source context reach the
   actual adapter boundary before implementing receive wiring. If the public route
   cannot establish this, record that concrete limitation and a reviewable route
   decision; do not infer it from sender status, logs or generic server hints.
3. **Implement approved image staging/association and explicit adoption.** After exact
   format review, add bounded private storage/readback/recovery, source + UUID + exact
   metadata binding, duplicate/identity-conflict checks and manual image replacement.
   Use meaningful process/failure fixtures. Feedback stays off by default. Neither
   received metadata nor valid PNG bytes automatically changes a local attachment.
4. **Integrate and verify the real Data Sync-feed image workflow.** Require supported
   route evidence, review destination/inclusion/frozen bytes, preserve private drafts,
   and independently observe actual recipient bytes/association and cancellation on
   two isolated clients. A contact transfer is not a substitute for the feed goal.
   No operational-server experiment follows from the earlier lab results.

## Remaining acceptance and owner decisions

- **Current candidate:** phone 5.6.0.12/OEM/provider regression and v21 connected
  two-client checks remain separate from v20 evidence. Smaller queued-worker /
  access-return-before-callback timing was not forced in the v21 native run; the
  deterministic tests and pending-confirmation native cases remain distinct.
- **Local UI:** #12/#52/#66/#67 retain targeted physical/accessibility/lifecycle
  follow-ups: TalkBack, active picker/viewer Unload, exact rotated-map selection and
  applicable phone-size detail/gesture cases. Most feature code is implemented;
  do not describe MGRS, 6400-mil entry or the 1920-image viewer as missing features.
- **Provenance/governance:** original SDK archive/source/terms evidence (#2), license /
  contribution/distribution and authoritative-owner review decisions (#55) require
  their own evidence or human decisions. Signing and green public CI do not resolve them.

Parallel work can cover exact-format fixtures, a bounded source/SDK investigation,
and remaining lightweight UI/accessibility checks on separate branches. Keep a
single owner of emulator/server resources; no two heavy lab runs are needed for
format/domain work. Human review controls merges, releases and operational sharing.

GitHub Issues are the live backlog. Everything below is a **historical plan**, not
current instructions or a claim that old implementation gaps remain open. Exact
candidate/runtime records above take precedence over these dated next-step notes.

## Historical implementation gate — 2026-10-25

**Signed v16 follow-up:** [the actual SDK-path trial](PUBLICATION_SDK_RUNTIME.md)
now passes its bounded fixed-PIR gate: one exact host copy, no pre-Done feed
membership, intended-feed/native confirmation and B-export preservation, with
comparison auto-publication controls and repeat/lifecycle observations. Lab resources
are stopped and original settings restored. Propose closing #83 after human review.

Next under #14: agree the production artifact/source-identity contract aligned
with the inbox, implement private Android preparation on the edit/delete/lifecycle
boundary, and explicitly review the manual-snapshot handoff limitation before any
real-draft host-copy action. No supported native Done callback or Data Sync Android
API has been established. #15 still owns trusted ingress and deferred draft
application; #26 owns images. No new ZIP is needed for this documentation-only result.

The merged #93 core preparation and #94 durable review inbox are implemented and
tested separately. The inbox's approved A–C does not apply received data to local
drafts (D remains deferred), authenticate an origin or connect an Android receiver.
The previous signing/runtime plan has been executed as recorded above. Keep the
earlier manual trials and later signed SDK evidence distinct; no source-tag
impersonation or direct-REST fallback was introduced to obtain this result.

GitHub Issues are the live backlog. This dated plan supersedes stale implementation
status in the initial [BACKLOG.md](BACKLOG.md), not the product/project requirements.
The owner-reported [version 15 offline test](PHONE_ACCEPTANCE.md) supports local
usability; it does not approve an operational-network test or release.

## 1. Verify connected private drafts — #4 with #7

**Update — 2026-10-23:** The bounded version 15 trial on two standard 5.6.0.25
emulators has been run; see [the verification record](CONNECTED_SIGNED15_VERIFICATION.md).
Its CoT controls, absence checks and local lifecycle observations are evidence for
human review of this gate. The procedure below remains the repeat protocol. A
separately authorized isolated phone trial and exact phone-host reconfirmation are
still needed; this result does not complete #7 companion workflows or authorize
an operational-network trial. The Data Sync automatic-publication/reconnect-queue
phase added below was not covered by this CoT-only run; that isolated-feed gate
is now recorded for human review in [the isolated-feed trial](DATA_SYNC_FEED_VERIFICATION.md).
It does not establish custom TMM feed publication.

Use the existing service-signed version 15 artifact first; no feature change or new
signing submission is needed solely to start verification. Reuse
[CONNECTED_LOCAL_TRIAL.md](CONNECTED_LOCAL_TRIAL.md), with these explicit gates:

1. Inventory two isolated **standard** ATAK clients, their exact full builds,
   Android versions, plugin APK hashes/signers and lab server version. Use the same
   signed candidate on both clients. Historical SDK 5.6.0.24 evidence does not
   substitute for this standard-binary run. Reconfirm the phone host version before
   any later phone trial; standard 5.6.0.25 lab and reported 5.6.0.12 phone are distinct.
2. Use the existing isolated test-server setup, synthetic records/images and no
   operational feed/profile/credentials. Keep raw traces and configuration private.
   Establish a recipient plus independent decrypted observation. Prove normal
   routing with unique positive controls before and after the private operations.
3. Test consent cancellation, explicit connected-local consent, create/edit/map
   move/image attach/delete, background/return, Unload/reload and cold restart.
   Consent resets on restart/Unload. Sharing and the CoT lab remain blocked for the
   consented session, including after returning offline. Connecting must not change
   lifecycle or Local state or silently publish previously saved records.
4. Search recipient, server and decrypted traffic for every synthetic UUID, ID,
   owner/comment, position and image/path canary. Include deletion/reconnection and
   record the observation window. Encryption or an empty recipient screen alone
   is insufficient evidence. Stop on any unexpected draft exposure.
5. Record exact tested artifact/hosts, controls, results and limitations in #4/#7.
   Only after review, plan a separately authorized phone test against the isolated
   lab. This is not permission to connect private drafts to an operational server.

Exit: reviewer-accepted standard-binary privacy evidence for the tested setup.
If the environment is unavailable, record the block rather than substitute an
operational server. Existing lab evidence remains bounded to its tested versions.

## 2. Complete coexistence and acceptance gaps — #7/#12

Compare baseline, TMM active and after Unload for supported HV Reports, Data Sync
and Voice packages using synthetic isolated report/feed/audio workflows. Normal
plugin loading/empty views do not prove workflows. Check active-picker/viewer
Unload, accessibility and rotated-map selection separately where still untested.
Review source/SDK provenance (#2), historical minimal loading evidence (#3) and
release/governance gaps (#55) against their actual criteria. Close narrowly
completed local bug issues after human review; retain explicit residual gates
rather than carrying obsolete 'not implemented' claims.

## 3. Finish one reviewed metadata route and durable receipt — #14/#15

These are the main remaining product-development tasks. Existing lab transmission
and core import/conflict logic are partial building blocks, not production sync.
Review existing implementations/contracts before coding. Agree the target/recipient,
UUID/revision/author semantics, duplicate/update/conflict rules and truthful
send-versus-receipt status. Wire durable received objects into storage/UI, preserve
concurrent edits, and expose explicit metadata review/confirmation with an image
not-shared warning. Test cancellation, failure, retry, duplicate receipt, stale and
concurrent edits, restart, withdrawal/deletion semantics and recipient persistence
on two isolated signed standard clients. Never infer Data Sync feed publication
from ordinary CoT. Do not silently enable the lab UI as production sharing.

Exit: one explicitly approved end-to-end metadata workflow, including receiver
persistence and clear failure/conflict handling. No automatic background sharing.

## 4. Add image delivery — #26

After metadata identity/import contracts stabilize, define object-to-attachment
identity, content integrity, inclusion consent, replacement/retry/acknowledgment and
retention/deletion behavior. Verify actual recipient bytes and association; never
send local image handles, file paths or provider URIs. Keep image status distinct
from metadata-send status. Use synthetic inputs in the isolated two-client lab.

## 5. Data Sync and release gates — #25/#7/#55

Data Sync integration remains deferred until an applicable official supported API
contract and test combination exist. In parallel, finish scanner/dependency triage,
license/distribution/role decisions and a release checklist. Signing success alone
is not release approval. Human review controls merges, releases and data sharing.

## Parallel work

- During #4/#7 lab verification, a separate issue/branch can review #14/#15 contracts,
  UI failure states and existing core tests without changing transport or sharing
  policy. Avoid two branches rewriting the same SDK/UI adapters.
- Documentation/governance/security-report triage can proceed independently.
- Image-transport implementation depends on metadata/import identity decisions;
  Data Sync implementation depends on its official API. Neither is a useful shortcut
  around the first privacy gate. Local UI refinements are lower priority unless a
  newly reproducible blocking defect appears.

This is a plan, not completed network testing. No runtime/network checks were run
for the documentation update, and no dates or release commitments are assigned.

## Owner-directed parallel investigation — 2026-10-23

The owner prioritized Data Sync as the intended collaboration workflow. Investigate
#25 now, in parallel with another agent's #4/#7 standard-client privacy work.
[DATA_SYNC_INTEGRATION.md](DATA_SYNC_INTEGRATION.md) records concrete sources,
candidate paths and the next spike. Do not finish another production transport
before reviewing this result. Research priority does not approve transport code;
image delivery still follows identity/import contracts. The parallel privacy run
should include Data Sync's optional automatic-publication setting and reconnect
queue behavior in an isolated feed. No operational server is needed for research.

## Isolated Data Sync baseline — 2026-10-24

[DATA_SYNC_FEED_VERIFICATION.md](DATA_SYNC_FEED_VERIFICATION.md) records ordinary
selected-feed delivery on two standard clients and the private-draft phase with
automatic publication off, then on with the content prompt disabled. Reconnect
and cold restart were included. No private exposure was observed in the bounded
channels; human review is still required. This replaces the earlier “not covered”
status for this setup, not the physical-phone or production-sharing gates.

Next under #25: review this evidence, then run the separately reviewed synthetic
metadata-only publication-copy experiment from [DATA_SYNC_PROBE.md](DATA_SYNC_PROBE.md).
Verify recipient-exported fields and UUID, destination boundaries, cancellation,
permissions, repeat/update/restart and conflicts before choosing an adapter. A
working ordinary Data Sync feed is now an observed baseline, not the missing proof.
No new APK/signing ZIP is needed for this documentation-only verification.

## Metadata spike result and next decision — 2026-10-24

[The metadata selection trial](DATA_SYNC_METADATA_TRIAL.md) attempted the canonical
PIR fixture after review of PR #82. Data Sync rejected its waypoint selection.
A separate native-type diagnostic preserved all 15 custom fields through recipient
export, so field loss must not be assumed to be the blocker.

Next: verify an intended selectable/exportable publication representation in
[#83](https://github.com/gustavtraff/tmm-sensor-planner/issues/83), under #25, retaining
explicit review and destination boundaries. Obtain the applicable
Data Sync type/export contract; do not silently replace the canonical envelope.
Test publication-copy creation with an unselected feed's auto-publication enabled
before designing the adapter. Canonical update/duplicate/recovery/conflict tests
remain downstream of that baseline. No app or signing input changed in the trial.

## Sensor candidate baseline — 2026-10-24

[#83's bounded sensor trial](DATA_SYNC_SENSOR_TRIAL.md) now demonstrates a selectable
manual publication representation and exact returned metadata on the standard lab
combination, including cancellation and an unselected auto-publishing feed. Review
[ADR 0011](decisions/0011-sensor-point-data-sync-lab.md) before treating this as the
accepted lab baseline. The waypoint failure remains documented; the envelope change
is explicit and experimental.

Next under #25: permission denial, same-UUID duplicate/update, disconnect/recovery,
receiver restart and conflict/removal behavior using the reviewed candidate. Keep
exact inner/outer position consistency when testing map edits. Then scope #14/#15
production review and durable import around an evidenced creation/selection path.
The manual import result does not approve a direct factory/dispatcher replacement,
automatic root-MapItem conversion or an operational phone/server test. This branch
changes offline tools/docs only; no new signing ZIP is needed.

## Same-UUID result and recovery gate — 2026-10-25

The [update trial](DATA_SYNC_UPDATE_TRIAL.md) found no duplicate after repeated
publication and preserved the exact updated PIR on the server and B after online
cold start. Reconnect and feed-list refresh still produced base metadata during
the bounded observation window. That run did not establish automatic recovery.

**Later controlled follow-up:** [The reconnect trial](DATA_SYNC_RECONNECT_TRIAL.md)
verified subscription, a real task-port TCP interruption, recipient routing controls
and exact updated fields before any manual sync or restart. Full sync and local
older/latest history downloads were checked separately. This is bounded positive
evidence for review; the earlier stale interval's cause remains unknown.

The later [acceptance trial](DATA_SYNC_ACCEPTANCE_TRIAL.md) covers normal read-only
UI prevention with actual assigned roles, divergent offline/online edits with
explicit native conflict choice, and separate local/server removal/re-download.
These are bounded observations, not durable TmmDraft import, permission-bypass
testing or a production deletion policy.

Next: review the combined #25 evidence and decide the intended integration route
and issue disposition. A supported callable third-party Android contract is still
unidentified. Then scope #14's exact reviewed publication-copy creation/confirmation
path and #15's durable receipt/conflicts/withdrawal behavior. Keep private drafts outside
ordinary map-item publication and retain explicit target/metadata review. This
evidence changes no app or signing input; no new ZIP is needed.
