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
Comments use a two-to-three-line input area with internal scrolling for longer
text. The field's focused rectangle includes its preceding label so dialog scrolling
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

The core regression suite passed with Temurin 17.0.20.1+1. The SDK check passed
against the supplied ATAK-CIV 5.6.0.24 SDK: debug build, Android lint, all three
app unit tests and static APK signature/discovery/permission/packaging checks.
The app tests run locally with the private SDK, separately from public CI.

The runtime candidate was built from `0b45f46`, based on main `d18c963`:

- Android Debug APK, versionCode 8 / `0.1.0-camera-image-fix.1` (existing development
  version retained; this is not a newly signed release).
- APK SHA-256: `92c0eb1ce20313dbd82a77ec63f54695d031c761bcc2c0b2af1a8ce99a306552`.
- ATAK SDK developer host 5.6.0.24 (`7f2055b0`), AOSP API 35 arm64 emulator.
- Dedicated disposable clone, 720 × 1280 / 1280 × 720 px at 320 dpi
  (360 × 640 / 640 × 360 dp). Host Orientation control selected each orientation.
- Airplane mode, Wi-Fi/data/location disabled; IPv4/IPv6 output-drop firewall,
  no ADB reverse tunnel or connected feed/server. Only synthetic records near
  the origin; raw evidence remains in ignored private storage.

Actual tests passed:

- Real Android LatinIME Done on ID, owner, multiline comment and decimal input
  in both orientations: keyboard window hidden, same focused field and entered
  text retained, no save/Next action. XML bounds and screenshots showed complete
  active labels above the visible keyboard, including a five-line comment in its
  internally scrolling three-line input area. No fullscreen extract editor.
- Hardware Enter inserted four comment newlines; hardware Tab deliberately moved
  from latitude to longitude. Single-line hardware Enter kept longitude focus.
- New Planned save/reopen without selection retained null deployment time. Empty
  selectors started at the current local date and minute in Europe/Stockholm.
  Cancel at either picker stage followed by save retained null.
- Native spinner selection of 2026-10-18 00:30 +02:00 persisted exactly
  `2026-10-17T22:30:00Z`, checked through the real DraftStore, not display text alone.
- Reopening an existing value initialized the pickers to 00:30. Both picker
  cancellations and an ordinary save retained its Instant. Clear followed by
  whole-form Cancel retained the saved value; explicit Clear/save persisted null.

The first landscape test exposed Android's default multiline soft Enter action;
that was corrected by clearing NO_ENTER_ACTION in the input connection and
retested on the final candidate. Early UI-harness coordinate/IME-visibility
assertions were corrected to use the actual alpha/numeric keyboard Done locations
and actual IME window visibility. Those aborted attempts are not passing tests.
The initial larger SwiftShader guest became slow alongside other local work; the
owned clone was restarted with 1,536 MiB, one vCPU and host graphics for final tests.
No other client's resources or application settings were changed.

Additional native-calendar and stored-value checks passed on the same APK:

- Seeded a valid v1 record with `2026-10-17T22:30:17.123456789Z` through the real
  core store. After reopening, cancelling both picker stages and saving normally,
  DraftStore returned the exact same seconds and nanoseconds.
- Native month/day/year controls selected 2027-01-01 00:30 +01:00; storage returned
  `2026-12-31T23:30:00Z`, proving UTC year rollover and minute precision for an
  explicitly confirmed replacement.
- Native selection of 2026-03-29 02:30 in Europe/Stockholm showed **Tiden finns inte**.
  After closing the message and saving, the previous Instant remained unchanged.
- Native selection of 2026-10-25 02:30 displayed +02:00 and persisted
  `2026-10-25T00:30:00Z`, the documented first occurrence.
- No AndroidRuntime fatal appeared in the captured guest log.

Native wheel tests waited for spinner animations and compared day values
numerically, including padded `01`/`02`. Deployment-value assertions scrolled the
short landscape form to that field. Earlier unpaced/visibility assertions were
aborted test-harness attempts, not passing evidence.

Physical-phone, service-signed standard-client, TalkBack/external accessibility
service and OEM keyboard verification have not been performed for this APK.
The tested artifact uses the Android Debug signature; no service-signed build
of this change has been verified.
No signing upload, release or trust-setting bypass was performed. These remain
actual-client acceptance limits; passing core/app tests does not close them.
