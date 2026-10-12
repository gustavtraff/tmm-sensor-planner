package se.tmm.sensorplanner.core;

public enum DeviceType {
    GATEWAY, PIR, SCOUT;

    public boolean supportsBearing() {
        return this == PIR || this == SCOUT;
    }
}
