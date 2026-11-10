package se.tmm.sensorplanner.images;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
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
public class ReceivedOwnedImageTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void exactVerifiedBytesReopenUnderNewOpaqueHandleWithoutChangingExistingCopy() throws Exception {
        Path root = temp.newFolder().toPath(); LocalImages images = new LocalImages(root);
        byte[] first = resource("h1"), second = resource("h2");
        AtomicReference<String> old = new AtomicReference<>(), next = new AtomicReference<>();
        try (LocalImages.Pending p = images.prepareReceived(verified(first, 64, 64))) { p.commit(old::set); }
        try (LocalImages.Pending p = images.prepareReceived(verified(second, 64, 64))) {
            assertArrayEquals(second, p.preview()); p.commit(next::set);
        }
        assertNotEquals(old.get(), next.get());
        assertTrue(next.get().matches("[0-9a-f-]{36}\\.png"));
        assertArrayEquals(first, new LocalImages(root).read(old.get()));
        assertArrayEquals(second, new LocalImages(root).read(next.get()));
        assertArrayEquals(resource("h1"), first); assertArrayEquals(resource("h2"), second);
        assertEquals(2, count(root));
    }

    @Test public void cancellationDeletesOnlyNewPendingCopyAndNeverCallsMetadataSave() throws Exception {
        Path root = temp.newFolder().toPath(); LocalImages images = new LocalImages(root);
        AtomicReference<String> old = new AtomicReference<>(); byte[] bytes = resource("h1");
        try (LocalImages.Pending p = images.prepareReceived(verified(bytes, 64, 64))) { p.commit(old::set); }
        LocalImages.Pending pending = images.prepareReceived(verified(resource("h2"), 64, 64));
        assertEquals(2, count(root)); pending.close(); pending.close();
        assertEquals(1, count(root)); assertArrayEquals(bytes, images.read(old.get()));
        assertThrows(IOException.class, pending::preview);
        assertThrows(IOException.class, () -> pending.commit(ref -> fail("Save after Cancel")));
    }

    @Test public void ambiguousSaveRetainsExactCandidateAndOldImageForReconciliation() throws Exception {
        Path root = temp.newFolder().toPath(); LocalImages images = new LocalImages(root);
        AtomicReference<String> old = new AtomicReference<>(), candidate = new AtomicReference<>();
        try (LocalImages.Pending p = images.prepareReceived(verified(resource("h1"), 64, 64))) { p.commit(old::set); }
        LocalImages.Pending pending = images.prepareReceived(verified(resource("h2"), 64, 64));
        assertThrows(IOException.class, () -> pending.commit(ref -> {
            candidate.set(ref); throw new IOException("Synthetic uncertain metadata save");
        }));
        assertThrows(IOException.class, () -> pending.commit(ref -> fail("Automatic retry")));
        pending.close(); assertEquals(2, count(root));
        assertArrayEquals(resource("h1"), new LocalImages(root).read(old.get()));
        assertArrayEquals(resource("h2"), new LocalImages(root).read(candidate.get()));
    }

    @Test public void missingVerificationAndUnavailableStorageCannotReturnPendingImage() throws Exception {
        Path root = temp.newFolder().toPath(); LocalImages images = new LocalImages(root);
        assertThrows(NullPointerException.class, () -> images.prepareReceived(null)); assertEquals(0, count(root));
        Path file = temp.newFile().toPath();
        ImagePreparationException failure = assertThrows(ImagePreparationException.class,
                () -> new LocalImages(file).prepareReceived(verified(resource("h1"), 64, 64)));
        assertEquals(ImagePreparationException.Reason.SAVE_FAILED, failure.reason);
    }

    @Test public void actualHighEntropy1920PngAboveSourceLimitRemainsExactAndSourceLimitStaysTenMiB() throws Exception {
        byte[] png = noise1920();
        assertTrue(png.length > ImageFormat.MAX_BYTES); assertTrue(png.length <= ImageFormat.MAX_OWNED_BYTES);
        ImageTransferPng.VerifiedContent content = verified(png, 1920, 1920);
        LocalImages images = new LocalImages(temp.newFolder().toPath());
        ImagePreparationException tooLarge = assertThrows(ImagePreparationException.class,
                () -> images.prepare(new ByteArrayInputStream(png)));
        assertEquals(ImagePreparationException.Reason.INPUT_TOO_LARGE, tooLarge.reason);
        AtomicReference<String> ref = new AtomicReference<>();
        try (LocalImages.Pending pending = images.prepareReceived(content)) {
            assertArrayEquals(png, pending.preview()); pending.commit(ref::set);
        }
        byte[] reopened = images.read(ref.get());
        assertArrayEquals(png, reopened);
        assertArrayEquals(content.identity.sha256(), MessageDigest.getInstance("SHA-256").digest(reopened));
        assertEquals(1920, ByteBuffer.wrap(reopened).getInt(16)); assertEquals(1920, ByteBuffer.wrap(reopened).getInt(20));
    }

    private static ImageTransferPng.VerifiedContent verified(byte[] png, int w, int h) throws Exception {
        return ImageTransferPng.verify(png, new ImageTransferPng.Identity(
                MessageDigest.getInstance("SHA-256").digest(png), png.length, w, h));
    }
    private static byte[] resource(String name) throws IOException {
        try (InputStream in = ReceivedOwnedImageTest.class.getResourceAsStream("/image-transfer/" + name + ".png")) {
            assertNotNull(in); ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; for (int n; (n = in.read(b)) != -1;) out.write(b, 0, n); return out.toByteArray();
        }
    }
    private static long count(Path root) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(root)) { return files.count(); }
    }
    private static byte[] noise1920() throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(); Random random = new Random(197);
        try (DeflaterOutputStream z = new DeflaterOutputStream(compressed)) {
            byte[] row = new byte[1920 * 3];
            for (int y = 0; y < 1920; y++) { random.nextBytes(row); z.write(0); z.write(row); }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        chunk(png, "IHDR", ByteBuffer.allocate(13).putInt(1920).putInt(1920).put(new byte[]{8,2,0,0,0}).array());
        chunk(png, "IDAT", compressed.toByteArray()); chunk(png, "IEND", new byte[0]); return png.toByteArray();
    }
    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] kind = type.getBytes(StandardCharsets.US_ASCII); CRC32 crc = new CRC32(); crc.update(kind); crc.update(data);
        DataOutputStream d = new DataOutputStream(out); d.writeInt(data.length); d.write(kind); d.write(data); d.writeInt((int)crc.getValue());
    }
}
