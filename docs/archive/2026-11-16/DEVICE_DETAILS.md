# Device review and 6400-mil bearing (#67)

VersionCode 15 / `0.1.0-device-details.1` prioritizes position, image and bearing.
The manual ID remains a wrapping title. Type and explicit lifecycle status appear
first, separate from **Lokalt utkast · delas inte automatiskt**. Server connectivity
never changes that publication state or derives a lifecycle status.

## Review and editing

- Position uses the existing MGRS adapter. **Kopiera MGRS** copies that displayed
  grid only after an explicit click. **Visa på kartan** centers the saved device.
  **Flytta på kartan** opens the retained editor and its existing map picker;
  Use changes the form, Save locally commits, and Cancel preserves stored data.
- A saved image has a bounded inline thumbnail above **Visa bild / Ersätt bild**.
  Tapping the thumbnail opens the existing large zoomable viewer. Missing/failed
  previews retain the reference and offer **Visa bild**; they never delete or detach
  an image. A device without an image shows **Lägg till bild**.
- PIR/Scout show bearing in mils with a read-only north-up direction cue. The cue
  represents true north independently of device/map rotation. It is not a sensor
  compass measurement, coverage sector or draggable control.
- Owner, comment, deployment time and eligible-device/gateway distances follow.
  **Fler uppgifter** reveals created/updated time, local revision, the full
  local-publication explanation, existing lab action (only where allowed) and
  remove-image/delete-device controls. **Färre uppgifter** collapses them.
- The editor keeps ID/type/status first, then MGRS/map selection and bearing;
  owner, deployment time and comment follow. **Redigera** remains available in the
  fixed dialog controls. All five statuses still change manually.
- The list action is **Uppdatera**. It reloads the saved snapshot and redisplays
  the list. Storage failure recovery points to the same action; there is no new
  help/details interpretation, retry policy or automatic storage reset.

## Bearing contract and preservation

A full circle is **6400 mils**, clockwise from **true north**. Input is an optional
whole number **0–6400**; 6400 is accepted as the same direction as zero. Fractions,
negative/signed values, scientific notation and values above 6400 fail inline.
The numeric keyboard does not silently strip punctuation from pasted text before
validation. Clearing an existing value is explicit; Mini/Gateway still reject a
retained nonempty bearing rather than silently dropping it on type change.

Canonical domain, snapshot v1, map-arrow geometry and lab wire units remain
**degrees in [0,360)**. New input converts as `(mils % 6400) * 360 / 6400`.
Display uses nearest whole mil (`Math.round(degrees * 6400 / 360) % 6400`), with
positive half-mil ties rounded upward. Thus N/NE/E/SE/S/SW/W/NW are
0/800/1600/2400/3200/4000/4800/5600. Integer display precision is not a positioning
or compass-accuracy claim.

The edit model caches the original exact degree value and rounded display text.
Unchanged text (ignoring surrounding whitespace) resolves to the original value;
an unrelated owner/status/comment edit must not quantize it. For example,
123.456789 degrees displays as 2195 mils but remains 123.456789 until the input is
changed. A near-360 value can display zero while retaining its exact original
angle. Explicitly typing 6400 converts to canonical zero. No background migration,
unit reinterpretation, UUID/image/position rewrite or new shared status is added.

## Thumbnail ownership

The existing serialized worker reads the existing owned image with its current
16 MiB bound and decodes at most 1024 pixels per edge through LocalImageBitmaps.
The detail-view thumbnail owns that display bitmap, detaches it before recycling
on dismiss/replacement/Unload and discards late callbacks. An unavailable owner
is checked before read and before attaching pixels. Errors expose no paths/provider
text. The large viewer keeps its separate display limit and ownership. No image
encoding, stored-copy quality, handle, original file or transport changes.

## Verification

- The independent suite passed **61 tests**, no failures/errors/skips: all 6400
  integer directions, cardinal/intercardinal conversion, 0/6400, near-360 wrap,
  unchanged high-precision degrees, clear/invalid input, native compass rendering
  on API 26/35 and asynchronous thumbnail close/stale-callback/unavailable-owner
  handling. Image lint passed. These checks do not establish physical TalkBack.
- Core regression script (including snapshot/separate-process/100-device checks)
  and actual-SDK MGRS conversion checks passed. Final SDK 5.6.0.24 debug/release
  assembly, app lint, three app tests and the APK checker passed. Existing unsigned
  metadata-entry signature warnings remain. Canonical snapshot/wire formats did
  not change, so no migration rewrites existing records.
- Actual isolated AOSP API 35 arm64 / SDK developer host 5.6.0.24: status-first
  review, bounded thumbnail tap-to-inspect/zoom, actual clipboard Copy then Paste
  of MGRS, unrelated owner Save and fractional mil rejection passed. Explicit 6400
  saved/displayed as north 0. A captured-snapshot core-reader comparison confirmed
  the old 123.45678901234567-degree value survived the unrelated edit exactly;
  UUID/createdAt/position/imageRef/deployment/comment and other records remained.
- Retained nonempty bearing blocked an unsupported Mini type change. Direct map
  action opened the existing picker and Cancel retained the form; cancelling the
  whole form saved nothing. Uppdatera reloaded the same list; expanded technical
  details showed revision and older actions without writes.
- Temporarily renaming only the generated owned image produced a readable thumbnail
  fallback and a safe full-view failure without changing metadata/reference. The
  file was restored byte-identically. Long synthetic IDs wrapped and status was
  visible in 1024 × 768 / 240 dpi landscape; scrolling reached image inspection.
  Cold reopening preserved snapshot/image bytes. This is not portrait evidence.
- Raw snapshots, generated image inputs, screenshot evidence and the synthetic
  fixture/probe remain private. No real photo/location, server connection or new
  image-sharing behavior was used.

A new signed standard-host/OnePlus run, portrait, physical accessibility and
active-viewer Unload remain separate acceptance gates under #67/#7. Camera-provider
acceptance stays in #49. The optional draggable compass is deferred; no SDK bearing
or magnetic-reference API is invented. CI is not awaited or claimed, per owner
instruction. No signing ZIP/upload, operational sharing or release was performed.


After removing the now-unused decimal-field branch, the exact final debug APK was
installed and its hash verified in the guest. Cold review/thumbnail/full-view and
fractional-input rejection were repeated: `16.5` remained visible and failed
without writes; snapshot and PNG bytes stayed identical. The captured crash buffer
had no fatal exception. Airplane mode, disabled Wi-Fi, first-rule IPv4/IPv6 OUTPUT
drops and no reverse tunnels were verified before stopping only the task guest.
This establishes no production-network privacy or signed-client result.

Final tested debug APK SHA-256:
`4242e48dce10339e6168b16689bafc8d75130fd2b9adc9406e4759739d6f76a9`.
