package se.tmm.sensorplanner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * <p>H (U4, amended twice): if any digest is bound to the feed's current M, the only distinct one
 * of those is H, several are AMBIGUOUS. Otherwise, over all digests for the UUID: none is "no
 * image"; one is H; several including a known image base give H = the base; else AMBIGUOUS.
 *
 * <p>Image precedence, first match wins: R absent, NOTHING; AMBIGUOUS as above; no image:
 * ADOPT_BASE with H = no image when L is present without an image and the image base is
 * UNKNOWN (R = L), otherwise NOTHING, so a missing image never clears or rebases a local one;
 * B and L both absent, APPLY; B present and
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
        ADOPT_BASE,  // remote image equals local image (or neither has one): store the image base := H
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

        public boolean sameAs(ImageState other) {
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
     * @param currentImages verified image digests bound to exactly the feed's current M (U4)
     * @param remoteImages verified image digests bound to this device for any M, including currentImages
     * @param baseImage UNKNOWN, NONE or a digest
     * @param localImage NONE or a digest; ignored when local is null
     */
    public static FeedSyncDecision decide(TmmDraft remote, TmmDraft base, TmmDraft local,
                                          List<byte[]> currentImages, List<byte[]> remoteImages,
                                          ImageState baseImage, ImageState localImage) {
        if (baseImage == null || (local != null && (localImage == null || localImage.unknown))) {
            throw new IllegalArgumentException("image states are required");
        }
        if (remote == null) {
            return new FeedSyncDecision(Metadata.NOTHING, Image.NOTHING);
        }
        return new FeedSyncDecision(metadata(remote, base, local),
                image(remoteImage(currentImages, remoteImages, baseImage), base, local, baseImage, localImage));
    }

    /** As above with no image bound to the current M. */
    public static FeedSyncDecision decide(TmmDraft remote, TmmDraft base, TmmDraft local,
                                          List<byte[]> remoteImages, ImageState baseImage,
                                          ImageState localImage) {
        return decide(remote, base, local, Collections.<byte[]>emptyList(), remoteImages, baseImage, localImage);
    }

    /** U4 H: a digest, NONE when no image is bound, or null when AMBIGUOUS. */
    public static ImageState remoteImage(List<byte[]> current, List<byte[]> all, ImageState baseImage) {
        List<ImageState> bound = distinct(current);
        if (!bound.isEmpty()) {
            return bound.size() == 1 ? bound.get(0) : null;
        }
        List<ImageState> any = distinct(all);
        if (any.isEmpty()) {
            return ImageState.NONE;
        }
        if (any.size() == 1) {
            return any.get(0);
        }
        // Several, none bound to the current M: the base stays current if it is among them.
        for (ImageState d : any) {
            if (d.sameAs(baseImage)) {
                return d;
            }
        }
        return null;
    }

    private static List<ImageState> distinct(List<byte[]> digests) {
        List<ImageState> distinct = new ArrayList<ImageState>();
        for (byte[] digest : digests) {
            ImageState s = ImageState.of(digest);
            boolean seen = false;
            for (ImageState d : distinct) {
                seen |= d.sameAs(s);
            }
            if (!seen) {
                distinct.add(s);
            }
        }
        return distinct;
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

    private static Image image(ImageState h, TmmDraft b, TmmDraft l,
                               ImageState baseImage, ImageState localImage) {
        if (h == null) {
            return Image.AMBIGUOUS;
        }
        if (h == ImageState.NONE) {
            // U3 R = L with "no image" on both sides: store image base NONE (H = none).
            return l != null && localImage == ImageState.NONE && baseImage.unknown ? Image.ADOPT_BASE : Image.NOTHING;
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
