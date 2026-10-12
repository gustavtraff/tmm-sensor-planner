package se.tmm.sensorplanner.core;

/** Decimal degrees. No altitude or terrain correction. */
public final class Position {
    public final double latitude;
    public final double longitude;

    public Position(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be finite and within [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be finite and within [-180, 180]");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
