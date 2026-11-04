package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.images.ImagePreparationException;

/** Private unassociated pairs only. Call off the UI thread with a trusted, dedicated no-backup root. */
@TargetApi(26)
public final class ImageStageStore {
    public static final int MAX_ENTRIES = 16;
    public static final long MAX_BYTES = 268435456L;
    private static final int MAX_OFFER = 262256, MAX_PNG = 16777216;
    private static final byte[] FORMAT = {'T','M','M','I','S','T','G','1'};
    private static final LinkOption[] NO_LINKS = {LinkOption.NOFOLLOW_LINKS};
    private final Path root;
    private final Faults faults;

    enum Point { ROOT_CREATED, MARKER_SYNCED, PENDING_CREATED, OFFER_SYNCED,
        PNG_SYNCED, PENDING_VERIFIED, RENAMED, READY_VERIFIED }
    interface Faults { void at(Point point) throws IOException; }
    private interface Work<T> { T run() throws IOException; }

    public enum Code { INVALID_STATE, RECOVERY_REQUIRED, CAPACITY, BUSY, IO,
        ALREADY_ATTEMPTED, NOT_FOUND, CONFLICT }
    public static final class Failure extends IOException {
        public final Code code;
        private Failure(Code code) { super("Private image stage: " + code.name()); this.code = code; }
    }
    public enum Outcome { ABSENT, PENDING, READY }

    public static final class Prepared {
        public final UUID id;
        private final byte[] offer, png;
        private final AtomicBoolean attempted = new AtomicBoolean();
        private Prepared(UUID id, byte[] offer, byte[] png) {
            this.id = id; this.offer = offer; this.png = png;
        }
    }

    public static final class Inspection {
        public final List<UUID> ready, pending;
        public final long logicalBytes;
        private Inspection(List<UUID> ready, List<UUID> pending, long bytes) {
            this.ready = Collections.unmodifiableList(new ArrayList<>(ready));
            this.pending = Collections.unmodifiableList(new ArrayList<>(pending));
            this.logicalBytes = bytes;
        }
        public boolean recoveryRequired() { return !pending.isEmpty(); }
    }

    public ImageStageStore(Path root) { this(root, point -> { }); }
    ImageStageStore(Path root, Faults faults) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    /** Fresh receiver-owned identity and frozen bytes, allocated before any filesystem mutation. */
    public static Prepared prepare(ReceivedImagePayload payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        try {
            return new Prepared(UUID.randomUUID(), ImageOfferCodec.encode(payload.offer), payload.image.bytes());
        } catch (OutOfMemoryError exhausted) { throw lowMemory(); }
    }

    /** Explicit initialization only; an existing or partially initialized root is never reset. */
    public Inspection initializeNew() throws IOException {
        return safe(() -> {
            Files.createDirectory(root);
            faults.at(Point.ROOT_CREATED);
            try (FileChannel channel = FileChannel.open(root.resolve("stage.lock"),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = acquire(channel)) {
                writeNew(root.resolve("format"), FORMAT);
                faults.at(Point.MARKER_SYNCED);
                return inspectLocked();
            }
        });
    }

    public Inspection inspect() throws IOException { return locked(this::inspectLocked); }

    /** Validates the entire stage before returning one pair; no image becomes an active attachment. */
    public ReceivedImagePayload read(UUID id) throws IOException {
        requireId(id);
        return locked(() -> {
            Inspection state = inspectLocked();
            if (!state.ready.contains(id)) throw fail(Code.NOT_FOUND);
            return readPair(root.resolve("ready-" + id));
        });
    }

    /** One attempt per handle. On any uncertain result retain the handle and reconcile it. */
    public UUID stage(Prepared operation) throws IOException {
        Objects.requireNonNull(operation, "operation");
        if (!operation.attempted.compareAndSet(false, true)) throw fail(Code.ALREADY_ATTEMPTED);
        return locked(() -> {
            Inspection before = inspectLocked();
            if (before.recoveryRequired()) throw fail(Code.RECOVERY_REQUIRED);
            if (before.ready.contains(operation.id)) throw fail(Code.CONFLICT);
            long added = (long) operation.offer.length + operation.png.length;
            if (before.ready.size() >= MAX_ENTRIES || added > MAX_BYTES - before.logicalBytes) {
                throw fail(Code.CAPACITY);
            }
            Path pending = root.resolve("pending-" + operation.id), ready = root.resolve("ready-" + operation.id);
            Files.createDirectory(pending);
            faults.at(Point.PENDING_CREATED);
            writeNew(pending.resolve("offer.bin"), operation.offer);
            faults.at(Point.OFFER_SYNCED);
            writeNew(pending.resolve("image.png"), operation.png);
            faults.at(Point.PNG_SYNCED);
            requireMatch(pending, operation);
            faults.at(Point.PENDING_VERIFIED);
            // Cooperative writers hold the same OS lock; foreign filesystem mutation is outside this contract.
            if (!Files.notExists(ready, NO_LINKS)) throw fail(Code.CONFLICT);
            Files.move(pending, ready, StandardCopyOption.ATOMIC_MOVE);
            faults.at(Point.RENAMED);
            requireMatch(ready, operation);
            faults.at(Point.READY_VERIFIED);
            return operation.id;
        });
    }

    /** Only the original frozen handle can establish a known READY outcome after an uncertain attempt. */
    public Outcome reconcile(Prepared operation) throws IOException {
        Objects.requireNonNull(operation, "operation");
        return locked(() -> {
            Inspection state = inspectLocked();
            if (state.ready.contains(operation.id)) {
                requireMatch(root.resolve("ready-" + operation.id), operation);
                return Outcome.READY;
            }
            return state.pending.contains(operation.id) ? Outcome.PENDING : Outcome.ABSENT;
        });
    }

    /** Read-only reconciliation when the caller retained the original local ID and frozen pair across restart. */
    public Outcome reconcile(UUID originalId, ReceivedImagePayload expected) throws IOException {
        requireId(originalId);
        Objects.requireNonNull(expected, "expected");
        return safe(() -> reconcile(new Prepared(originalId,
                ImageOfferCodec.encode(expected.offer), expected.image.bytes())));
    }

    private Inspection inspectLocked() throws IOException {
        directory(root);
        List<Path> entries = entries(root, MAX_ENTRIES + 2);
        if (regular(root.resolve("format"), FORMAT.length) != FORMAT.length
                || !Arrays.equals(readBounded(root.resolve("format"), FORMAT.length), FORMAT)
                || regular(root.resolve("stage.lock"), 0) != 0) throw fail(Code.INVALID_STATE);
        List<UUID> ready = new ArrayList<>(), pending = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        long bytes = FORMAT.length;
        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (name.equals("format") || name.equals("stage.lock")) continue;
            boolean published = name.startsWith("ready-");
            String prefix = published ? "ready-" : "pending-";
            if (!name.startsWith(prefix)) throw fail(Code.INVALID_STATE);
            UUID id;
            try { id = UUID.fromString(name.substring(prefix.length())); }
            catch (IllegalArgumentException invalid) { throw fail(Code.INVALID_STATE); }
            requireId(id);
            if (!name.equals(prefix + id) || !ids.add(id)) throw fail(Code.INVALID_STATE);
            directory(entry);
            List<Path> files = entries(entry, 2);
            Set<String> names = new HashSet<>();
            for (Path file : files) {
                String filename = file.getFileName().toString();
                if (!filename.equals("offer.bin") && !filename.equals("image.png")) throw fail(Code.INVALID_STATE);
                names.add(filename);
                bytes += regular(file, filename.equals("offer.bin") ? MAX_OFFER : MAX_PNG);
                if (bytes > MAX_BYTES) throw fail(Code.CAPACITY);
            }
            if (published) {
                if (names.size() != 2) throw fail(Code.INVALID_STATE);
                readPair(entry);
                ready.add(id);
            } else pending.add(id);
        }
        ready.sort(Comparator.comparing(UUID::toString));
        pending.sort(Comparator.comparing(UUID::toString));
        return new Inspection(ready, pending, bytes);
    }

    private ReceivedImagePayload readPair(Path directory) throws IOException {
        byte[] encoded = readBounded(directory.resolve("offer.bin"), MAX_OFFER);
        regular(directory.resolve("image.png"), MAX_PNG);
        try (InputStream descriptor = new ByteArrayInputStream(encoded);
             InputStream png = Files.newInputStream(directory.resolve("image.png"), LinkOption.NOFOLLOW_LINKS)) {
            ReceivedImagePayload payload = ReceivedImagePayload.read(descriptor, png);
            if (!Arrays.equals(encoded, ImageOfferCodec.encode(payload.offer))) throw fail(Code.INVALID_STATE);
            return payload;
        }
    }

    private void requireMatch(Path directory, Prepared operation) throws IOException {
        directory(directory);
        if (entries(directory, 2).size() != 2) throw fail(Code.INVALID_STATE);
        ReceivedImagePayload actual = readPair(directory);
        if (!Arrays.equals(operation.offer, ImageOfferCodec.encode(actual.offer))
                || !Arrays.equals(operation.png, actual.image.bytes())) throw fail(Code.CONFLICT);
    }

    private <T> T locked(Work<T> work) throws IOException {
        return safe(() -> {
            directory(root);
            Path lock = root.resolve("stage.lock");
            if (regular(lock, 0) != 0) throw fail(Code.INVALID_STATE);
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = acquire(channel)) { return work.run(); }
        });
    }
    private static FileLock acquire(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw fail(Code.BUSY);
            return lock;
        } catch (OverlappingFileLockException busy) { throw fail(Code.BUSY); }
    }
    private static void directory(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, NO_LINKS).isDirectory()) throw fail(Code.INVALID_STATE);
    }
    private static long regular(Path path, long cap) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, NO_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > cap) throw fail(Code.INVALID_STATE);
        return attributes.size();
    }
    private static List<Path> entries(Path directory, int cap) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (result.size() == cap) throw fail(Code.CAPACITY);
                result.add(path);
            }
        }
        return result;
    }
    private static byte[] readBounded(Path file, int cap) throws IOException {
        long length = regular(file, cap);
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[(int) length];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) throw fail(Code.INVALID_STATE);
                if (read == 0) {
                    int value = input.read();
                    if (value < 0) throw fail(Code.INVALID_STATE);
                    bytes[offset++] = (byte) value;
                } else offset += read;
            }
            if (input.read() != -1) throw fail(Code.INVALID_STATE);
            return bytes;
        }
    }
    private static void writeNew(Path file, byte[] bytes) throws IOException {
        try (FileChannel output = FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) output.write(buffer);
            output.force(true);
        }
    }
    private static void requireId(UUID id) throws IOException {
        if (id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0)) {
            throw fail(Code.INVALID_STATE);
        }
    }
    private static <T> T safe(Work<T> work) throws IOException {
        try { return work.run(); }
        catch (Failure known) { throw known; }
        catch (ImagePreparationException known) { throw known; }
        catch (Exception rejected) { throw fail(Code.IO); }
        catch (OutOfMemoryError exhausted) { throw lowMemory(); }
    }
    private static Failure fail(Code code) { return new Failure(code); }
    private static ImagePreparationException lowMemory() {
        return new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
    }
}
