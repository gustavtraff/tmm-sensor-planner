# Device catalogue

Tracking Issue: [#8](https://github.com/gustavtraff/tmm-sensor-planner/issues/8).
The repository owner selected exactly **Mini, Scout, PIR and Gateway** on 2026-10-12.
These are the plugin's display names. Other devices mentioned in the manual are
outside the current type catalogue.

| Display name | Domain type | Bearing | Manual terminology / evidence |
|---|---|---|---|
| Mini | MINI | Not supported | The manual lists a seismic/acoustic sensor; the owner confirmed the Mini-to-seismic/acoustic symbol mapping in #43 (2026-10-17) |
| Scout | SCOUT | Optional | The manual's §4.4.12.8 explicitly associates Scout Camera settings with the EO/IR camera |
| PIR | PIR | Optional | The manual identifies PIR with the passive infrared sensor |
| Gateway | GATEWAY | Not supported | Gateway TMM; base station and mobile-base symbols describe roles, not additional plugin types |

## Source and symbol references

Source: user-provided FMV manual **M7786-043711, edition 3**. Its component
catalogue is on printed pages 9–10 (PDF pages 13–14); device-symbol examples are
on printed page 70 (PDF page 74). Names are documented in our own words; no manual
pages, screenshots or original graphics are included in this repository.

Original reference PNGs were extracted locally at native resolution (27–46 pixels).
The source colors indicate live detection/alarm state and must not be reused as
planning statuses such as planned or deployed. A Gateway role is distinct from a
device type. Mini mapping is now confirmed; permission to redistribute the manual
graphics remains unestablished. Use original plugin artwork. The owner approved
planning-status colors on 2026-10-18; these colors carry no detection/alarm meaning.

## Data impact

MINI is added to the existing Java enum; GATEWAY, PIR and SCOUT keep their names and
order. There is no serialized storage format yet, so no stored records are migrated.
Do not serialize enum ordinals when storage is introduced. Existing bearing rules
remain restricted to PIR and Scout, and distances include all four types.

Local status and ID rules were subsequently adopted in [ADR 0006](decisions/0006-local-mvp-product-rules.md).
Symbol replacement is tracked separately in [#43](https://github.com/gustavtraff/tmm-sensor-planner/issues/43).

## Original prototype symbols

The offline planner uses four original 64 px transparent PNG symbols in
`app/src/main/res/drawable-nodpi`: a black disc with a white M/S/P/G glyph and ring,
tinted by planning status. Glyphs are simple independently drawn vector strokes;
no font, manual graphic or third-party icon is copied. Type is visible even if ATAK
declutters the longer label. These are local prototype symbols, not claimed manual
symbol identifications or sensor alarm states. See [LOCAL_PLANNER.md](LOCAL_PLANNER.md).

## Replacement artwork review (#43)

[The original pictogram proposal](DEVICE_SYMBOLS.md) shows all four device types
in the owner-approved yellow/green/gray planning palette, including 32 px samples.
Artwork approval and actual map rendering remain pending; the proposal has not
replaced runtime assets. Snapshot/wire contracts and bearing rules are unchanged.
