package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;

/** Test-only oracle for a proposed envelope, not a production image codec or source verifier. */
public final class ImageOfferFormatProposalTest {
    private static final byte[] MAGIC = {'T','M','M','I','O','F','R',0};
    private static final int MAX_METADATA = 262144, MAX_FRAME = 112 + MAX_METADATA;
    private static final UUID NIL = new UUID(0, 0);

    public static void main(String[] args) throws Exception {
        if (!ImageOfferFormatProposalTest.class.desiredAssertionStatus()) throw new IllegalStateException("Enable assertions");
        String[] hashes = {"7d6c72fed2cf76a5405bec599ec5fea220d03de880999f7051850c9a6918601c",
                "0c88b527eba372175139150359411ee14e9fa5beba275c20e8d5133964ea9908"};
        byte[] first = null;
        for (int i = 1; i <= 2; i++) {
            byte[] bytes = Files.readAllBytes(Paths.get("test-fixtures/synthetic/image-offer-proposal/offer-h" + i + ".bin"));
            assert bytes.length == 669 && hex(hash(bytes)).equals(hashes[i - 1]);
            Offer offer = inspect(bytes);
            ImageOffer actual = ImageOfferCodec.decode(new ByteArrayInputStream(bytes));
            assert actual.associationId.equals(offer.association) && actual.deliveryId.equals(offer.delivery);
            assert actual.pngLength == offer.length && actual.width == offer.width && actual.height == offer.height;
            assert Arrays.equals(actual.sha256(), offer.digest);
            assert InboxCodec.sameMetadata(carrier(actual.metadata), offer.metadata);
            assert Arrays.equals(ImageOfferCodec.encode(actual), bytes) : "Canonical encoder changed independent golden";
            assert offer.association.equals(UUID.fromString("00000000-0000-4000-8026-00000000010" + i));
            assert offer.delivery.equals(UUID.fromString("00000000-0000-4000-8026-00000000020" + i));
            assert offer.metadata.uid.equals(UUID.fromString("00000000-0000-4000-8014-000000000003"));
            assert offer.metadata.displayId.equals("PIR 134") && offer.metadata.type == DeviceType.PIR;
            assert offer.metadata.status == DeviceStatus.ACTIVE;
            assert offer.metadata.createdAt.getNano() == 123456789 && offer.metadata.deployedAt.getNano() == 42;
            assert offer.metadata.imageRef == null && offer.metadata.revision == 1;
            byte[] xml = Files.readAllBytes(Paths.get("test-fixtures/synthetic/publication/pir-v1.xml"));
            assert Arrays.equals(xml, Arrays.copyOfRange(bytes, 108, bytes.length - 4));
            byte[] png = Files.readAllBytes(Paths.get("images/src/test/resources/image-transfer/h" + i + ".png"));
            assert contentMatches(offer, png) && offer.width == 64 && offer.height == 64;
            if (i == 1) first = bytes;
        }
        invalidFrames(first);
        semanticChecks(first);
        System.out.println("Image-offer proposal fixtures passed (independent pinned bytes, strict envelope, existing metadata and content measurements only; production codec cross-check; no association).");
    }

    private static void invalidFrames(byte[] valid) throws Exception {
        for (int length = 0; length < valid.length; length++) rejected(Arrays.copyOf(valid, length));
        rejected(Arrays.copyOf(valid, valid.length + 1));
        byte[] corrupt = valid.clone(); corrupt[120] ^= 1; rejected(corrupt);
        byte[] magic = valid.clone(); magic[0] ^= 1; rejected(crc(magic));
        for (int[] field : new int[][]{{8,0},{8,2},{8,0x01000000},{60,0},{60,16777217},
                {64,0},{64,1921},{68,0},{68,1921},{104,0},{104,-1},{104,262145}}) {
            byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(field[0], field[1]); rejected(crc(bad));
        }
        for (int offset : new int[]{12,28}) {
            byte[] nil = valid.clone(); Arrays.fill(nil, offset, offset + 16, (byte)0); rejected(crc(nil));
        }
        byte[] wrongDevice = valid.clone(); wrongDevice[44] ^= 1; rejected(crc(wrongDevice));
        String xml = new String(Arrays.copyOfRange(valid,108,valid.length-4),StandardCharsets.UTF_8);
        rejected(withMetadata(valid, xml.replace("imageIncluded=\"false\"", "imageIncluded=\"true\"").getBytes(StandardCharsets.UTF_8)));
        rejected(withMetadata(valid, ("<!DOCTYPE tmmPublication SYSTEM 'file:///SYNTHETIC-NOT-READ'>"+xml).getBytes(StandardCharsets.UTF_8)));
        rejected(withMetadata(valid, new byte[]{(byte)0xff}));
        byte[] maximum = Arrays.copyOf(xml.getBytes(StandardCharsets.UTF_8), MAX_METADATA);
        Arrays.fill(maximum, xml.getBytes(StandardCharsets.UTF_8).length, maximum.length, (byte)' ');
        byte[] frame = withMetadata(valid, maximum);
        assert frame.length == MAX_FRAME && InboxCodec.sameMetadata(inspect(frame).metadata, inspect(valid).metadata);
        assert ImageOfferCodec.decode(new ByteArrayInputStream(frame)).sameBinding(ImageOfferCodec.decode(new ByteArrayInputStream(valid)));
        rejected(Arrays.copyOf(frame, frame.length + 1));
    }

    private static void semanticChecks(byte[] valid) throws Exception {
        Offer original = inspect(valid);
        String xml = new String(Arrays.copyOfRange(valid,108,valid.length-4),StandardCharsets.UTF_8);
        Offer same = inspect(withMetadata(valid, xml.replace("<tmmPublication ", "<tmmPublication\n ").getBytes(StandardCharsets.UTF_8)));
        assert sameBinding(original, same) : "Metadata syntax is not metadata identity";
        Offer changed = inspect(withMetadata(valid, xml.replace("status=\"ACTIVE\"", "status=\"PLANNED\"").getBytes(StandardCharsets.UTF_8)));
        assert original.association.equals(changed.association) && !sameBinding(original, changed);
        byte[] digestClaim = valid.clone(); Arrays.fill(digestClaim,72,104,(byte)0);
        Offer unverified = inspect(crc(digestClaim));
        byte[] png = Files.readAllBytes(Paths.get("images/src/test/resources/image-transfer/h1.png"));
        assert !contentMatches(unverified, png) : "Valid envelope is not verified image content";
        byte[] anotherDelivery = valid.clone(); anotherDelivery[43] ^= 1;
        Offer resent = inspect(crc(anotherDelivery));
        assert original.association.equals(resent.association) && !original.delivery.equals(resent.delivery);
        assert sameBinding(original, resent) : "Delivery identity is separate from immutable offer binding";
    }

    private static Offer inspect(byte[] bytes) throws IOException {
        try {
            require(bytes.length >= 113 && bytes.length <= MAX_FRAME);
            ByteBuffer b = ByteBuffer.wrap(bytes); byte[] magic = new byte[8]; b.get(magic);
            require(Arrays.equals(magic,MAGIC) && b.getInt() == 1);
            UUID association = uuid(b), delivery = uuid(b), device = uuid(b);
            require(!association.equals(NIL) && !delivery.equals(NIL));
            int length = b.getInt(), width = b.getInt(), height = b.getInt();
            require(length >= 1 && length <= 16777216 && width >= 1 && width <= 1920 && height >= 1 && height <= 1920);
            byte[] digest = new byte[32]; b.get(digest); int size = b.getInt();
            require(size >= 1 && size <= MAX_METADATA && bytes.length == 112 + size);
            CRC32 crc = new CRC32(); crc.update(bytes,0,bytes.length-4);
            require((int)crc.getValue() == b.getInt(bytes.length-4));
            ShareReview.Metadata m = PublicationCodec.decode(new ByteArrayInputStream(bytes,108,size));
            require(device.equals(m.uid));
            TmmDraft metadata = new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt,
                    m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
            return new Offer(association,delivery,metadata,length,width,height,digest);
        } catch (RuntimeException invalid) { throw new IOException("Rejected proposed image offer"); }
    }
    private static TmmDraft carrier(ShareReview.Metadata m) {
        return new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt,
                m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
    }
    private static UUID uuid(ByteBuffer b) { return new UUID(b.getLong(),b.getLong()); }
    private static void require(boolean condition) throws IOException { if (!condition) throw new IOException("Rejected proposed image offer"); }
    private static void rejected(byte[] bytes) throws Exception {
        try { inspect(bytes); throw new AssertionError("Accepted invalid proposed envelope"); } catch (IOException expected) { }
        try { ImageOfferCodec.decode(new ByteArrayInputStream(bytes)); throw new AssertionError("Production codec accepted invalid envelope"); } catch (IOException expected) { }
    }
    private static byte[] withMetadata(byte[] original, byte[] metadata) {
        byte[] result = Arrays.copyOf(original,112+metadata.length);
        ByteBuffer.wrap(result).putInt(104,metadata.length); System.arraycopy(metadata,0,result,108,metadata.length); return crc(result);
    }
    private static byte[] crc(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes,0,bytes.length-4);
        ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue()); return bytes;
    }
    private static boolean sameBinding(Offer a, Offer b) {
        return InboxCodec.sameMetadata(a.metadata,b.metadata) && a.length==b.length && a.width==b.width
                && a.height==b.height && Arrays.equals(a.digest,b.digest);
    }
    private static boolean contentMatches(Offer offer, byte[] png) throws Exception {
        return offer.length==png.length && Arrays.equals(offer.digest,hash(png))
                && offer.width==ByteBuffer.wrap(png).getInt(16) && offer.height==ByteBuffer.wrap(png).getInt(20);
    }
    private static byte[] hash(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private static String hex(byte[] bytes) { StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString(); }
    private static final class Offer {
        final UUID association,delivery;final TmmDraft metadata;final int length,width,height;final byte[] digest;
        Offer(UUID a,UUID d,TmmDraft m,int l,int w,int h,byte[] s){association=a;delivery=d;metadata=m;length=l;width=w;height=h;digest=s;}
    }
}
