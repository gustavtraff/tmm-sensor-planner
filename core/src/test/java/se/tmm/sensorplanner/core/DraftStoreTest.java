package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public final class DraftStoreTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            process(args[0], Paths.get(args[1]));
            return;
        }
        Path root = Files.createTempDirectory("tmm-store-test-");
        try {
            roundTrip(root.resolve("round-trip"));
            fixtureAndFailures(root.resolve("fixture"));
            limits(root.resolve("limits"));
            legacyRevisionReuseIsNotAFreshnessToken(root.resolve("revision-reuse"));
            System.out.println("Storage tests passed (reopen, fixture, failures, limits and locking).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
    }

    private static TmmDraft sample() {
        return new TmmDraft(new UUID(0, 1), "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE,
                "Test group", Instant.ofEpochSecond(0, 123), Instant.ofEpochSecond(1, 456),
                Instant.ofEpochSecond(1), "Synthetic åäö 🌲", new Position(0, 0),
                0.0, "synthetic-image", 2);
    }

    private static TmmDraft edit(TmmDraft draft) {
        return draft.edit(draft.displayId, draft.type, DeviceStatus.RETRIEVED, draft.owner,
                null, "", new Position(1, 1), null, null, draft.updatedAt.plusSeconds(1));
    }

    private static void roundTrip(Path directory) throws Exception {
        DraftStore store = new DraftStore(directory);
        assert store.load().isEmpty();
        TmmDraft first = TmmDraft.create("PIR 134", DeviceType.PIR, "Group", new Position(0, 0), Instant.EPOCH);
        TmmDraft second = TmmDraft.create("PIR 134", DeviceType.GATEWAY, "Group", new Position(0, 1), Instant.EPOCH);
        store.save(first);
        store.save(second);
        assert new DraftStore(directory).load().size() == 2;
        expectFailure(() -> store.save(first));
        TmmDraft edited = edit(first);
        store.save(edited);
        expectFailure(() -> store.save(edited));
        expectFailure(() -> store.delete(first.uid, first.revision));
        assert new DraftStore(directory).load().get(0).status == DeviceStatus.RETRIEVED;
        byte[] original = Files.readAllBytes(directory.resolve("drafts.bin"));
        DraftStore failing = new DraftStore(directory, (from, to) -> { throw new IOException("Simulated replacement failure"); });
        expectFailure(() -> failing.save(edit(edited)));
        assert Arrays.equals(original, Files.readAllBytes(directory.resolve("drafts.bin")));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assert files.noneMatch(path -> path.toString().endsWith(".tmp"));
        }
        try (FileChannel channel = FileChannel.open(directory.resolve("drafts.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            expectFailure(store::load);
            expectFailure(() -> store.save(edit(edited)));
        }
        store.delete(first.uid, edited.revision);
        assert store.load().size() == 1;
        assert store.load().get(0).uid.equals(second.uid);
        store.delete(second.uid, second.revision);
        assert new DraftStore(directory).load().isEmpty();
        try {
            store.load().add(first);
            throw new AssertionError("Mutable snapshot");
        } catch (UnsupportedOperationException expected) { }
    }

    /** Characterizes v1 only: D must not use its revision or full snapshot bytes as an incarnation token. */
    private static void legacyRevisionReuseIsNotAFreshnessToken(Path directory) throws Exception {
        DraftStore store = new DraftStore(directory);
        TmmDraft original = TmmDraft.create("PIR SYNTHETIC D", DeviceType.PIR, "SYNTHETIC ROLE",
                new Position(0, 1), Instant.EPOCH);
        store.save(original);
        TmmDraft preparedBeforeDeletion = edit(original);
        byte[] reviewed = Files.readAllBytes(directory.resolve("drafts.bin"));
        store.delete(original.uid, original.revision);
        store.save(original);
        assert Arrays.equals(reviewed, Files.readAllBytes(directory.resolve("drafts.bin")));
        // Even an identical full-state check cannot distinguish delete/recreate in this legacy format.
        store.save(preparedBeforeDeletion);
        assertSame(preparedBeforeDeletion, new DraftStore(directory).load().get(0));
        store.delete(original.uid, preparedBeforeDeletion.revision);
        store.save(original);
        store.delete(original.uid, original.revision); // A pre-deletion revision can also delete the recreated record.
        assert new DraftStore(directory).load().isEmpty();
    }

    private static void fixtureAndFailures(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path data = directory.resolve("drafts.bin");
        byte[] original = Base64.getDecoder().decode(new String(Files.readAllBytes(
                Paths.get("test-fixtures/synthetic/storage/v1.base64")), java.nio.charset.StandardCharsets.US_ASCII).trim());
        Files.write(data, original);
        DraftStore store = new DraftStore(directory);
        assertSame(sample(), store.load().get(0));
        store.save(edit(sample()));
        TmmDraft reloaded = new DraftStore(directory).load().get(0);
        assertSame(edit(sample()), reloaded);
        for (int length : new int[]{0, 10, original.length - 1}) {
            rejectedUnchanged(store, data, Arrays.copyOf(original, length));
        }
        byte[] corrupt = original.clone();
        corrupt[40] ^= 1;
        rejectedUnchanged(store, data, corrupt);
        byte[] future = original.clone();
        ByteBuffer.wrap(future).putInt(4, 2);
        rejectedUnchanged(store, data, checksum(future));
        byte[] badCount = original.clone();
        ByteBuffer.wrap(badCount).putInt(8, 1001);
        rejectedUnchanged(store, data, checksum(badCount));
        byte[] badLength = original.clone();
        ByteBuffer.wrap(badLength).putInt(28, Integer.MAX_VALUE);
        rejectedUnchanged(store, data, checksum(badLength));
        byte[] badUtf8 = original.clone();
        badUtf8[32] = (byte) 0xff;
        rejectedUnchanged(store, data, checksum(badUtf8));
        byte[] paddedId = original.clone();
        paddedId[32] = ' ';
        rejectedUnchanged(store, data, checksum(paddedId));
        byte[] badType = original.clone();
        badType[43] = 'X';
        rejectedUnchanged(store, data, checksum(badType));
        byte[] invalidRevision = original.clone();
        ByteBuffer.wrap(invalidRevision).putLong(original.length - 40, 0);
        rejectedUnchanged(store, data, checksum(invalidRevision));
        byte[] trailing = Arrays.copyOf(original, original.length + 1);
        rejectedUnchanged(store, data, checksum(trailing));
        byte[] duplicate = new byte[12 + 2 * (original.length - 44) + 32];
        System.arraycopy(original, 0, duplicate, 0, 12);
        ByteBuffer.wrap(duplicate).putInt(8, 2);
        System.arraycopy(original, 12, duplicate, 12, original.length - 44);
        System.arraycopy(original, 12, duplicate, original.length - 32, original.length - 44);
        rejectedUnchanged(store, data, checksum(duplicate));
    }

    private static void limits(Path directory) throws Exception {
        DraftStore store = new DraftStore(directory);
        TmmDraft draft = TmmDraft.create("PIR 134", DeviceType.PIR, "Group", new Position(0, 0), Instant.EPOCH);
        store.save(draft);
        byte[] saved = Files.readAllBytes(directory.resolve("drafts.bin"));
        String huge = String.join("", Collections.nCopies(65537, "x"));
        TmmDraft large = draft.edit(huge, draft.type, draft.status, draft.owner, null, "", draft.position, null, null, Instant.EPOCH);
        expectFailure(() -> store.save(large));
        TmmDraft malformed = draft.edit("\ud800", draft.type, draft.status, draft.owner, null, "", draft.position, null, null, Instant.EPOCH);
        expectFailure(() -> store.save(malformed));
        assert Arrays.equals(saved, Files.readAllBytes(directory.resolve("drafts.bin")));
        Files.write(directory.resolve("drafts.bin"), new byte[16 * 1024 * 1024 + 1]);
        expectFailure(store::load);
        Files.delete(directory.resolve("drafts.bin"));
        Files.createSymbolicLink(directory.resolve("drafts.bin"), directory.resolve("missing.bin"));
        expectFailure(store::load);
        expectFailure(() -> store.save(draft));
        assert Files.isSymbolicLink(directory.resolve("drafts.bin"));
        Files.delete(directory.resolve("drafts.bin"));
        byte[] fixture = Base64.getDecoder().decode(new String(Files.readAllBytes(
                Paths.get("test-fixtures/synthetic/storage/v1.base64")), java.nio.charset.StandardCharsets.US_ASCII).trim());
        int recordSize = fixture.length - 44;
        byte[] full = new byte[12 + 1000 * recordSize + 32];
        System.arraycopy(fixture, 0, full, 0, 12);
        ByteBuffer.wrap(full).putInt(8, 1000);
        for (int i = 0; i < 1000; i++) {
            int offset = 12 + i * recordSize;
            System.arraycopy(fixture, 12, full, offset, recordSize);
            ByteBuffer.wrap(full).putLong(offset + 8, i + 1);
        }
        Files.write(directory.resolve("drafts.bin"), checksum(full));
        assert store.load().size() == 1000;
        expectFailure(() -> store.save(draft));
        assert Arrays.equals(full, Files.readAllBytes(directory.resolve("drafts.bin")));
    }

    private static void rejectedUnchanged(DraftStore store, Path data, byte[] bytes) throws Exception {
        Files.write(data, bytes);
        expectFailure(store::load);
        expectFailure(() -> store.save(TmmDraft.create("Synthetic", DeviceType.MINI, "Test", new Position(0, 0), Instant.EPOCH)));
        expectFailure(() -> store.delete(sample().uid, 2));
        assert Arrays.equals(bytes, Files.readAllBytes(data));
    }

    private static byte[] checksum(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes, bytes.length - 32));
        System.arraycopy(digest, 0, bytes, bytes.length - 32, 32);
        return bytes;
    }

    private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected storage failure");
        } catch (IOException expected) { }
    }

    private static void assertSame(TmmDraft a, TmmDraft b) {
        assert a.uid.equals(b.uid) && a.displayId.equals(b.displayId) && a.type == b.type && a.status == b.status;
        assert a.owner.equals(b.owner) && a.createdAt.equals(b.createdAt) && a.updatedAt.equals(b.updatedAt);
        assert Objects.equals(a.deployedAt, b.deployedAt) && a.comment.equals(b.comment);
        assert a.position.latitude == b.position.latitude && a.position.longitude == b.position.longitude;
        assert Objects.equals(a.bearingDeg, b.bearingDeg) && Objects.equals(a.imageRef, b.imageRef);
        assert a.revision == b.revision;
    }

    private static void process(String mode, Path directory) throws Exception {
        DraftStore store = new DraftStore(directory);
        if (mode.equals("write")) {
            TmmDraft draft = sample();
            store.save(new TmmDraft(draft.uid, draft.displayId, draft.type, draft.status, draft.owner,
                    draft.createdAt, draft.updatedAt, draft.deployedAt, draft.comment, draft.position,
                    draft.bearingDeg, draft.imageRef, 1));
            store.save(sample());
            byte[] golden = Base64.getDecoder().decode(new String(Files.readAllBytes(
                    Paths.get("test-fixtures/synthetic/storage/v1.base64")), java.nio.charset.StandardCharsets.US_ASCII).trim());
            assert Arrays.equals(golden, Files.readAllBytes(directory.resolve("drafts.bin")));
            store.save(TmmDraft.create("Gateway test", DeviceType.GATEWAY, "Test group",
                    new Position(0, 1), Instant.EPOCH));
        } else if (mode.equals("read")) {
            assert store.load().size() == 2;
            assert store.load().get(1).type == DeviceType.GATEWAY;
            assert store.load().get(1).bearingDeg == null && store.load().get(1).imageRef == null;
            assertSame(sample(), store.load().get(0));
            System.out.println("Separate-process storage reopen passed.");
        } else {
            throw new AssertionError("Unknown test mode");
        }
    }
}
