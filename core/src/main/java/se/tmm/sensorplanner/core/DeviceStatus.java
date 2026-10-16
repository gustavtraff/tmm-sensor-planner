package se.tmm.sensorplanner.core;

/** Manual planning states; no telemetry or automatic transitions. */
public enum DeviceStatus {
    PLANNED, DEPLOYED, ACTIVE, RETRIEVED, CANCELLED;

    public boolean isDistanceCandidate() {
        return this == PLANNED || this == DEPLOYED || this == ACTIVE;
    }
}
