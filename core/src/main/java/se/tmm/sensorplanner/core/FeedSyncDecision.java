package se.tmm.sensorplanner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure U3/U4 decision for one device UUID (docs/AUTOMATIC_FEED_SYNC_PROPOSAL.md).
 * Metadata equality is {@link ImportReview#sameMetadata}; time and arrival order never decide.
 *
 * <p>Metadata precedence, first match wins: R absent, NOTHING; B and L both absent, ADD;
 * B present and L absent (deleted locally), NOTHING if R = B else CONFLICT; R = B, NOTHING;
 * R = L, ADOPT_BASE; L = B (so R differs), APPLY; otherwise CONFLICT. With L present and no
 * base, L = B is never true, so the result is ADOPT_BASE if R = L, else CONFLICT.
 *
 * <p>Image precedence, first match wins: R absent, NOTHING; no distinct bound digest (amended
 * U4), NOTHING; several distinct digests and not exactly one differing from a known image
 * base, AMBIGUOUS (else that one is H); B and L both absent, APPLY; B present and
 * L absent, NOTHING if H = image base or the image base is UNKNOWN, else CONFLICT;
 * H = image base, NOTHING; H = local image, ADOPT_BASE; local image = image base, APPLY;
 * otherwise CONFLICT. An UNKNOWN image base equals nothing, so it behaves like no base.
 * "B present" always means the metadata base exists.
 */
public final class FeedSyncDecision {
    public enum Metadata {
        NOTHING,    // no change: not in feed, or already matches the base
        ADOPT_BASE, // remote equals local: store B := R, change nothing locally
        APPLY,      // local equals base: apply R locally, then store B := R
        ADD,        // not present locally and no base: add the device from R
        CONFLICT    // keep local, keep R in the inbox, mark the device
    }

    public enum Image {
        NOTHING,     // no change
        ADOPT_BASE,  // remote image equals local image: store the image base := H
        APPLY,       // adopt remote image H locally, then store the image base := H
        CONFLICT,    // local image and remote image both differ from the base and each other
        AMBIGUOUS    // U4: several images are bound and no single one differs from the base; change nothing
    }

    /** An image state: UNKNOWN (base only), NONE, or a SHA-256 digest. */
    public static final class ImageState {
        public static final ImageState UNKNOWN = new ImageState(null, true);
        public static final ImageState NONE = new ImageState(null, false);
        private final byte[] sha256;
        private final boolean unknown;

        private ImageState(byte[] sha256, boolean unknown) {
            this.sha256 = sha256;
            this.unknown = unknown;
        }

        public static ImageState of(byte[] sha256) {
            if (sha256 == null || sha256.length != 32) {
                throw new IllegalArgumentException("SHA-256 digest must be 32 bytes");
            }
            return new ImageState(sha256.clone(), false);
        }

        boolean sameAs(ImageState other) {
            return !unknown && !other.unknown && Arrays.equals(sha256, other.sha256);
        }
    }

    public final Metadata metadata;
    public final Image image;

    private FeedSyncDecision(Metadata metadata, Image image) {
        this.metadata = metadata;
        this.image = image;
    }

    /**
     * @param remote null if the UUID is not in the fetched feed
     * @param base null if no base yet
     * @param local null if absent locally
     * @param remoteImages distinct verified image digests bound to this device for any M (U4)
     * @param baseImage UNKNOWN, NONE or a digest
     * @param localImage NONE or a digest; ignored when local is null
     */
    public static FeedSyncDecision decide(TmmDraft remote, TmmDraft base, TmmDraft local,
                                          List<byte[]> remoteImages, ImageState baseImage,
                                          ImageState localImage) {
        if (baseImage == null || (local != null && (localImage == null || localImage.unknown))) {
            throw new IllegalArgumentException("image states are required");
        }
        if (remote == null) {
            return new FeedSyncDecision(Metadata.NOTHING, Image.NOTHING);
        }
        return new FeedSyncDecision(metadata(remote, base, local),
                image(remoteImages, base, local, baseImage, localImage));
    }

    private static Metadata metadata(TmmDraft r, TmmDraft b, TmmDraft l) {
        if (b == null && l == null) {
            return Metadata.ADD;
        }
        if (l == null) {
            return ImportReview.sameMetadata(b, r) ? Metadata.NOTHING : Metadata.CONFLICT;
        }
        if (b != null && ImportReview.sameMetadata(b, r)) {
            return Metadata.NOTHING;
        }
        if (ImportReview.sameMetadata(l, r)) {
            return Metadata.ADOPT_BASE;
        }
        return b != null && ImportReview.sameMetadata(b, l) ? Metadata.APPLY : Metadata.CONFLICT;
    }

    private static Image image(List<byte[]> bound, TmmDraft b, TmmDraft l,
                               ImageState baseImage, ImageState localImage) {
        List<ImageState> distinct = new ArrayList<ImageState>();
        for (byte[] digest : bound) {
            ImageState s = ImageState.of(digest);
            boolean seen = false;
            for (ImageState d : distinct) {
                seen |= d.sameAs(s);
            }
            if (!seen) {
                distinct.add(s);
            }
        }
        if (distinct.isEmpty()) {
            return Image.NOTHING;
        }
        ImageState h = distinct.get(0);
        if (distinct.size() > 1) {
            // Amended U4: the one digest that differs from a known image base is H.
            ImageState differing = null;
            int count = 0;
            for (ImageState d : distinct) {
                if (!d.sameAs(baseImage)) {
                    differing = d;
                    count++;
                }
            }
            if (count != 1) {
                return Image.AMBIGUOUS;
            }
            h = differing;
        }
        if (b == null && l == null) {
            return Image.APPLY;
        }
        if (l == null) {
            return h.sameAs(baseImage) || baseImage.unknown ? Image.NOTHING : Image.CONFLICT;
        }
        if (h.sameAs(baseImage)) {
            return Image.NOTHING;
        }
        if (h.sameAs(localImage)) {
            return Image.ADOPT_BASE;
        }
        return localImage.sameAs(baseImage) ? Image.APPLY : Image.CONFLICT;
    }
}
