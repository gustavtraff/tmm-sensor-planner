package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;

/** Approved registry v1: "association verified for review" only; never adopts images, reads stage files or sends receipts. */
public final class ImageAssociationRegistry {
    static final int MAX_RECORDS = 256, MAX_CONTEXT_BYTES = 4096;
    static final int MAX_BYTES = 76 + MAX_RECORDS * (140 + MAX_CONTEXT_BYTES);
    private static final int MAGIC = 0x544d4d41, VERSION = 1;
    private static final UUID NIL = new UUID(0, 0);
    public interface StageProof { void requireReady(UUID stageLocalId, ImageOffer offer) throws IOException; }
    public interface StageAction<T> { T run() throws IOException; }
    private interface Operation<T> { T run() throws IOException; }

    private final Path directory, data;
    private final InboxStore.Committer committer;

    public ImageAssociationRegistry(Path directory) {
        this(directory, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    ImageAssociationRegistry(Path directory, InboxStore.Committer committer) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.data = directory.resolve("associations.bin");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    /** Explicit start, bound to one inbox because receipt IDs are unique only within it. Refuses any existing file. */
    public synchronized Snapshot initializeNew(UUID inboxId) throws IOException {
        Objects.requireNonNull(inboxId, "inboxId");
        return locked(() -> {
            if (!Files.notExists(data, LinkOption.NOFOLLOW_LINKS)) throw new SafeFailure("Registry already exists or is unavailable; initialization refused");
            Snapshot empty = new Snapshot(UUID.randomUUID(), inboxId, Collections.emptyList());
            write(empty);
            return empty;
        });
    }

    /** Missing, unreadable or unsupported storage is an error, never an empty registry. */
    public synchronized Snapshot load() throws IOException { return locked(this::read); }

    /**
     * The caller must prove contextId came from the approved adapter. An exact repeat keeps the original record;
     * a conflicting binding changes nothing. The stage proof runs under the registry lock, so that discard cannot
     * remove the entry before the record is committed.
     */
    public synchronized Record associate(InboxStore inbox, String contextId, ImageOffer offer, UUID stageLocalId, Instant verifiedAt, StageProof stage) throws IOException {
        Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(stage, "stage");
        // Inbox calls finish before the registry lock; receipts are never removed, so the match stays valid.
        UUID inboxId = inbox.load().inboxId;
        InboxStore.Receipt receipt = inbox.find(contextId, ImageOffer.carrier(offer.metadata));
        if (receipt == null) throw new SafeFailure("No retained metadata receipt matches this offer; nothing changed");
        Record candidate = new Record(offer.associationId, offer.deliveryId, offer.metadata.uid, receipt.id, stageLocalId,
                contextId, offer.pngLength, offer.width, offer.height, offer.sha256(), verifiedAt);
        return locked(() -> {
            Snapshot current = read();
            if (!current.inboxId.equals(inboxId)) throw new SafeFailure("Registry belongs to another inbox; nothing changed");
            for (Record record : current.records)
                if (record.sameDelivery(candidate) && record.sameBinding(candidate)) return record;
            if (current.records.size() >= MAX_RECORDS) throw new SafeFailure("Registry is full; nothing changed");
            try { stage.requireReady(stageLocalId, offer); }
            catch (IOException notReady) { throw new SafeFailure("Stage entry is not a verified match; nothing changed"); }
            List<Record> next = new ArrayList<>(current.records);
            next.add(candidate);
            try { validate(next); }
            catch (IllegalArgumentException conflict) { throw new SafeFailure("Association conflicts with a stored record; nothing changed"); }
            write(new Snapshot(current.registryId, current.inboxId, next));
            return candidate;
        });
    }

    /** Runs a stage removal under the registry lock, only if no record references that entry (decision R3). */
    public synchronized <T> T unlessReferenced(UUID stageLocalId, StageAction<T> action) throws IOException {
        Objects.requireNonNull(stageLocalId, "stageLocalId");
        Objects.requireNonNull(action, "action");
        IOException[] failed = new IOException[1];
        T result = locked(() -> {
            for (Record record : read().records)
                if (record.stageLocalId.equals(stageLocalId)) throw new SafeFailure("Stage entry is referenced by a record; nothing changed");
            try { return action.run(); }
            catch (IOException failure) { failed[0] = failure; return null; }
        });
        if (failed[0] != null) throw failed[0]; // The action's own failure, unchanged: it may have partly applied.
        return result;
    }

    /** Runs a read-only stage action while this exact recorded association still belongs to the loaded registry. */
    public synchronized <T> T withRecord(UUID registryId, UUID inboxId, Record expected, StageAction<T> action) throws IOException {
        Objects.requireNonNull(registryId, "registryId"); Objects.requireNonNull(inboxId, "inboxId");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(action, "action");
        IOException[] failed = new IOException[1];
        T result = locked(() -> {
            Snapshot current = read(); boolean found = false;
            if (current.registryId.equals(registryId) && current.inboxId.equals(inboxId)) {
                for (Record record : current.records) {
                    if (record.sameDelivery(expected) && record.sameBinding(expected)
                            && record.stageLocalId.equals(expected.stageLocalId) && record.verifiedAt.equals(expected.verifiedAt)) {
                        found = true; break;
                    }
                }
            }
            if (!found) throw new SafeFailure("Reviewed image association changed or is unavailable");
            try { return action.run(); }
            catch (IOException failure) { failed[0] = failure; return null; }
        });
        if (failed[0] != null) throw failed[0];
        return result;
    }

    private <T> T locked(Operation<T> operation) throws IOException {
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(data)) throw new SafeFailure("Registry path is not a private regular store");
            try (FileChannel channel = FileChannel.open(directory.resolve("associations.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new SafeFailure("Registry is busy; retry explicitly");
                return operation.run();
            }
        } catch (OverlappingFileLockException busy) {
            throw new SafeFailure("Registry is busy; retry explicitly");
        } catch (SafeFailure safe) {
            throw safe;
        } catch (IOException unknown) {
            // A replacement may already have committed. Do not expose paths or imply rollback.
            throw new SafeFailure("Registry I/O failed; reload committed state before retrying");
        }
    }

    private Snapshot read() throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_BYTES) throw new SafeFailure("Registry exceeds its file limit; original data preserved");
                buffer.write(chunk, 0, count);
            }
            bytes = buffer.toByteArray();
        } catch (NoSuchFileException missing) {
            throw new SafeFailure("Registry not initialized or missing; no empty history inferred");
        }
        return decode(bytes);
    }

    private void write(Snapshot snapshot) throws IOException {
        byte[] bytes = encode(snapshot);
        Path temporary = Files.createTempFile(directory, "associations-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(bytes);
                output.getFD().sync();
            }
            committer.replace(temporary, data);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static byte[] encode(Snapshot snapshot) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            uuid(out, snapshot.registryId);
            uuid(out, snapshot.inboxId);
            out.writeInt(snapshot.records.size());
            for (Record r : snapshot.records) {
                for (UUID id : new UUID[]{r.associationId, r.deliveryId, r.deviceUid, r.receiptId, r.stageLocalId}) uuid(out, id);
                byte[] context = r.contextId.getBytes(StandardCharsets.UTF_8);
                out.writeInt(context.length);
                out.write(context);
                out.writeInt(r.pngLength);
                out.writeInt(r.width);
                out.writeInt(r.height);
                out.write(r.digest);
                out.writeLong(r.verifiedAt.getEpochSecond());
                out.writeInt(r.verifiedAt.getNano());
            }
            out.flush();
            out.write(sha256(bytes.toByteArray(), bytes.size()));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return bytes.toByteArray();
    }

    static Snapshot decode(byte[] bytes) throws IOException {
        try {
            if (bytes.length < 76 || bytes.length > MAX_BYTES) throw new IllegalArgumentException();
            byte[] digest = Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
            if (!MessageDigest.isEqual(digest, sha256(bytes, bytes.length - 32))) throw new IllegalArgumentException();
            ByteBuffer in = ByteBuffer.wrap(bytes, 0, bytes.length - 32);
            if (in.getInt() != MAGIC || in.getInt() != VERSION) throw new IllegalArgumentException();
            UUID registryId = uuid(in), inboxId = uuid(in);
            if (registryId.equals(NIL)) throw new IllegalArgumentException();
            int count = in.getInt();
            if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException();
            List<Record> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID association = uuid(in), delivery = uuid(in), device = uuid(in), receipt = uuid(in), stage = uuid(in);
                int size = in.getInt();
                if (size < 1 || size > MAX_CONTEXT_BYTES || size > in.remaining()) throw new IllegalArgumentException();
                byte[] raw = new byte[size];
                in.get(raw);
                String context = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
                int length = in.getInt(), width = in.getInt(), height = in.getInt();
                byte[] png = new byte[32];
                in.get(png);
                long second = in.getLong();
                int nano = in.getInt();
                if (nano < 0 || nano > 999_999_999) throw new IllegalArgumentException();
                records.add(new Record(association, delivery, device, receipt, stage, context, length, width, height, png,
                        Instant.ofEpochSecond(second, nano)));
            }
            if (in.hasRemaining()) throw new IllegalArgumentException();
            validate(records);
            return new Snapshot(registryId, inboxId, records);
        } catch (IllegalArgumentException | BufferUnderflowException | CharacterCodingException | DateTimeException invalid) {
            throw new SafeFailure("Registry is corrupt or unsupported; original data preserved");
        }
    }

    private static void validate(List<Record> records) {
        Set<UUID> stages = new HashSet<>();
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (!stages.add(record.stageLocalId)) throw new IllegalArgumentException("Stage entry already associated");
            for (int j = 0; j < i; j++) {
                Record earlier = records.get(j);
                if (record.sameDelivery(earlier)) throw new IllegalArgumentException("Duplicate delivery");
                if (record.sameAssociation(earlier) && !record.sameBinding(earlier)) throw new IllegalArgumentException("Identity conflict");
            }
        }
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(ByteBuffer in) { return new UUID(in.getLong(), in.getLong()); }

    private static byte[] sha256(byte[] bytes, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class SafeFailure extends IOException {
        SafeFailure(String message) { super(message); }
    }

    public static final class Snapshot {
        public final UUID registryId, inboxId;
        public final List<Record> records;

        Snapshot(UUID registryId, UUID inboxId, List<Record> records) {
            this.registryId = registryId;
            this.inboxId = inboxId;
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
        }
    }

    public static final class Record {
        public final UUID associationId, deliveryId, deviceUid, receiptId, stageLocalId;
        public final String contextId;
        public final int pngLength, width, height;
        public final Instant verifiedAt;
        private final byte[] digest;

        Record(UUID associationId, UUID deliveryId, UUID deviceUid, UUID receiptId, UUID stageLocalId, String contextId,
               int pngLength, int width, int height, byte[] digest, Instant verifiedAt) {
            if (associationId.equals(NIL) || deliveryId.equals(NIL) || stageLocalId.equals(NIL)
                    || contextId.isEmpty() || !contextId.equals(contextId.trim())
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(contextId)
                    || contextId.getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES
                    || pngLength < 1 || pngLength > ImageOffer.MAX_PNG_BYTES
                    || width < 1 || width > ImageOffer.MAX_EDGE || height < 1 || height > ImageOffer.MAX_EDGE
                    || digest.length != 32) throw new IllegalArgumentException("Invalid association record");
            this.associationId = associationId;
            this.deliveryId = deliveryId;
            this.deviceUid = deviceUid;
            this.receiptId = receiptId;
            this.stageLocalId = stageLocalId;
            this.contextId = contextId;
            this.pngLength = pngLength;
            this.width = width;
            this.height = height;
            this.digest = digest.clone();
            this.verifiedAt = verifiedAt;
        }

        public byte[] sha256() { return digest.clone(); }

        boolean sameAssociation(Record other) { return contextId.equals(other.contextId) && associationId.equals(other.associationId); }

        boolean sameDelivery(Record other) { return sameAssociation(other) && deliveryId.equals(other.deliveryId); }

        boolean sameBinding(Record other) {
            return deviceUid.equals(other.deviceUid) && receiptId.equals(other.receiptId) && pngLength == other.pngLength
                    && width == other.width && height == other.height && Arrays.equals(digest, other.digest);
        }
    }
}
