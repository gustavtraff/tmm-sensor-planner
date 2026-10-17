# Device symbol proposal (#43)

Status: **artwork awaiting owner review**. The runtime still uses its provisional
letter icons until the proposed pictograms are approved.

![Revised reference-based device pictograms and approved planning palette](assets/device-symbols/review.svg)

| Device | Proposed original pictogram | Mapping |
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
readable in labels/details. The preview contains enlarged and 32 px samples. Small
pictogram recognition, map-background contrast, actual SDK rendering at relevant
scales and PIR/Scout bearing cues still require verification after artwork approval;
physical screen/accessibility checks remain under #12.

No domain, snapshot or wire change is proposed. Source masters are the four SVGs
in `docs/assets/device-symbols/`. White areas receive the SDK planning tint; black
outlines/pictograms provide contrast and shape identification independently of hue.
