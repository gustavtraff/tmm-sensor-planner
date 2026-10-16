package se.tmm.sensorplanner.images;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Structural checks before Android decoding; filenames and supplied MIME types are ignored. */
final class ImageFormat {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final long MAX_PIXELS = 20_000_000L;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    static void check(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw invalid();
        if (isPng(bytes)) {
            png(bytes);
        } else if (bytes.length >= 4 && u(bytes[0]) == 255 && u(bytes[1]) == 216) {
            jpeg(bytes);
        } else {
            throw invalid();
        }
    }

    static void checkOwnedPng(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES || !isPng(bytes)) throw invalid();
        png(bytes);
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 8 && Arrays.equals(PNG, Arrays.copyOf(bytes, 8));
    }

    static void dimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) throw invalid();
    }

    private static void png(byte[] bytes) throws IOException {
        int offset = 8;
        boolean header = false;
        boolean data = false;
        while (offset <= bytes.length - 12) {
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            if (length < 0 || length > bytes.length - offset - 12) throw invalid();
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, length + 4);
            if ((int) crc.getValue() != ByteBuffer.wrap(bytes, offset + 8 + length, 4).getInt()) throw invalid();
            if (!header && !type.equals("IHDR")) throw invalid();
            switch (type) {
                case "IHDR":
                    if (header || length != 13) throw invalid();
                    dimensions(ByteBuffer.wrap(bytes, offset + 8, 4).getInt(),
                            ByteBuffer.wrap(bytes, offset + 12, 4).getInt());
                    header = true;
                    break;
                case "acTL": case "fcTL": case "fdAT":
                    throw invalid();
                case "IDAT":
                    data = true;
                    break;
                case "IEND":
                    if (length != 0 || !data || offset + 12 != bytes.length) throw invalid();
                    return;
                default:
                    // Unknown critical chunks cannot be safely interpreted as a static PNG.
                    if ((bytes[offset + 4] & 32) == 0 && !type.equals("PLTE")) throw invalid();
            }
            offset += length + 12;
        }
        throw invalid();
    }

    private static void jpeg(byte[] bytes) throws IOException {
        int offset = 2;
        boolean frame = false;
        boolean scan = false;
        while (offset < bytes.length) {
            if (u(bytes[offset++]) != 255) throw invalid();
            while (offset < bytes.length && u(bytes[offset]) == 255) offset++;
            if (offset == bytes.length) throw invalid();
            int marker = u(bytes[offset++]);
            if (marker == 217) {
                if (!frame || !scan || offset != bytes.length) throw invalid();
                return;
            }
            if (marker == 0 || marker == 216 || marker == 1 || (marker >= 208 && marker <= 215)
                    || offset > bytes.length - 2) throw invalid();
            int length = u(bytes[offset]) * 256 + u(bytes[offset + 1]);
            if (length < 2 || length > bytes.length - offset) throw invalid();
            if (marker == 226 && length >= 6 && bytes[offset + 2] == 'M'
                    && bytes[offset + 3] == 'P' && bytes[offset + 4] == 'F' && bytes[offset + 5] == 0) {
                throw invalid(); // MPO carries multiple pictures rather than one static image.
            }
            if (marker == 192 || marker == 193 || marker == 194) {
                if (frame || length < 8) throw invalid();
                dimensions(u(bytes[offset + 5]) * 256 + u(bytes[offset + 6]),
                        u(bytes[offset + 3]) * 256 + u(bytes[offset + 4]));
                frame = true;
            }
            offset += length;
            if (marker == 218) {
                if (!frame) throw invalid();
                scan = true;
                while (offset < bytes.length) {
                    if (u(bytes[offset]) != 255) { offset++; continue; }
                    int next = offset + 1;
                    while (next < bytes.length && u(bytes[next]) == 255) next++;
                    if (next == bytes.length) throw invalid();
                    int code = u(bytes[next]);
                    if (code == 0 || (code >= 208 && code <= 215)) { offset = next + 1; continue; }
                    break;
                }
            }
        }
        throw invalid();
    }

    private static int u(byte value) { return value & 255; }

    static IOException invalid() {
        return new IOException("Image is unsupported, damaged, animated or exceeds the image limits");
    }
}
