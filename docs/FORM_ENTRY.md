# Form keyboard and deployment time (#51)

This changes the guarded local form only. Snapshot v1, the UTC Instant contract,
manual status changes and optional deployment time are unchanged. There is no
sharing, synchronization or automatic deployment timestamp.

## Editing semantics

Every text/decimal input requests the keyboard's Done action and disables the
landscape fullscreen extract editor. Done hides the keyboard, keeps focus and
entered text, and neither saves nor navigates to another field. Hardware Enter in
single-line fields also completes editing; multiline comments retain hardware
Enter for newlines. Touch, hardware Tab and accessibility navigation are available.
The field's focused rectangle includes its preceding label so dialog scrolling
and keyboard resizing keep the active label in view.

Deployment time has **Välj datum och tid** and **Rensa utplaceringstid** controls.
The optional value is displayed as local `yyyy-MM-dd HH:mm:ss` with a numeric UTC
offset, plus the device timezone ID and a reminder that storage uses UTC.

- An empty form remains empty on opening or saving, including Planned drafts and
  manual status changes. No value is inferred from status.
- Opening the selector starts from the existing Instant, or the current device
  date/time for an empty value, in the device timezone captured when opened.
- Confirm the date, then the time. Only confirming both replaces the value.
  Cancelling either dialog preserves the previous value, including an empty value.
- A new selection has minute precision (seconds/nanoseconds become zero). Merely
  opening/editing/saving an existing value preserves its exact Instant, including
  seconds/nanoseconds; it is never round-tripped through the displayed text.
- Explicit Clear removes the value; cancelling the whole form still saves nothing.
- Nonexistent spring-forward clock times are rejected with a visible message,
  preserving the previous value. For an autumn overlap, the first occurrence
  (earlier offset) is selected; its numeric UTC offset is visible before saving.
- Dismissing the form or unloading the plugin dismisses any owned picker.

The adapter passes the selected Instant's canonical UTC text to the existing
DraftForm validator. Parsing, domain rules and persisted bytes have no new format;
no migration is needed. Other legacy core callers may still use strict UTC input.

## Checks

Run `sh scripts/test.sh` for core regression tests. In the documented private SDK
environment, `sh scripts/check-sdk.sh` additionally runs app unit tests, Android
build/lint and static APK checks. App tests assert UTC conversion across midnight
and New Year, the autumn overlap and spring gap in named timezones. They do not
establish actual keyboard, picker or ATAK behavior.

Actual-client procedure, using only synthetic records:

1. On a small portrait and landscape client, create/edit a draft. Enter ID, owner,
   multiline comment and decimal coordinates. Use the visible Done key: verify the
   keyboard closes, focus/text remain, and no hidden field is selected. Reopen by
   touching another field; check its label while the keyboard is visible. Exercise
   hardware Tab/Enter and intentional field navigation.
2. Save without choosing a time and reopen: **Ej angivet** remains. Change status
   without choosing a time; it remains optional. Opening the empty selector must
   default to now. Cancel at both stages and check no timestamp was assigned.
3. Choose a local time spanning UTC midnight and save/reopen. Confirm the stored
   UTC Instant, local display and explicit zone/offset. Reopen/cancel and save an
   existing value with seconds/nanoseconds: the exact Instant must remain.
4. Explicitly clear and save/reopen. Cancel a whole edited form and ensure its
   original record persists. Test end-of-month/year dates and the local DST gap
   and overlap. Dismiss/unload with a picker open and confirm no orphan dialog.

## Verification record — 2026-10-18

Implementation and SDK-emulator verification in progress. Physical-phone and
service-signed standard-client verification remain separate acceptance checks.
