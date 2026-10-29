package se.tmm.sensorplanner.images;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Frozen, verified content only: no device/source association, storage, adoption or transport. */
@TargetApi(26)
public final class ImageTransferPng {
    public static final int MAX_BYTES = ImageFormat.MAX_OWNED_BYTES;
    public static final int MAX_EDGE = LocalImages.MAX_COPY_EDGE;
    private ImageTransferPng() { }

    /** Logical values only; no wire encoding, authority or attachment identity is implied. */
    public static final class Identity {
        public final int length, width, height;
        private final byte[] digest;

        public Identity(byte[] sha256, int length, int width, int height) {
            if (sha256 == null || sha256.length != 32 || length <= 0 || length > MAX_BYTES
                    || width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE) {
                throw new IllegalArgumentException("Invalid image content identity");
            }
            this.digest = sha256.clone(); this.length = length;
            this.width = width; this.height = height;
        }

        public byte[] sha256() { return digest.clone(); }
    }

    public static final class VerifiedContent {
        public final Identity identity;
        private final byte[] content;
        private VerifiedContent(byte[] content, Identity identity) {
            this.content = content; this.identity = identity;
        }
        public byte[] bytes() { return content.clone(); }
    }

    /** Prepare a separately previewable copy of an already owned PNG; never change its source. */
    public static VerifiedContent prepareExport(byte[] ownedPng) throws IOException {
        Objects.requireNonNull(ownedPng, "ownedPng");
        if (ownedPng.length > MAX_BYTES) throw ImageFormat.invalid();
        try {
            byte[] source = ownedPng.clone();
            ImageFormat.checkOwnedPng(source);
            byte[] encoded = LocalImages.normalizeValidated(source, source.length);
            ImageFormat.checkOwnedPng(encoded);
            // Android's encoder includes ancillary sRGB/sBIT chunks. Export a new closed-profile copy.
            ByteArrayOutputStream clean = new ByteArrayOutputStream(encoded.length);
            clean.write(encoded, 0, 8);
            for (int offset = 8; offset < encoded.length;) {
                int length = integer(encoded, offset);
                String type = type(encoded, offset);
                if (type.equals("IHDR") || type.equals("IDAT") || type.equals("IEND")) {
                    clean.write(encoded, offset, length + 12);
                }
                offset += length + 12;
            }
            return checked(clean.toByteArray(), null);
        } catch (OutOfMemoryError exhausted) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
    }

    /** Verify complete bounded payload bytes without re-encoding them. Caller owns input framing. */
    public static VerifiedContent verify(byte[] payload, Identity expected) throws IOException {
        Objects.requireNonNull(payload, "payload"); Objects.requireNonNull(expected, "expected");
        if (payload.length > MAX_BYTES || payload.length != expected.length) throw ImageFormat.invalid();
        try {
            return checked(payload.clone(), expected);
        } catch (OutOfMemoryError exhausted) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
    }

    private static VerifiedContent checked(byte[] bytes, Identity expected) throws IOException {
        ImageFormat.checkOwnedPng(bytes);
        int width = integer(bytes, 16), height = integer(bytes, 20);
        int color = bytes[25] & 255;
        if (width > MAX_EDGE || height > MAX_EDGE || (bytes[24] & 255) != 8
                || (color != 2 && color != 6) || bytes[26] != 0 || bytes[27] != 0 || bytes[28] != 0) {
            throw ImageFormat.invalid();
        }
        byte[] digest = sha256(bytes);
        if (expected != null && (expected.width != width || expected.height != height
                || !MessageDigest.isEqual(expected.digest, digest))) throw ImageFormat.invalid();
        Inflater inflater = new Inflater();
        long produced = 0;
        int stride = 1 + width * (color == 2 ? 3 : 4);
        long required = (long) stride * height;
        byte[] buffer = new byte[8192];
        try {
            for (int offset = 8; offset < bytes.length;) {
                int length = integer(bytes, offset);
                String type = type(bytes, offset);
                if (type.equals("IDAT")) {
                    if (inflater.finished()) {
                        if (length != 0) throw ImageFormat.invalid();
                    } else {
                        inflater.setInput(bytes, offset + 8, length);
                        while (!inflater.finished()) {
                            int count = inflater.inflate(buffer);
                            if (inflater.needsDictionary() || (count == 0
                                    && !inflater.needsInput() && !inflater.finished())) throw ImageFormat.invalid();
                            if (count > required - produced) throw ImageFormat.invalid();
                            int nextFilter = (int) ((stride - produced % stride) % stride);
                            for (int i = nextFilter; i < count; i += stride) {
                                if ((buffer[i] & 255) > 4) throw ImageFormat.invalid();
                            }
                            produced += count;
                            if (count == 0 && inflater.needsInput()) break;
                        }
                        if (inflater.finished() && inflater.getRemaining() != 0) throw ImageFormat.invalid();
                    }
                } else if (!type.equals("IHDR") && !type.equals("IEND")) {
                    throw ImageFormat.invalid();
                }
                offset += length + 12;
            }
            if (!inflater.finished() || produced != required) throw ImageFormat.invalid();
        } catch (DataFormatException invalid) {
            throw ImageFormat.invalid();
        } finally {
            inflater.end();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (decoded == null) throw ImageFormat.invalid();
        try {
            if (decoded.getWidth() != width || decoded.getHeight() != height) throw ImageFormat.invalid();
        } finally {
            decoded.recycle();
        }
        return new VerifiedContent(bytes, new Identity(digest, bytes.length, width, height));
    }

    private static int integer(byte[] bytes, int offset) { return ByteBuffer.wrap(bytes, offset, 4).getInt(); }
    private static String type(byte[] bytes, int offset) {
        return new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
    }
    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
