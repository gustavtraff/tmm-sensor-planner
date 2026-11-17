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

Symbol replacement changes no enum names, snapshot-v1 bytes or experimental wire
fields. No migration is required. UUID remains identity; bearing remains restricted
to PIR/Scout. Current local status/ID rules are recorded in
[ADR 0006](decisions/0006-local-mvp-product-rules.md). Symbol replacement is tracked
separately in [#43](https://github.com/gustavtraff/tmm-sensor-planner/issues/43).

## Approved map symbols (#43)

On 2026-10-18 the owner approved the revised reference-based Mini, Scout, PIR and
Gateway pictograms and the yellow/green/gray planning palette. The map now uses
four 64 px transparent PNGs in `app/src/main/res/drawable-nodpi`, rendered from the
[approved SVG masters and review sheet](DEVICE_SYMBOLS.md). White areas receive
status tint; black shapes/rings retain type identification independently of hue.
The previous M/S/P/G disc artwork is replaced; type/status text and bearing rules
remain unchanged. The manual and its extracted bitmaps are not packaged.

Planned is yellow; Deployed/Active are green; Retrieved/Cancelled are gray. Color
means manually maintained planning status, never sensor detection, alarms, hardware
health or connectivity. Status names remain in labels/details. Snapshot/wire
contracts are unchanged; actual test evidence and physical-client gaps are in
[the symbol record](DEVICE_SYMBOLS.md).
