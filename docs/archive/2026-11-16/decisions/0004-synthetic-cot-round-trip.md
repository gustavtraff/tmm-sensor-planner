# ADR 0004: Investigate explicit single-recipient CoT with synthetic fixtures

Status: experimental; not a production publishing or wire-format decision.
Date: 2026-10-15. Tracking: [Issue #5](https://github.com/gustavtraff/tmm-sensor-planner/issues/5).

## Choice

Use a separate loopback-only SDK tool to construct disposable CoT fixtures, select
one individual contact, review every field and require active confirmation.
Dispatch with explicit nonempty `toUIDs` and `CoTSendMethod.TAK_SERVER` through the
SDK external dispatcher. Do not create a local ATAK marker before confirmation.

Register a narrowly scoped detail handler before reception, retain only validated
synthetic fields on the received marker, then require exactly one matching marker
and validate its actual host export before manual return. Observe transport without
replacing global import processors or writing raw incoming events to app logs.
See [COT_ROUND_TRIP.md](../COT_ROUND_TRIP.md) for API evidence and runtime results.

## Alternatives and consequences

The stock generic contact sender can expose broadcast; a small explicit picker
avoids an accidental null/default recipient. The SDK dispatcher return collection
is not a reliable delivery acknowledgment. The probe therefore never claims shared
or delivered status; evidence comes from actual recipients and server records.

A raw event echo alone would miss metadata lost during host marker import/export.
Validating the actual exporter is required. Reconstructing only approved fields
after that validation avoids forwarding unknown details or local image paths.

The fixed `tmmProbe` format and private marker key are experimental. This does not
freeze the domain schema, implement draft persistence, conflict handling, images,
Data Sync or production sharing. Those remain separate decisions and gates.

## Evidence

The bounded experiment passed on 2026-10-15–16: all four fixed device types retained
UUID, fields and point through actual marker export on both clients. Same-UUID
resends retained one marker; cancellation and final-confirmation disconnect sent
no recorded event. Reload/recovery produced one fresh arrival. A lab JVM encoding
defect was corrected to UTF-8 without relaxing validation. Human PR review accepts
this feasibility result; production schema and release decisions remain separate.
