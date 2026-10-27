package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.time.Instant;
import java.util.UUID;

/** Primitives shared only by the local v2 frame and TMMR fingerprint, independent of older codecs. */
final class LocalV2Binary {
    static IOException invalid() { return new IOException("Invalid or unsupported local v2 data"); }
    static long positive(long value) throws IOException { if (value < 1) throw invalid(); return value; }
    static void validateText(String value, boolean required) throws IOException {
        if (value == null || required && (value.isEmpty() || !value.equals(value.trim()))) throw invalid();
        textBytes(value);
    }
    static byte[] textBytes(String value) throws IOException {
        if (value.length() > 65536) throw invalid();
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
        if (buffer.remaining() > 65536) throw invalid();
        byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
    }
    static void validateDraft(TmmDraft draft) throws IOException {
        validateText(draft.displayId, true); validateText(draft.owner, true); validateText(draft.comment, false);
        if (draft.imageRef != null) validateText(draft.imageRef, true);
    }
    static TmmDraft body(DataInputStream in, UUID uid) throws IOException {
        String label = required(in); DeviceType type = DeviceType.valueOf(required(in));
        DeviceStatus status = DeviceStatus.valueOf(required(in)); String owner = required(in);
        Instant created = instant(in), updated = instant(in), deployed = flag(in) ? instant(in) : null;
        String comment = text(in); Position position = new Position(in.readDouble(), in.readDouble());
        Double bearing = flag(in) ? in.readDouble() : null;
        return new TmmDraft(uid, label, type, status, owner, created, updated, deployed, comment, position, bearing, null, 1);
    }
    static void body(DataOutputStream out, TmmDraft m, boolean canonical) throws IOException {
        text(out, m.displayId); text(out, m.type.name()); text(out, m.status.name()); text(out, m.owner);
        instant(out, m.createdAt); instant(out, m.updatedAt); out.writeBoolean(m.deployedAt != null);
        if (m.deployedAt != null) instant(out, m.deployedAt);
        text(out, m.comment); out.writeDouble(number(m.position.latitude, canonical));
        out.writeDouble(number(m.position.longitude, canonical)); out.writeBoolean(m.bearingDeg != null);
        if (m.bearingDeg != null) out.writeDouble(number(m.bearingDeg, canonical));
    }
    static void local(DataOutputStream out, TmmDraft m, boolean canonical) throws IOException {
        body(out, m, canonical); out.writeBoolean(m.imageRef != null);
        if (m.imageRef != null) text(out, m.imageRef); out.writeLong(m.revision);
    }
    static TmmDraft local(TmmDraft m, String image, long revision) {
        return new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                m.deployedAt, m.comment, m.position, m.bearingDeg, image, revision);
    }
    private static double number(double value, boolean canonical) { return canonical && value == 0 ? 0.0 : value; }
    static TmmDraft metadata(DataInputStream in) throws IOException { return body(in, uuid(in)); }
    static void metadata(DataOutputStream out, TmmDraft value) throws IOException { uuid(out, value.uid); body(out, value, false); }
    static String text(DataInputStream in) throws IOException {
        int size = in.readInt(); if (size < 0 || size > 65536 || size > in.available()) throw invalid();
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    static String required(DataInputStream in) throws IOException {
        String value = text(in); validateText(value, true); return value;
    }
    static void text(DataOutputStream out, String value) throws IOException { byte[] bytes = textBytes(value); out.writeInt(bytes.length); out.write(bytes); }
    static Instant instant(DataInputStream in) throws IOException {
        long seconds = in.readLong(); int nanos = in.readInt(); if (nanos < 0 || nanos > 999999999) throw invalid();
        return Instant.ofEpochSecond(seconds, nanos);
    }
    static void instant(DataOutputStream out, Instant value) throws IOException { out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano()); }
    static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    static boolean flag(DataInputStream in) throws IOException { return code(in, 0, 1) == 1; }
    static int code(DataInputStream in, int min, int max) throws IOException { int n = in.readUnsignedByte(); if (n < min || n > max) throw invalid(); return n; }
    static int count(DataInputStream in) throws IOException { int n = in.readInt(); if (n < 0 || n > 1000) throw invalid(); return n; }
    static byte[] sha(byte[] bytes) { return sha(bytes, bytes.length); }
    static byte[] sha(byte[] bytes, int length) {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(bytes, 0, length); return digest.digest(); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static final class Buffer extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        Buffer(int limit) { this.limit = limit; }
        public void write(int value) throws IOException {
            if (bytes.size() == limit) throw invalid(); bytes.write(value);
        }
        public void write(byte[] source, int offset, int length) throws IOException {
            if (length > limit - bytes.size()) throw invalid(); bytes.write(source, offset, length);
        }
        byte[] bytes() { return bytes.toByteArray(); }
    }
}
