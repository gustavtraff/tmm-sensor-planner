# Next steps after offline phone acceptance — 2026-10-22

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
remains open.

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
