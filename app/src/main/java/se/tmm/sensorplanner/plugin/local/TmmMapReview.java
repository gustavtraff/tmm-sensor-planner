package se.tmm.sensorplanner.plugin.local;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Resolves a map UID against current local eligibility, never a cached display label. */
public final class TmmMapReview implements AutoCloseable {
    private final Predicate<UUID> accepted;
    private final Consumer<UUID> open;
    private volatile boolean closed;

    public TmmMapReview(Predicate<UUID> accepted, Consumer<UUID> open) {
        this.accepted = Objects.requireNonNull(accepted);
        this.open = Objects.requireNonNull(open);
    }

    public UUID match(String rawUid) {
        if (closed || rawUid == null) return null;
        UUID uid;
        try { uid = UUID.fromString(rawUid); }
        catch (IllegalArgumentException invalid) { return null; }
        if (!uid.toString().equals(rawUid)) return null;
        return accepted.test(uid) && !closed ? uid : null;
    }

    /** Recheck when invoked: a menu may outlive the device or its local eligibility. */
    public void open(String rawUid) {
        UUID uid = match(rawUid);
        if (uid != null && !closed) open.accept(uid);
    }

    @Override public void close() { closed = true; }
}
