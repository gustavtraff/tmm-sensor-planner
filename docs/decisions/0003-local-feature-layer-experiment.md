# ADR 0003: Investigate an in-memory feature layer for local previews

Status: experimental; production representation and transmission privacy not accepted.
Date: 2026-10-13. Tracking: [Issue #4](https://github.com/gustavtraff/tmm-sensor-planner/issues/4).

## Choice for the experiment

Test a plugin-owned `FeatureLayer3` backed by `FeatureSetDatabase2(null)` instead of
an ordinary ATAK Marker in the root MapGroup. The proposed advantage is separation
of local rendering from the normal MapItem/CoT persistence and dispatch path.
The probe has no publishing adapter, uses only one disposable synthetic point and
requires offline test mode. See [LOCAL_MAP_PROBE.md](../LOCAL_MAP_PROBE.md).

## Consequences and required evidence

The existing Java domain model is unchanged. The experiment does not implement
storage, a complete device catalogue, editing, selection, image handling or sharing.
Points are lost on unload/restart by design; this is unsuitable for F04 storage.

Compilation and one-client offline rendering cannot prove N03. Two clients and
server/traffic observation, standard signing/phone tests and review of export/share
integration remain required before accepting this as the production representation.
Do not infer privacy from absence of plugin permissions: it runs inside ATAK.
