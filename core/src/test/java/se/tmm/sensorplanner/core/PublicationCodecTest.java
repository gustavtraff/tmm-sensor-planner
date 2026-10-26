package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class PublicationCodecTest {
    public static void main(String[] args) throws Exception {
        if (!PublicationCodecTest.class.desiredAssertionStatus()) throw new IllegalStateException("Enable assertions");
        byte[] fixture = Files.readAllBytes(Paths.get("test-fixtures/synthetic/publication/pir-v1.xml"));
        streamOwnershipAndBounds(fixture);
        failedInputIsSanitized(fixture);
        valuesAndPreservation();
        concurrentCalls(fixture);
        System.out.println("Publication codec passed (bounded streams, safe errors, exact values, preservation and concurrency).");
    }

    private static void streamOwnershipAndBounds(byte[] fixture) throws Exception {
        TrackingInput input = new TrackingInput(fixture);
        PublicationCodec.decode(input);
        assert input.readBytes == fixture.length && !input.closed;
        input = new TrackingInput(new byte[]{0});
        rejected(input); assert !input.closed;

        byte[] exact = Arrays.copyOf(fixture, PublicationCodec.MAX_BYTES);
        Arrays.fill(exact, fixture.length, exact.length, (byte) ' ');
        PublicationCodec.decode(new ByteArrayInputStream(exact));
        class Growing extends InputStream {
            int count;
            boolean closed;
            @Override public int read() { int i = count++; return i < fixture.length ? fixture[i] & 255 : ' '; }
            @Override public void close() { closed = true; }
        }
        Growing grows = new Growing(); rejected(grows);
        assert grows.count == PublicationCodec.MAX_BYTES + 1 && !grows.closed;
        InputStream zeroRead = new ByteArrayInputStream(fixture) {
            boolean first = true;
            @Override public int read(byte[] b, int off, int len) {
                if (first) { first = false; return 0; }
                return super.read(b, off, Math.min(len, 3));
            }
        };
        assert PublicationCodec.decode(zeroRead).displayId.equals("PIR 134");
    }

    private static void failedInputIsSanitized(byte[] fixture) throws Exception {
        InputStream failing = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("SYNTHETIC-PRIVATE-PATH"); }
        };
        rejected(failing);
        rejected(null);
        String property = "javax.xml.parsers.SAXParserFactory", previous = System.getProperty(property);
        try {
            System.setProperty(property, "SYNTHETIC-UNAVAILABLE-PRIVATE-PROVIDER");
            rejected(new ByteArrayInputStream(fixture));
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
        }
        String xml = new String(fixture, StandardCharsets.UTF_8);
        String root = xml.substring(xml.indexOf("<tmmPublication"));
        rejected(new ByteArrayInputStream(("<!DOCTYPE tmmPublication [<!ENTITY x SYSTEM 'file:///not-opened'>]>" + root)
                .getBytes(StandardCharsets.UTF_8)));
        // A declaration need not exist; mixed quote styles and standalone are valid XML 1.0 syntax.
        assert PublicationCodec.decode(new ByteArrayInputStream(root.getBytes(StandardCharsets.UTF_8))).type == DeviceType.PIR;
        String declaration = "<?xml version=\"1.0\" encoding='UTF-8' standalone=\"yes\"?>\n";
        assert PublicationCodec.decode(new ByteArrayInputStream((declaration + root).getBytes(StandardCharsets.UTF_8)))
                .type == DeviceType.PIR;
        rejected(new ByteArrayInputStream(xml.replace(" />", "><![CDATA[ ]]></tmmPublication>").getBytes(StandardCharsets.UTF_8)));
    }

    private static void valuesAndPreservation() throws Exception {
        Path dir = Files.createTempDirectory("tmm-publication-codec-");
        try {
            DraftStore store = new DraftStore(dir);
            for (DeviceType type : DeviceType.values()) for (DeviceStatus status : DeviceStatus.values()) {
                TmmDraft original = new TmmDraft(new UUID(0, 1), "PIR 134", type, status, "Synthetic role 🌲",
                        Instant.MIN, Instant.MAX, Instant.EPOCH.plusNanos(42), "Synthetic\n\t\u0000<&>\r\n🌲",
                        new Position(-0.0, 180), type.supportsBearing() ? Double.valueOf(-0.0) : null,
                        "SYNTHETIC-IMAGE-HANDLE-NOT-SHARED", 1);
                store.save(original);
                byte[] before = Files.readAllBytes(dir.resolve("drafts.bin"));
                PublicationPreparationReview.Prepared prepared = new PublicationPreparationReview(original).prepare(original);
                Map<String, String> fields = PublicationCodec.fields(prepared);
                assert fields.size() == 15 && fields.get("deviceUid").equals(original.uid.toString());
                assert fields.get("latitude").equals("-0.0") && fields.get("imageIncluded").equals("false");
                try { fields.put("revision", "1"); throw new AssertionError("Mutable projection"); }
                catch (UnsupportedOperationException expected) { }
                byte[] encoded = PublicationCodec.encode(prepared);
                assert Arrays.equals(encoded, PublicationCodec.encode(prepared));
                String xml = new String(encoded, StandardCharsets.UTF_8);
                assert !xml.contains(original.imageRef) && !xml.contains("imageRef") && !xml.contains("revision=");
                assert !xml.contains(Base64.getEncoder().encodeToString(original.imageRef.getBytes(StandardCharsets.UTF_8)));
                ShareReview.Metadata m = PublicationCodec.decode(new ByteArrayInputStream(encoded));
                TmmDraft carrier = new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt,
                        m.updatedAt, m.deployedAt, m.comment, m.position, m.bearingDeg, null, 1);
                assert ImportReview.assess(Collections.singletonList(original), carrier).kind == ImportReview.Kind.SAME_METADATA;
                assert Double.doubleToRawLongBits(m.position.latitude) == Long.MIN_VALUE;
                assert m.bearingDeg == null || Double.doubleToRawLongBits(m.bearingDeg) == Long.MIN_VALUE;
                assert prepared.imageStatus == ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED;
                assert Arrays.equals(before, Files.readAllBytes(dir.resolve("drafts.bin")));
                assert new DraftStore(dir).load().get(0).imageRef.equals(original.imageRef);
                store.delete(original.uid, original.revision);
            }
            for (String owner : new String[]{"bad-surrogate-\ud800", String.join("", Collections.nCopies(65537, "x"))}) {
                TmmDraft invalid = TmmDraft.create("Synthetic", DeviceType.MINI, owner, new Position(0, 0), Instant.EPOCH);
                try {
                    PublicationCodec.encode(new PublicationPreparationReview(invalid).prepare(invalid));
                    throw new AssertionError("Unpublishable text accepted");
                } catch (IllegalArgumentException expected) {
                    assert expected.getMessage().equals("Unsupported or invalid publication metadata") && expected.getCause() == null;
                }
                assert invalid.owner.equals(owner);
            }
        } finally {
            Files.deleteIfExists(dir.resolve("drafts.bin")); Files.deleteIfExists(dir.resolve("drafts.lock")); Files.delete(dir);
        }
    }

    private static void concurrentCalls(byte[] fixture) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> PublicationCodec.decode(new ByteArrayInputStream(fixture)).uid));
            for (Future<UUID> result : results) assert result.get(5, TimeUnit.SECONDS)
                    .equals(UUID.fromString("00000000-0000-4000-8014-000000000003"));
        } finally { pool.shutdownNow(); assert pool.awaitTermination(5, TimeUnit.SECONDS); }
    }

    private static void rejected(InputStream input) throws Exception {
        try { PublicationCodec.decode(input); throw new AssertionError("Invalid input accepted"); }
        catch (IOException expected) {
            assert expected.getMessage().equals("Unsupported or invalid publication metadata") && expected.getCause() == null;
        }
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        int readBytes;
        boolean closed;
        TrackingInput(byte[] bytes) { super(bytes); }
        @Override public int read(byte[] b, int off, int len) {
            int n = super.read(b, off, Math.min(len, 3)); if (n > 0) readBytes += n; return n;
        }
        @Override public void close() { closed = true; }
    }
}
