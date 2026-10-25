package se.tmm.sensorplanner.core;

import java.util.Objects;

/** One-use private metadata preparation; no destination, encoding, file or transport operation. */
public final class PublicationPreparationReview implements AutoCloseable {
    public final ShareReview.Metadata metadata;
    public final ShareReview.ImageStatus imageStatus;
    private TmmDraft reviewed;

    public PublicationPreparationReview(TmmDraft savedDraft) {
        reviewed = Objects.requireNonNull(savedDraft, "savedDraft");
        metadata = new ShareReview.Metadata(savedDraft);
        imageStatus = savedDraft.imageRef == null ? ShareReview.ImageStatus.NO_LOCAL_IMAGE
                : ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED;
    }

    /** Caller must reload current on the same serialized boundary as edits/deletes. */
    public synchronized Prepared prepare(TmmDraft current) {
        if (reviewed == null) throw new IllegalStateException("Preparation review is no longer pending");
        TmmDraft original = reviewed;
        // Consume even a failed attempt: stale or unavailable data requires a new review.
        reviewed = null;
        if (!ShareReview.sameSavedState(original, current)) {
            throw new IllegalStateException("Saved draft changed or is unavailable; review again");
        }
        return new Prepared(metadata, imageStatus);
    }

    /** Cancel a pending review; already returned values cannot be revoked or securely erased. */
    @Override public synchronized void close() {
        reviewed = null;
    }

    /** Immutable in-memory candidate only; no local handle, revision or publication outcome. */
    public static final class Prepared {
        public final ShareReview.Metadata metadata;
        public final ShareReview.ImageStatus imageStatus;

        private Prepared(ShareReview.Metadata metadata, ShareReview.ImageStatus imageStatus) {
            this.metadata = metadata;
            this.imageStatus = imageStatus;
        }
    }
}
