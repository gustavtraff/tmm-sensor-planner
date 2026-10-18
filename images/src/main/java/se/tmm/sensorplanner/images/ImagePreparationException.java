package se.tmm.sensorplanner.images;

import java.io.IOException;

/** Safe failure categories; never carries source paths, URIs or embedded metadata. */
public final class ImagePreparationException extends IOException {
    public enum Reason { INPUT_TOO_LARGE, PIXEL_LIMIT, INVALID_IMAGE, LOW_MEMORY, OUTPUT_TOO_LARGE, SAVE_FAILED }
    public final Reason reason;

    public ImagePreparationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
