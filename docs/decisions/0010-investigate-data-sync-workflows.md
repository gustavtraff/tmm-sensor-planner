# ADR 0010: Investigate Data Sync workflows before choosing a publishing adapter

Status: proposed for human review; investigation only.
Date: 2026-10-23. Tracking: #25, #4/#7, #14/#15.

## Context

The owner confirms an existing working Data Sync/8S server and wants TMM to use
that workflow. The supplied Data Sync APK contains user manuals describing feed
selection and addition of map items. Official server source exposes Mission API
operations. Neither establishes a supported third-party Android Data Sync contract
or preservation of the TMM model. Exact SDK export/detail signatures compile.

## Proposed decision

Investigate a reviewed published representation using Data Sync's own feed UI
before committing to a new direct HTTP client. Treat this as a test candidate,
not an implementation approval or promise that private features are selectable.
If a callable plugin API is provided, evaluate it against the same criteria.
A server Mission API adapter remains a separate version-matched architecture
choice requiring explicit review of authentication, TLS, permissions and delivery.

Keep ADR 0005's production deferral in force. This proposal expands the evidenced
investigation paths; it does not approve guessed broadcasts, decompilation of
private plugin interfaces, automatic sharing or a direct REST workaround.
Do not replace private feature-layer records with normal MapItems. The manual's
optional automatic publication and offline queue require dedicated privacy checks.
Images remain local and excluded from the first metadata spike.

## Consequences and exit gate

[DATA_SYNC_INTEGRATION.md](../DATA_SYNC_INTEGRATION.md) records source pins,
manual hashes, compile-only evidence, unanswered questions and a two-client plan.
Network experimentation follows reviewed #4/#7 evidence and coordinated lab
ownership. A stock-marker pass alone does not establish custom-field preservation,
durable TMM import, receipt acknowledgment or conflict correctness. #25 stays open.
No storage/wire/runtime change, transport activation or new app dependency occurs.
