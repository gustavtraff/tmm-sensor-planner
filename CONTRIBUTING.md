# Contributing

The maintainer and sole authoritative human reviewer/merge decision maker is
[@gustavtraff](https://github.com/gustavtraff). Other users may propose changes
and provide feedback; only the maintainer accepts a change, merges it or approves
a release. Agent review comments are supporting evidence, not human approval.

## Before contributing

The project-code license and contribution terms are still undecided in [LICENSE](LICENSE).
Agree those terms with the maintainer **before submitting code for acceptance**;
public visibility is not an approved contribution or distribution policy. Synthetic
bug reports and product feedback are welcome. Do not send private SDK material,
real positions/photos, server details, credentials or certificates.

Use [SECURITY.md](SECURITY.md) for sensitive findings. Do not place them in public
Issues, PRs, workflow logs or attachments. All documentation, code comments,
commit messages and GitHub content must be in English. Plugin UI text may be Swedish.

## Contribution workflow

Once contribution terms are agreed:

1. Open or agree an Issue with a small scope and acceptance criteria.
2. External contributors fork the repository; no repository write access is needed.
3. Branch from current `main`: `feature/<issue>-description`,
   `fix/<issue>-description`, `docs/<issue>-description` or `chore/<issue>-description`.
4. Keep the Java core independent of Android/ATAK and compiled for Java 8;
   use the pinned JDK 17/toolchain in [DEVELOPMENT.md](docs/DEVELOPMENT.md).
5. Run `sh scripts/test.sh`. For image changes also run
   `./gradlew -p images --no-daemon testDebugUnitTest lintDebug` with the documented
   Android environment. Android/ATAK changes additionally need separate local SDK
   build/lint/APK evidence for the exact PR commit; public CI does not provide it.
6. Open a PR using the template. State the problem, Issue/requirement IDs, test
   evidence, data/schema impact and remaining device/SDK limitations. Use synthetic
   fixtures. Keep APKs, SDKs, keys and raw sensitive evidence outside Git.
7. Resolve feedback and rerun affected checks after changes or an updated base.
   Both `core` and `images` should pass. The maintainer makes the final review and
   squash-merge decision; automated or agent reviews do not authorize a merge.

Do not add automatic sharing, relax the offline guard, alter a data contract or
claim an ATAK API without the approvals/evidence required by the project plan.
Follow [TESTING.md](docs/TESTING.md) and update affected contracts/migration tests.

## Review routing and main protection

[CODEOWNERS](.github/CODEOWNERS) names `@gustavtraff` for every path, including CI
and contribution rules. It requests owner review for applicable PRs. It does not
prevent public review comments or make owner approval mandatory by itself.

Current main protection requires a PR, strict `core` status, resolved conversations,
linear history, and blocked force pushes/deletion, with no bypass actors. Only the
owner currently has repository write/admin rights. Requiring `images`, limiting
merges to squash and automatic branch deletion are proposed setting changes in
[the current governance audit](docs/REPOSITORY_GOVERNANCE.md).

Keep zero formal approvals while the owner also authors PRs (including PRs opened
with the owner's token): GitHub prevents self-approval. Human review and a deliberate
merge remain required by project policy. If technical enforcement of the owner's
approval is needed, first use a separate bot/App identity for contributor PRs,
then review required code-owner approval and stale-approval dismissal without
creating a bypass. Do not add another human reviewer automatically: the owner
has explicitly requested sole authoritative review.

## Parallel agents and worktrees

Each task uses its own worktree and branch from the latest `origin/main`. Never
switch branches, stash, reset, clean or edit files in another agent's worktree.
For example, for Issue #9:

```sh
git fetch origin
git worktree add -b chore/9-ci-repository-decisions /tmp/tmm-ci-issue-9 origin/main
```

`chore/<issue>-description` is also accepted for repository/CI work. Worktrees
share Git refs and remotes, so give every task a unique branch and avoid changing
shared Git configuration. Stage explicit paths and inspect the staged diff before
committing. Push only the task branch and open one PR referencing the Issue.

After another PR merges, update this task branch from `origin/main` in its own
worktree, resolve overlaps and rerun the affected checks. The strict required
`core` check must pass against current `main` before human squash merge. Remove
the task worktree/branch only after merge or deliberate abandonment.

The current protection evidence, separate SDK procedure and unresolved owner
choices are tracked in [repository governance](docs/REPOSITORY_GOVERNANCE.md).
