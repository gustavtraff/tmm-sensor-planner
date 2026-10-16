# ADR 0005: Defer Data Sync integration and production image sharing

Status: proposed for human review; bounded attachment feasibility evidence only.
Date: 2026-10-16. Tracking: [Issue #6](https://github.com/gustavtraff/tmm-sensor-planner/issues/6).

## Decision

Defer direct Data Sync integration until an official, applicable plugin API
contract and authorized test package are available. The inspected 5.6.0.24 SDK
does not provide that contract. This does not establish that no API exists.
Ordinary CoT and Mission Packages must not be advertised as Data Sync feed content.

Keep images local in the MVP. Mission Packages are a documented candidate for
later image transport, evaluated separately from the existing CoT fixture.
Do not put the local `imageRef`, absolute paths or content URIs in a shared object.
Metadata-only sharing must say **Image will not be shared** when an image exists.

## Evidence and consequences

See [DATA_SYNC_AND_ATTACHMENTS.md](../DATA_SYNC_AND_ATTACHMENTS.md) for the exact
SDK methods, synthetic fixture, isolated lab procedure, results and limits.
The standalone image and ZIP arrived byte-identically; recipient deletion removed
local bytes but retained sender/server copies. Stock PNG import started a placement
tool, cancelled without choosing a position. These behaviors must inform a later adapter.
The stock-host experiment does not implement TMM attachment association, a
publishing adapter, acknowledgment policy or Data Sync integration.

A direct server REST client or guessed plugin broadcasts would add a new,
unverified sharing route and are outside this issue. Reconsider Data Sync only
with documented feed selection, permissions and two-client evidence for the
reported plugin version. Standard phone coexistence remains Issue #7.

Before implementing image sharing, resolve the size/EXIF policy in Issue #8 and
test object/content identity, review, cancellation, missing files, disconnects,
restart, replacement, deletion and recipient acknowledgment. Package-local
deletion must not imply server erasure or remote withdrawal. Local image support
remains Issue #13; this investigation does not close that feature gate.
