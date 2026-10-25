# Local image inspection (#66)

VersionCode 12 / `0.1.0-image-viewer.1` adds read-only inspection. **Visa bild**
opens a large window directly. Tap a pending image preview or **Visa större bild**
to inspect it before saving. Pinch to zoom, drag to pan, double-tap to zoom/reset,
or use **Zooma in**, **Zooma ut**, **Anpassa** and **Stäng**. Changing the viewport
refits the image; zoom/pan are not stored on the device record.

Closing inspection returns to the pending preview when one exists. It neither
saves nor cancels that preview. **Spara bilden lokalt** and **Avbryt** retain their
existing explicit commit/discard meaning. Back/Close, plugin Unload and stale
worker callbacks must release display bitmaps without writing metadata or deleting
an attached image. Missing/decode failures retain the saved reference.

## Detail and memory boundary

Inspection decodes the owned PNG on the existing serialized worker, independently
of the small preview. A current copy up to 1920 pixels is read at its stored resolution;
it is no longer enlarged only from the old approximately-1024-pixel thumbnail.
Legacy larger copies are sampled to at most 2048 pixels on either display-bitmap
edge (at most 4,194,304 ARGB pixels, approximately 16 MiB). The pending preview still
uses the smaller decode; a second bounded inspection bitmap is released on close.

Zoom is between fit and eight times fit; panning is clamped so the image cannot
be lost outside the viewport. Zoom enlarges available pixels and cannot recover
detail removed during earlier normalization. No new dependency, permission,
transport, image encoding, handle or snapshot migration is added by the viewer.
The [#66 quality amendment](IMAGE_QUALITY.md) raises new stored copies to 1920 pixels /
16 MiB, with the source limit still 10 MiB. Previously stored 1600-pixel images
remain unchanged. The larger storage cap does not raise this viewer's display-memory limit.

## Verification gates

Native API 26/35 tests exercise rendered fit/zoom/pan bounds, viewport refit,
bitmap ownership, a two-pointer pinch sequence and independent inspection/preview
decoding of current and legacy PNGs. Test actual SDK integration separately:
read-only open/zoom/reset/close, preview enlargement and return, Cancel/Save,
portrait/landscape, restart and Unload. Physical gesture/accessibility/placement
inspection and newly signed standard-host acceptance remain open under #66/#7.


### SDK candidate verification — 2026-10-20

- The standalone image/coordinator/session suite passed **48 tests**, with no
  failures, errors or skips, using native graphics on API 26 and 35. Image lint
  passed. SDK 5.6.0.24 debug/release assembly, app lint and all three app unit
  tests passed. The final debug APK checker passed; existing unsigned metadata
  signature-entry warnings remain. No domain/storage contract changed; the core
  script was not rerun locally for this viewer-only change.
- On an isolated AOSP API 35 arm64 / **ATAK-CIV 5.6.0.24 developer host**, saved
  inspection opened directly. Button zoom changed rendered pixels; Fit restored
  them. Dragging a generated grid moved the inspected area. Close and Android Back
  returned without changing snapshot bytes. Pending enlargement returned to the
  original Save/Cancel preview; Cancel preserved the prior snapshot/image and
  removed only the pending copy. Inspecting and then explicitly saving a generated
  grid succeeded, and subsequent inspection left the new snapshot unchanged.
- A first SDK attempt exposed controls drawn behind the system navigation area.
  The candidate now applies system-window insets; actual button presses and
  rendered zoom/reset were rerun successfully. A higher-density viewport was also
  checked. ATAK kept landscape orientation during that attempt, so it is **not**
  evidence of portrait acceptance. Screenshots, generated inputs and raw logs
  remain private.

The saved image also reopened after a cold host restart; zoom/reset/close left
the saved snapshot unchanged. The crash buffer contained no fatal exception.
Airplane mode, disabled Wi-Fi, first-rule IPv4/IPv6 OUTPUT drops and no reverse
tunnels were checked before stopping the task guest. No server was configured.

Tested debug APK SHA-256:
`450a28a541d0587c2300cb109c489ba4319acf7da647cd821b019eb5b5b8a2a4`.
Physical OnePlus camera/provider acceptance, physical pinch/TalkBack, portrait,
active-viewer Unload and a newly service-signed standard-host build remain open.
This change does not close #66 or #49 and does not authorize sharing or a release.

## Prepared local follow-up — 2026-10-25

[The offline acceptance protocol](LOCAL_ACCEPTANCE_PLAN.md) covers active-viewer,
picker and pending-preview Unload with saved-byte/source preservation checks.
It is preparation only: no new runtime result or completed acceptance is claimed.
