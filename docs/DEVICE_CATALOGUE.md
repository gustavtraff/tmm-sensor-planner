# Device catalogue

Tracking Issue: [#8](https://github.com/gustavtraff/tmm-sensor-planner/issues/8).
The repository owner selected exactly **Mini, Scout, PIR and Gateway** on 2026-10-12.
These are the plugin's display names. Other devices mentioned in the manual are
outside the current type catalogue.

| Display name | Domain type | Bearing | Manual terminology / evidence |
|---|---|---|---|
| Mini | MINI | Not supported | The manual lists a seismic/acoustic sensor; associating its symbol with Mini is a candidate, not a confirmed model identification |
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
device type. The Mini symbol mapping and permission to redistribute the graphics
are not established. Use original plugin artwork until those decisions are settled.

## Data impact

MINI is added to the existing Java enum; GATEWAY, PIR and SCOUT keep their names and
order. There is no serialized storage format yet, so no stored records are migrated.
Do not serialize enum ordinals when storage is introduced. Existing bearing rules
remain restricted to PIR and Scout, and distances include all four types.

The proposed status catalogue, allowed transitions, ID/owner conventions and other
product decisions remain open in Issue #8. Selecting the names does not close that Issue.
