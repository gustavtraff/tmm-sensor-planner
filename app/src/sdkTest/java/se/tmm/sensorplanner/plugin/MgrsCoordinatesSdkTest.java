package se.tmm.sensorplanner.plugin;

import se.tmm.sensorplanner.core.Position;

/** Runs against the actual external SDK classes; all coordinates are synthetic. */
public final class MgrsCoordinatesSdkTest {
    public static void main(String[] args) {
        for (double[] value : new double[][]{{0, 0}, {1, 3}, {-1, 3}, {60, 15}, {60, 6},
                {75, 15}, {75, 25}, {40, -120}, {-40, 150}, {0, 179.999}, {0, -179.999},
                {-80, 0}, {84, 0}, {0, 180}, {7.99999, 5.99999}, {8.00001, 6.00001}, {-79.9, 0}, {83.9, 0}}) {
            Position source = new Position(value[0], value[1]);
            String grid = MgrsCoordinates.format(source);
            Position restored = MgrsCoordinates.parse(grid.toLowerCase(java.util.Locale.ROOT));
            double latitudeMeters = Math.abs(source.latitude - restored.latitude) * 111320;
            double longitudeMeters = Math.min(Math.abs(source.longitude - restored.longitude), 360 - Math.abs(source.longitude - restored.longitude)) * 111320 * Math.cos(Math.toRadians(source.latitude));
            if (latitudeMeters > 2 || longitudeMeters > 2) throw new AssertionError("MGRS tolerance");
            MgrsPositionEdit edit = new MgrsPositionEdit(source, true);
            if (edit.resolve(edit.text()) != source) throw new AssertionError("Original precision changed");
            if (edit.resolve(" " + edit.text().toLowerCase(java.util.Locale.ROOT)) != source) throw new AssertionError("Whitespace changed precision");
        }
        for (String bad : new String[]{"", "61N AA 00000 00000", "00N AA 00000 00000", "31I AA 00000 00000",
                "31N ZZ 00000 00000", "31N AA 0000 0000", "31N AA 000000 000000", "31N AA -1000 00000",
                "31N AA 00000 0000x", "31C AA 00000 00000"}) {
            try { MgrsCoordinates.parse(bad); throw new AssertionError("Invalid grid accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        MgrsCoordinates.parse("31n\taa\r66021\n00000");
        Position point = new Position(1.123456789, 2.987654321);
        MgrsPositionEdit edit = new MgrsPositionEdit(point, true);
        String before = edit.text();
        try { edit.resolve("invalid"); throw new AssertionError(); } catch (IllegalArgumentException expected) { }
        if (edit.resolve(before) != point) throw new AssertionError("Failed parse changed position");
        Position selected = new Position(-1.123456789, 3.123456789);
        edit.accept(selected);
        if (edit.resolve(edit.text()) != selected) throw new AssertionError("Map precision changed");
        Position pole = new Position(89, 0);
        MgrsPositionEdit legacy = new MgrsPositionEdit(pole, true);
        if (legacy.resolve(legacy.text()) != pole) throw new AssertionError("Existing polar position changed");
        try { new MgrsPositionEdit(pole, false).resolve(""); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
        System.out.println("SDK MGRS format/parse, invalid input, hemisphere/zone boundaries and exact edit preservation passed.");
    }
}
