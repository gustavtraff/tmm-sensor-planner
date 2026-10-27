package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static se.tmm.sensorplanner.core.LocalV2.*;
import static se.tmm.sensorplanner.core.LocalV2Store.*;

public final class LocalV2MigrationTest {
    private interface Task { void run() throws Exception; }
    private static final String FIXTURES = "test-fixtures/synthetic/storage-v2/";
    public static void main(String[] args) throws Exception {
        if (args.length != 0) { child(args); return; }
        Path root = Files.createTempDirectory("tmm-migration-");
        try {
            golden(root.resolve("golden"), "test-fixtures/synthetic/storage/v1.base64", "migrated-v1-v2.base64");
            golden(root.resolve("empty"), FIXTURES + "empty-v1.base64", "empty-v2.base64");
            preservedFieldsAndWriters(root.resolve("fields"));
            rejectedSources(root.resolve("invalid"));
            limits(root.resolve("limits"));
            failures(root.resolve("failures"));
            locksAndRaces(root.resolve("locks"));
            processCrash(root.resolve("before"), false);
            processCrash(root.resolve("after"), true);
            System.out.println("Local v2 migration passed (independent old/new fixtures, preservation, bounds, legacy rejection, locks, faults and process crashes).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
    private static byte[] fixture(String name) throws IOException {
        return Base64.getDecoder().decode(new String(Files.readAllBytes(Paths.get(name)), StandardCharsets.US_ASCII).trim());
    }
    private static byte[] source() throws IOException { return fixture("test-fixtures/synthetic/storage/v1.base64"); }
    private static Path data(Path directory) { return directory.resolve("drafts.bin"); }
    private static void seed(Path directory, byte[] bytes) throws IOException {
        Files.createDirectories(directory); Files.write(data(directory), bytes);
    }
    private static void golden(Path directory, String source, String expected) throws Exception {
        seed(directory, fixture(source));
        LocalV2Store store = new LocalV2Store(directory);
        Snapshot actual = store.migrateV1();
        Snapshot oracle = LocalV2Codec.decode(fixture(FIXTURES + expected));
        assert actual.decisions.isEmpty() && !actual.storeId.equals(oracle.storeId);
        // Replace only the random dataset identity, leaving the independently specified body unchanged.
        assert Arrays.equals(fixture(FIXTURES + expected), LocalV2Codec.encode(new Snapshot(oracle.storeId, actual.slots, actual.decisions)));
        byte[] committed = Files.readAllBytes(data(directory));
        assert store.migrateV1().storeId.equals(actual.storeId);
        assert new LocalV2Store(directory).load().storeId.equals(actual.storeId);
        assert Arrays.equals(committed, Files.readAllBytes(data(directory)));
        legacyFailure(() -> new DraftStore(directory).load());
        assert Arrays.equals(committed, Files.readAllBytes(data(directory)));
    }
    private static TmmDraft draft(int n, DeviceType type) {
        return new TmmDraft(new UUID(0, n), "SYNTHETIC " + n, type, DeviceStatus.ACTIVE, "ROLE åäö 🌲",
                Instant.ofEpochSecond(10, 123), Instant.ofEpochSecond(20, 456), Instant.ofEpochSecond(15, 789),
                "SYNTHETIC metadata", new Position(-0.0, -0.0), type.supportsBearing() ? -0.0 : null,
                "synthetic-" + n + ".png", 1);
    }
    private static TmmDraft edit(TmmDraft d, String comment) {
        return d.edit(d.displayId, d.type, d.status, d.owner, d.deployedAt, comment, d.position,
                d.bearingDeg, d.imageRef, d.updatedAt.plusNanos(1));
    }
    private static void preservedFieldsAndWriters(Path root) throws Exception {
        Path directory = root.resolve("drafts"); DraftStore legacy = new DraftStore(directory);
        int n = 1;
        for (DeviceType type : DeviceType.values()) {
            TmmDraft d = draft(n++, type); legacy.save(d);
            legacy.save(edit(d, d.comment));
        }
        TmmDraft removed = draft(99, DeviceType.PIR); legacy.save(removed); legacy.delete(removed.uid, removed.revision);
        List<TmmDraft> before = legacy.load();
        for (TmmDraft d : before) Files.write(directory.resolve(d.imageRef), new byte[]{1, 3, 7});
        Path orphan = directory.resolve("synthetic-orphan.png"); Files.write(orphan, new byte[]{2, 4});
        InboxStore inbox = new InboxStore(root.resolve("inbox")); inbox.initializeNew();
        TmmDraft metadata = LocalV2Binary.local(before.get(0), null, 1);
        inbox.receive("SYNTHETIC SOURCE", metadata, before.get(0), Instant.EPOCH);
        byte[] inboxBytes = Files.readAllBytes(root.resolve("inbox/inbox.bin"));
        Object lockIdentity = Files.readAttributes(directory.resolve("drafts.lock"), java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        LocalV2Store store = new LocalV2Store(directory); Snapshot migrated = store.migrateV1();
        assert migrated.slots.size() == before.size() && migrated.decisions.isEmpty();
        for (int i = 0; i < before.size(); i++) {
            State state = migrated.slots.get(i); TmmDraft old = before.get(i);
            assert state.presence == Presence.ACTIVE && state.version == 1 && state.draft.revision == 2;
            same(old, state.draft);
            assert Arrays.equals(new byte[]{1, 3, 7}, Files.readAllBytes(directory.resolve(old.imageRef)));
            assert !state.uid.equals(removed.uid);
        }
        assert Objects.equals(lockIdentity, Files.readAttributes(directory.resolve("drafts.lock"), java.nio.file.attribute.BasicFileAttributes.class).fileKey());
        assert Arrays.equals(inboxBytes, Files.readAllBytes(root.resolve("inbox/inbox.bin")));
        assert Arrays.equals(new byte[]{2, 4}, Files.readAllBytes(orphan));
        byte[] committed = Files.readAllBytes(data(directory));
        legacyFailure(() -> legacy.save(edit(before.get(0), "stale v1 edit")));
        legacyFailure(() -> legacy.delete(before.get(0).uid, before.get(0).revision));
        assert Arrays.equals(committed, Files.readAllBytes(data(directory)));
        State first = migrated.slots.get(0);
        State updated = store.save(migrated.storeId, first, edit(first.draft, "new v2 edit"));
        assert updated.version == 2 && updated.draft.revision == 3;
        State deleted = store.delete(migrated.storeId, updated);
        State readded = store.save(migrated.storeId, deleted, LocalV2Binary.local(updated.draft, updated.draft.imageRef, 1));
        assert readded.version == 4 && readded.draft.revision == 1;
        // A later invocation must not remigrate v2, reset versions/history, or undo ordinary edits.
        UUID receipt = inbox.receive("SYNTHETIC SOURCE", edit(metadata, "remote"), null, Instant.EPOCH).id;
        InboxStore.Snapshot in = inbox.load(); Snapshot local = store.load();
        ImportDecisionPreparation.Prepared prepared = ImportDecisionPreparation.keepLocal(in, receipt, local).prepare(in, local);
        store.commit(inbox, prepared);
        byte[] withHistory = Files.readAllBytes(data(directory));
        Snapshot retried = store.migrateV1();
        assert retried.storeId.equals(migrated.storeId) && retried.slots.get(0).version == 4 && retried.decisions.size() == 1;
        assert Arrays.equals(withHistory, Files.readAllBytes(data(directory)));
    }
    private static void rejectedSources(Path directory) throws Exception {
        Files.createDirectories(directory); LocalV2Store store = new LocalV2Store(directory);
        expect(Reason.UNKNOWN, store::migrateV1); assert !Files.exists(data(directory));
        byte[] v1 = source(); List<byte[]> invalid = new ArrayList<>();
        invalid.add(new byte[0]); invalid.add(Arrays.copyOf(v1, v1.length - 1));
        byte[] corrupt = v1.clone(); corrupt[32] ^= 1; invalid.add(corrupt);
        for (int[] field : new int[][]{{4, 3}, {8, -1}, {8, 1001}, {28, Integer.MAX_VALUE}}) {
            byte[] bytes = v1.clone(); ByteBuffer.wrap(bytes).putInt(field[0], field[1]); invalid.add(checksum(bytes));
        }
        for (int[] field : new int[][]{{32, 255}, {32, 32}, {43, 88}}) {
            byte[] bytes = v1.clone(); bytes[field[0]] = (byte) field[1]; invalid.add(checksum(bytes));
        }
        byte[] zeroRevision = v1.clone(); ByteBuffer.wrap(zeroRevision).putLong(v1.length - 40, 0); invalid.add(checksum(zeroRevision));
        invalid.add(checksum(Arrays.copyOf(v1, v1.length + 1)));
        byte[] duplicate = new byte[12 + 2 * (v1.length - 44) + 32];
        System.arraycopy(v1, 0, duplicate, 0, 12); ByteBuffer.wrap(duplicate).putInt(8, 2);
        System.arraycopy(v1, 12, duplicate, 12, v1.length - 44);
        System.arraycopy(v1, 12, duplicate, v1.length - 32, v1.length - 44); invalid.add(checksum(duplicate));
        byte[] badV2 = fixture(FIXTURES + "history-v2.base64"); badV2[30] ^= 1; invalid.add(badV2);
        invalid.add(new byte[LocalV2Codec.MAX_BYTES + 1]);
        for (byte[] bytes : invalid) {
            Files.write(data(directory), bytes);
            expect(Reason.UNKNOWN, store::migrateV1);
            assert Arrays.equals(bytes, Files.readAllBytes(data(directory))) && temps(directory) == 0;
        }
        byte[] existingV2 = fixture(FIXTURES + "history-v2.base64"); Files.write(data(directory), existingV2);
        Snapshot retained = store.migrateV1();
        assert retained.slots.size() == 4 && retained.slots.get(1).presence == Presence.DELETED && retained.decisions.size() == 5;
        assert Arrays.equals(existingV2, Files.readAllBytes(data(directory)));
        byte[] exhaustedRevision = v1.clone(); ByteBuffer.wrap(exhaustedRevision).putLong(v1.length - 40, Long.MAX_VALUE);
        Files.write(data(directory), checksum(exhaustedRevision));
        State exhausted = store.migrateV1().slots.get(0);
        assert exhausted.version == 1 && exhausted.draft.revision == Long.MAX_VALUE;
        Files.delete(data(directory));
        Path actual = directory.resolve("source.bin"); Files.write(actual, v1); Files.createSymbolicLink(data(directory), actual);
        expect(Reason.UNKNOWN, store::migrateV1);
        assert Files.isSymbolicLink(data(directory)) && Arrays.equals(v1, Files.readAllBytes(actual));
    }
    private static void limits(Path directory) throws Exception {
        byte[] v1 = source(); int record = v1.length - 44;
        byte[] thousand = new byte[44 + 1000 * record];
        System.arraycopy(v1, 0, thousand, 0, 12); ByteBuffer.wrap(thousand).putInt(8, 1000);
        for (int i = 0; i < 1000; i++) {
            int offset = 12 + i * record; System.arraycopy(v1, 12, thousand, offset, record);
            ByteBuffer.wrap(thousand).putLong(offset + 8, i + 1);
        }
        seed(directory, checksum(thousand));
        Snapshot migrated = new LocalV2Store(directory).migrateV1();
        assert migrated.slots.size() == 1000 && migrated.decisions.isEmpty();
        for (State slot : migrated.slots) assert slot.version == 1 && slot.draft.revision == 2;
        // Independent v1 framing: expand only displayId and UUID in the pinned source body.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(0x544d4d44); out.writeInt(1); out.writeInt(256);
            int lastLength = LocalV2Codec.MAX_BYTES - (44 + 256 * (record - 7) + 255 * 65536);
            assert lastLength > 0 && lastLength <= 65536;
            for (int i = 0; i < 256; i++) {
                out.writeLong(0); out.writeLong(i); int length = i == 255 ? lastLength : 65536;
                out.writeInt(length); byte[] label = new byte[length]; Arrays.fill(label, (byte) 'x'); out.write(label);
                out.write(v1, 39, v1.length - 32 - 39);
            }
            out.write(new byte[32]);
        }
        byte[] full = checksum(buffer.toByteArray()); assert full.length == LocalV2Codec.MAX_BYTES;
        Files.write(data(directory), full); assert new DraftStore(directory).load().size() == 256;
        LocalV2Store noWrite = new LocalV2Store(directory, new LocalV2Store.Committer() {
            public void writeAndSync(Path a, byte[] b) { throw new AssertionError("Oversize migration created a temp"); }
            public void replace(Path a, Path b) { throw new AssertionError("Oversize migration reached replacement"); }
        });
        expect(Reason.INVALID, noWrite::migrateV1);
        assert Arrays.equals(full, Files.readAllBytes(data(directory))) && temps(directory) == 0;
    }
    private static void failures(Path directory) throws Exception {
        seed(directory, source()); byte[] old = Files.readAllBytes(data(directory));
        for (boolean partial : new boolean[]{true, false}) {
            LocalV2Store failed = new LocalV2Store(directory, new LocalV2Store.Committer() {
                public void writeAndSync(Path p, byte[] b) throws IOException {
                    Files.write(p, partial ? Arrays.copyOf(b, b.length / 2) : b);
                    throw new IOException("private-write-or-sync-failure");
                }
                public void replace(Path a, Path b) { throw new AssertionError("Write failure reached rename"); }
            });
            expect(Reason.UNKNOWN, failed::migrateV1);
            assert Arrays.equals(old, Files.readAllBytes(data(directory))) && temps(directory) == 0;
        }
        LocalV2Store unsupported = new LocalV2Store(directory, (a, b) -> {
            throw new AtomicMoveNotSupportedException("private-source", "private-target", "unsupported");
        });
        expect(Reason.UNKNOWN, unsupported::migrateV1);
        assert Arrays.equals(old, Files.readAllBytes(data(directory))) && temps(directory) == 0;
        LocalV2Store after = new LocalV2Store(directory, (a, b) -> {
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("private-after-commit");
        });
        expect(Reason.UNKNOWN, after::migrateV1);
        LocalV2Store healthy = new LocalV2Store(directory); UUID committedId = healthy.load().storeId;
        byte[] committed = Files.readAllBytes(data(directory));
        assert healthy.migrateV1().storeId.equals(committedId) && Arrays.equals(committed, Files.readAllBytes(data(directory)));
        Files.write(data(directory), old);
        LocalV2Store cleanup = new LocalV2Store(directory, (a, b) -> {
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectory(a); Files.write(a.resolve("synthetic"), new byte[]{1});
        });
        expect(Reason.UNKNOWN, cleanup::migrateV1); assert healthy.load().slots.size() == 1;
    }
    private static void locksAndRaces(Path root) throws Exception {
        Path directory = root.resolve("drafts"); seed(directory, source());
        // Create the permanent lock using the existing v1 caller before the migration attempt.
        new DraftStore(directory).load();
        try (FileChannel channel = FileChannel.open(directory.resolve("drafts.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) { expect(Reason.BUSY, () -> new LocalV2Store(directory).migrateV1()); }
        AtomicInteger replacements = new AtomicInteger();
        LocalV2Store.Committer committer = (a, b) -> {
            legacyFailure(() -> new DraftStore(directory).save(draft(88, DeviceType.PIR)));
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); replacements.incrementAndGet();
        };
        CountDownLatch start = new CountDownLatch(1); ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                start.await();
                try { return new LocalV2Store(directory, committer).migrateV1().storeId; }
                catch (Failure f) { assert f.reason == Reason.BUSY; return null; }
            }));
            start.countDown(); UUID savedId = null;
            for (Future<UUID> result : results) {
                UUID id = result.get(15, TimeUnit.SECONDS);
                if (id != null) { assert savedId == null || savedId.equals(id); savedId = id; }
            }
            assert savedId != null && replacements.get() == 1;
        } finally { pool.shutdownNow(); }
        Files.write(data(directory), source()); Process holder = process(root, "hold");
        try {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(root.resolve("ready")) && System.nanoTime() < until && holder.isAlive()) Thread.sleep(10);
            assert Files.exists(root.resolve("ready"));
            expect(Reason.BUSY, () -> new LocalV2Store(directory).migrateV1());
            assert Arrays.equals(source(), Files.readAllBytes(data(directory)));
        } finally { holder.destroyForcibly(); assert holder.waitFor(10, TimeUnit.SECONDS); }
    }
    private static void processCrash(Path root, boolean after) throws Exception {
        Path directory = root.resolve("drafts"); seed(directory, source());
        Process process = process(root, after ? "after" : "before");
        try { assert process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 77; }
        finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); } }
        UUID candidate = UUID.fromString(new String(Files.readAllBytes(root.resolve("candidate")), StandardCharsets.US_ASCII));
        LocalV2Store store = new LocalV2Store(directory);
        if (after) {
            Snapshot reopened = store.load(); assert reopened.storeId.equals(candidate) && reopened.slots.size() == 1 && reopened.decisions.isEmpty();
            byte[] committed = Files.readAllBytes(data(directory));
            assert store.migrateV1().storeId.equals(candidate) && Arrays.equals(committed, Files.readAllBytes(data(directory)));
            legacyFailure(() -> new DraftStore(directory).load());
            assert temps(directory) == 0;
        } else {
            expect(Reason.UNKNOWN, store::load);
            assert Arrays.equals(source(), Files.readAllBytes(data(directory))) && new DraftStore(directory).load().size() == 1;
            assert temps(directory) == 1;
            Snapshot retried = store.migrateV1(); assert !retried.storeId.equals(candidate) && temps(directory) == 1;
        }
    }
    private static Process process(Path root, String mode) throws IOException {
        return new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-ea", "-cp",
                System.getProperty("java.class.path"), LocalV2MigrationTest.class.getName(), mode, root.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("child.log").toFile()).start();
    }
    private static void child(String[] args) throws Exception {
        Path root = Paths.get(args[1]), directory = root.resolve("drafts");
        if (args[0].equals("hold")) {
            try (FileChannel channel = FileChannel.open(directory.resolve("drafts.lock"), StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) { Files.write(root.resolve("ready"), new byte[]{1}); Thread.sleep(20000); }
            return;
        }
        new LocalV2Store(directory, (a, b) -> {
            Snapshot candidate = LocalV2Codec.decode(Files.readAllBytes(a));
            Files.write(root.resolve("candidate"), candidate.storeId.toString().getBytes(StandardCharsets.US_ASCII));
            if (args[0].equals("after")) Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Runtime.getRuntime().halt(77);
        }).migrateV1();
        throw new AssertionError("Expected halted process");
    }
    private static long temps(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(".tmp")).count();
        }
    }
    private static byte[] checksum(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes, bytes.length - 32));
        System.arraycopy(digest, 0, bytes, bytes.length - 32, 32); return bytes;
    }
    private static void expect(Reason reason, Task task) throws Exception {
        try { task.run(); throw new AssertionError("Expected failed migration"); }
        catch (Failure f) { assert f.reason == reason : f.reason; assert f.getCause() == null && f.getSuppressed().length == 0; }
    }
    private static void legacyFailure(Task task) throws IOException {
        try { task.run(); throw new AssertionError("Legacy caller unexpectedly wrote/read v2 or ignored its lock"); }
        catch (IOException expected) { }
        catch (Exception unexpected) { throw new AssertionError(unexpected); }
    }
    private static void same(TmmDraft a, TmmDraft b) {
        assert a.uid.equals(b.uid) && a.displayId.equals(b.displayId) && a.type == b.type && a.status == b.status;
        assert a.owner.equals(b.owner) && a.createdAt.equals(b.createdAt) && a.updatedAt.equals(b.updatedAt);
        assert Objects.equals(a.deployedAt, b.deployedAt) && a.comment.equals(b.comment) && a.revision == b.revision;
        assert Double.doubleToRawLongBits(a.position.latitude) == Double.doubleToRawLongBits(b.position.latitude);
        assert Double.doubleToRawLongBits(a.position.longitude) == Double.doubleToRawLongBits(b.position.longitude);
        assert a.bearingDeg == null ? b.bearingDeg == null : b.bearingDeg != null
                && Double.doubleToRawLongBits(a.bearingDeg) == Double.doubleToRawLongBits(b.bearingDeg);
        assert Objects.equals(a.imageRef, b.imageRef);
    }
}
