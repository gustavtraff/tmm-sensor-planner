package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.images.ImagePreparationException;
import se.tmm.sensorplanner.images.ImageTransferPng;

/** Approved container v1 only: immutable claims/content, never source authority, storage or adoption. */
@TargetApi(26)
public final class ImageContainerCodec {
    public static final int MAX_BYTES = 20 + ImageOfferCodec.MAX_BYTES + ImageOffer.MAX_PNG_BYTES;
    private static final byte[] MAGIC = {'T','M','M','I','P','K','G',0};
    private ImageContainerCodec() { }

    /** One complete bounded container through EOF; caller owns input lifetime, deadline and cancellation. */
    public static ReceivedImagePayload read(InputStream input) throws IOException {
        try {
            byte[] header = new byte[20]; readFully(input, header);
            ByteBuffer in = ByteBuffer.wrap(header);
            byte[] magic = new byte[8]; in.get(magic);
            int version = in.getInt(), descriptorLength = in.getInt(), pngLength = in.getInt();
            if (!Arrays.equals(magic, MAGIC) || version != 1 || descriptorLength < 113
                    || descriptorLength > ImageOfferCodec.MAX_BYTES || pngLength < 1
                    || pngLength > ImageOffer.MAX_PNG_BYTES) throw new IOException();
            byte[] descriptor = new byte[descriptorLength]; readFully(input, descriptor);
            ImageOffer offer = ImageOfferCodec.decode(new ByteArrayInputStream(descriptor));
            if (offer.pngLength != pngLength) throw new IOException();
            // PNG is the final component: its existing expected-length/EOF check also checks outer EOF.
            return ReceivedImagePayload.read(new ByteArrayInputStream(descriptor), input);
        } catch (ImagePreparationException safe) {
            throw safe;
        } catch (Exception rejected) {
            throw new IOException("Image container could not be verified");
        } catch (OutOfMemoryError exhausted) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
    }

    /** Validates the frozen pair before any output. Failure during writing may leave incomplete output. */
    public static void write(OutputStream output, ImageOffer offer,
                             ImageTransferPng.VerifiedContent image) throws IOException {
        try {
            if (output == null || offer == null || image == null
                    || offer.pngLength != image.identity.length || offer.width != image.identity.width
                    || offer.height != image.identity.height
                    || !MessageDigest.isEqual(offer.sha256(), image.identity.sha256())) throw new IOException();
            byte[] descriptor = ImageOfferCodec.encode(offer), png = image.bytes();
            byte[] header = ByteBuffer.allocate(20).put(MAGIC).putInt(1)
                    .putInt(descriptor.length).putInt(png.length).array();
            output.write(header); output.write(descriptor); output.write(png);
        } catch (Exception failure) {
            throw new IOException("Image container write failed; output may be incomplete");
        } catch (OutOfMemoryError exhausted) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
    }

    private static void readFully(InputStream input, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, Math.min(8192, bytes.length - offset));
            if (count < 0) throw new EOFException();
            if (count == 0) {
                int one = input.read(); if (one < 0) throw new EOFException();
                bytes[offset++] = (byte) one;
            } else offset += count;
        }
    }
}
