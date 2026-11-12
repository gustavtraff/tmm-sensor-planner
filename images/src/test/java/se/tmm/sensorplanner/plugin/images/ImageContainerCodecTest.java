package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.ImagePreparationException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageContainerCodecTest {
    @Test public void independentGoldensDecodeAndEncodeByteForByteWithoutClosingStreams() throws Exception {
        assertEquals(17039492, ImageContainerCodec.MAX_BYTES);
        for (int n = 1; n <= 2; n++) {
            byte[] golden = resource("/h" + n + ".tmmi"), original = golden.clone();
            TrackingInput input = new TrackingInput(golden, 7, false);
            ReceivedImagePayload pair = ImageContainerCodec.read(input);
            assertEquals(golden.length, input.position); assertFalse(input.closed);
            assertArrayEquals(resource("/offer-h" + n + ".bin"), ImageOfferCodec.encode(pair.offer));
            assertArrayEquals(resource("/image-transfer/h" + n + ".png"), pair.image.bytes());
            TrackingOutput out = new TrackingOutput(); ImageContainerCodec.write(out, pair.offer, pair.image);
            assertArrayEquals(golden, out.toByteArray()); assertFalse(out.closed); assertFalse(out.flushed);
            Arrays.fill(golden, (byte) 0); pair.image.bytes()[0] = 0; pair.offer.sha256()[0] ^= 1;
            TrackingOutput again = new TrackingOutput(); ImageContainerCodec.write(again, pair.offer, pair.image);
            assertArrayEquals(original, again.toByteArray());
        }
    }

    @Test public void malformedHeaderRefusesBeforeReadingAnyDescriptor() throws Exception {
        byte[] golden = resource("/h1.tmmi");
        List<byte[]> bad = new ArrayList<>();
        for (int offset : new int[]{0, 7, 8, 11}) { byte[] b = golden.clone(); b[offset] ^= 1; bad.add(b); }
        for (int d : new int[]{0,112,262257,-1,Integer.MAX_VALUE}) {
            byte[] b = golden.clone(); ByteBuffer.wrap(b).putInt(12,d); bad.add(b);
        }
        for (int p : new int[]{0,16777217,-1,Integer.MAX_VALUE}) {
            byte[] b = golden.clone(); ByteBuffer.wrap(b).putInt(16,p); bad.add(b);
        }
        for (byte[] bytes : bad) {
            TrackingInput in = new TrackingInput(bytes, 5, false);
            assertThrows(IOException.class, () -> ImageContainerCodec.read(in));
            assertEquals(20, in.position); assertFalse(in.closed);
        }
    }

    @Test public void descriptorAndOuterLengthMismatchRefuseBeforePngRead() throws Exception {
        byte[] golden = resource("/h1.tmmi"); int d = ByteBuffer.wrap(golden).getInt(12);
        byte[] wrongCrc = golden.clone(); wrongCrc[20+d-1] ^= 1;
        byte[] wrongVersion = golden.clone(); ByteBuffer.wrap(wrongVersion).putInt(28,2);
        byte[] wrongLength = golden.clone(); ByteBuffer.wrap(wrongLength).putInt(16,211);
        for (byte[] bytes : new byte[][]{wrongCrc,wrongVersion,wrongLength}) {
            TrackingInput input = new TrackingInput(bytes, 7, false);
            assertThrows(IOException.class, () -> ImageContainerCodec.read(input));
            assertEquals(20+d, input.position); assertFalse(input.closed);
        }
    }

    @Test public void everyTruncationAndTrailingContentIsRejected() throws Exception {
        byte[] frame = resource("/h1.tmmi");
        for (int size = 0; size < frame.length; size++) {
            byte[] prefix = Arrays.copyOf(frame,size);
            assertThrows("prefix " + size, IOException.class,
                    () -> ImageContainerCodec.read(new ByteArrayInputStream(prefix)));
        }
        byte[] trailing = Arrays.copyOf(frame,frame.length+1000);
        TrackingInput input = new TrackingInput(trailing,8192,false);
        assertThrows(IOException.class, () -> ImageContainerCodec.read(input));
        assertEquals(frame.length+1,input.position); assertFalse(input.closed);
    }

    @Test public void mixedPairAndMatchingHashOverCorruptPngAreRejected() throws Exception {
        byte[] one = resource("/h1.tmmi"), two = resource("/h2.tmmi");
        int d = ByteBuffer.wrap(one).getInt(12);
        byte[] mixed = two.clone(); System.arraycopy(one,20,mixed,20,d);
        assertThrows(IOException.class, () -> ImageContainerCodec.read(new ByteArrayInputStream(mixed)));
        byte[] bad = one.clone(); int png = 20+d; bad[png+29] ^= 1;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(bad,png,bad.length));
        System.arraycopy(digest,0,bad,20+72,32);
        CRC32 crc = new CRC32(); crc.update(bad,20,d-4); ByteBuffer.wrap(bad).putInt(20+d-4,(int)crc.getValue());
        assertThrows(IOException.class, () -> ImageContainerCodec.read(new ByteArrayInputStream(bad)));
    }

    @Test public void zeroBulkReadsAreHandledWithoutAReadSpin() throws Exception {
        byte[] golden = resource("/h1.tmmi"); TrackingInput input = new TrackingInput(golden,1,true);
        ReceivedImagePayload pair = ImageContainerCodec.read(input);
        assertEquals(golden.length,input.position); assertArrayEquals(resource("/image-transfer/h1.png"),pair.image.bytes());
        assertTrue(input.calls <= golden.length*2+2); assertFalse(input.closed);
    }

    @Test public void rejectedPairNeverTouchesOutputAndCannotSmuggleDifferentContent() throws Exception {
        ReceivedImagePayload a = pair(1), b = pair(2);
        TrackingOutput output = new TrackingOutput();
        assertThrows(IOException.class, () -> ImageContainerCodec.write(output,a.offer,b.image));
        ImageOffer wrongDimensions = new ImageOffer(a.offer.associationId,a.offer.deliveryId,a.offer.metadata,
                a.offer.sha256(),a.offer.pngLength,63,64);
        assertThrows(IOException.class, () -> ImageContainerCodec.write(output,wrongDimensions,a.image));
        assertThrows(IOException.class, () -> ImageContainerCodec.write(output,null,a.image));
        assertThrows(IOException.class, () -> ImageContainerCodec.write(output,a.offer,null));
        assertEquals(0,output.size()); assertFalse(output.closed);
    }

    @Test public void providerFailuresAreSafeAndPartialOutputIsNotReportedAsRollback() throws Exception {
        InputStream failed = new InputStream() { @Override public int read() throws IOException {
            throw new IOException("SYNTHETIC-PRIVATE-PROVIDER"); } };
        IOException error = assertThrows(IOException.class, () -> ImageContainerCodec.read(failed));
        assertFalse(error.getMessage().contains("PRIVATE")); assertNull(error.getCause());
        ReceivedImagePayload p = pair(1); TrackingOutput partial = new TrackingOutput() {
            @Override public synchronized void write(byte[] b,int off,int len) {
                super.write(b,off,Math.min(4,len)); throw new IllegalStateException("SYNTHETIC-PRIVATE-TARGET");
            }
        };
        IOException outputError = assertThrows(IOException.class, () -> ImageContainerCodec.write(partial,p.offer,p.image));
        assertTrue(partial.size()>0); assertTrue(outputError.getMessage().contains("may be incomplete"));
        assertFalse(outputError.getMessage().contains("PRIVATE")); assertNull(outputError.getCause()); assertFalse(partial.closed);
    }

    @Test public void memoryFailureKeepsTheExistingSafeCategory() throws Exception {
        InputStream exhausted = new InputStream() { @Override public int read() { throw new OutOfMemoryError("synthetic"); } };
        ImagePreparationException error = assertThrows(ImagePreparationException.class, () -> ImageContainerCodec.read(exhausted));
        assertEquals(ImagePreparationException.Reason.LOW_MEMORY,error.reason);
    }

    private static ReceivedImagePayload pair(int n) throws Exception {
        return ImageContainerCodec.read(new ByteArrayInputStream(resource("/h"+n+".tmmi")));
    }
    private static byte[] resource(String path) throws IOException {
        try (InputStream in = ImageContainerCodecTest.class.getResourceAsStream(path)) {
            assertNotNull(path,in); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096];
            for (int n; (n=in.read(b))!=-1;) out.write(b,0,n); return out.toByteArray();
        }
    }
    private static final class TrackingInput extends InputStream {
        final byte[] bytes; final int chunk; final boolean zero; int position,calls; boolean closed;
        TrackingInput(byte[] bytes,int chunk,boolean zero) { this.bytes=bytes;this.chunk=chunk;this.zero=zero; }
        @Override public int read(byte[] b,int off,int len) {
            calls++; if(zero)return 0; if(position==bytes.length)return -1;
            int n=Math.min(len,Math.min(chunk,bytes.length-position));System.arraycopy(bytes,position,b,off,n);position+=n;return n;
        }
        @Override public int read() { calls++;return position==bytes.length?-1:bytes[position++]&255; }
        @Override public void close() { closed=true; }
    }
    private static class TrackingOutput extends ByteArrayOutputStream {
        boolean closed,flushed;
        @Override public void close() { closed=true; }
        @Override public void flush() { flushed=true; }
    }
}
