package se.tmm.sensorplanner.plugin.local;

import java.math.BigDecimal;

/** UI-only 6400-circle conversion; untouched rounded text retains exact canonical degrees. */
public final class BearingMilsEdit {
    private final Double original;
    private final String display;

    public BearingMilsEdit(Double original) {
        this.original = original;
        display = format(original);
    }

    public String text() { return display; }

    public Double degrees(String text) {
        String value = text == null ? "" : text.trim();
        if (value.equals(display)) return original;
        if (value.isEmpty()) return null;
        if (!value.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Whole mils required");
        int mils = Integer.parseInt(value);
        if (mils > 6400) throw new IllegalArgumentException("Mils range");
        return (mils % 6400) * 360d / 6400d;
    }

    public String degreesText(String text) {
        Double degrees = degrees(text);
        return degrees == null ? "" : BigDecimal.valueOf(degrees).stripTrailingZeros().toPlainString();
    }

    public static String format(Double degrees) {
        if (degrees == null) return "";
        if ((Double.isNaN(degrees) || Double.isInfinite(degrees)) || degrees < 0 || degrees >= 360) throw new IllegalArgumentException("Degrees range");
        return Long.toString(Math.round(degrees * 6400d / 360d) % 6400);
    }
}
