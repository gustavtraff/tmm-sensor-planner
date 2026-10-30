package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;

public final class ImageOfferCodecTest {
    public static void main(String[] args) throws Exception {
        if (!ImageOfferCodecTest.class.desiredAssertionStatus()) throw new IllegalStateException("Enable assertions");
        byte[] golden = Files.readAllBytes(Paths.get("test-fixtures/synthetic/image-offer-proposal/offer-h1.bin"));
        ImageOffer offer = ImageOfferCodec.decode(new ByteArrayInputStream(golden));
        immutableAndInvalid(offer, golden);
        bindingSemantics(offer);
        streams(offer, golden);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i=0;i<32;i++) jobs.add(() -> Arrays.equals(golden,ImageOfferCodec.encode(ImageOfferCodec.decode(new ByteArrayInputStream(golden)))));
            for (Future<Boolean> result : pool.invokeAll(jobs)) assert result.get();
        } finally { pool.shutdownNow(); }
        System.out.println("Image descriptor codec passed (immutable claims, exact goldens, strict/bounded streams, safe errors, metadata compatibility and concurrency; no image verification or storage).");
    }

    private static void immutableAndInvalid(ImageOffer original, byte[] golden) throws Exception {
        byte[] digest=original.sha256(), expected=digest.clone();
        ImageOffer offer=copy(original,original.metadata,digest,original.pngLength,64,64);
        digest[0]^=1;offer.sha256()[0]^=1;
        assert Arrays.equals(expected,offer.sha256());
        byte[] wire=ImageOfferCodec.encode(offer);wire[0]=0;
        assert Arrays.equals(golden,ImageOfferCodec.encode(offer));
        byte[] input=golden.clone();ImageOffer decoded=ImageOfferCodec.decode(new ByteArrayInputStream(input));
        Arrays.fill(input,(byte)0);assert Arrays.equals(golden,ImageOfferCodec.encode(decoded));
        for (int[] shape:new int[][]{{0,1,1},{16777217,1,1},{1,0,1},{1,1921,1},{1,1,0},{1,1,Integer.MAX_VALUE}})
            invalid(() -> copy(original,original.metadata,original.sha256(),shape[0],shape[1],shape[2]));
        invalid(() -> copy(original,original.metadata,new byte[31],1,1,1));
        invalid(() -> copy(original,original.metadata,new byte[33],1,1,1));
        invalid(() -> copy(original,original.metadata,null,1,1,1));
        invalid(() -> new ImageOffer(null,original.deliveryId,original.metadata,expected,1,1,1));
        invalid(() -> new ImageOffer(new UUID(0,0),original.deliveryId,original.metadata,expected,1,1,1));
        invalid(() -> new ImageOffer(original.associationId,new UUID(0,0),original.metadata,expected,1,1,1));
        invalid(() -> new ImageOffer(original.associationId,null,original.metadata,expected,1,1,1));
        invalid(() -> copy(original,null,expected,1,1,1));
        TmmDraft carrier=draft(original.metadata,original.metadata.uid,original.metadata.displayId,
                original.metadata.createdAt,original.metadata.position,null,42);
        TmmDraft withImage=carrier.edit(carrier.displayId,carrier.type,carrier.status,carrier.owner,
                carrier.deployedAt,carrier.comment,carrier.position,carrier.bearingDeg,"SYNTHETIC-PRIVATE-HANDLE",carrier.updatedAt);
        ShareReview.Metadata m=metadata(withImage);
        assert Arrays.equals(golden,ImageOfferCodec.encode(copy(original,m,expected,original.pngLength,64,64)));
        String encoded=new String(ImageOfferCodec.encode(offer),StandardCharsets.US_ASCII);
        assert !encoded.contains("SYNTHETIC-PRIVATE-HANDLE") && !encoded.contains("imageRef");
        TmmDraft invalidUtf=draft(original.metadata,original.metadata.uid,"\ud800",original.metadata.createdAt,original.metadata.position,null,1);
        invalid(() -> copy(original,metadata(invalidUtf),expected,1,1,1));
        char[] huge=new char[65537];Arrays.fill(huge,'X');
        TmmDraft oversized=draft(original.metadata,original.metadata.uid,new String(huge),original.metadata.createdAt,original.metadata.position,null,1);
        invalid(() -> copy(original,metadata(oversized),expected,1,1,1));
        // Device UUID follows existing metadata rules; only association/delivery UUIDs forbid nil.
        ImageOffer nilDevice=copy(original,metadata(draft(original.metadata,new UUID(0,0),original.metadata.displayId,
                original.metadata.createdAt,original.metadata.position,null,1)),expected,1,1,1);
        assert ImageOfferCodec.decode(new ByteArrayInputStream(ImageOfferCodec.encode(nilDevice))).metadata.uid.equals(new UUID(0,0));
    }

    private static void bindingSemantics(ImageOffer original) throws Exception {
        ImageOffer differentIds=new ImageOffer(UUID.randomUUID(),UUID.randomUUID(),original.metadata,original.sha256(),original.pngLength,64,64);
        assert original.sameBinding(differentIds) && !original.sameBinding(null);
        byte[] changed=original.sha256();changed[0]^=1;
        assert !original.sameBinding(copy(original,original.metadata,changed,original.pngLength,64,64));
        assert !original.sameBinding(copy(original,original.metadata,original.sha256(),original.pngLength+1,64,64));
        assert !original.sameBinding(copy(original,original.metadata,original.sha256(),original.pngLength,63,64));
        assert !original.sameBinding(copy(original,original.metadata,original.sha256(),original.pngLength,64,63));
        TmmDraft other=draft(original.metadata,UUID.randomUUID(),original.metadata.displayId,original.metadata.createdAt,original.metadata.position,null,1);
        assert !original.sameBinding(copy(original,metadata(other),original.sha256(),original.pngLength,64,64));
        TmmDraft differentTime=draft(original.metadata,original.metadata.uid,original.metadata.displayId,
                original.metadata.createdAt.plusNanos(1),original.metadata.position,null,1);
        assert !original.sameBinding(copy(original,metadata(differentTime),original.sha256(),original.pngLength,64,64));
        TmmDraft zero=draft(original.metadata,original.metadata.uid,original.metadata.displayId,original.metadata.createdAt,new Position(0,0),null,1);
        TmmDraft negative=draft(original.metadata,original.metadata.uid,original.metadata.displayId,original.metadata.createdAt,new Position(-0.0,-0.0),null,99);
        ImageOffer a=copy(original,metadata(zero),original.sha256(),1,1,1),b=copy(original,metadata(negative),original.sha256(),1,1,1);
        assert a.sameBinding(b) && !Arrays.equals(ImageOfferCodec.encode(a),ImageOfferCodec.encode(b));
        String xml=new String(Arrays.copyOfRange(ImageOfferCodec.encode(original),108,ImageOfferCodec.encode(original).length-4),StandardCharsets.UTF_8);
        byte[] equivalent=frame(original,xml.replace("<tmmPublication ","<tmmPublication\n ").getBytes(StandardCharsets.UTF_8));
        ImageOffer decoded=ImageOfferCodec.decode(new ByteArrayInputStream(equivalent));
        assert original.sameBinding(decoded) && Arrays.equals(ImageOfferCodec.encode(original),ImageOfferCodec.encode(decoded));
    }

    private static void streams(ImageOffer offer, byte[] golden) throws Exception {
        for(int fragment:new int[]{1,7,8192}) {
            Observed source=new Observed(golden,fragment,true);
            assert offer.sameBinding(ImageOfferCodec.decode(source));assert source.eof && !source.closed;
            source.close();assert source.closed && source.maxRequest<=8192;
        }
        byte[] xml=Arrays.copyOfRange(golden,108,golden.length-4), maximum=Arrays.copyOf(xml,PublicationCodec.MAX_BYTES);
        Arrays.fill(maximum,xml.length,maximum.length,(byte)' ');byte[] maxFrame=frame(offer,maximum);
        Observed max=new Observed(maxFrame,8192,false);
        assert offer.sameBinding(ImageOfferCodec.decode(max)) && max.offset==ImageOfferCodec.MAX_BYTES && max.eof;
        InputStream endless=new InputStream(){int count;public int read(){count++;if(count>109)throw new AssertionError("Unbounded invalid-header read");return 0;}};
        rejected(endless);
        Observed trailing=new Observed(Arrays.copyOf(maxFrame,maxFrame.length+100),8192,false);
        rejected(trailing);assert trailing.offset==ImageOfferCodec.MAX_BYTES+1 && !trailing.closed;
        for (IOException error:new IOException[]{new IOException("SYNTHETIC-PRIVATE-PROVIDER-PATH"),new InterruptedIOException("SYNTHETIC-CANCEL")}) {
            Observed source=new Observed(golden,7,false){@Override public int read(byte[] b,int o,int n)throws IOException{if(offset>=112)throw error;return super.read(b,o,n);}};
            IOException failed=rejected(source);assert !failed.getMessage().contains("SYNTHETIC") && failed.getCause()==null && !source.closed;
        }
        Observed eofFailure=new Observed(golden,8192,false){@Override public int read()throws IOException{if(offset==bytes.length)throw new IOException("SYNTHETIC-EOF");return super.read();}};
        rejected(eofFailure);assert !eofFailure.closed;
        InputStream zeroEof=new InputStream(){public int read(byte[] b,int o,int n){return 0;}public int read(){return -1;}};
        rejected(zeroEof);rejected(new ByteArrayInputStream(new byte[0]));
    }

    private static ImageOffer copy(ImageOffer from,ShareReview.Metadata m,byte[] hash,int length,int w,int h){return new ImageOffer(from.associationId,from.deliveryId,m,hash,length,w,h);}
    private static ShareReview.Metadata metadata(TmmDraft d){return new PublicationPreparationReview(d).prepare(d).metadata;}
    private static TmmDraft draft(ShareReview.Metadata m,UUID uid,String id,Instant created,Position p,String image,long revision){return new TmmDraft(uid,id,m.type,m.status,m.owner,created,m.updatedAt,m.deployedAt,m.comment,p,m.bearingDeg,image,revision);}
    private static byte[] frame(ImageOffer offer,byte[] xml){byte[] bytes=Arrays.copyOf(ImageOfferCodec.encode(offer),112+xml.length);ByteBuffer.wrap(bytes).putInt(104,xml.length);System.arraycopy(xml,0,bytes,108,xml.length);CRC32 c=new CRC32();c.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)c.getValue());return bytes;}
    private static IOException rejected(InputStream input)throws Exception{try{ImageOfferCodec.decode(input);throw new AssertionError("Accepted invalid stream");}catch(IOException expected){return expected;}}
    private static void invalid(Runnable action){try{action.run();throw new AssertionError("Accepted invalid descriptor values");}catch(IllegalArgumentException expected){}}
    private static class Observed extends InputStream{
        final byte[] bytes;final int fragment;final boolean zeros;int offset,maxRequest;boolean eof,closed,zeroNext=true;
        Observed(byte[] b,int f,boolean z){bytes=b;fragment=f;zeros=z;}
        @Override public int read(byte[] b,int o,int n)throws IOException{maxRequest=Math.max(maxRequest,n);if(zeros&&zeroNext){zeroNext=false;return 0;}zeroNext=true;if(offset==bytes.length){eof=true;return -1;}int count=Math.min(n,Math.min(fragment,bytes.length-offset));System.arraycopy(bytes,offset,b,o,count);offset+=count;return count;}
        @Override public int read()throws IOException{if(offset==bytes.length){eof=true;return -1;}return bytes[offset++]&255;}
        @Override public int available(){throw new AssertionError("Do not trust available");}
        @Override public long skip(long n){throw new AssertionError("Do not skip bytes");}
        @Override public void mark(int n){throw new AssertionError("No mark dependency");}
        @Override public void reset(){throw new AssertionError("No reset dependency");}
        @Override public void close(){closed=true;}
    }
}
