package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.core.PublicationPreparationReview;
import se.tmm.sensorplanner.core.ShareReview;
import se.tmm.sensorplanner.core.TmmDraft;
import se.tmm.sensorplanner.images.ImagePreparationException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceivedImagePayloadTest {
    @Test public void independentOfferAndActualImageAreReturnedAsOneImmutableCandidate() throws Exception {
        for(int i=1;i<=2;i++) {
            byte[] descriptor=resource("/offer-h"+i+".bin"), png=resource("/image-transfer/h"+i+".png");
            byte[] original=png.clone(), originalDescriptor=descriptor.clone();
            OwnedStream d=new OwnedStream(descriptor),p=new OwnedStream(png);
            ReceivedImagePayload result=ReceivedImagePayload.read(d,p);
            assertEquals(UUID.fromString("00000000-0000-4000-8014-000000000003"),result.offer.metadata.uid);
            assertEquals(64,result.image.identity.width);assertEquals(64,result.image.identity.height);
            assertArrayEquals(result.offer.sha256(),result.image.identity.sha256());
            assertEquals(result.offer.pngLength,result.image.identity.length);
            assertArrayEquals(original,result.image.bytes());
            assertFalse(d.closed);assertFalse(p.closed);
            assertArrayEquals(original,png);assertArrayEquals(originalDescriptor,descriptor);
            Arrays.fill(descriptor,(byte)0);Arrays.fill(png,(byte)0);
            result.image.bytes()[0]=0;result.offer.sha256()[0]^=1;result.image.identity.sha256()[0]^=1;
            assertArrayEquals(original,result.image.bytes());assertArrayEquals(hash(original),result.offer.sha256());
            assertArrayEquals(originalDescriptor,ImageOfferCodec.encode(result.offer));
        }
    }

    @Test public void validButDifferentImageCannotSatisfyOffer() throws Exception {
        byte[] d=resource("/offer-h1.bin"),h2=resource("/image-transfer/h2.png");
        assertThrows(IOException.class,()->ReceivedImagePayload.read(new ByteArrayInputStream(d),new ByteArrayInputStream(h2)));
        ImageOffer first=ImageOfferCodec.decode(new ByteArrayInputStream(d));
        ImageOffer correctLengthWrongHash=new ImageOffer(first.associationId,first.deliveryId,first.metadata,
                first.sha256(),h2.length,64,64);
        assertThrows(IOException.class,()->read(correctLengthWrongHash,h2));
        ImageOffer wrongDimensions=new ImageOffer(first.associationId,first.deliveryId,first.metadata,hash(h2),h2.length,63,64);
        assertThrows(IOException.class,()->read(wrongDimensions,h2));
    }

    @Test public void rejectedDescriptorNeverReadsPngAndClosesNeitherInput() throws Exception {
        byte[] original=resource("/offer-h1.bin");
        for(int kind=0;kind<3;kind++) {
            byte[] bad=original.clone();
            if(kind==0)bad[0]^=1;
            if(kind==1){ByteBuffer.wrap(bad).putInt(8,2);crc(bad);}
            if(kind==2){bad[44]^=1;crc(bad);}
            OwnedStream descriptor=new OwnedStream(bad);
            InputStream png=new InputStream(){@Override public int read(){throw new AssertionError("Read PNG before valid descriptor");}};
            assertThrows(IOException.class,()->ReceivedImagePayload.read(descriptor,png));assertFalse(descriptor.closed);
        }
    }

    @Test public void matchingDigestDoesNotBypassPngCrcOrForbiddenMetadata() throws Exception {
        ImageOffer base=offer();byte[] original=resource("/image-transfer/h1.png"),bad=original.clone();bad[29]^=1;
        assertThrows(IOException.class,()->read(contentOffer(base,bad),bad));
        byte[] text="Comment\0SYNTHETIC-NOTE".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(original,0,33);
        DataOutputStream data=new DataOutputStream(out);byte[] type="tEXt".getBytes(StandardCharsets.US_ASCII);
        CRC32 c=new CRC32();c.update(type);c.update(text);data.writeInt(text.length);data.write(type);data.write(text);data.writeInt((int)c.getValue());
        out.write(original,33,original.length-33);byte[] metadataPng=out.toByteArray();
        assertThrows(IOException.class,()->read(contentOffer(base,metadataPng),metadataPng));
    }

    @Test public void partialTrailingAndFailedImageReadsNeverReturnCandidate() throws Exception {
        byte[] descriptor=resource("/offer-h1.bin"),png=resource("/image-transfer/h1.png");
        for(byte[] bad:new byte[][]{Arrays.copyOf(png,png.length-1),Arrays.copyOf(png,png.length+1),new byte[0]}) {
            OwnedStream source=new OwnedStream(bad);
            assertThrows(IOException.class,()->ReceivedImagePayload.read(new ByteArrayInputStream(descriptor),source));assertFalse(source.closed);
        }
        InputStream failed=new InputStream(){@Override public int read()throws IOException{throw new IOException("SYNTHETIC-PRIVATE-URI");}};
        IOException error=assertThrows(IOException.class,()->ReceivedImagePayload.read(new ByteArrayInputStream(descriptor),failed));
        assertFalse(error.getMessage().contains("PRIVATE"));assertNull(error.getCause());
    }

    @Test public void bothInputOwnersCanCloseAfterSuccessOrFailure() throws Exception {
        OwnedStream descriptor=new OwnedStream(resource("/offer-h1.bin")),png=new OwnedStream(resource("/image-transfer/h1.png"));
        ReceivedImagePayload.read(descriptor,png);assertFalse(descriptor.closed);assertFalse(png.closed);
        descriptor.close();png.close();assertTrue(descriptor.closed);assertTrue(png.closed);
        OwnedStream badDescriptor=new OwnedStream(new byte[0]),untouched=new OwnedStream(resource("/image-transfer/h1.png"));
        assertThrows(IOException.class,()->ReceivedImagePayload.read(badDescriptor,untouched));
        assertFalse(badDescriptor.closed);assertFalse(untouched.closed);assertEquals(0,untouched.offset);
    }

    @Test public void separateOffersForSameMetadataKeepSeparateIdentities() throws Exception {
        ReceivedImagePayload first=ReceivedImagePayload.read(new ByteArrayInputStream(resource("/offer-h1.bin")),new ByteArrayInputStream(resource("/image-transfer/h1.png")));
        ReceivedImagePayload second=ReceivedImagePayload.read(new ByteArrayInputStream(resource("/offer-h2.bin")),new ByteArrayInputStream(resource("/image-transfer/h2.png")));
        assertEquals(first.offer.metadata.uid,second.offer.metadata.uid);
        assertNotEquals(first.offer.associationId,second.offer.associationId);assertNotEquals(first.offer.deliveryId,second.offer.deliveryId);
        assertFalse(first.offer.sameBinding(second.offer));assertFalse(Arrays.equals(first.image.bytes(),second.image.bytes()));
        // No local store/attachment or choice is an input to this boundary.
        assertEquals("PIR 134",first.offer.metadata.displayId);assertEquals("PIR 134",second.offer.metadata.displayId);
    }

    @Test public void equalImageAndVisibleLabelDoNotMergeDifferentDeviceClaims() throws Exception {
        ImageOffer first=offer();ShareReview.Metadata m=first.metadata;
        TmmDraft other=new TmmDraft(UUID.randomUUID(),m.displayId,m.type,m.status,m.owner,m.createdAt,
                m.updatedAt,m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
        ShareReview.Metadata secondMetadata=new PublicationPreparationReview(other).prepare(other).metadata;
        ImageOffer second=new ImageOffer(UUID.randomUUID(),UUID.randomUUID(),secondMetadata,
                first.sha256(),first.pngLength,first.width,first.height);
        byte[] png=resource("/image-transfer/h1.png");
        ReceivedImagePayload a=read(first,png),b=read(second,png);
        assertArrayEquals(a.image.bytes(),b.image.bytes());
        assertEquals(a.offer.metadata.displayId,b.offer.metadata.displayId);
        assertNotEquals(a.offer.metadata.uid,b.offer.metadata.uid);assertFalse(a.offer.sameBinding(b.offer));
        assertNotEquals(a.offer.associationId,b.offer.associationId);
    }

    @Test public void allocationFailureUsesSafeReasonWithoutReadingImage() throws Exception {
        InputStream exhausted=new InputStream(){@Override public int read(){throw new OutOfMemoryError("synthetic");}};
        InputStream image=new InputStream(){@Override public int read(){throw new AssertionError("Unexpected image read");}};
        ImagePreparationException error=assertThrows(ImagePreparationException.class,()->ReceivedImagePayload.read(exhausted,image));
        assertEquals(ImagePreparationException.Reason.LOW_MEMORY,error.reason);
    }

    private static ReceivedImagePayload read(ImageOffer offer,byte[] png)throws Exception{return ReceivedImagePayload.read(new ByteArrayInputStream(ImageOfferCodec.encode(offer)),new ByteArrayInputStream(png));}
    private static ImageOffer offer()throws Exception{return ImageOfferCodec.decode(new ByteArrayInputStream(resource("/offer-h1.bin")));}
    private static ImageOffer contentOffer(ImageOffer base,byte[] bytes)throws Exception{return new ImageOffer(UUID.randomUUID(),UUID.randomUUID(),base.metadata,hash(bytes),bytes.length,64,64);}
    private static byte[] hash(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    private static void crc(byte[] bytes){CRC32 c=new CRC32();c.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)c.getValue());}
    private static byte[] resource(String name)throws Exception{try(InputStream in=ReceivedImagePayloadTest.class.getResourceAsStream(name)){assertNotNull(name,in);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
    private static class OwnedStream extends InputStream{
        final byte[] bytes;int offset;boolean closed;OwnedStream(byte[] bytes){this.bytes=bytes;}
        @Override public int read(byte[] b,int start,int length){if(offset==bytes.length)return -1;int n=Math.min(length,Math.min(7,bytes.length-offset));System.arraycopy(bytes,offset,b,start,n);offset+=n;return n;}
        @Override public int read(){return offset==bytes.length?-1:bytes[offset++]&255;}
        @Override public void close(){closed=true;}
    }
}
