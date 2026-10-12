package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable local draft. This model has no publication operation or shared state. */
public final class TmmDraft {
    public static final int SCHEMA_VERSION = 1;
    // shortcut: provisional text limit, confirm with the product owner before schema freeze.
    public static final int MAX_COMMENT_LENGTH = 2000;

    public final UUID uid;
    public final String displayId;
    public final DeviceType type;
    public final DeviceStatus status;
    public final String owner;
    public final Instant createdAt;
    public final Instant updatedAt;
    public final Instant deployedAt;
    public final String comment;
    public final Position position;
    /** Optional degrees clockwise from true north, in [0, 360). */
    public final Double bearingDeg;
    /** Optional opaque local attachment reference; resolution belongs to local storage. */
    public final String imageRef;
    public final long revision;

    public TmmDraft(UUID uid, String displayId, DeviceType type, DeviceStatus status,
                    String owner, Instant createdAt, Instant updatedAt, Instant deployedAt,
                    String comment, Position position, Double bearingDeg, String imageRef,
                    long revision) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.displayId = requiredText(displayId, "displayId");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.owner = requiredText(owner, "owner");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
        this.deployedAt = deployedAt;
        this.comment = comment == null ? "" : comment;
        if (this.comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("comment exceeds " + MAX_COMMENT_LENGTH + " characters");
        }
        this.position = Objects.requireNonNull(position, "position");
        if (bearingDeg != null && (!type.supportsBearing() || !Double.isFinite(bearingDeg)
                || bearingDeg < 0 || bearingDeg >= 360)) {
            throw new IllegalArgumentException("bearingDeg requires PIR/Scout and a finite value in [0, 360)");
        }
        this.bearingDeg = bearingDeg;
        this.imageRef = imageRef == null ? null : requiredText(imageRef, "imageRef");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        this.revision = revision;
    }

    public static TmmDraft create(String displayId, DeviceType type, String owner,
                                  Position position, Instant now) {
        return new TmmDraft(UUID.randomUUID(), displayId, type, DeviceStatus.PLANNED, owner,
                now, now, null, "", position, null, null, 1);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
