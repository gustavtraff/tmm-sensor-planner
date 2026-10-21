package se.tmm.sensorplanner.plugin;

import se.tmm.sensorplanner.core.Position;

/** Keeps exact saved/map coordinates until the user actually changes their displayed grid. */
final class MgrsPositionEdit {
    private Position precise;
    private String display;
    private final boolean existing;

    MgrsPositionEdit(Position initial, boolean existing) {
        this.existing = existing;
        precise = initial;
        try { display = MgrsCoordinates.format(initial); }
        catch (IllegalArgumentException outsideUtm) { display = ""; }
    }

    String text() { return display; }

    Position resolve(String text) {
        if ((!display.isEmpty() || existing) && MgrsCoordinates.compact(text).equals(MgrsCoordinates.compact(display))) {
            return precise;
        }
        return MgrsCoordinates.parse(text);
    }

    void accept(Position point) {
        String next = MgrsCoordinates.format(point);
        precise = point;
        display = next;
    }
}
