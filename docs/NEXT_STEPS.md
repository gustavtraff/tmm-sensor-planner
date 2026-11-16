# Current implementation and next steps — 2026-11-16

## Owner-prioritized image delivery — current pilot

The owner prioritizes #26 for an exercise. This is a delivery target, not a tested
date commitment. Metadata round-trip groundwork remains available; do not rebuild
it or postpone images behind optional geometry/UI work.

- Frozen sender preparation and byte-identical verified-PNG owned-copy preparation
  already exist (PRs #194/#198), including actual 1920-edge content. Candidate 30 now
  wires the sender export UI; local-image adoption remains separate.
- The owner approved C1–C3; [the bounded container codec](IMAGE_CONTAINER_CODEC.md)
  is merged in PR #199 and #195 is closed. It verifies the independent descriptor
  and exact strict PNG bytes; it is not source authority or a delivery receipt.
- [Selected-feed receive backend](SELECTED_FEED_IMAGE_INTAKE.md) is merged in PR #207; #206 is closed. It adds bounded authenticated-source composition and existing-store intake without a UI caller in that slice.
- [Read-only received-image review/opening](RECEIVED_IMAGE_REVIEW.md) is merged in
  PR #203 and #202 is closed. It retains all UUID/source/delivery variants and
  rechecks actual content against the exact retained metadata. Candidate 30 connects
  the separate viewer and selected-feed caller; private active images remain unchanged.
- [The native trial](IMAGE_CONTAINER_NATIVE_TRIAL.md) proves bounded actual file
  transfer. The newer [approved first viewing-trial contract](SHARED_IMAGE_VIEW_TRIAL_CONTRACT.md)
  records strict-TLS hash GET evidence: byte access alone does not prove membership
  in the selected feed. Both tested clients were permitted; denial remains open.

Next decision/implementation sequence:

1. **I1–I5 are now explicitly approved** against PR #201 / exact proposal head
   `afa20c4`. [The owner decision](https://github.com/gustavtraff/tmm-sensor-planner/issues/200#issuecomment-6820302379)
   covers Downloads export/final native-handoff limitations, selected-feed image
   ingestion and read-only viewing. This came from explicit conversation approval,
   not the proposal merge. Existing implementation/verification gates remain.
2. [Candidate 30 image-trial UI](SHARED_IMAGE_TRIAL_UI.md) connects frozen export,
   selected-feed resource intake, separate received viewing and independently
   confirmed metadata Add. Its actual isolated SDK-host controls now cover Full HD receive/readback, repeat,
   offline/cold start, separate viewer, export Cancel/real file and pending-read
   cancellation. Complete review/signing and standard-client acceptance next.
3. Complete the approved signing handoff for candidate 30 source `e407bda`, verify
   the returned package/version/signature, and run the integrated synthetic two-client path,
   then run a separately authorized two-phone/server trial. Current signed v29 does
   not contain the image UI; SDK acceptance does not prove the signed standard route.
4. Keep explicit local-image adoption/replacement and optional outbound receipts as
   separate product work. The approved first useful slice permits viewing a
   received picture without overwriting a private attachment; it does not remove
   the eventual adoption goal. No receipt means no verified delivery claim.

[Partial signed v29 consent/connection/restart controls](CONFIGURED_PUBLICATION_TRIAL_CONTRACT.md#partial-signed-standard-client-controls--2026-11-12)
now supplement #178; its full configured-publication matrix remains open. The
actual target TAK Server 5.6/physical-phone combination remains untested. This work
is not permission for operational images, feeds or server access.

The next bounded deliverable is a usable **signed synthetic image trial**. Keep
critical privacy/content/source/UUID checks and required CI, and defer unrelated
polish, active-image adoption and receipts. Already passed local checks need not
be repeated for documentation or unchanged code. G1–G5 for [#189 geometry](SHARED_GEOMETRY_UPDATE.md)
are now approved; its lab measurements can progress while signing is pending,
without treating that as an activated production writer or closing the image gate.

## Maintenance and acceptance

- #221: owner selected a required self-hosted SDK build. No runner is registered;
  the proposed host is a Windows Dell with 32 GB RAM, using an isolated Linux VM.
  Draft PR #235 prepares the workflow; runner provisioning and failing-build
  enforcement must still be verified.
- #224: documentation consolidation is in PR #234 for review. #225 is complete
  (merged PR #232). #231 consolidates remaining local physical/accessibility/
  rotation/active-dialog unload acceptance; #12/#52/#66/#67 are closed as implemented.
- #226: inventory merged/closed PR branches and stale worktrees. Owner confirmation
  of the exact list is required before deletion. #227 waits until after the exercise.
- #2/#220: SDK provenance/distribution terms and repository visibility remain open;
  all rights are reserved. Releases and operational sharing require owner decisions.

## Where to look

[Open issues](https://github.com/gustavtraff/tmm-sensor-planner/issues) own bounded
remaining work. [Testing](TESTING.md) owns reusable checks and release gates.
[Data model](DATA_MODEL.md) indexes contracts; dated evidence and old status are in
[the archive](archive/README.md). Replace this status on update; do not append history.
