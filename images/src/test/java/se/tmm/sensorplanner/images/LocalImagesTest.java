package se.tmm.sensorplanner.images;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import java.time.Instant;
import se.tmm.sensorplanner.core.DeviceType;
import se.tmm.sensorplanner.core.DraftStore;
import se.tmm.sensorplanner.core.Position;
import se.tmm.sensorplanner.core.TmmDraft;
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
public class LocalImagesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void normalizesAllOrientationsAndStripsMetadata() throws Exception {
        int[] pixels = {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffff00, 0xffff00ff, 0xff00ffff};
        int[][] expected = {
            {0, 1, 2, 3, 4, 5}, {1, 0, 3, 2, 5, 4}, {5, 4, 3, 2, 1, 0}, {4, 5, 2, 3, 0, 1},
            {0, 2, 4, 1, 3, 5}, {4, 2, 0, 5, 3, 1}, {5, 3, 1, 4, 2, 0}, {1, 3, 5, 0, 2, 4}
        };
        LocalImages images = store();
        for (int orientation = 1; orientation <= 8; orientation++) {
            Path source = temporary.newFile("orientation-" + orientation + ".png").toPath();
            Files.write(source, encode(pixels, 2, 3, Bitmap.CompressFormat.PNG));
            ExifInterface exif = new ExifInterface(source.toFile());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "SYNTHETIC-METADATA");
            exif.setLatLong(0, 0);
            exif.saveAttributes();
            byte[] original = Files.readAllBytes(source);
            try (InputStream input = Files.newInputStream(source); LocalImages.Pending pending = images.prepare(input)) {
                byte[] output = pending.preview();
                Bitmap result = decode(output);
                assertEquals(orientation <= 4 ? 2 : 3, result.getWidth());
                assertEquals(orientation <= 4 ? 3 : 2, result.getHeight());
                int[] actual = new int[6];
                result.getPixels(actual, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());
                result.recycle();
                for (int i = 0; i < 6; i++) assertEquals(pixels[expected[orientation - 1][i]], actual[i]);
                ExifInterface clean = new ExifInterface(new ByteArrayInputStream(output));
                assertNull(clean.getAttribute(ExifInterface.TAG_GPS_LATITUDE));
                assertNull(clean.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION));
                assertFalse(new String(output, StandardCharsets.ISO_8859_1).contains("SYNTHETIC-METADATA"));
            }
            assertArrayEquals(original, Files.readAllBytes(source));
        }
        assertEquals(0, ownedCount(imagesDirectory));
    }

    @Test public void pngTextIsNotCopiedAndPixelBoundaryUsesLongArithmetic() throws Exception {
        ImageFormat.dimensions(20_000, 1_000);
        for (int[] dimensions : new int[][]{{0, 1}, {-1, 2}, {20_000_001, 1}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
            Assert.assertThrows(IOException.class, () -> ImageFormat.dimensions(dimensions[0], dimensions[1]));
        }
        byte[] source = insertChunk(png(), "tEXt", "Comment\0SYNTHETIC-TEXT".getBytes(StandardCharsets.US_ASCII));
        LocalImages images = store();
        try (LocalImages.Pending pending = images.prepare(new ByteArrayInputStream(source))) {
            assertFalse(new String(pending.preview(), StandardCharsets.ISO_8859_1).contains("SYNTHETIC-TEXT"));
        }
    }

    @Test public void jpegOrientationIsAppliedAndSourceIsUntouched() throws Exception {
        Path source = temporary.newFile("synthetic.jpeg").toPath();
        Files.write(source, encode(new int[]{0xffff0000, 0xff0000ff}, 2, 1, Bitmap.CompressFormat.JPEG));
        ExifInterface exif = new ExifInterface(source.toFile());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
        exif.setAttribute(ExifInterface.TAG_MAKE, "SYNTHETIC-CAMERA");
        exif.saveAttributes();
        byte[] original = Files.readAllBytes(source);
        LocalImages images = store();
        try (LocalImages.Pending pending = images.prepare(new ByteArrayInputStream(original))) {
            byte[] output = pending.preview();
            Bitmap result = decode(output);
            assertEquals(1, result.getWidth());
            assertEquals(2, result.getHeight());
            Bitmap decodedSource = decode(original);
            assertEquals(decodedSource.getPixel(0, 0), result.getPixel(0, 0));
            assertEquals(decodedSource.getPixel(1, 0), result.getPixel(0, 1));
            decodedSource.recycle();
            result.recycle();
            assertNull(new ExifInterface(new ByteArrayInputStream(output)).getAttribute(ExifInterface.TAG_MAKE));
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test public void cancellationReplacementFailureAndReopenPreserveOwnedCopies() throws Exception {
        LocalImages images = store();
        String[] saved = {null};
        try (LocalImages.Pending first = images.prepare(input())) { first.commit(ref -> saved[0] = ref); }
        String old = saved[0];
        byte[] original = images.read(old);
        try (LocalImages.Pending cancelled = images.prepare(input())) { assertNotNull(cancelled.preview()); }
        assertEquals(1, ownedCount(imagesDirectory));
        IOException ambiguous = new IOException("Synthetic ambiguous metadata commit");
        try (LocalImages.Pending replacement = images.prepare(input())) {
            assertSame(ambiguous, Assert.assertThrows(IOException.class, () -> replacement.commit(ref -> { throw ambiguous; })));
        }
        assertEquals(2, ownedCount(imagesDirectory));
        assertEquals(old, saved[0]);
        assertArrayEquals(original, new LocalImages(imagesDirectory).read(old));
        // A later successful save switches metadata first; only then may the detached old file go.
        try (LocalImages.Pending replacement = images.prepare(input())) {
            replacement.commit(ref -> saved[0] = ref);
            Assert.assertThrows(IOException.class, () -> replacement.commit(ref -> fail("Committed twice")));
        }
        assertNotEquals(old, saved[0]);
        assertArrayEquals(original, images.read(old));
        images.deleteUnreferenced(old);
        Assert.assertThrows(IOException.class, () -> images.read(old));
        assertArrayEquals(original, new LocalImages(imagesDirectory).read(saved[0]));
    }

    @Test public void ambiguousFailureAfterMetadataWriteKeepsTheNewReferenceReadable() throws Exception {
        LocalImages images = store();
        String[] saved = {null};
        try (LocalImages.Pending pending = images.prepare(input())) {
            Assert.assertThrows(IOException.class, () -> pending.commit(ref -> {
                saved[0] = ref;
                throw new IOException("Synthetic failure after metadata replacement");
            }));
        }
        assertNotNull(new LocalImages(imagesDirectory).read(saved[0]));
    }

    @Test public void realSnapshotKeepsReadableImageAfterAnAmbiguousSaveAndDetach() throws Exception {
        LocalImages images = store();
        Path metadata = temporary.getRoot().toPath().resolve("drafts");
        DraftStore drafts = new DraftStore(metadata);
        TmmDraft draft = TmmDraft.create("PIR 134", DeviceType.PIR, "Synthetic group", new Position(0, 0), Instant.EPOCH);
        drafts.save(draft);
        try (LocalImages.Pending pending = images.prepare(input())) {
            Assert.assertThrows(IOException.class, () -> pending.commit(ref -> {
                drafts.save(withImage(draft, ref));
                throw new IOException("Synthetic error after the snapshot committed");
            }));
        }
        TmmDraft reopened = new DraftStore(metadata).load().get(0);
        assertEquals(draft.uid, reopened.uid);
        assertEquals(draft.revision + 1, reopened.revision);
        String first = reopened.imageRef;
        assertNotNull(new LocalImages(imagesDirectory).read(first));
        try (LocalImages.Pending replacement = images.prepare(input())) {
            Assert.assertThrows(IOException.class, () -> replacement.commit(ref -> drafts.save(withImage(draft, ref))));
        }
        assertEquals(first, drafts.load().get(0).imageRef);
        assertNotNull(images.read(first));
        drafts.save(withImage(reopened, null));
        assertNull(new DraftStore(metadata).load().get(0).imageRef);
        assertNotNull(images.read(first));
        images.deleteUnreferenced(first);
        Assert.assertThrows(IOException.class, () -> images.read(first));
    }

    private static TmmDraft withImage(TmmDraft draft, String reference) {
        return draft.edit(draft.displayId, draft.type, draft.status, draft.owner, draft.deployedAt,
                draft.comment, draft.position, draft.bearingDeg, reference, draft.updatedAt.plusSeconds(1));
    }

    @Test public void rejectsInputLimitsAnimationCorruptionAndUnsupportedTypesBeforeWriting() throws Exception {
        LocalImages images = store();
        byte[] png = png();
        List<byte[]> invalid = new ArrayList<>();
        invalid.add(new byte[0]);
        invalid.add("GIF89a".getBytes(StandardCharsets.US_ASCII));
        invalid.add(Arrays.copyOf(png, png.length - 1));
        invalid.add(png.clone());
        invalid.get(invalid.size() - 1)[29] ^= 1;
        invalid.add(insertChunk(png, "acTL", ByteBuffer.allocate(8).putInt(2).putInt(0).array()));
        invalid.add(insertChunk(png, "fcTL", new byte[26]));
        byte[] huge = png.clone();
        ByteBuffer.wrap(huge).putInt(16, 20_000_001).putInt(20, 1);
        updateHeaderCrc(huge);
        invalid.add(huge);
        byte[] overflow = huge.clone();
        ByteBuffer.wrap(overflow).putInt(16, Integer.MAX_VALUE).putInt(20, Integer.MAX_VALUE);
        updateHeaderCrc(overflow);
        invalid.add(overflow);
        byte[] jpeg = encode(new int[]{0xff000000}, 1, 1, Bitmap.CompressFormat.JPEG);
        invalid.add(Arrays.copyOf(jpeg, jpeg.length - 2));
        invalid.add(concat(Arrays.copyOf(jpeg, jpeg.length - 2), jpeg));
        byte[] badLength = jpeg.clone();
        badLength[4] = (byte) 0xff; badLength[5] = (byte) 0xff;
        invalid.add(badLength);
        for (byte[] bytes : invalid) {
            Assert.assertThrows(IOException.class, () -> images.prepare(new ByteArrayInputStream(bytes)));
        }
        InputStream tooLarge = new InputStream() {
            int count;
            @Override public int read() { return count++ <= ImageFormat.MAX_BYTES ? 0 : -1; }
        };
        Assert.assertThrows(IOException.class, () -> images.prepare(tooLarge));
        assertTrue(imagesDirectory.toFile().list() == null || ownedCount(imagesDirectory) == 0);
    }

    @Test public void cameraJpegUsesOnlyThePrimaryStillAndDiscardsAuxiliaryContent() throws Exception {
        Path primaryFile = temporary.newFile("primary-camera.jpg").toPath();
        Files.write(primaryFile, encode(new int[]{0xffff0000, 0xff0000ff}, 2, 1, Bitmap.CompressFormat.JPEG));
        ExifInterface exif = new ExifInterface(primaryFile.toFile());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
        exif.setAttribute(ExifInterface.TAG_MAKE, "SYNTHETIC-PRIMARY-CAMERA");
        exif.saveAttributes();
        byte[] primary = Files.readAllBytes(primaryFile);
        byte[] secondary = encode(new int[]{0xff00ff00}, 1, 1, Bitmap.CompressFormat.JPEG);
        byte[] trailer = "SYNTHETIC-CAMERA-AUXILIARY-DATA".getBytes(StandardCharsets.US_ASCII);
        LocalImages images = store();
        byte[] expected;
        try (LocalImages.Pending baseline = images.prepare(new ByteArrayInputStream(primary))) {
            expected = baseline.preview();
        }
        for (byte[] source : new byte[][]{mpfJpeg(primary, secondary, false), mpfJpeg(primary, secondary, true), concat(primary, trailer),
                concat(primary, secondary), concat(primary, new byte[16])}) {
            Path file = temporary.newFile().toPath();
            Files.write(file, source);
            String[] saved = {null};
            try (InputStream input = Files.newInputStream(file); LocalImages.Pending pending = images.prepare(input)) {
                assertArrayEquals(expected, pending.preview());
                pending.commit(ref -> saved[0] = ref);
            }
            assertArrayEquals(expected, new LocalImages(imagesDirectory).read(saved[0]));
            assertArrayEquals(source, Files.readAllBytes(file));
        }
        byte[] oversized = Arrays.copyOf(primary, ImageFormat.MAX_BYTES + 1);
        ImagePreparationException failure = Assert.assertThrows(ImagePreparationException.class,
                () -> images.prepare(new ByteArrayInputStream(oversized)));
        assertEquals(ImagePreparationException.Reason.INPUT_TOO_LARGE, failure.reason);
    }

    // Two generated JPEGs indexed by an MPF APP2 segment; not a real camera photograph.
    private static byte[] mpfJpeg(byte[] primary, byte[] secondary, boolean afterMetadata) throws IOException {
        int insertion = 2;
        if (afterMetadata) {
            while ((primary[insertion + 1] & 255) != 218) {
                int length = ByteBuffer.wrap(primary, insertion + 2, 2).getShort() & 0xffff;
                insertion += length + 2;
            }
        }
        int segmentSize = 90;
        ByteBuffer payload = ByteBuffer.allocate(segmentSize - 4);
        payload.put(new byte[]{'M', 'P', 'F', 0});
        payload.put(new byte[]{'M', 'M'}).putShort((short) 42).putInt(8);
        payload.putShort((short) 3);
        payload.putShort((short) 0xb000).putShort((short) 7).putInt(4).putInt(0x30313030);
        payload.putShort((short) 0xb001).putShort((short) 4).putInt(1).putInt(2);
        payload.putShort((short) 0xb002).putShort((short) 7).putInt(32).putInt(50);
        payload.putInt(0);
        payload.putInt(0x20030000).putInt(primary.length + segmentSize).putInt(0).putInt(0);
        payload.putInt(0).putInt(secondary.length).putInt(primary.length + segmentSize - insertion - 8).putInt(0);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(primary, 0, insertion);
        DataOutputStream segment = new DataOutputStream(output);
        segment.writeShort(0xffe2);
        segment.writeShort(segmentSize - 2);
        segment.write(payload.array());
        output.write(primary, insertion, primary.length - insertion);
        output.write(secondary);
        return output.toByteArray();
    }

    @Test public void cameraSizedJpegPreparesABoundedCopyAndPreservesTheOriginal() throws Exception {
        int[] noisy = new int[3072 * 4096];
        Random random = new Random(49);
        for (int i = 0; i < noisy.length; i++) noisy[i] = 0xff000000 | random.nextInt(0x1000000);
        Bitmap camera = Bitmap.createBitmap(noisy, 3072, 4096, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(camera.compress(Bitmap.CompressFormat.JPEG, 40, encoded));
        camera.recycle();
        byte[] jpeg = encoded.toByteArray();
        assertTrue(jpeg.length > 3 * 1024 * 1024);
        assertTrue(jpeg.length <= ImageFormat.MAX_BYTES);
        Path source = temporary.newFile("synthetic-camera.jpg").toPath();
        Files.write(source, jpeg);
        ExifInterface exif = new ExifInterface(source.toFile());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
        exif.setAttribute(ExifInterface.TAG_MAKE, "SYNTHETIC-CAMERA");
        exif.setLatLong(0, 0);
        exif.saveAttributes();
        byte[] original = Files.readAllBytes(source);
        Bitmap fullSize = decode(original);
        ByteArrayOutputStream fullPng = new ByteArrayOutputStream();
        assertTrue(fullSize.compress(Bitmap.CompressFormat.PNG, 100, fullPng));
        fullSize.recycle();
        assertTrue("Full-resolution PNG exceeds the old output cap", fullPng.size() > ImageFormat.MAX_BYTES);
        LocalImages images = store();
        String[] saved = {null};
        try (InputStream input = Files.newInputStream(source); LocalImages.Pending pending = images.prepare(input)) {
            byte[] output = pending.preview();
            assertTrue(output.length <= ImageFormat.MAX_BYTES);
            Bitmap result = decode(output);
            assertEquals(1600, result.getWidth());
            assertEquals(1200, result.getHeight());
            result.recycle();
            ExifInterface clean = new ExifInterface(new ByteArrayInputStream(output));
            assertNull(clean.getAttribute(ExifInterface.TAG_MAKE));
            assertNull(clean.getLatLong());
            pending.commit(ref -> saved[0] = ref);
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        assertNotNull(new LocalImages(imagesDirectory).read(saved[0]));
    }

    @Test public void newCopiesAreBoundedButExistingFullResolutionPngsAreNotRewritten() throws Exception {
        LocalImages images = store();
        for (int edge : new int[]{1599, 1600, 1601}) {
            byte[] source = encode(new int[edge * 2], edge, 2, Bitmap.CompressFormat.PNG);
            try (LocalImages.Pending pending = images.prepare(new ByteArrayInputStream(source))) {
                Bitmap copy = decode(pending.preview());
                assertEquals(Math.min(edge, 1600), copy.getWidth());
                assertEquals(2, copy.getHeight());
                assertEquals(0, copy.getPixel(0, 0));
                copy.recycle();
            }
        }
        String legacy = UUID.randomUUID() + ".png";
        byte[] original = encode(new int[2048 * 2], 2048, 2, Bitmap.CompressFormat.PNG);
        Files.write(imagesDirectory.resolve(legacy), original);
        assertArrayEquals(original, new LocalImages(imagesDirectory).read(legacy));
        assertArrayEquals(original, Files.readAllBytes(imagesDirectory.resolve(legacy)));
    }

    @Test public void inputPixelFormatAndStorageFailuresHaveDistinctSafeReasons() throws Exception {
        LocalImages images = store();
        ImagePreparationException invalid = Assert.assertThrows(ImagePreparationException.class,
                () -> images.prepare(new ByteArrayInputStream(new byte[0])));
        assertEquals(ImagePreparationException.Reason.INVALID_IMAGE, invalid.reason);
        ImagePreparationException pixel = Assert.assertThrows(ImagePreparationException.class,
                () -> ImageFormat.dimensions(3072, 7000));
        assertEquals(ImagePreparationException.Reason.PIXEL_LIMIT, pixel.reason);
        InputStream tooLarge = new InputStream() {
            int count;
            @Override public int read() { return count++ <= ImageFormat.MAX_BYTES ? 0 : -1; }
        };
        ImagePreparationException bytes = Assert.assertThrows(ImagePreparationException.class,
                () -> images.prepare(tooLarge));
        assertEquals(ImagePreparationException.Reason.INPUT_TOO_LARGE, bytes.reason);
        Path file = temporary.newFile("not-an-image-directory").toPath();
        ImagePreparationException storage = Assert.assertThrows(ImagePreparationException.class,
                () -> new LocalImages(file).prepare(input()));
        assertEquals(ImagePreparationException.Reason.SAVE_FAILED, storage.reason);
    }

    @Test public void pathTraversalSymlinksMissingAndDamagedOwnedFilesAreErrors() throws Exception {
        LocalImages images = store();
        Path outside = temporary.newFile("outside.png").toPath();
        Files.write(outside, png());
        for (String bad : new String[]{"../outside.png", outside.toString(), "content://test/image", "synthetic-image", "", null}) {
            Assert.assertThrows(IOException.class, () -> images.read(bad));
            Assert.assertThrows(IOException.class, () -> images.deleteUnreferenced(bad));
        }
        String ref = UUID.randomUUID() + ".png";
        Files.createDirectories(imagesDirectory);
        Path owned = imagesDirectory.resolve(ref);
        Files.createSymbolicLink(owned, outside);
        Assert.assertThrows(IOException.class, () -> images.read(ref));
        Assert.assertThrows(IOException.class, () -> images.deleteUnreferenced(ref));
        assertArrayEquals(png(), Files.readAllBytes(outside));
        Files.delete(owned);
        Assert.assertThrows(IOException.class, () -> images.read(ref));
        Files.write(owned, new byte[]{1, 2, 3});
        Assert.assertThrows(IOException.class, () -> images.read(ref));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(owned));
        Files.write(owned, encode(new int[]{0xff000000}, 1, 1, Bitmap.CompressFormat.JPEG));
        Assert.assertThrows(IOException.class, () -> images.read(ref));
    }

    @Test public void ioFailureNeverReplacesTheOriginalAndClosedPreviewCannotCommit() throws Exception {
        Path notDirectory = temporary.newFile("not-directory").toPath();
        Assert.assertThrows(IOException.class, () -> new LocalImages(notDirectory).prepare(input()));
        assertEquals(0, Files.size(notDirectory));
        LocalImages images = store();
        LocalImages.Pending cancelled = images.prepare(input());
        cancelled.close();
        cancelled.close();
        Assert.assertThrows(IOException.class, cancelled::preview);
        Assert.assertThrows(IOException.class, () -> cancelled.commit(ref -> fail("Closed preview committed")));
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("Synthetic read failure"); }
        };
        Assert.assertThrows(IOException.class, () -> images.prepare(broken));
        assertEquals(0, ownedCount(imagesDirectory));
    }

    private Path imagesDirectory;
    private LocalImages store() throws IOException {
        imagesDirectory = temporary.getRoot().toPath().resolve("images");
        return new LocalImages(imagesDirectory);
    }
    private static long ownedCount(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) { return files.count(); }
    }
    private static Bitmap decode(byte[] bytes) {
        Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        assertNotNull(result);
        return result;
    }
    private static byte[] encode(int[] pixels, int width, int height, Bitmap.CompressFormat format) {
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(format, 100, output));
        bitmap.recycle();
        return output.toByteArray();
    }
    private static byte[] png() { return encode(new int[]{0xff123456}, 1, 1, Bitmap.CompressFormat.PNG); }
    private static InputStream input() { return new ByteArrayInputStream(png()); }
    private static byte[] insertChunk(byte[] png, String type, byte[] data) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(png, 0, 33);
        DataOutputStream out = new DataOutputStream(result);
        out.writeInt(data.length);
        byte[] kind = type.getBytes(StandardCharsets.US_ASCII);
        out.write(kind);
        out.write(data);
        CRC32 crc = new CRC32(); crc.update(kind); crc.update(data);
        out.writeInt((int) crc.getValue());
        out.write(png, 33, png.length - 33);
        return result.toByteArray();
    }
    private static void updateHeaderCrc(byte[] png) {
        CRC32 crc = new CRC32(); crc.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
    }
    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
