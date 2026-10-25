package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable metadata review only; no confirmation, transport or shared-state operation. */
public final class ShareReview {
    public enum ImageStatus { NO_LOCAL_IMAGE, LOCAL_IMAGE_NOT_SHARED }

    public final String destinationId;
    public final String destinationLabel;
    public final Metadata metadata;
    public final ImageStatus imageStatus;
    private final TmmDraft reviewed;

    public ShareReview(TmmDraft draft, String destinationId, String destinationLabel) {
        reviewed = Objects.requireNonNull(draft, "draft");
        this.destinationId = destinationText(destinationId);
        this.destinationLabel = destinationText(destinationLabel);
        metadata = new Metadata(draft);
        imageStatus = draft.imageRef == null ? ImageStatus.NO_LOCAL_IMAGE : ImageStatus.LOCAL_IMAGE_NOT_SHARED;
    }

    /** Compare against a fresh local record and the exact current selection; null means unavailable. */
    public boolean isCurrent(TmmDraft current, String selectedId, String selectedLabel) {
        return destinationId.equals(selectedId) && destinationLabel.equals(selectedLabel)
                && sameSavedState(reviewed, current);
    }

    static boolean sameSavedState(TmmDraft reviewed, TmmDraft current) {
        return current != null && reviewed.uid.equals(current.uid) && reviewed.revision == current.revision
                && Objects.equals(reviewed.imageRef, current.imageRef)
                && ImportReview.sameMetadata(reviewed, current);
    }

    /** Proposed field allowlist for later review/encoding; not a frozen wire schema. */
    public static final class Metadata {
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
        public final Double bearingDeg;

        Metadata(TmmDraft draft) {
            uid = draft.uid;
            displayId = draft.displayId;
            type = draft.type;
            status = draft.status;
            owner = draft.owner;
            createdAt = draft.createdAt;
            updatedAt = draft.updatedAt;
            deployedAt = draft.deployedAt;
            comment = draft.comment;
            position = draft.position;
            bearingDeg = draft.bearingDeg;
        }
    }

    private static String destinationText(String value) {
        if (value == null || value.isEmpty()
                || space(value.codePointAt(0)) || space(value.codePointBefore(value.length()))) {
            throw new IllegalArgumentException("Select destination text without surrounding whitespace");
        }
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            int type = Character.getType(point);
            if (Character.isISOControl(point) || type == Character.FORMAT || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("Destination text must be one line without control or format characters");
            }
            offset += Character.charCount(point);
        }
        return value;
    }

    private static boolean space(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point);
    }
}
