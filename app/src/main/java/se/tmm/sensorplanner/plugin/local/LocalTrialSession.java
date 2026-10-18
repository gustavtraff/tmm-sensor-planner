package se.tmm.sensorplanner.plugin.local;

/** Explicit process-local consent; no persistence, network configuration or send capability. */
public final class LocalTrialSession {
    private volatile boolean connectedTrial;

    public boolean canUseLocal(boolean offline) { return offline || connectedTrial; }
    public boolean canUseLab(boolean offline) { return offline && !connectedTrial; }
    public boolean isConnectedTrial() { return connectedTrial; }
    public void startConnectedTrial() { connectedTrial = true; }
    public void reset() { connectedTrial = false; }
}
