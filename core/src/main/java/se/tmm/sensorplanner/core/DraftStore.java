package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Local-only snapshot storage. Requires Java NIO file APIs (Android API 26+). */
public final class DraftStore {
    private static final int MAGIC = 0x544d4d44; // TMMD
    private static final int MAX_RECORDS = 1000;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 64 * 1024;
    private final Path directory;
    private final Path data;
    private final Committer committer;

    interface Committer {
        void replace(Path temporary, Path destination) throws IOException;
    }

    public DraftStore(Path directory) {
        this(directory, (temporary, destination) -> Files.move(temporary, destination,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    DraftStore(Path directory, Committer committer) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.data = directory.resolve("drafts.bin");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    /** Returns an immutable snapshot. No corrupt/unknown record is skipped. */
    public synchronized List<TmmDraft> load() throws IOException {
        return locked(() -> Collections.unmodifiableList(new ArrayList<>(read().values())));
    }

    /** New records require revision 1; updates must advance the saved revision by one. */
    public synchronized void save(TmmDraft draft) throws IOException {
        Objects.requireNonNull(draft, "draft");
        locked(() -> {
            Map<UUID, TmmDraft> records = read();
            TmmDraft previous = records.get(draft.uid);
            if (previous == null ? draft.revision != 1
                    : previous.revision == Long.MAX_VALUE || draft.revision != previous.revision + 1
                    || !draft.createdAt.equals(previous.createdAt)
                    || draft.updatedAt.isBefore(previous.updatedAt)) {
                throw new IOException("Draft changed; reload before saving");
            }
            records.put(draft.uid, draft);
            write(records);
            return null;
        });
    }

    /** Deletes metadata only; attachment deletion belongs to the image adapter. */
    public synchronized void delete(UUID uid, long expectedRevision) throws IOException {
        Objects.requireNonNull(uid, "uid");
        locked(() -> {
            Map<UUID, TmmDraft> records = read();
            TmmDraft previous = records.get(uid);
            if (previous == null || previous.revision != expectedRevision) {
                throw new IOException("Draft changed; reload before deleting");
            }
            records.remove(uid);
            write(records);
            return null;
        });
    }

    private interface Operation<T> { T run() throws IOException; }

    private <T> T locked(Operation<T> operation) throws IOException {
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("drafts.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Draft storage is busy; retry");
            return operation.run();
        } catch (java.nio.channels.OverlappingFileLockException e) {
            throw new IOException("Draft storage is busy; retry");
        }
    }

    private Map<UUID, TmmDraft> read() throws IOException {
        Map<UUID, TmmDraft> records = new LinkedHashMap<>();
        byte[] bytes;
        try (InputStream file = Files.newInputStream(data)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = file.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_BYTES) throw invalid();
                buffer.write(chunk, 0, count);
            }
            bytes = buffer.toByteArray();
        } catch (NoSuchFileException e) {
            if (Files.notExists(data, LinkOption.NOFOLLOW_LINKS)) return records;
            throw new IOException("Draft storage is unavailable; original data preserved");
        }
        if (bytes.length < 44) throw invalid();
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(digest(payload), Arrays.copyOfRange(bytes, payload.length, bytes.length))) {
            throw invalid();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) throw invalid();
            if (input.readInt() != TmmDraft.SCHEMA_VERSION) {
                throw new IOException("Unsupported draft storage version; original data preserved");
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_RECORDS) throw invalid();
            for (int i = 0; i < count; i++) {
                UUID uid = new UUID(input.readLong(), input.readLong());
                TmmDraft draft = new TmmDraft(uid, requiredText(input), DeviceType.valueOf(text(input)),
                        DeviceStatus.valueOf(text(input)), requiredText(input), instant(input), instant(input),
                        flag(input) ? instant(input) : null, text(input),
                        new Position(input.readDouble(), input.readDouble()),
                        flag(input) ? input.readDouble() : null, flag(input) ? requiredText(input) : null,
                        input.readLong());
                if (records.put(uid, draft) != null) throw invalid();
            }
            if (input.available() != 0) throw invalid();
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw invalid();
        } catch (EOFException | CharacterCodingException e) {
            throw invalid();
        }
        return records;
    }

    private void write(Map<UUID, TmmDraft> records) throws IOException {
        if (records.size() > MAX_RECORDS) throw new IOException("Draft storage limit reached");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(TmmDraft.SCHEMA_VERSION);
            output.writeInt(records.size());
            for (TmmDraft draft : records.values()) {
                output.writeLong(draft.uid.getMostSignificantBits());
                output.writeLong(draft.uid.getLeastSignificantBits());
                text(output, draft.displayId);
                text(output, draft.type.name());
                text(output, draft.status.name());
                text(output, draft.owner);
                instant(output, draft.createdAt);
                instant(output, draft.updatedAt);
                output.writeBoolean(draft.deployedAt != null);
                if (draft.deployedAt != null) instant(output, draft.deployedAt);
                text(output, draft.comment);
                output.writeDouble(draft.position.latitude);
                output.writeDouble(draft.position.longitude);
                output.writeBoolean(draft.bearingDeg != null);
                if (draft.bearingDeg != null) output.writeDouble(draft.bearingDeg);
                output.writeBoolean(draft.imageRef != null);
                if (draft.imageRef != null) text(output, draft.imageRef);
                output.writeLong(draft.revision);
                if (buffer.size() > MAX_BYTES - 32) throw new IOException("Draft storage limit reached");
            }
        }
        byte[] payload = buffer.toByteArray();
        Path temporary = Files.createTempFile(directory, "drafts-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(payload);
                output.write(digest(payload));
                output.getFD().sync();
            }
            // No copy/delete fallback: failed atomic replacement must preserve the saved snapshot.
            committer.replace(temporary, data);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean flag(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) throw invalid();
        return value == 1;
    }

    private static Instant instant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999999999) throw invalid();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static void instant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static String requiredText(DataInputStream input) throws IOException {
        String value = text(input);
        if (value.isEmpty() || !value.equals(value.trim())) throw invalid();
        return value;
    }

    private static String text(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) throw invalid();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
        if (bytes.remaining() > MAX_TEXT_BYTES) throw new IOException("Draft text storage limit reached");
        output.writeInt(bytes.remaining());
        output.write(bytes.array(), bytes.position(), bytes.remaining());
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }

    private static IOException invalid() {
        return new IOException("Invalid draft storage; original data preserved");
    }
}
