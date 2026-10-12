# Contributing

Follow Issue → branch → PR → review → squash merge. Use feature/<issue>-description,
fix/<issue>-description, or docs/<issue>-description. For initial setup, use local
backlog IDs until GitHub Issues exist. Include requirement IDs, test evidence,
and data impact in the PR. Protect main once CI checks exist and have been verified.

All documentation, code comments, commit messages, and GitHub content must be in
English, including Issues, PRs, reviews, comments, labels, and milestones.
Plugin UI text may be Swedish.

Keep the Java core free of Android/ATAK dependencies and compatible with Java 8
until the 5.5.0 SDK determines the toolchain. Run sh scripts/test.sh.
Use only synthetic data. The license and contribution terms are not yet decided;
obtain that decision before accepting external contributions or distributing the project.

## Recommended main protection

Protect `main` with a required pull request, the GitHub Actions `core` check,
up-to-date branches, resolved conversations, and blocked force pushes and deletion.
Apply the rules to administrators as well. Use squash merge.

The repository currently has one collaborator, who also authors the PRs.
Require zero formal approvals until another human reviewer has write access;
GitHub does not allow authors to approve their own PRs. Human review and a deliberate
merge decision still apply. Once a second reviewer is available, require one approval
and dismiss approvals when new commits are pushed. An agent review comment is not
an independent human approval.

These are recommendations, not a claim that protection has been enabled.

Verified on 2026-10-12 after PR #16 was merged: an active ruleset on `main` requires
a pull request, resolved conversations, the GitHub Actions `core` check and an
up-to-date branch. It also blocks deletion and force pushes and requires linear
history. Required approvals are zero. No bypass actor is shown in the applicable
ruleset. The failing-PR enforcement test remains pending in Issue #9.
