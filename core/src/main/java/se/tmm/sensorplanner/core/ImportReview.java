package se.tmm.sensorplanner.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure metadata comparison for review; never imports, saves, authenticates or resolves conflicts. */
public final class ImportReview {
    public enum Kind { NEW_UID, SAME_METADATA, REVIEW_REQUIRED }

    public final Kind kind;
    /** Null only for NEW_UID; otherwise the unchanged local snapshot. */
    public final TmmDraft local;
    public final TmmDraft incoming;

    private ImportReview(Kind kind, TmmDraft local, TmmDraft incoming) {
        this.kind = kind;
        this.local = local;
        this.incoming = incoming;
    }

    /** Incoming must be validated metadata with no client-local image handle. */
    public static ImportReview assess(Collection<TmmDraft> snapshot, TmmDraft incoming) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(incoming, "incoming");
        if (incoming.imageRef != null) {
            throw new IllegalArgumentException("Incoming metadata must not contain a local image reference");
        }
        Set<UUID> seen = new HashSet<>();
        TmmDraft local = null;
        for (TmmDraft draft : snapshot) {
            Objects.requireNonNull(draft, "snapshot entry");
            if (!seen.add(draft.uid)) {
                throw new IllegalArgumentException("Local snapshot contains duplicate UUIDs");
            }
            if (draft.uid.equals(incoming.uid)) local = draft;
        }
        Kind kind = local == null ? Kind.NEW_UID
                : sameMetadata(local, incoming) ? Kind.SAME_METADATA : Kind.REVIEW_REQUIRED;
        return new ImportReview(kind, local, incoming);
    }

    static boolean sameMetadata(TmmDraft local, TmmDraft incoming) {
        // Revisions count local writes; image handles belong to one client's private storage.
        return local.displayId.equals(incoming.displayId)
                && local.type == incoming.type && local.status == incoming.status
                && local.owner.equals(incoming.owner)
                && local.createdAt.equals(incoming.createdAt)
                && local.updatedAt.equals(incoming.updatedAt)
                && Objects.equals(local.deployedAt, incoming.deployedAt)
                && local.comment.equals(incoming.comment)
                && local.position.latitude == incoming.position.latitude
                && local.position.longitude == incoming.position.longitude
                && (local.bearingDeg == null ? incoming.bearingDeg == null
                    : incoming.bearingDeg != null && local.bearingDeg.doubleValue() == incoming.bearingDeg.doubleValue());
    }
}
