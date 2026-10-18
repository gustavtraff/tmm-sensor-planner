# CI and repository/release governance

Issue: [E0-08 / #9](https://github.com/gustavtraff/tmm-sensor-planner/issues/9).
Evidence date: 2026-10-17. This document records observed settings and open human
decisions; it does not authorize a merge, release or operational data sharing.

## Current audit and owner-only review — 2026-10-18 (#55)

The earlier 2026-10-17 investigation below is retained as historical evidence.
Issue #9 is now closed; the unresolved decisions and new hardening scope are
tracked in [#55](https://github.com/gustavtraff/tmm-sensor-planner/issues/55).
This audit read live GitHub settings; this PR does **not** change those settings.

| Item | Observed state | Recommendation / boundary |
|---|---|---|
| Access | Only `gustavtraff`, admin | Keep sole human write/admin authority; contributors use forks |
| Main ruleset | Active; PR, resolved threads, strict `core`, linear history, no force push/deletion, no bypass actors | Retain; a 404 from classic branch protection does not mean the ruleset is absent |
| Image CI | Runs and passed recent PRs; not a required rule | Make `images` required alongside `core`, bound to GitHub Actions |
| Approvals | Zero required, no required code-owner approval | Retain temporarily for owner-authored PRs; self-approval is forbidden |
| Merge options | Merge, rebase and squash enabled; branch auto-delete off | Permit squash only and enable deletion of merged short-lived branches |
| Actions | Default token read-only; actions cannot approve PRs; all actions allowed, repository SHA-pinning enforcement off | Retain read-only/no bot approval; review allowed actions and repository-wide SHA enforcement. Current workflow actions are already SHA-pinned |
| Secret scanning / push protection | Enabled | Retain; this does not prove all history/artifacts are clean |
| Dependabot security updates | Disabled | Review enabling security-update PRs after confirming dependency coverage; no auto-merge |
| Private vulnerability reporting | Disabled | Enable an owner-controlled private reporting route before advertising one |
| License | Pending notice; no project license selected | Rights holder must decide contribution/distribution terms; external code acceptance remains gated |

The new CODEOWNERS file routes all paths to `@gustavtraff`. People on a public
repository can still comment/review; code ownership is not a ban on public
feedback. Only the maintainer's human decision is authoritative under this policy.
GitHub still sees automation using the owner's personal token **as the owner**;
it cannot distinguish a human merge from an agent using that same token.
The current no-auto-merge rule is procedural, not a separate credential boundary.

Do not turn on one mandatory approval while the maintainer authors all PRs:
GitHub prevents self-approval. If technically enforced owner approval is required,
first establish a separate bot/App PR identity with narrowly reviewed permissions,
then evaluate one code-owner approval by `gustavtraff` and stale-approval dismissal.
Keep checks/no bypass; do not weaken protection to work around an identity problem.
No bot identity or token/account changes are introduced here.
[GitHub self-approval rules](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/approving-a-pull-request-with-required-reviews)
and [CODEOWNERS behavior](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)
explain the difference between review routing and enforcement.

### CI coverage and next steps

Public CI has useful independent `core` and `images` jobs. The latter runs native
Android image/coordinator tests and lint without ATAK. The core job also covers
storage compatibility, 100-device scenarios, safe auth-helper behavior, signing
archive boundaries and the synthetic attachment fixture. This is an appropriate
public baseline; it is **not sufficient release or ATAK compatibility evidence**.

This PR retains both job names and existing checks/actions. It runs on PRs, pushes
to main and manual dispatch, avoiding duplicate task-branch push/PR runs. Concurrency
cancels superseded runs; core/images timeouts are 10/20 minutes. A weekly Dependabot
configuration proposes GitHub Actions version/pin updates for human review; it does
not automatically merge them or cover every Gradle/SDK dependency.

Do not add fake/skipped SDK status checks. Full plugin builds/lint/static APK checks
remain local on the exact tested commit until an approved isolated private-input
runner/status publisher is established. Public fork code must never receive SDK,
signing or account secrets. The current workflow uses `pull_request` with read-only
permissions, not privileged fork-code execution via `pull_request_target`.
Fork-workflow approval settings were not independently read in this audit; review
them in Settings → Actions before accepting external contribution runs.
[GitHub fork workflow guidance](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
describes its token/secret boundaries.

Next security automation work is to evaluate CodeQL/static analysis and Gradle
vulnerability/dependency update coverage without exposing private SDK inputs.
It is not implemented or claimed by adding Actions update PRs. Supply-chain/code
scanning, independent SDK builds, signing reports, physical trials, network/privacy
acceptance and rollback remain separate release evidence. No automatic signing,
APK release upload or operational data distribution is added.

### Documentation and remaining owner decisions

README now distinguishes implemented features, bounded signed-client evidence,
owner phone feedback and unsigned/new-version gates. CONTRIBUTING documents the
fork/Issue/branch/PR/test/owner-review path and contribution-term gate. SECURITY
reflects the actual lab transport/image policy and the missing private channel.
The PR template asks for exact commit, CI/SDK/device evidence and data impact.

The owner still needs to decide the project license/contribution terms, private
security route, release/distribution ownership and whether to adopt a separate
automation identity. This PR does not infer organizational roles or distribution
rights from GitHub admin status. Human review is the final merge/release decision.

## Historical evidence — 2026-10-17

## Public CI

The GitHub Actions workflow `Core checks` runs on push and pull request with
read-only contents permission and a commit-pinned checkout Action. The required
job name stays `core` so the existing ruleset continues to recognize it. It checks
patch whitespace, Java core/domain/storage tests, the offline authentication-helper
tests and the synthetic attachment fixture. No private SDK, credentials or signing
material are needed. Changing the domain/test script in #12 will therefore be
checked without coupling #9 to that branch.

## Separate SDK check

Run `sh scripts/check-sdk.sh` on the exact PR head in an approved local environment
with the pinned toolchain and external SDK described in DEVELOPMENT.md. This runs
Android build, lint and the static APK/signature contract check separately from
`core`. Record the commit SHA, toolchain/SDK versions, commands, result and limitations
in the PR; keep APKs, SDK files, detailed private logs and credentials outside GitHub.
It does not install an APK or prove runtime loading, coexistence or privacy.

No approved persistent SDK runner or authenticated status publisher has been
established. Accordingly, no automated `sdk` required check is claimed or added:
a skipped or placeholder job would not prove an SDK build. Until that environment
exists, human reviewers must obtain the separate SDK evidence for Android/SDK
changes. This is a documented gap against project-plan section 8, not equivalent
to an enforced SDK status. A future runner must isolate untrusted PR code from
private SDK inputs/credentials and publish results for the exact tested SHA before
its check is made required. Do not attach a persistent self-hosted runner directly
to arbitrary public PRs.

## Observed main protection

Read from the repository API on 2026-10-17:

- Repository visibility: public; default branch: `main`.
- Active ruleset: [Protect main](https://github.com/gustavtraff/tmm-sensor-planner/rules/24950534), applies to `refs/heads/main`.
- Pull request and resolved review threads required; zero formal approvals.
- GitHub Actions `core` required, with strict up-to-date checks.
- Linear history required; deletion and force pushes blocked.
- No bypass actors; the API reports the current caller can never bypass.
- Repository permits squash, rebase and merge commits; automatic branch deletion
  is disabled. The project process still requires a human squash merge.

Zero approvals accommodates the single human maintainer documented in
CONTRIBUTING.md. Parallel agents are not independent human reviewers. Once another
human reviewer has write access, require one approval and dismiss stale approvals.
Do not weaken protection to unblock an agent. Restricting repository merge methods
to squash remains a proposed owner setting change; no settings were changed here.

## Failing-PR enforcement evidence

Observed on [PR #31](https://github.com/gustavtraff/tmm-sensor-planner/pull/31),
commit `3fdd76058a5c7b5c9dd45a638672a7eb575509e7`: a temporary `exit 1` step
made the required `core` check [fail](https://github.com/gustavtraff/tmm-sensor-planner/actions/runs/38978073099/job/117232189312).
The GitHub API then reported an open, non-draft PR with no merge conflicts
(`mergeable: MERGEABLE`), current base `d073d606b3ad2903355ed038ba121e038bf396ba`,
failing status rollup and `mergeStateStatus: BLOCKED`. Together with the active
required-check rule and no bypass actors, this verifies the observed merge block.
No merge endpoint was called and no failing change was merged. The temporary
failure step is removed from the final branch; a fresh successful `core` run is
required before human review/merge. This is API merge-state evidence, not an
attempted merge rejection or a device/SDK test.

## Owner decisions and release boundary

| Decision | Recorded position / next action |
|---|---|
| Project-code license and contribution terms | Pending in LICENSE. A human rights holder must select and approve the exact license; no license is inferred from the repository being public. External contributions and distribution remain gated. |
| SDK/third-party distribution rights | Pending provenance/terms review in #2; no SDK publication. Project-code licensing cannot grant third-party rights. |
| APK distribution/signing | No release channel approved; no release exists. #7 retains standard-host signing, physical-device and coexistence gates. No APK upload, signing submission or release automation is added. |
| Product owner | Not formally appointed by name/date; repository owner must confirm. |
| Technical lead | Not formally appointed by name/date; repository owner must confirm, including the approved SDK check environment. |
| Information/security owner | Not formally appointed by name/date; repository owner must confirm publication review and a private reporting route. |
| Release owner | Not formally appointed by name/date; repository owner must confirm signing, distribution and release approval. |

The repository maintainer remains the routing point for these decisions; that is
not evidence of an organizational appointment or distribution authorization.
One human may hold several roles. Record each appointment and decision with name,
date and scope before closing #9. No data contracts change in this task.

At that time #9 retained the separate SDK enforcement, approved license and
distribution decisions and accountable-owner gates. These unresolved decisions
are now tracked in #55. The failure-enforcement
experiment above is complete. The existing public repository does not establish that its source,
history and artifacts have passed the information/licensing review required by
the project plan. Review those separately before any further distribution.
