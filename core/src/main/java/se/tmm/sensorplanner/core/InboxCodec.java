package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** Package-private codec for the approved TMMI frame; not a wire protocol. */
final class InboxCodec {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TEXT = 65536, LIMIT = 1000, MAGIC = 0x544d4d49;

    static boolean sameMetadata(TmmDraft a, TmmDraft b) {
        return a.uid.equals(b.uid) && ImportReview.sameMetadata(a, b);
    }

    static boolean sameObservation(TmmDraft a, TmmDraft b) {
        return a == null ? b == null : b != null && a.revision == b.revision && sameMetadata(a, b);
    }

    static void validateMetadata(TmmDraft metadata) throws IOException {
        if (metadata.imageRef != null) throw invalid();
        textBytes(metadata.displayId); textBytes(metadata.owner); textBytes(metadata.comment);
    }

    static byte[] textBytes(String value) throws IOException {
        if (value.length() > MAX_TEXT) throw InboxStore.failure("Inbox text limit exceeded; nothing stored");
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            if (encoded.remaining() > MAX_TEXT) throw InboxStore.failure("Inbox text limit exceeded; nothing stored");
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
        } catch (CharacterCodingException e) { throw invalid(); }
    }

    static byte[] encode(InboxStore.Snapshot snapshot) throws IOException {
        validate(snapshot);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC); out.writeInt(1); uuid(out, snapshot.inboxId); out.writeInt(snapshot.receipts.size());
            for (InboxStore.Receipt r : snapshot.receipts) {
                uuid(out, r.id); text(out, r.contextId); instant(out, r.firstObservedAt); metadata(out, r.incoming);
                checkSize(buffer);
                out.writeInt(r.localObservations.size());
                for (TmmDraft local : r.localObservations) {
                    out.writeBoolean(local != null);
                    if (local != null) { out.writeLong(local.revision); metadata(out, local); }
                    checkSize(buffer);
                }
                checkSize(buffer);
            }
        }
        byte[] payload = buffer.toByteArray();
        byte[] bytes = Arrays.copyOf(payload, payload.length + 32);
        System.arraycopy(digest(payload), 0, bytes, payload.length, 32);
        return bytes;
    }

    static InboxStore.Snapshot decode(byte[] bytes) throws IOException {
        if (bytes.length < 60 || bytes.length > MAX_BYTES) throw invalid();
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(digest(payload), Arrays.copyOfRange(bytes, payload.length, bytes.length))) throw invalid();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC || in.readInt() != 1) throw invalid();
            UUID id = uuid(in); int count = count(in, 0), budget = count;
            List<InboxStore.Receipt> receipts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                UUID receiptId = uuid(in); String context = required(in); Instant received = instant(in);
                TmmDraft metadata = metadata(in, 1); int observations = count(in, 1);
                budget += observations; if (budget > LIMIT) throw invalid();
                List<TmmDraft> local = new ArrayList<>();
                for (int j = 0; j < observations; j++) local.add(flag(in) ? metadata(in, positive(in.readLong())) : null);
                receipts.add(new InboxStore.Receipt(receiptId, context, received, metadata, local));
            }
            if (in.available() != 0) throw invalid();
            InboxStore.Snapshot result = new InboxStore.Snapshot(id, receipts); validate(result); return result;
        } catch (IllegalArgumentException | DateTimeException | EOFException | CharacterCodingException e) {
            throw invalid();
        }
    }

    private static void checkSize(ByteArrayOutputStream buffer) throws IOException {
        if (buffer.size() > MAX_BYTES - 32) throw InboxStore.failure("Inbox file limit exceeded; nothing stored");
    }

    private static void validate(InboxStore.Snapshot snapshot) throws IOException {
        int budget = snapshot.receipts.size();
        if (budget > LIMIT) throw full();
        Set<UUID> ids = new HashSet<>();
        List<InboxStore.Receipt> seen = new ArrayList<>();
        for (InboxStore.Receipt r : snapshot.receipts) {
            if (!ids.add(r.id) || r.contextId.isEmpty() || !r.contextId.equals(r.contextId.trim())) throw invalid();
            textBytes(r.contextId); validateMetadata(r.incoming);
            if (r.incoming.revision != 1 || r.localObservations.isEmpty()) throw invalid();
            budget += r.localObservations.size(); if (budget > LIMIT) throw full();
            for (InboxStore.Receipt old : seen) {
                if (old.contextId.equals(r.contextId) && sameMetadata(old.incoming, r.incoming)) throw invalid();
            }
            seen.add(r);
            List<TmmDraft> observed = new ArrayList<>();
            for (TmmDraft local : r.localObservations) {
                if (local != null) {
                    validateMetadata(local);
                    if (!local.uid.equals(r.incoming.uid) || local.revision < 1) throw invalid();
                }
                for (TmmDraft old : observed) if (sameObservation(old, local)) throw invalid();
                observed.add(local);
            }
        }
    }

    private static TmmDraft metadata(DataInputStream in, long revision) throws IOException {
        UUID id = uuid(in); String label = required(in);
        DeviceType type = DeviceType.valueOf(required(in)); DeviceStatus status = DeviceStatus.valueOf(required(in));
        String owner = required(in); Instant created = instant(in), updated = instant(in);
        Instant deployed = flag(in) ? instant(in) : null; String comment = text(in);
        Position position = new Position(in.readDouble(), in.readDouble()); Double bearing = flag(in) ? in.readDouble() : null;
        return new TmmDraft(id, label, type, status, owner, created, updated, deployed, comment, position, bearing, null, revision);
    }
    private static void metadata(DataOutputStream out, TmmDraft m) throws IOException {
        uuid(out, m.uid); text(out, m.displayId); text(out, m.type.name()); text(out, m.status.name()); text(out, m.owner);
        instant(out, m.createdAt); instant(out, m.updatedAt); out.writeBoolean(m.deployedAt != null);
        if (m.deployedAt != null) instant(out, m.deployedAt);
        text(out, m.comment); out.writeDouble(m.position.latitude); out.writeDouble(m.position.longitude);
        out.writeBoolean(m.bearingDeg != null); if (m.bearingDeg != null) out.writeDouble(m.bearingDeg);
    }
    private static String text(DataInputStream in) throws IOException {
        int size = in.readInt(); if (size < 0 || size > MAX_TEXT || size > in.available()) throw invalid();
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static String required(DataInputStream in) throws IOException {
        String value = text(in); if (value.isEmpty() || !value.equals(value.trim())) throw invalid(); return value;
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = textBytes(value); out.writeInt(bytes.length); out.write(bytes);
    }
    private static Instant instant(DataInputStream in) throws IOException {
        long seconds = in.readLong(); int nanos = in.readInt(); if (nanos < 0 || nanos > 999999999) throw invalid();
        return Instant.ofEpochSecond(seconds, nanos);
    }
    private static void instant(DataOutputStream out, Instant value) throws IOException {
        out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static boolean flag(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte(); if (value > 1) throw invalid(); return value == 1;
    }
    private static int count(DataInputStream in, int min) throws IOException {
        int value = in.readInt(); if (value < min || value > LIMIT) throw invalid(); return value;
    }
    private static long positive(long value) throws IOException { if (value < 1) throw invalid(); return value; }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static IOException invalid() { return InboxStore.failure("Invalid or unsupported inbox; original data preserved"); }
    private static IOException full() { return InboxStore.failure("Inbox entry limit exceeded; nothing stored"); }
}
