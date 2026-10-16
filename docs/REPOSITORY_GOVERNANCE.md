# CI and repository/release governance

Issue: [E0-08 / #9](https://github.com/gustavtraff/tmm-sensor-planner/issues/9).
Evidence date: 2026-10-17. This document records observed settings and open human
decisions; it does not authorize a merge, release or operational data sharing.

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

Pending: create an isolated, deliberately failing `core` check, observe its failed
conclusion and the PR's blocked merge state under this ruleset, then repair or close
the probe. Never merge the failing change or call the merge endpoint as a test.
Record the PR, failing commit and Actions result here. Green checks alone do not
establish that failure blocks merging.

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

Issue #9 remains open until the separate SDK enforcement, approved license and
distribution decisions, accountable owners and failure-enforcement evidence are
complete. The existing public repository does not establish that its source,
history and artifacts have passed the information/licensing review required by
the project plan. Review those separately before any further distribution.
