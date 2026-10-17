# Device symbols (#43)

Status: **artwork and palette approved by the owner on 2026-10-18**. The map now
uses the approved pictograms in place of the provisional letter icons.

![Revised reference-based device pictograms and approved planning palette](assets/device-symbols/review.svg)

| Device | Approved pictogram | Mapping |
|---|---|---|
| Mini | Compact sensor head on a narrow stake | Owner-confirmed association recorded in #43 |
| Scout | Angled elongated camera body and front lens | EO/IR camera |
| PIR | Horizontal sensor housing with side extension | Passive infrared sensor |
| Gateway | Round G mark with directional accent | Gateway device, independent of base/mobile roles |

The owner rejected the first generic pictograms on 2026-10-18 and requested a
closer resemblance to the locally retained references. This revision redraws the
reference silhouettes and orientations using SVG shapes; the extracted manual
bitmaps remain private and excluded from Git/PR attachments. No permission to
redistribute the manual or its artwork is inferred. Gateway roles, sensor alarms, radio links and
hardware health are not represented by these planning symbols.

The owner approved the following palette in the project conversation on 2026-10-18:

| Planning status | Tint |
|---|---|
| Planned | Yellow (`#FFFF00`) |
| Deployed / Active | Green (`#00FF00`) |
| Retrieved / Cancelled | Gray (`#CCCCCC`) |

No red/fault state is introduced. Status remains manually maintained and explicitly
readable in labels/details. The preview contains enlarged and 32 px samples. Actual SDK rendering and remaining verification are recorded below; physical
screen/accessibility checks remain under #12.

No domain, snapshot or wire change is introduced. Source masters are the four SVGs
in `docs/assets/device-symbols/`. White areas receive the SDK planning tint; black
outlines/pictograms provide contrast and shape identification independently of hue.

## Implementation and verification

The four 64 px transparent PNGs retain the existing resource names, numeric resource
URIs and 32 px IconPointStyle. They were rasterized from the approved SVG masters
using macOS AppKit; no manual bitmap is packaged. Tint changes only the planning
colors. Existing text labels, selection radius, private feature layer and fixed
100 m PIR/Scout bearing cues are unchanged. No SDK API was added.

### Recorded evidence — 2026-10-18

Implementation commit: `0cbd571`. Tested Android Debug APK SHA-256:
`823b8051f6c4da913a77aa125be6ef924fe21e5570eb08d9f1fc2c79f11b41b7`.
A fresh, dedicated API-35 arm64 revision-2 guest ran SDK developer host 5.6.0.24.
Airplane mode was on, Wi-Fi/data/location off and guest IPv4/IPv6 egress blocked
except loopback. No server profile/feed, ADB reverse route or companion package was
configured. Other agents' emulators, signing worktrees and source files were untouched.
Generated fixtures and screenshots remain private.

| Check | Result |
|---|---|
| Approved masters / PNGs | All five SVGs parse without scripts, embedded images or external references. Four runtime PNGs are 64×64 RGBA and use the existing resource names. Enlarged/32 px review sheet was rendered and inspected. |
| SDK build / lint / static APK | `sh scripts/check-sdk.sh` passed with the supplied 5.6.0.24 SDK and Temurin 17.0.20.1+1. APK signature/discovery/permission/packaging checks passed; Android Debug signing is not standard-host approval. |
| Core regressions | `sh scripts/test.sh` passed, including domain/forms/reviews/lab fields, snapshot-v1/failure cases, separate-process reopen and 100-device scenario. No domain/snapshot/wire changes were needed. |
| Actual SDK map rendering | Twenty synthetic records covered all four types × all five statuses. Mini, angled Scout, horizontal PIR and G Gateway loaded through actual resource-backed IconPointStyle. Planned was yellow, Deployed/Active green, Retrieved/Cancelled gray. |
| Scale and direction | Normal scale and one zoom step either side retained the device shapes and tint. North-up PIR 0° arrows pointed north; Scout 90° arrows pointed east, with the matching planning color. These remain fixed 100 m cues. |
| Map selection / data preservation | Tapping the actual PIR Active icon opened its correct saved review. Snapshot bytes before and after map/selection tests were identical. |
| Plugin lifecycle | Plugin Manager unload/reload passed; all 20 drafts reopened and actual map symbols rendered again. Snapshot bytes remained identical. |
| Screen samples | Actual rendering inspected at 1920×1080 / 160 dpi and 1280×720 / 320 dpi landscape. The smaller view was zoomed out to fit the grid. This is bounded emulator evidence, not a physical readability/accessibility pass. |

Long synthetic IDs can overlap or be decluttered at dense scales, as with the
previous layer; the list and saved review remain available. This change does not
claim that all labels are simultaneously readable. Physical phone/standard signing,
portrait/rotated-map field use, diverse light/imagery backgrounds and accessibility
acceptance remain under #12 and the existing release gates. Color is secondary to
shape/text and does not distinguish Deployed from Active or Retrieved from Cancelled.
