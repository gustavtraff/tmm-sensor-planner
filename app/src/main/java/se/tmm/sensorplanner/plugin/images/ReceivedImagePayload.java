package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.*;
import java.util.Objects;
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.images.ImagePreparationException;
import se.tmm.sensorplanner.images.ImageTransferPng;

/** Coherent in-memory claims/content only, never trusted provenance or durable association. */
@TargetApi(26)
public final class ReceivedImagePayload {
    public final ImageOffer offer;
    public final ImageTransferPng.VerifiedContent image;
    private ReceivedImagePayload(ImageOffer offer, ImageTransferPng.VerifiedContent image) {
        this.offer = offer; this.image = image;
    }

    /** One completed descriptor and one completed PNG; caller owns both streams/deadlines. */
    public static ReceivedImagePayload read(InputStream descriptor, InputStream png) throws IOException {
        try {
            Objects.requireNonNull(descriptor, "descriptor"); Objects.requireNonNull(png, "png");
            ImageOffer offer = ImageOfferCodec.decode(descriptor);
            ImageTransferPng.Identity expected = new ImageTransferPng.Identity(offer.sha256(),
                    offer.pngLength, offer.width, offer.height);
            return new ReceivedImagePayload(offer, ImageTransferPng.readAndVerify(png, expected));
        } catch (ImagePreparationException safe) {
            throw safe;
        } catch (Exception rejected) {
            throw new IOException("Image payload could not be verified");
        } catch (OutOfMemoryError exhausted) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
    }
}
