package se.tmm.sensorplanner.core;

/** Session-only consent for the approved configured-server trial; never persisted, grants no send capability. */
public final class ConfiguredPublicationTrial {
    public static final int STATEMENTS = 4;

    public static final class Stream {
        final boolean enabled;
        final boolean connected;
        final String connectString;

        public Stream(boolean enabled, boolean connected, String connectString) {
            this.enabled = enabled;
            this.connected = connected;
            this.connectString = connectString;
        }
    }

    private String selected;

    public synchronized void begin(String stream, boolean[] statements) {
        if (stream == null || stream.isEmpty() || statements == null || statements.length != STATEMENTS)
            throw new IllegalArgumentException("Incomplete consent");
        for (boolean confirmed : statements) if (!confirmed) throw new IllegalArgumentException("Incomplete consent");
        selected = stream;
    }

    public synchronized void end() { selected = null; }

    public synchronized boolean live() { return selected != null; }

    /** Requires exactly one enabled stream, connected and selected. Any failure ends consent for good. */
    public synchronized boolean allows(Iterable<Stream> streams, boolean supportedProvider) {
        if (selected == null) return false;
        if (supportedProvider && onlyStream(streams, selected)) return true;
        selected = null;
        return false;
    }

    /** The single-stream rule, also used by ordinary Publicera (M2): exactly one enabled stream, connected and equal to this one. */
    public static boolean onlyStream(Iterable<Stream> streams, String connectString) {
        if (streams == null || connectString == null) return false;
        int enabled = 0;
        for (Stream stream : streams) {
            if (stream == null) return false;
            if (!stream.enabled) continue;
            if (!stream.connected || !connectString.equals(stream.connectString)) return false;
            enabled++;
        }
        return enabled == 1;
    }
}
