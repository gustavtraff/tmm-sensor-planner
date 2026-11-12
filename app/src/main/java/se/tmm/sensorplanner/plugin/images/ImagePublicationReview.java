package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.core.LocalV2;
import se.tmm.sensorplanner.core.ShareReview;
import se.tmm.sensorplanner.images.ImageTransferPng;
import se.tmm.sensorplanner.images.LocalImages;

/** One-shot frozen image review only; no file write, container, transport or delivery result. */
@TargetApi(26)
public final class ImagePublicationReview implements AutoCloseable {
    public final ImageOffer offer;
    public final String destinationId, destinationLabel;
    private final LocalV2.Selection selected;
    private final ShareReview metadataReview;
    private final byte[] sourceDigest;
    private final int sourceLength;
    private ImageTransferPng.VerifiedContent image;

    /** Call off the UI thread with a saved active selection and its private owned-image store. */
    public ImagePublicationReview(LocalV2.Selection selected, String destinationId,
                                  String destinationLabel, LocalImages images) throws IOException {
        this.selected = Objects.requireNonNull(selected, "selected");
        if (selected.draft == null || selected.draft.imageRef == null) {
            throw new IOException("Select a saved device with a local image");
        }
        metadataReview = new ShareReview(selected.draft, destinationId, destinationLabel);
        this.destinationId = metadataReview.destinationId;
        this.destinationLabel = metadataReview.destinationLabel;
        byte[] source = images.read(selected.draft.imageRef);
        sourceDigest = digest(source);
        sourceLength = source.length;
        image = ImageTransferPng.prepareExport(source);
        offer = new ImageOffer(UUID.randomUUID(), UUID.randomUUID(), metadataReview.metadata,
                image.identity.sha256(), image.identity.length, image.identity.width, image.identity.height);
    }

    /** Preview exactly the separately sanitized bytes whose identity appears in the offer. */
    public synchronized byte[] preview() throws IOException {
        if (image == null) throw new IOException("Image review is no longer pending");
        return image.bytes();
    }

    /**
     * Caller owns fresh source/session authorization and the serialized reload/read/handoff boundary.
     * Every attempt consumes this review, including a missing image or a stale destination/state.
     */
    public synchronized Prepared confirm(LocalV2.Snapshot current, String destinationId,
                                         String destinationLabel, LocalImages images) throws IOException {
        ImageTransferPng.VerifiedContent frozen = image;
        image = null;
        if (frozen == null) throw new IOException("Image review is no longer pending");
        if (current == null || !selected.isCurrent(current)
                || !metadataReview.isCurrent(current.state(selected.state.uid).draft, destinationId, destinationLabel)) {
            throw new IOException("Saved device or destination changed; review again");
        }
        byte[] actual = images.read(selected.draft.imageRef);
        if (actual.length != sourceLength || !MessageDigest.isEqual(sourceDigest, digest(actual))) {
            throw new IOException("Local image changed; review again");
        }
        return new Prepared(offer, frozen);
    }

    /** Cancels pending preparation; an already confirmed value cannot be recalled or securely erased. */
    @Override public synchronized void close() { image = null; }

    public static final class Prepared {
        public final ImageOffer offer;
        private final ImageTransferPng.VerifiedContent image;
        private Prepared(ImageOffer offer, ImageTransferPng.VerifiedContent image) {
            this.offer = offer; this.image = image;
        }
        public byte[] descriptor() { return ImageOfferCodec.encode(offer); }
        public byte[] png() { return image.bytes(); }
        /** Writes only this already frozen pair; does not select a file, route or start transport. */
        public void writeContainer(OutputStream output) throws IOException {
            ImageContainerCodec.write(output, offer, image);
        }
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
