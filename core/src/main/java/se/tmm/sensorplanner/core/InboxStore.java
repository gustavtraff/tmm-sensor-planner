package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Private metadata staging only: never applies drafts, resolves conflicts or acknowledges transport. */
public final class InboxStore {
    interface Committer { void replace(Path source, Path target) throws IOException; }
    private interface Operation<T> { T run() throws IOException; }
    private final Path directory;
    private final Path data;
    private final Committer committer;

    public InboxStore(Path directory) {
        this(directory, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    InboxStore(Path directory, Committer committer) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.data = directory.resolve("inbox.bin");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    /** Explicitly starts without receipt history. Refuses every existing file, even invalid data. */
    public synchronized Snapshot initializeNew() throws IOException {
        return locked(() -> {
            if (!Files.notExists(data, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("Inbox already exists or is unavailable; initialization refused");
            }
            Snapshot empty = new Snapshot(UUID.randomUUID(), Collections.emptyList());
            write(empty);
            return empty;
        });
    }

    /** Missing, unreadable or unsupported storage is an error, never an empty inbox. */
    public synchronized Snapshot load() throws IOException { return locked(this::read); }

    /** Returns null only when a valid committed inbox has no matching logical receipt. */
    public synchronized Receipt find(String contextId, TmmDraft incoming) throws IOException {
        String context = context(contextId);
        TmmDraft metadata = incoming(incoming);
        return locked(() -> match(read(), context, metadata));
    }

    /** The caller supplies validated source context and an immutable local observation, or null. */
    public synchronized Receipt receive(String contextId, TmmDraft incoming,
                                        TmmDraft observedLocal, Instant firstObservedAt) throws IOException {
        String context = context(contextId);
        TmmDraft metadata = incoming(incoming);
        TmmDraft observed = observation(metadata.uid, observedLocal);
        Objects.requireNonNull(firstObservedAt, "firstObservedAt");
        return locked(() -> {
            Snapshot saved = read();
            Receipt existing = match(saved, context, metadata);
            if (existing != null) return existing;
            List<Receipt> receipts = new ArrayList<>(saved.receipts);
            Receipt added = new Receipt(UUID.randomUUID(), context, firstObservedAt, metadata,
                    Collections.singletonList(observed));
            receipts.add(added);
            write(new Snapshot(saved.inboxId, receipts));
            return added;
        });
    }

    /** Retains review evidence without changing the receipt's incoming variant or any live draft. */
    public synchronized Receipt retainLocalObservation(UUID receiptId, TmmDraft observedLocal) throws IOException {
        Objects.requireNonNull(receiptId, "receiptId");
        return locked(() -> {
            Snapshot saved = read();
            List<Receipt> receipts = new ArrayList<>(saved.receipts);
            for (int i = 0; i < receipts.size(); i++) {
                Receipt receipt = receipts.get(i);
                if (!receipt.id.equals(receiptId)) continue;
                TmmDraft observed = observation(receipt.incoming.uid, observedLocal);
                for (TmmDraft old : receipt.localObservations) {
                    if (InboxCodec.sameObservation(old, observed)) return receipt;
                }
                List<TmmDraft> observations = new ArrayList<>(receipt.localObservations);
                observations.add(observed);
                Receipt updated = new Receipt(receipt.id, receipt.contextId, receipt.firstObservedAt,
                        receipt.incoming, observations);
                receipts.set(i, updated);
                write(new Snapshot(saved.inboxId, receipts));
                return updated;
            }
            throw failure("Receipt not found; no observation stored");
        });
    }

    private static Receipt match(Snapshot saved, String context, TmmDraft incoming) {
        for (Receipt receipt : saved.receipts) {
            if (receipt.contextId.equals(context) && InboxCodec.sameMetadata(receipt.incoming, incoming)) return receipt;
        }
        return null;
    }

    private static String context(String value) throws IOException {
        Objects.requireNonNull(value, "contextId");
        if (value.isEmpty() || !value.equals(value.trim())) throw new IllegalArgumentException("Context must be nonempty and canonical");
        InboxCodec.textBytes(value);
        return value;
    }

    private static TmmDraft incoming(TmmDraft value) throws IOException {
        Objects.requireNonNull(value, "incoming");
        if (value.imageRef != null) throw new IllegalArgumentException("Incoming metadata must not contain a local image reference");
        TmmDraft copy = metadataCopy(value, 1);
        InboxCodec.validateMetadata(copy);
        return copy;
    }

    private static TmmDraft observation(UUID uid, TmmDraft value) throws IOException {
        if (value == null) return null;
        if (!uid.equals(value.uid)) throw new IllegalArgumentException("Local observation has another device UUID");
        TmmDraft copy = metadataCopy(value, value.revision);
        InboxCodec.validateMetadata(copy);
        return copy;
    }

    private static TmmDraft metadataCopy(TmmDraft value, long revision) {
        return new TmmDraft(value.uid, value.displayId, value.type, value.status, value.owner,
                value.createdAt, value.updatedAt, value.deployedAt, value.comment, value.position,
                value.bearingDeg, null, revision);
    }

    private <T> T locked(Operation<T> operation) throws IOException {
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(data)) throw failure("Inbox path is not a private regular store");
            try (FileChannel channel = FileChannel.open(directory.resolve("inbox.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw failure("Inbox is busy; retry explicitly");
                return operation.run();
            }
        } catch (OverlappingFileLockException e) {
            throw failure("Inbox is busy; retry explicitly");
        } catch (SafeFailure e) {
            throw e;
        } catch (IOException e) {
            // A replacement may already have committed. Do not expose paths or imply rollback.
            throw failure("Inbox I/O failed; reload committed state before retrying");
        }
    }

    private Snapshot read() throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > InboxCodec.MAX_BYTES) throw failure("Inbox exceeds its file limit; original data preserved");
                buffer.write(chunk, 0, count);
            }
            bytes = buffer.toByteArray();
        } catch (NoSuchFileException e) {
            throw failure("Inbox not initialized or missing; no empty history inferred");
        }
        return InboxCodec.decode(bytes);
    }

    private void write(Snapshot snapshot) throws IOException {
        byte[] bytes = InboxCodec.encode(snapshot);
        Path temporary = Files.createTempFile(directory, "inbox-", ".tmp");
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

    static IOException failure(String message) { return new SafeFailure(message); }
    private static final class SafeFailure extends IOException {
        SafeFailure(String message) { super(message); }
    }

    public static final class Snapshot {
        public final UUID inboxId;
        public final List<Receipt> receipts;
        Snapshot(UUID inboxId, List<Receipt> receipts) {
            this.inboxId = Objects.requireNonNull(inboxId, "inboxId");
            this.receipts = Collections.unmodifiableList(new ArrayList<>(receipts));
        }
    }

    public static final class Receipt {
        public final UUID id;
        public final String contextId;
        public final Instant firstObservedAt;
        /** Metadata-only carrier: imageRef is null and revision is 1, not a sender version. */
        public final TmmDraft incoming;
        /** Null entries record observed absence; present carriers retain local revision but no imageRef. */
        public final List<TmmDraft> localObservations;
        Receipt(UUID id, String contextId, Instant firstObservedAt, TmmDraft incoming, List<TmmDraft> localObservations) {
            this.id = Objects.requireNonNull(id, "id");
            this.contextId = Objects.requireNonNull(contextId, "contextId");
            this.firstObservedAt = Objects.requireNonNull(firstObservedAt, "firstObservedAt");
            this.incoming = Objects.requireNonNull(incoming, "incoming");
            this.localObservations = Collections.unmodifiableList(new ArrayList<>(localObservations));
        }
    }
}
