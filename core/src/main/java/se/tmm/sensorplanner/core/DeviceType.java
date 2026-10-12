package se.tmm.sensorplanner.core;

public enum DeviceType {
    GATEWAY("Gateway"), PIR("PIR"), SCOUT("Scout"), MINI("Mini");

    public final String displayName;

    DeviceType(String displayName) {
        this.displayName = displayName;
    }

    public boolean supportsBearing() {
        return this == PIR || this == SCOUT;
    }
}
