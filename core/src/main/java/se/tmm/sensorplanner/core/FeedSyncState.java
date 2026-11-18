package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Approved feed sync state v1 (docs/FEED_SYNC_STATE_FORMAT.md): bytes and file store only, no fetch, conflict or transport logic. */
public final class FeedSyncState {
    static final int MAX_RECORDS = 256, MAX_CONTEXT_BYTES = 4096, MAX_LABEL_BYTES = 1024;
    public static final int IMAGE_UNKNOWN = 0, IMAGE_NONE = 1, IMAGE_DIGEST = 2;
    static final int MAX_BYTES = 57 + MAX_CONTEXT_BYTES + 2 * MAX_LABEL_BYTES + MAX_RECORDS * 116 + 32;
    private static final int MAGIC = 0x544d4d53, VERSION = 1, MIN_BYTES = 57 + 3 + 32;
    private static final String CONTEXT_PREFIX = "mission-api-v1|";
    private static final UUID NIL = new UUID(0, 0);
    private interface Operation<T> { T run() throws IOException; }

    private final Path directory, data;
    private final InboxStore.Committer committer;

    /** The caller supplies the private directory; sync.bin and sync.lock live directly in it. */
    public FeedSyncState(Path directory) {
        this(directory, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    FeedSyncState(Path directory, InboxStore.Committer committer) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.data = directory.resolve("sync.bin");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    /** Explicit initialization (Följ feed). Refuses any existing file. */
    public synchronized void create(Snapshot initial) throws IOException {
        Objects.requireNonNull(initial, "initial");
        locked(() -> {
            if (!Files.notExists(data, LinkOption.NOFOLLOW_LINKS)) throw new SafeFailure("Sync state already exists or is unavailable; creation refused");
            write(initial);
            return null;
        });
    }

    /** Returns null when no file exists ("not following"). Never creates anything; replacement is atomic, so no lock is needed. */
    public synchronized Snapshot read() throws IOException {
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(data)) throw new SafeFailure("Sync state path is not a private regular store");
        byte[] bytes = readBytes();
        return bytes == null ? null : decode(bytes);
    }

    /** Replaces the whole snapshot only if the file still holds exactly {@code expected}. An identical rewrite is a no-op. */
    public synchronized void replace(Snapshot expected, Snapshot next) throws IOException {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        locked(() -> {
            byte[] current = readBytes();
            if (current == null) throw new SafeFailure("Sync state not initialized; nothing changed");
            decode(current);
            if (!Arrays.equals(current, encode(expected))) throw new SafeFailure("Sync state changed since it was read; nothing changed");
            if (!Arrays.equals(current, encode(next))) write(next);
            return null;
        });
    }

    private <T> T locked(Operation<T> operation) throws IOException {
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(data)) throw new SafeFailure("Sync state path is not a private regular store");
            try (FileChannel channel = FileChannel.open(directory.resolve("sync.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new SafeFailure("Sync state is busy; retry explicitly");
                return operation.run();
            }
        } catch (OverlappingFileLockException busy) {
            throw new SafeFailure("Sync state is busy; retry explicitly");
        } catch (SafeFailure safe) {
            throw safe;
        } catch (IOException unknown) {
            throw new SafeFailure("Sync state I/O failed; reload committed state before retrying");
        }
    }

    private byte[] readBytes() throws IOException {
        try (InputStream input = Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_BYTES) throw new SafeFailure("Sync state exceeds its file limit; original data preserved");
                buffer.write(chunk, 0, count);
            }
            return buffer.toByteArray();
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    private void write(Snapshot snapshot) throws IOException {
        byte[] bytes = encode(snapshot);
        Path temporary = Files.createTempFile(directory, "sync-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(bytes);
                output.getFD().sync();
            }
            if (!Arrays.equals(bytes, Files.readAllBytes(temporary))) throw new IOException("read-back mismatch");
            committer.replace(temporary, data);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static byte[] encode(Snapshot s) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            uuid(out, s.syncId);
            uuid(out, s.inboxId);
            out.writeByte(s.following ? 1 : 0);
            string(out, s.contextId);
            string(out, s.feedName);
            string(out, s.streamConnect);
            out.writeInt(s.records.size());
            for (Record r : s.records) {
                uuid(out, r.deviceUid);
                uuid(out, r.remoteReceiptId);
                optional(out, r.baseReceiptId);
                out.writeByte(r.imageKind);
                if (r.imageKind == IMAGE_DIGEST) out.write(r.imageDigest);
                optional(out, r.adoptNew);
                optional(out, r.adoptOld);
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
            if (bytes.length < MIN_BYTES || bytes.length > MAX_BYTES) throw new IllegalArgumentException();
            byte[] digest = Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
            if (!MessageDigest.isEqual(digest, sha256(bytes, bytes.length - 32))) throw new IllegalArgumentException();
            ByteBuffer in = ByteBuffer.wrap(bytes, 0, bytes.length - 32);
            if (in.getInt() != MAGIC || in.getInt() != VERSION) throw new IllegalArgumentException();
            UUID syncId = uuid(in), inboxId = uuid(in);
            boolean following = flag(in);
            String context = string(in, MAX_CONTEXT_BYTES), feed = string(in, MAX_LABEL_BYTES), stream = string(in, MAX_LABEL_BYTES);
            int count = in.getInt();
            if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException();
            List<Record> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID device = uuid(in), remote = uuid(in);
                UUID base = flag(in) ? uuid(in) : null;
                int kind = in.get();
                byte[] image = null;
                if (kind == IMAGE_DIGEST) { image = new byte[32]; in.get(image); }
                UUID adoptNew = flag(in) ? uuid(in) : null;
                UUID adoptOld = flag(in) ? uuid(in) : null;
                Record record = new Record(device, remote, base, kind, image, adoptNew, adoptOld);
                if (i > 0 && compare(records.get(i - 1).deviceUid, device) >= 0) throw new IllegalArgumentException("Order");
                records.add(record);
            }
            if (in.hasRemaining()) throw new IllegalArgumentException();
            return new Snapshot(syncId, inboxId, following, context, feed, stream, records);
        } catch (IllegalArgumentException | BufferUnderflowException | CharacterCodingException invalid) {
            throw new SafeFailure("Sync state is corrupt or unsupported; original data preserved");
        }
    }

    private static int compare(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    private static boolean flag(ByteBuffer in) {
        byte value = in.get();
        if (value != 0 && value != 1) throw new IllegalArgumentException();
        return value == 1;
    }

    private static String string(ByteBuffer in, int limit) throws CharacterCodingException {
        int size = in.getInt();
        if (size < 1 || size > limit || size > in.remaining()) throw new IllegalArgumentException();
        byte[] raw = new byte[size];
        in.get(raw);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(raw.length);
        out.write(raw);
    }

    private static void optional(DataOutputStream out, UUID id) throws IOException {
        out.writeByte(id == null ? 0 : 1);
        if (id != null) uuid(out, id);
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(ByteBuffer in) {
        UUID id = new UUID(in.getLong(), in.getLong());
        if (id.equals(NIL)) throw new IllegalArgumentException();
        return id;
    }

    private static byte[] sha256(byte[] bytes, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void requireText(String value, int limit, String prefix) {
        if (value.isEmpty() || !value.equals(value.trim())
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.getBytes(StandardCharsets.UTF_8).length > limit
                || (prefix != null && (!value.startsWith(prefix) || value.length() == prefix.length())))
            throw new IllegalArgumentException("Invalid sync state text");
    }

    private static void requireId(UUID id) {
        if (id.equals(NIL)) throw new IllegalArgumentException("Invalid sync state id");
    }

    private static final class SafeFailure extends IOException {
        SafeFailure(String message) { super(message); }
    }

    /** Records are sorted into canonical order; duplicate devices are rejected. */
    public static final class Snapshot {
        public final UUID syncId, inboxId;
        public final boolean following;
        public final String contextId, feedName, streamConnect;
        public final List<Record> records;

        public Snapshot(UUID syncId, UUID inboxId, boolean following, String contextId, String feedName,
                        String streamConnect, List<Record> records) {
            requireId(syncId);
            requireId(inboxId);
            requireText(contextId, MAX_CONTEXT_BYTES, CONTEXT_PREFIX);
            requireText(feedName, MAX_LABEL_BYTES, null);
            requireText(streamConnect, MAX_LABEL_BYTES, null);
            if (records.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many records");
            List<Record> sorted = new ArrayList<>(records);
            Collections.sort(sorted, (a, b) -> compare(a.deviceUid, b.deviceUid));
            for (int i = 1; i < sorted.size(); i++)
                if (sorted.get(i - 1).deviceUid.equals(sorted.get(i).deviceUid)) throw new IllegalArgumentException("Duplicate device");
            this.syncId = syncId;
            this.inboxId = inboxId;
            this.following = following;
            this.contextId = contextId;
            this.feedName = feedName;
            this.streamConnect = streamConnect;
            this.records = Collections.unmodifiableList(sorted);
        }
    }

    /** imageKind: 0 unknown, 1 base has no image, 2 base image SHA-256 in imageDigest (null otherwise). */
    public static final class Record {
        public final UUID deviceUid, remoteReceiptId, baseReceiptId, adoptNew, adoptOld;
        public final int imageKind;
        private final byte[] imageDigest;

        public Record(UUID deviceUid, UUID remoteReceiptId, UUID baseReceiptId, int imageKind, byte[] imageDigest,
                      UUID adoptNew, UUID adoptOld) {
            requireId(deviceUid);
            requireId(remoteReceiptId);
            for (UUID optional : new UUID[]{baseReceiptId, adoptNew, adoptOld}) if (optional != null) requireId(optional);
            if (imageKind < IMAGE_UNKNOWN || imageKind > IMAGE_DIGEST
                    || (imageKind == IMAGE_DIGEST) != (imageDigest != null)
                    || (imageDigest != null && imageDigest.length != 32)
                    || (adoptOld != null && (adoptNew == null || adoptOld.equals(adoptNew))))
                throw new IllegalArgumentException("Invalid sync record");
            this.deviceUid = deviceUid;
            this.remoteReceiptId = remoteReceiptId;
            this.baseReceiptId = baseReceiptId;
            this.imageKind = imageKind;
            this.imageDigest = imageDigest == null ? null : imageDigest.clone();
            this.adoptNew = adoptNew;
            this.adoptOld = adoptOld;
        }

        public byte[] imageDigest() { return imageDigest == null ? null : imageDigest.clone(); }
    }
}
