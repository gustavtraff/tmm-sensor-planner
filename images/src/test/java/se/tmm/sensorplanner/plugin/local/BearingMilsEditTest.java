package se.tmm.sensorplanner.plugin.local;

import org.junit.Test;
import static org.junit.Assert.*;

public class BearingMilsEditTest {
    @Test public void cardinalsIntercardinalsAndEveryWholeMilRoundTrip() {
        for (int i = 0; i < 8; i++) {
            assertEquals(Integer.toString(i * 800), BearingMilsEdit.format(i * 45d));
            assertEquals(i * 45d, new BearingMilsEdit(null).degrees(Integer.toString(i * 800)), 0);
        }
        for (int mils = 0; mils < 6400; mils++) {
            Double degrees = new BearingMilsEdit(null).degrees(Integer.toString(mils));
            assertTrue(degrees >= 0 && degrees < 360);
            assertEquals(Integer.toString(mils), BearingMilsEdit.format(degrees));
        }
        assertEquals(0d, new BearingMilsEdit(null).degrees("6400"), 0);
    }

    @Test public void untouchedRoundedValuesKeepTheirExactDegreesAndClearingIsExplicit() {
        for (Double original : new Double[]{0d, 89.999999d, 123.45678901234567d, 359.999999d, null}) {
            BearingMilsEdit edit = new BearingMilsEdit(original);
            assertEquals(original, edit.degrees(edit.text()));
            assertEquals(original, edit.degrees("  " + edit.text() + "  "));
            if (original != null) assertNull(edit.degrees(""));
            if (original != null) assertEquals(original, Double.valueOf(edit.degreesText(edit.text())));
        }
        BearingMilsEdit nearNorth = new BearingMilsEdit(359.999999d);
        assertEquals("0", nearNorth.text());
        assertEquals(359.999999d, nearNorth.degrees("0"), 0);
        assertEquals(0d, nearNorth.degrees("6400"), 0);
        assertEquals(90d, new BearingMilsEdit(12.345d).degrees("1600"), 0);
    }

    @Test public void invalidTextIsRejectedWithoutChangingTheOriginal() {
        BearingMilsEdit edit = new BearingMilsEdit(123.456789d);
        for (String invalid : new String[]{"-1", "+1", "6401", "1600.5", "1600,5", "1e3", "NaN", "∞", "1 600"}) {
            assertThrows(IllegalArgumentException.class, () -> edit.degrees(invalid));
            assertEquals(123.456789d, edit.degrees(edit.text()), 0);
        }
    }
}
