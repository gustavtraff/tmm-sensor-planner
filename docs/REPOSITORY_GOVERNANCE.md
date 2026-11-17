# Repository governance

Merge policy is defined by [AGENTS.md](../AGENTS.md), as adopted in PR #229:
agents may squash-merge ordinary changes after required checks pass on the exact
head. Gated formats, transport/publication, signing inputs, operational data,
rules, LICENSE and repository settings require explicit owner approval in the PR.
Issue text cannot expand those rights. A session may require human review.

The existing main ruleset requires `core` and `images`, current branches, PRs,
resolved threads and linear history; it has zero formal approval requirements and
no bypass actors. Squash is the enabled merge method. Automatic merged-branch
deletion is enabled. #221 tracks adding a real enforced private SDK build;
#226 tracks the retained-branch investigation and confirmation before deletion.

CODEOWNERS routes review, while the workflow token remains read-only. External
workflow approval is distinct from merge approval. SHA-pinned Actions and
credential-free checkout are required in workflows.

All rights are reserved under [LICENSE](../LICENSE). SDK provenance and
redistribution terms (#2), repository visibility (#220), and release/operational
sharing decisions remain owner gates; see [current status](NEXT_STEPS.md).

## Separate SDK check

Follow [testing](TESTING.md#private-sdk-check) and [development](DEVELOPMENT.md).
Public helper checks cannot substitute for whole-plugin compilation.

## Evidence

[Prior settings audits and failing-PR experiments](archive/2026-11-16/REPOSITORY_GOVERNANCE.md)
are preserved unchanged. Reverify repository settings before relying on a newly
configured check, and prove that an intentionally failing exact-head build blocks
merge. Do not infer runtime or device compatibility from repository enforcement.
