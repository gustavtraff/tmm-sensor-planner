package se.tmm.sensorplanner.images;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageTransferPngTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void independentGoldenContentHasExactIdentityAndPixels() throws Exception {
        String[] hashes = {"d6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5",
                "8cc05bbabd5fa56beaf58ed6d0d2dbf4c6e497689d295ec8644eda5c4ebb645a"};
        for (int i = 1; i <= 2; i++) {
            byte[] bytes = golden(i);
            assertEquals(i == 1 ? 210 : 220, bytes.length);
            assertEquals(hashes[i - 1], hex(hash(bytes)));
            ImageTransferPng.VerifiedContent result = ImageTransferPng.verify(bytes, identity(bytes));
            assertEquals(64, result.identity.width); assertEquals(64, result.identity.height);
            assertArrayEquals(bytes, result.bytes()); assertArrayEquals(hash(bytes), result.identity.sha256());
            Bitmap image = BitmapFactory.decodeByteArray(result.bytes(), 0, bytes.length);
            try { assertEquals(i == 1 ? 0xff00ffff : 0xffff00ff, image.getPixel(0, 0)); }
            finally { image.recycle(); }
        }
    }

    @Test public void digestIdentityAndVerifiedBytesDoNotAliasCallerArrays() throws Exception {
        byte[] bytes = golden(1), original = bytes.clone(), digest = hash(bytes);
        ImageTransferPng.Identity expected = new ImageTransferPng.Identity(digest, bytes.length, 64, 64);
        digest[0] ^= 1;
        ImageTransferPng.VerifiedContent result = ImageTransferPng.verify(bytes, expected);
        bytes[0] = 0; result.bytes()[0] = 0; result.identity.sha256()[0] ^= 1; expected.sha256()[0] ^= 1;
        assertArrayEquals(original, result.bytes()); assertArrayEquals(hash(original), result.identity.sha256());
    }

    @Test public void wrongExpectedHashLengthOrDimensionsNeverVerify() throws Exception {
        byte[] bytes = golden(1);
        for (ImageTransferPng.Identity wrong : new ImageTransferPng.Identity[]{
                new ImageTransferPng.Identity(new byte[32], bytes.length, 64, 64),
                new ImageTransferPng.Identity(hash(bytes), bytes.length + 1, 64, 64),
                new ImageTransferPng.Identity(hash(bytes), bytes.length, 63, 64),
                new ImageTransferPng.Identity(hash(bytes), bytes.length, 64, 63)}) {
            assertThrows(IOException.class, () -> ImageTransferPng.verify(bytes, wrong));
        }
    }

    @Test public void invalidIdentityAndPayloadBoundsFailBeforeContentAllocation() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ImageTransferPng.Identity(new byte[31], 1, 1, 1));
        for (int[] shape : new int[][]{{0, 1, 1}, {ImageTransferPng.MAX_BYTES + 1, 1, 1},
                {1, 0, 1}, {1, 1, -1}, {1, 1921, 1}, {1, 1, Integer.MAX_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> new ImageTransferPng.Identity(new byte[32], shape[0], shape[1], shape[2]));
        }
        byte[] oversized = new byte[ImageTransferPng.MAX_BYTES + 1];
        assertThrows(IOException.class, () -> ImageTransferPng.prepareExport(oversized));
        assertThrows(IOException.class, () -> ImageTransferPng.verify(oversized,
                new ImageTransferPng.Identity(new byte[32], ImageTransferPng.MAX_BYTES, 1, 1)));
    }

    @Test public void metadataAnimationAndUnknownChunksAreNotReceivedContent() throws Exception {
        byte[] h = header(64, 64, 2);
        for (String name : new String[]{"tEXt", "eXIf", "iCCP", "sRGB", "sBIT", "acTL", "fcTL", "fdAT", "PLTE", "ABCD"}) {
            reject(join(signature(), h, chunk(name, new byte[]{0}), idat(golden(1)), chunk("IEND", new byte[0])));
        }
    }

    @Test public void onlyClosedEightBitNoninterlacedRgbOrRgbaIsAccepted() throws Exception {
        for (int index : new int[]{8, 9, 10, 11, 12}) {
            byte[] ihdr = headerData(64, 64, 2); ihdr[index] = (byte) (index == 8 ? 16 : 1);
            reject(join(signature(), chunk("IHDR", ihdr), idat(golden(1)), chunk("IEND", new byte[0])));
        }
        for (int color : new int[]{0, 3, 4}) {
            reject(join(signature(), header(64, 64, color), idat(golden(1)), chunk("IEND", new byte[0])));
        }
        byte[] rgba = png(2, 1, 6, new byte[]{0, (byte)255, 0, 0, (byte)255, 0, 0, (byte)255, 127}, 6);
        assertArrayEquals(rgba, ImageTransferPng.verify(rgba, identity(rgba)).bytes());
    }

    @Test public void crcLengthsChunkOrderAndTrailingContentAreValidated() throws Exception {
        byte[] bytes = golden(1), badCrc = bytes.clone(); badCrc[29] ^= 1; reject(badCrc);
        byte[] badLength = bytes.clone(); Arrays.fill(badLength, 8, 12, (byte)255); reject(badLength);
        reject(Arrays.copyOf(bytes, bytes.length - 1)); reject(join(bytes, new byte[]{0}));
        reject(join(signature(), idat(bytes), header(64, 64, 2), chunk("IEND", new byte[0])));
        reject(join(signature(), header(64, 64, 2), header(64, 64, 2), idat(bytes), chunk("IEND", new byte[0])));
        reject(join(signature(), header(64, 64, 2), chunk("IEND", new byte[0])));
    }

    @Test public void oneExactZlibStreamAndValidRowFiltersAreRequired() throws Exception {
        byte[] raw = {0, 1, 2, 3}, valid = compressed(raw, 6);
        for (byte[] invalid : new byte[][]{new byte[]{1, 2, 3}, Arrays.copyOf(valid, valid.length - 1),
                join(valid, valid), join(valid, new byte[]{0}), compressed(new byte[]{0, 1, 2}, 6),
                compressed(new byte[]{0, 1, 2, 3, 4}, 6), compressed(new byte[]{5, 1, 2, 3}, 6),
                compressed(new byte[1024 * 1024], 6)}) {
            reject(join(signature(), header(1, 1, 2), chunk("IDAT", invalid), chunk("IEND", new byte[0])));
        }
        for (int filter = 0; filter <= 4; filter++) {
            byte[] png = png(1, 1, 2, new byte[]{(byte) filter, 0, 0, 0}, 6);
            assertArrayEquals(png, ImageTransferPng.verify(png, identity(png)).bytes());
        }
    }

    @Test public void dictionaryStreamsAreRejected() throws Exception {
        Deflater d = new Deflater(); byte[] encoded;
        try {
            d.setDictionary(new byte[]{1, 2, 3}); d.setInput(new byte[]{0, 1, 2, 3}); d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[64];
            while (!d.finished()) out.write(b, 0, d.deflate(b));
            encoded = out.toByteArray();
        } finally { d.end(); }
        reject(join(signature(), header(1, 1, 2), chunk("IDAT", encoded), chunk("IEND", new byte[0])));
    }

    @Test public void everyCompressedByteMayCrossAnIdatBoundaryWithoutChangingPixels() throws Exception {
        byte[] original = golden(1), c = idatData(original); ByteArrayOutputStream split = new ByteArrayOutputStream();
        split.write(signature()); split.write(header(64, 64, 2));
        for (byte value : c) split.write(chunk("IDAT", new byte[]{value}));
        split.write(chunk("IEND", new byte[0])); byte[] changed = split.toByteArray();
        assertArrayEquals(changed, ImageTransferPng.verify(changed, identity(changed)).bytes());
        assertFalse(Arrays.equals(hash(original), hash(changed)));
        assertThrows(IOException.class, () -> ImageTransferPng.verify(changed, identity(original)));
    }

    @Test public void exportUsesRealOwnedEncoderOutputWithoutChangingLocalFiles() throws Exception {
        Path directory = temporary.newFolder().toPath(); LocalImages images = new LocalImages(directory);
        byte[] source = golden(1), before = source.clone();
        try (LocalImages.Pending pending = images.prepare(new ByteArrayInputStream(source))) {
            byte[] owned = pending.preview(), original = owned.clone();
            ImageTransferPng.VerifiedContent export = ImageTransferPng.prepareExport(owned);
            assertArrayEquals(before, source); assertArrayEquals(original, owned); assertArrayEquals(original, pending.preview());
            assertEquals(64, export.identity.width); assertEquals(64, export.identity.height);
            assertClosedChunks(export.bytes()); assertArrayEquals(export.bytes(), ImageTransferPng.verify(export.bytes(), export.identity).bytes());
            try (java.util.stream.Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
        }
    }

    @Test public void exportStripsEmbeddedMetadataButReceiptDoesNotSanitizeItSilently() throws Exception {
        byte[] withText = join(signature(), header(64, 64, 2),
                chunk("tEXt", "Comment\0SYNTHETIC-PRIVATE-NOTE".getBytes(StandardCharsets.US_ASCII)),
                idat(golden(1)), chunk("IEND", new byte[0]));
        reject(withText); byte[] before = withText.clone();
        ImageTransferPng.VerifiedContent result = ImageTransferPng.prepareExport(withText);
        assertClosedChunks(result.bytes()); assertArrayEquals(before, withText);
        assertFalse(new String(result.bytes(), StandardCharsets.ISO_8859_1).contains("SYNTHETIC-PRIVATE-NOTE"));
    }

    @Test public void legacyOwnedExportResizesButReceiveNeverResizes() throws Exception {
        byte[] old = png(2048, 1, 2, new byte[1 + 2048 * 3], 6), before = old.clone();
        reject(old); ImageTransferPng.VerifiedContent result = ImageTransferPng.prepareExport(old);
        assertEquals(1920, result.identity.width); assertEquals(1, result.identity.height); assertArrayEquals(before, old);
        byte[] small = png(1, 1, 2, new byte[4], 6);
        assertEquals(1, ImageTransferPng.prepareExport(small).identity.width);
    }

    @Test public void ownedExportBudgetDoesNotLoosenOriginalInputBudget() throws Exception {
        int width = 1440, height = 1920;
        byte[] owned = png(width, height, 6, new byte[(1 + width * 4) * height], Deflater.NO_COMPRESSION);
        assertTrue(owned.length > 10 * 1024 * 1024); assertTrue(owned.length <= ImageTransferPng.MAX_BYTES);
        LocalImages local = new LocalImages(temporary.newFolder().toPath());
        assertThrows(IOException.class, () -> local.prepare(new ByteArrayInputStream(owned)));
        byte[] digest = hash(owned); ImageTransferPng.VerifiedContent export = ImageTransferPng.prepareExport(owned);
        assertEquals(width, export.identity.width); assertEquals(height, export.identity.height);
        assertArrayEquals(digest, hash(owned)); assertClosedChunks(export.bytes());
        assertArrayEquals(owned, ImageTransferPng.verify(owned, identity(owned)).bytes());
    }

    @Test public void streamedGoldenBytesSurviveShortAndZeroProgressReads() throws Exception {
        for (int number = 1; number <= 2; number++) {
            byte[] bytes = golden(number);
            for (int fragment : new int[]{1, 7, 8192}) {
                ObservedStream stream = new ObservedStream(bytes, fragment, true);
                ImageTransferPng.VerifiedContent result = ImageTransferPng.readAndVerify(stream, identity(bytes));
                assertArrayEquals(bytes, result.bytes()); assertEquals(bytes.length, stream.offset);
                assertTrue(stream.eofRead); assertFalse(stream.closed);
                assertTrue(stream.maxRequest <= Math.min(8192, bytes.length));
            }
        }
        byte[] onePixel = png(1, 1, 2, new byte[4], 6);
        assertArrayEquals(onePixel, ImageTransferPng.readAndVerify(
                new ByteArrayInputStream(onePixel), identity(onePixel)).bytes());
    }

    @Test public void streamedEarlyEofAndDishonestLengthsFailWithoutClosingSource() throws Exception {
        byte[] bytes = golden(1);
        for (int size : new int[]{0, 1, bytes.length - 1}) {
            ObservedStream stream = new ObservedStream(Arrays.copyOf(bytes, size), 7, false);
            assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(stream, identity(bytes)));
            assertEquals(size, stream.offset); assertTrue(stream.eofRead); assertFalse(stream.closed);
        }
        for (int declared : new int[]{1, bytes.length - 1, bytes.length + 1}) {
            ObservedStream stream = new ObservedStream(bytes, 7, false);
            ImageTransferPng.Identity expected = new ImageTransferPng.Identity(hash(bytes), declared, 64, 64);
            assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(stream, expected));
            assertEquals(Math.min(bytes.length, declared + 1), stream.offset); assertFalse(stream.closed);
        }
    }

    @Test public void oversizedEndlessStreamStopsAtMaximumPlusOneActualBytes() throws Exception {
        GeneratedStream stream = new GeneratedStream(Long.MAX_VALUE);
        ImageTransferPng.Identity expected = new ImageTransferPng.Identity(new byte[32], ImageTransferPng.MAX_BYTES, 1, 1);
        assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(stream, expected));
        assertEquals(ImageTransferPng.MAX_BYTES + 1L, stream.count);
        assertTrue(stream.maxRequest <= 8192); assertFalse(stream.closed);
    }

    @Test public void exactMaximumStillRequiresEofAndRealContentValidation() throws Exception {
        GeneratedStream stream = new GeneratedStream(ImageTransferPng.MAX_BYTES);
        ImageTransferPng.Identity expected = new ImageTransferPng.Identity(new byte[32], ImageTransferPng.MAX_BYTES, 1, 1);
        assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(stream, expected));
        assertEquals(ImageTransferPng.MAX_BYTES, stream.count); assertTrue(stream.eofRead);
        assertFalse(stream.closed);
        GeneratedStream empty = new GeneratedStream(0);
        assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(empty, expected));
        assertEquals(0, empty.count); assertEquals(8192, empty.maxRequest);
    }

    @Test public void streamReadFailuresAndInterruptionsPropagateWithoutPositiveResult() throws Exception {
        for (IOException failure : new IOException[]{new IOException("synthetic failure"), new InterruptedIOException("synthetic cancellation")}) {
            ObservedStream stream = new ObservedStream(golden(1), 7, false) {
                @Override public int read(byte[] b, int offset, int length) throws IOException {
                    if (this.offset >= 7) throw failure;
                    return super.read(b, offset, length);
                }
            };
            IOException actual = assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(stream, identity(golden(1))));
            assertSame(failure, actual); assertEquals(7, stream.offset); assertFalse(stream.closed);
        }
    }

    @Test public void streamEofProbeFailureCannotBecomeSuccessfulVerification() throws Exception {
        byte[] bytes = golden(1); IOException failure = new IOException("synthetic EOF failure");
        ObservedStream stream = new ObservedStream(bytes, 8192, false) {
            @Override public int read() throws IOException { if (offset == bytes.length) throw failure; return super.read(); }
        };
        assertSame(failure, assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(stream, identity(bytes))));
        assertFalse(stream.closed);
    }

    @Test public void streamCompletionDoesNotBypassHashDimensionsOrClosedProfile() throws Exception {
        byte[] bytes = golden(1);
        for (ImageTransferPng.Identity wrong : new ImageTransferPng.Identity[]{
                new ImageTransferPng.Identity(new byte[32], bytes.length, 64, 64),
                new ImageTransferPng.Identity(hash(bytes), bytes.length, 63, 64)}) {
            assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(new ByteArrayInputStream(bytes), wrong));
        }
        byte[] metadata = join(signature(), header(64, 64, 2), chunk("tEXt", new byte[]{0}), idat(bytes), chunk("IEND", new byte[0]));
        assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(new ByteArrayInputStream(metadata), identity(metadata)));
        ObservedStream stream = new ObservedStream(bytes, 7, false);
        ImageTransferPng.readAndVerify(stream, identity(bytes)); assertFalse(stream.closed);
        stream.close(); assertTrue(stream.closed);
    }

    @Test public void zeroProgressThenEofFailsAndAllocationFailureUsesSafeCategory() throws Exception {
        InputStream empty = new InputStream() {
            @Override public int read(byte[] b, int offset, int length) { return 0; }
            @Override public int read() { return -1; }
        };
        assertThrows(IOException.class, () -> ImageTransferPng.readAndVerify(empty, identity(golden(1))));
        InputStream exhausted = new InputStream() {
            @Override public int read() { throw new OutOfMemoryError("synthetic allocation failure"); }
        };
        ImagePreparationException failure = assertThrows(ImagePreparationException.class,
                () -> ImageTransferPng.readAndVerify(exhausted, identity(golden(1))));
        assertEquals(ImagePreparationException.Reason.LOW_MEMORY, failure.reason);
    }

    private static class ObservedStream extends InputStream {
        final byte[] bytes; final int fragment; final boolean zeroReads;
        int offset, maxRequest; boolean closed, eofRead, zeroNext = true;
        ObservedStream(byte[] bytes, int fragment, boolean zeroReads) {
            this.bytes = bytes; this.fragment = fragment; this.zeroReads = zeroReads;
        }
        @Override public int read(byte[] b, int start, int length) throws IOException {
            maxRequest = Math.max(maxRequest, length);
            if (zeroReads && zeroNext) { zeroNext = false; return 0; }
            zeroNext = true;
            if (offset == bytes.length) { eofRead = true; return -1; }
            int count = Math.min(Math.min(length, fragment), bytes.length - offset);
            System.arraycopy(bytes, offset, b, start, count); offset += count; return count;
        }
        @Override public int read() throws IOException {
            if (offset == bytes.length) { eofRead = true; return -1; }
            return bytes[offset++] & 255;
        }
        @Override public int available() { throw new AssertionError("Do not trust available()"); }
        @Override public long skip(long count) { throw new AssertionError("Do not skip payload bytes"); }
        @Override public void mark(int limit) { throw new AssertionError("No mark dependency"); }
        @Override public void reset() { throw new AssertionError("No reset dependency"); }
        @Override public void close() { closed = true; }
    }

    private static final class GeneratedStream extends InputStream {
        final long limit; long count; int maxRequest; boolean eofRead, closed;
        GeneratedStream(long limit) { this.limit = limit; }
        @Override public int read(byte[] bytes, int offset, int length) {
            maxRequest = Math.max(maxRequest, length);
            if (count == limit) { eofRead = true; return -1; }
            int n = (int) Math.min(length, limit - count); Arrays.fill(bytes, offset, offset + n, (byte)0); count += n; return n;
        }
        @Override public int read() { if (count == limit) { eofRead = true; return -1; } count++; return 0; }
        @Override public void close() { closed = true; }
    }

    private static byte[] golden(int number) throws Exception {
        try (InputStream input = ImageTransferPngTest.class.getResourceAsStream("/image-transfer/h" + number + ".png")) {
            assertNotNull(input); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[1024];
            for (int n; (n = input.read(b)) != -1;) out.write(b, 0, n); return out.toByteArray();
        }
    }
    private static ImageTransferPng.Identity identity(byte[] b) throws Exception {
        int w = b.length >= 24 ? ByteBuffer.wrap(b, 16, 4).getInt() : 1;
        int h = b.length >= 24 ? ByteBuffer.wrap(b, 20, 4).getInt() : 1;
        return new ImageTransferPng.Identity(hash(b), b.length, Math.max(1, Math.min(1920, w)), Math.max(1, Math.min(1920, h)));
    }
    private static void reject(byte[] bytes) throws Exception {
        ImageTransferPng.Identity expected = identity(bytes);
        assertThrows(IOException.class, () -> ImageTransferPng.verify(bytes, expected));
    }
    private static byte[] hash(byte[] b) throws Exception { return MessageDigest.getInstance("SHA-256").digest(b); }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x & 255)); return s.toString(); }
    private static byte[] signature() { return new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10}; }
    private static byte[] headerData(int w, int h, int color) {
        return ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte)8).put((byte)color).put(new byte[3]).array();
    }
    private static byte[] header(int w, int h, int color) throws Exception { return chunk("IHDR", headerData(w, h, color)); }
    private static byte[] png(int w, int h, int color, byte[] raw, int level) throws Exception {
        return join(signature(), header(w, h, color), chunk("IDAT", compressed(raw, level)), chunk("IEND", new byte[0]));
    }
    private static byte[] compressed(byte[] raw, int level) throws Exception {
        Deflater d = new Deflater(level);
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); try (DeflaterOutputStream stream = new DeflaterOutputStream(out, d)) { stream.write(raw); } return out.toByteArray(); }
        finally { d.end(); }
    }
    private static byte[] chunk(String type, byte[] data) throws Exception {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII); CRC32 crc = new CRC32(); crc.update(t); crc.update(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(out);
        d.writeInt(data.length); d.write(t); d.write(data); d.writeInt((int)crc.getValue()); return out.toByteArray();
    }
    private static byte[] join(byte[]... parts) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] part : parts) out.write(part); return out.toByteArray();
    }
    private static byte[] idat(byte[] png) throws Exception { return chunk("IDAT", idatData(png)); }
    private static byte[] idatData(byte[] png) {
        int offset = 33, length = ByteBuffer.wrap(png, offset, 4).getInt(); return Arrays.copyOfRange(png, offset + 8, offset + 8 + length);
    }
    private static void assertClosedChunks(byte[] b) {
        for (int offset = 8; offset < b.length;) {
            String type = new String(b, offset + 4, 4, StandardCharsets.US_ASCII);
            assertTrue(type.equals("IHDR") || type.equals("IDAT") || type.equals("IEND"));
            offset += ByteBuffer.wrap(b, offset, 4).getInt() + 12;
        }
    }
}
