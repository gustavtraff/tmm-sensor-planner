package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.CRC32;

/** Approved image descriptor v1 only; no container, image verification, authority or persistence. */
public final class ImageOfferCodec {
    public static final int MAX_BYTES = 112 + PublicationCodec.MAX_BYTES;
    private static final byte[] MAGIC = {'T','M','M','I','O','F','R',0};
    private static final String INVALID = "Unsupported or invalid image descriptor";
    private ImageOfferCodec() { }

    /** Canonical descriptor bytes; callers still own freshness, inclusion and destination review. */
    public static byte[] encode(ImageOffer offer) {
        try {
            Objects.requireNonNull(offer, "offer");
            byte[] metadata = PublicationCodec.encodeMetadata(offer.metadata);
            ByteBuffer out = ByteBuffer.allocate(112 + metadata.length);
            out.put(MAGIC).putInt(1); uuid(out, offer.associationId); uuid(out, offer.deliveryId);
            uuid(out, offer.metadata.uid); out.putInt(offer.pngLength).putInt(offer.width).putInt(offer.height);
            out.put(offer.sha256()).putInt(metadata.length).put(metadata);
            CRC32 crc = new CRC32(); crc.update(out.array(), 0, out.position()); out.putInt((int)crc.getValue());
            return out.array();
        } catch (Exception rejected) { throw new IllegalArgumentException(INVALID); }
    }

    /** One complete descriptor through EOF, <=MAX_BYTES+1 actual bytes; leaves input open. */
    public static ImageOffer decode(InputStream input) throws IOException {
        try {
            Objects.requireNonNull(input, "input");
            byte[] header = new byte[108]; readFully(input, header);
            ByteBuffer h = ByteBuffer.wrap(header); byte[] magic = new byte[8]; h.get(magic);
            require(Arrays.equals(magic, MAGIC) && h.getInt() == 1);
            UUID association = uuid(h), delivery = uuid(h), device = uuid(h);
            require(!association.equals(new UUID(0, 0)) && !delivery.equals(new UUID(0, 0)));
            int length = h.getInt(), width = h.getInt(), height = h.getInt();
            require(length >= 1 && length <= ImageOffer.MAX_PNG_BYTES && width >= 1
                    && width <= ImageOffer.MAX_EDGE && height >= 1 && height <= ImageOffer.MAX_EDGE);
            byte[] digest = new byte[32]; h.get(digest); int size = h.getInt();
            require(size >= 1 && size <= PublicationCodec.MAX_BYTES);
            byte[] metadata = new byte[size]; readFully(input, metadata);
            byte[] checksum = new byte[4]; readFully(input, checksum);
            require(input.read() == -1);
            CRC32 crc = new CRC32(); crc.update(header); crc.update(metadata);
            require((int)crc.getValue() == ByteBuffer.wrap(checksum).getInt());
            ShareReview.Metadata decoded = PublicationCodec.decode(new ByteArrayInputStream(metadata));
            require(device.equals(decoded.uid));
            return new ImageOffer(association, delivery, decoded, digest, length, width, height);
        } catch (Exception rejected) { throw new IOException(INVALID); }
    }

    private static void readFully(InputStream input, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, Math.min(8192, bytes.length - offset));
            if (count < 0) throw new IOException(INVALID);
            if (count == 0) {
                int one = input.read(); if (one < 0) throw new IOException(INVALID);
                bytes[offset++] = (byte)one;
            } else offset += count;
        }
    }
    private static UUID uuid(ByteBuffer input) { return new UUID(input.getLong(), input.getLong()); }
    private static void uuid(ByteBuffer output, UUID value) { output.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()); }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(INVALID); }
}
