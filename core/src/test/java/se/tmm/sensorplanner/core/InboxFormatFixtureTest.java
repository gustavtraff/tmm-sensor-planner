package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Test-only inspector of a proposed local format; no production store, parser API or writer. */
public final class InboxFormatFixtureTest {
    private static final int LIMIT = 1000;
    private static final UUID DEVICE = UUID.fromString("00000000-0000-4000-8015-000000000003");

    public static void main(String[] args) throws Exception {
        Path path = Paths.get("test-fixtures/synthetic/inbox/candidate-v1.base64");
        byte[] source = Files.readAllBytes(path);
        byte[] bytes = Base64.getDecoder().decode(new String(source, StandardCharsets.US_ASCII).trim());
        assert hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(
                "b836b42f3fc195046817fb4746faecbc9f9c130e486c4db80505bffdd1f4e513");
        ByteBuffer header = ByteBuffer.wrap(bytes);
        assert new UUID(header.getLong(8), header.getLong(16)).equals(
                UUID.fromString("00000000-0000-4000-8015-000000000001"));
        List<Receipt> receipts = inspect(bytes);
        assert receipts.size() == 3;
        for (int i = 0; i < 3; i++) {
            Receipt receipt = receipts.get(i);
            assert receipt.id.equals(UUID.fromString(String.format(
                    Locale.ROOT, "00000000-0000-4000-8015-%012d", 10 + i)));
            assert receipt.context.equals("SYNTHETIC-CONTEXT-" + (i == 2 ? "B" : "A"));
            assert receipt.received.equals(Instant.ofEpochSecond(110 + i, 10 + i));
        }
        TmmDraft base = expected("PLANNED", "Synthetic åäö 🌲", 10, 456, -0.0, 1.25, -0.0, null);
        assert equal(receipts.get(0).metadata, base);
        assert Double.doubleToRawLongBits(receipts.get(0).metadata.position.latitude) == Long.MIN_VALUE;
        assert Double.doubleToRawLongBits(receipts.get(0).metadata.bearingDeg) == Long.MIN_VALUE;
        assert equal(receipts.get(1).metadata, expected("DEPLOYED", "Remote variant", 20, 987,
                0.25, 1.5, 90.0, Instant.ofEpochSecond(15, 42)));
        assert equal(receipts.get(2).metadata, base);
        assert Double.doubleToRawLongBits(receipts.get(2).metadata.position.latitude) == 0;
        List<Observation> local = receipts.get(0).observations;
        assert local.size() == 2 && local.get(0).revision == 7 && local.get(1).revision == 8;
        assert equal(local.get(0).metadata, expected("ACTIVE", "Local before review", 12, 456,
                0.0, 1.25, 45.25, Instant.ofEpochSecond(5, 6)));
        assert equal(local.get(1).metadata, expected("CANCELLED", "Local concurrent edit", 13, 789,
                0.0, 1.25, null, null));
        for (int i : new int[]{1, 2}) {
            assert receipts.get(i).observations.size() == 1;
            assert receipts.get(i).observations.get(0).metadata == null;
        }
        assert ImportReview.assess(Collections.singletonList(local.get(0).metadata), base).kind
                == ImportReview.Kind.REVIEW_REQUIRED;
        assert ImportReview.assess(Collections.emptyList(), receipts.get(1).metadata).kind
                == ImportReview.Kind.NEW_UID;
        byte[] empty = Arrays.copyOf(bytes, 28);
        ByteBuffer.wrap(empty).putInt(24, 0);
        assert inspect(seal(empty)).isEmpty();
        malformed(bytes);
        assert Arrays.equals(source, Files.readAllBytes(path));
        System.out.println("Proposed inbox fixture passed (independent bytes, variants, equality and malformed inputs; no storage implementation).");
    }

    private static void malformed(byte[] bytes) throws Exception {
        // Fixed fixture offsets are documented with the independent field values in its README.
        byte[] bad = bytes.clone(); bad[100] ^= 1; rejected(bad);
        rejected(Arrays.copyOf(bytes, bytes.length - 1));
        rejected(seal(Arrays.copyOf(bytes, bytes.length - 33)));
        byte[] trailing = Arrays.copyOf(bytes, bytes.length - 31);
        trailing[trailing.length - 1] = 0; rejected(seal(trailing));
        for (int[] change : new int[][]{{0, 0}, {4, 2}, {24, -1}, {24, 1001},
                {44, -1}, {44, 65537}, {75, 1000000000}, {221, 0}, {221, 1000}}) {
            bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(change[0], change[1]); rejected(reseal(bad));
        }
        for (int[] change : new int[][]{{48, 255}, {48, 32}, {121, 63}, {170, 2}}) {
            bad = bytes.clone(); bad[change[0]] = (byte) change[1]; rejected(reseal(bad));
        }
        bad = bytes.clone(); ByteBuffer.wrap(bad).putLong(226, 0); rejected(reseal(bad));
        bad = bytes.clone(); ByteBuffer.wrap(bad).putLong(67, Long.MAX_VALUE); rejected(reseal(bad));
        bad = bytes.clone(); ByteBuffer.wrap(bad).putDouble(196, Double.NaN); rejected(reseal(bad));
        bad = bytes.clone(); bad[249] ^= 1; rejected(reseal(bad));
        bad = bytes.clone(); System.arraycopy(bytes, 28, bad, 530, 16); rejected(reseal(bad));
        // +0/-0 serialize differently but are equal metadata, so context B -> A duplicates receipt 10.
        bad = bytes.clone(); bad[772] = 'A'; rejected(reseal(bad));
    }

    private static List<Receipt> inspect(byte[] bytes) throws Exception {
        if (bytes.length < 60 || bytes.length > 16 * 1024 * 1024) throw invalid();
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload),
                Arrays.copyOfRange(bytes, payload.length, bytes.length))) throw invalid();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != 0x544d4d49 || in.readInt() != 1) throw invalid();
            uuid(in); // Inbox identity is opaque; the golden fixture value is checked above.
            int count = count(in, 0), total = count;
            List<Receipt> receipts = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Receipt r = new Receipt();
                r.id = uuid(in); if (!ids.add(r.id)) throw invalid();
                r.context = required(in); r.received = instant(in); r.metadata = metadata(in);
                for (Receipt old : receipts) {
                    if (old.context.equals(r.context) && equal(old.metadata, r.metadata)) throw invalid();
                }
                int observations = count(in, 1); total += observations;
                if (total > LIMIT) throw invalid();
                for (int j = 0; j < observations; j++) {
                    Observation o = new Observation();
                    if (flag(in)) {
                        o.revision = in.readLong(); if (o.revision < 1) throw invalid();
                        o.metadata = metadata(in); if (!o.metadata.uid.equals(r.metadata.uid)) throw invalid();
                    }
                    for (Observation old : r.observations) {
                        if (old.metadata == null ? o.metadata == null
                                : o.metadata != null && old.revision == o.revision && equal(old.metadata, o.metadata)) throw invalid();
                    }
                    r.observations.add(o);
                }
                receipts.add(r);
            }
            if (in.available() != 0) throw invalid();
            return receipts;
        } catch (IllegalArgumentException | java.time.DateTimeException e) { throw invalid(); }
    }

    private static TmmDraft metadata(DataInputStream in) throws IOException {
        UUID uid = uuid(in); String id = required(in);
        DeviceType type = DeviceType.valueOf(required(in)); DeviceStatus status = DeviceStatus.valueOf(required(in));
        String owner = required(in); Instant created = instant(in), updated = instant(in);
        Instant deployed = flag(in) ? instant(in) : null; String comment = text(in);
        Position position = new Position(in.readDouble(), in.readDouble());
        Double bearing = flag(in) ? in.readDouble() : null;
        return new TmmDraft(uid, id, type, status, owner, created, updated, deployed, comment, position, bearing, null, 1);
    }
    private static int count(DataInputStream in, int min) throws IOException {
        int value = in.readInt(); if (value < min || value > LIMIT) throw invalid(); return value;
    }
    private static String text(DataInputStream in) throws IOException {
        int length = in.readInt(); if (length < 0 || length > 65536 || length > in.available()) throw invalid();
        byte[] value = new byte[length]; in.readFully(value);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
    }
    private static String required(DataInputStream in) throws IOException {
        String value = text(in); if (value.isEmpty() || !value.equals(value.trim())) throw invalid(); return value;
    }
    private static Instant instant(DataInputStream in) throws IOException {
        long seconds = in.readLong(); int nanos = in.readInt(); if (nanos < 0 || nanos > 999999999) throw invalid();
        return Instant.ofEpochSecond(seconds, nanos);
    }
    private static boolean flag(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte(); if (value > 1) throw invalid(); return value == 1;
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static boolean equal(TmmDraft a, TmmDraft b) { return a.uid.equals(b.uid) && ImportReview.sameMetadata(a, b); }
    private static TmmDraft expected(String status, String comment, long seconds, int nanos,
                                     double lat, double lon, Double bearing, Instant deployed) {
        return new TmmDraft(DEVICE, "PIR INBOX15", DeviceType.PIR, DeviceStatus.valueOf(status), "SYNTHETIC ROLE",
                Instant.ofEpochSecond(0, 123), Instant.ofEpochSecond(seconds, nanos), deployed, comment,
                new Position(lat, lon), bearing, null, 1);
    }
    private static byte[] seal(byte[] payload) throws Exception {
        byte[] bytes = Arrays.copyOf(payload, payload.length + 32);
        System.arraycopy(MessageDigest.getInstance("SHA-256").digest(payload), 0, bytes, payload.length, 32); return bytes;
    }
    private static byte[] reseal(byte[] bytes) throws Exception { return seal(Arrays.copyOf(bytes, bytes.length - 32)); }
    private static void rejected(byte[] bytes) throws Exception {
        try { inspect(bytes); throw new AssertionError("Malformed proposed fixture accepted"); }
        catch (IOException expected) { }
    }
    private static IOException invalid() { return new IOException("Invalid proposed inbox fixture"); }
    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString();
    }
    private static final class Receipt {
        UUID id; String context; Instant received; TmmDraft metadata; List<Observation> observations = new ArrayList<>();
    }
    private static final class Observation { long revision; TmmDraft metadata; }
}
