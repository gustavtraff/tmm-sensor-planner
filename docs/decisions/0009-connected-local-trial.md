# ADR 0009: Explicit connected local trial

Date: 2026-10-18. Status: proposed for Issue #4 human PR review.

## Decision

Keep offline local planning as the default. Permit a connected, synthetic-data
trial only after the user selects **Starta lokalt test** in TMM's warning dialog.
Consent is held in memory and reset on plugin Unload or process restart. Connected
startup does not load the draft layer before that choice. This is a test gate,
not authorization to use an operational server or to share real data.

Use the existing private feature layer, namespaced no-backup store and local image
pipeline. Do not register domain drafts as normal ATAK markers or add any transport.
Disable both the saved-metadata sharing UI and CoT lab for the entire trial session,
including when the device subsequently returns offline. Invalidate pending lab
confirmation/receiver state when the session starts. Existing offline lab routing
restrictions remain unchanged. No data contract or migration changes.

## Consequences and verification

This narrowly amends ADR 0008's offline-only entry restriction. It does not approve
ordinary publishing, Data Sync, attachment sharing or production connected privacy.
The session-policy test covers both connectivity inputs, reset and fresh instances;
actual Android lifecycle, host traffic and recipient observation require runtime tests.
Follow [the connected trial procedure](../CONNECTED_LOCAL_TRIAL.md). SDK-host results
cannot establish standard-host or phone compatibility; a new service-signed candidate
and explicit human review are required for that stage under #7.
