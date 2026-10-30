package se.tmm.sensorplanner.core;

import java.util.*;

/** Immutable descriptor claims only; no verified image bytes, source, storage or send permission. */
public final class ImageOffer {
    public static final int MAX_PNG_BYTES = 16 * 1024 * 1024;
    public static final int MAX_EDGE = 1920;
    public final UUID associationId, deliveryId;
    public final ShareReview.Metadata metadata;
    public final int pngLength, width, height;
    private final byte[] digest;

    public ImageOffer(UUID associationId, UUID deliveryId, ShareReview.Metadata metadata,
                      byte[] sha256, int pngLength, int width, int height) {
        if (associationId == null || deliveryId == null || metadata == null
                || associationId.equals(new UUID(0, 0)) || deliveryId.equals(new UUID(0, 0))
                || sha256 == null || sha256.length != 32 || pngLength < 1 || pngLength > MAX_PNG_BYTES
                || width < 1 || width > MAX_EDGE || height < 1 || height > MAX_EDGE) {
            throw new IllegalArgumentException("Invalid image offer");
        }
        PublicationCodec.encodeMetadata(metadata);
        this.associationId = associationId; this.deliveryId = deliveryId; this.metadata = metadata;
        this.pngLength = pngLength; this.width = width; this.height = height; this.digest = sha256.clone();
    }

    public byte[] sha256() { return digest.clone(); }

    /** Values only: callers must separately compare IDs and independently validated source context. */
    public boolean sameBinding(ImageOffer other) {
        if (other == null || !metadata.uid.equals(other.metadata.uid)) return false;
        return InboxCodec.sameMetadata(carrier(metadata), carrier(other.metadata))
                && pngLength == other.pngLength && width == other.width && height == other.height
                && Arrays.equals(digest, other.digest);
    }

    private static TmmDraft carrier(ShareReview.Metadata m) {
        return new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                m.deployedAt, m.comment, m.position, m.bearingDeg, null, 1);
    }
}
