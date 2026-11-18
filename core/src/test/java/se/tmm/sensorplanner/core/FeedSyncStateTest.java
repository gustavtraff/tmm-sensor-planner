package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class FeedSyncStateTest {
    private static final String CONTEXT = "mission-api-v1|tak.example.invalid|00000000-0000-4000-8026-000000000401";
    private static final String FEED = "SYNTHETIC FEED", STREAM = "tak.example.invalid:8089:ssl";
    private static final UUID SYNC = u(0x801), INBOX = u(0x502);
    private static final UUID DEVICE = new UUID(0x4000L, 0x8014000000000003L), DEVICE2 = new UUID(0x4000L, 0x8014000000000004L);
    private static final byte[] H1 = hex("d6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5");
    private static final String DIR = "test-fixtures/synthetic/feed-sync-state-proposal/";
    private interface Action { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("tmm-feed-sync-test-");
        try {
            goldens();
            rejections();
            limits();
            store(root.resolve("store"));
            failures(root.resolve("fail"));
            System.out.println("Feed sync state passed (goldens, rejections, limits, store, stale writes, failed commits and future versions).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static FeedSyncState.Snapshot snap(FeedSyncState.Record... records) {
        return new FeedSyncState.Snapshot(SYNC, INBOX, true, CONTEXT, FEED, STREAM, Arrays.asList(records));
    }

    private static FeedSyncState.Record current() { return new FeedSyncState.Record(DEVICE, u(0x601), u(0x601), 2, H1, null, null); }
    private static FeedSyncState.Record changed() { return new FeedSyncState.Record(DEVICE, u(0x602), u(0x601), 2, H1, null, null); }
    private static FeedSyncState.Record adopting() { return new FeedSyncState.Record(DEVICE2, u(0x603), u(0x603), 1, null, u(0x901), u(0x902)); }

    private static void goldens() throws Exception {
        Object[][] cases = {{"empty.bin", 202, snap()}, {"current.bin", 286, snap(current())},
                {"changed-adopting.bin", 370, snap(adopting(), changed())}}; // input order is canonicalised
        for (Object[] c : cases) {
            byte[] golden = Files.readAllBytes(Paths.get(DIR + c[0]));
            assert golden.length == (Integer) c[1] : c[0];
            FeedSyncState.Snapshot decoded = FeedSyncState.decode(golden);
            assert Arrays.equals(golden, FeedSyncState.encode(decoded)) : c[0];
            assert Arrays.equals(golden, FeedSyncState.encode((FeedSyncState.Snapshot) c[2])) : c[0];
        }
        FeedSyncState.Snapshot two = FeedSyncState.decode(Files.readAllBytes(Paths.get(DIR + "changed-adopting.bin")));
        assert two.records.size() == 2 && two.following && two.syncId.equals(SYNC) && two.inboxId.equals(INBOX);
        assert two.records.get(0).remoteReceiptId.equals(u(0x602)) && Arrays.equals(two.records.get(0).imageDigest(), H1);
        assert two.records.get(1).imageKind == 1 && two.records.get(1).adoptNew.equals(u(0x901)) && two.records.get(1).adoptOld.equals(u(0x902));
    }

    private static void rejections() throws Exception {
        byte[] good = Files.readAllBytes(Paths.get(DIR + "current.bin"));
        rejects(() -> FeedSyncState.decode(new byte[0]));
        rejects(() -> FeedSyncState.decode(Arrays.copyOf(good, good.length - 1)));
        rejects(() -> FeedSyncState.decode(Arrays.copyOf(good, good.length + 1)));
        byte[] flipped = good.clone();
        flipped[60] ^= 1;
        rejects(() -> FeedSyncState.decode(flipped));
        byte[] body = Arrays.copyOf(good, good.length - 32);
        for (int[] mv : new int[][]{{3, 0x54 ^ 1}, {7, 2}, {7, 0}}) { // magic, version 2, version 0
            byte[] bad = body.clone();
            bad[mv[0]] = (byte) mv[1];
            rejectsBody(bad);
        }
        byte[] flag = body.clone();
        flag[40] = 2;
        rejectsBody(flag);

        for (UUID nil : new UUID[]{new UUID(0, 0)}) {
            rejectsBody(raw(nil, INBOX, 1, CONTEXT, FEED, STREAM, 0));
            rejectsBody(raw(SYNC, nil, 1, CONTEXT, FEED, STREAM, 0));
        }
        String longContext = CONTEXT + new String(new char[FeedSyncState.MAX_CONTEXT_BYTES - CONTEXT.length() + 1]).replace('\0', 'x');
        String longLabel = new String(new char[FeedSyncState.MAX_LABEL_BYTES + 1]).replace('\0', 'x');
        String[][] texts = {{"", FEED, STREAM}, {"mission-api-v1|", FEED, STREAM}, {"other|tak.example.invalid|x", FEED, STREAM},
                {CONTEXT + " ", FEED, STREAM}, {longContext, FEED, STREAM}, {CONTEXT, "", STREAM}, {CONTEXT, " FEED", STREAM},
                {CONTEXT, longLabel, STREAM}, {CONTEXT, FEED, ""}, {CONTEXT, FEED, longLabel}, {CONTEXT, FEED, "x "}};
        for (String[] t : texts) rejectsBody(raw(SYNC, INBOX, 1, t[0], t[1], t[2], 0));
        rejectsBody(rawBytes(SYNC, INBOX, 1, CONTEXT.getBytes(StandardCharsets.UTF_8), new byte[]{(byte) 0xc3, 0x28}, STREAM.getBytes(StandardCharsets.UTF_8), 0));
        assert !FeedSyncState.decode(seal(raw(SYNC, INBOX, 0, CONTEXT, FEED, STREAM, 0))).following;

        for (int count : new int[]{-1, 1, 257}) rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, count));
        byte[] one = rec(DEVICE, u(0x601), u(0x601), 2, H1, null, null);
        rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 0, one)); // trailing record bytes
        rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 2, one));
        FeedSyncState.decode(seal(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 1, one)));

        byte[][] badRecords = {
                rec(new UUID(0, 0), u(0x601), null, 0, null, null, null), rec(DEVICE, new UUID(0, 0), null, 0, null, null, null),
                rec(DEVICE, u(0x601), new UUID(0, 0), 0, null, null, null), rec(DEVICE, u(0x601), null, 0, null, new UUID(0, 0), null),
                rec(DEVICE, u(0x601), null, 0, null, null, u(0x902)), rec(DEVICE, u(0x601), null, 0, null, u(0x902), u(0x902)),
                rec(DEVICE, u(0x601), null, 3, null, null, null)};
        for (byte[] r : badRecords) rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 1, r));
        for (int offset : new int[]{32, 33, 34, 35}) { // base flag, image kind, adoptNew flag, adoptOld flag in a 36-byte record
            byte[] r = rec(DEVICE, u(0x601), null, 0, null, null, null);
            r[offset] = 3;
            rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 1, r));
            r[offset] = 2;
            rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 1, r)); // kind 2 would also need 32 more bytes
        }

        byte[] a = rec(DEVICE, u(0x601), null, 0, null, null, null), b = rec(DEVICE2, u(0x601), null, 0, null, null, null);
        rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 2, b, a)); // wrong order
        rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 2, a, a)); // duplicate
        FeedSyncState.decode(seal(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 2, a, b)));
        // Unsigned order: a high-bit uuid sorts after a low one.
        byte[] high = rec(new UUID(0x8000000000000000L, 1), u(0x601), null, 0, null, null, null);
        FeedSyncState.decode(seal(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 2, a, high)));
        rejectsBody(raw(SYNC, INBOX, 1, CONTEXT, FEED, STREAM, 2, high, a));
        // Constructors enforce the same rules.
        rejects(() -> snap(current(), current()));
        rejects(() -> new FeedSyncState.Record(DEVICE, u(1), null, 0, null, null, u(2)));
        rejects(() -> new FeedSyncState.Record(DEVICE, u(1), null, 0, null, u(2), u(2)));
        rejects(() -> new FeedSyncState.Record(DEVICE, u(1), null, 2, new byte[31], null, null));
    }

    private static void limits() throws Exception {
        String context = CONTEXT + new String(new char[FeedSyncState.MAX_CONTEXT_BYTES - CONTEXT.length()]).replace('\0', 'x');
        String label = new String(new char[FeedSyncState.MAX_LABEL_BYTES]).replace('\0', 'x');
        List<FeedSyncState.Record> full = new ArrayList<>();
        for (int i = 1; i <= FeedSyncState.MAX_RECORDS; i++)
            full.add(new FeedSyncState.Record(new UUID(0, i), u(0x601), u(0x601), 2, H1, u(0x901), u(0x902)));
        byte[] bytes = FeedSyncState.encode(new FeedSyncState.Snapshot(SYNC, INBOX, true, context, label, label, full));
        assert bytes.length == 35929 && bytes.length == FeedSyncState.MAX_BYTES;
        assert FeedSyncState.decode(bytes).records.size() == FeedSyncState.MAX_RECORDS;
        full.add(new FeedSyncState.Record(new UUID(0, 999), u(0x601), null, 0, null, null, null));
        rejects(() -> new FeedSyncState.Snapshot(SYNC, INBOX, true, context, label, label, full));
    }

    private static void store(Path dir) throws Exception {
        FeedSyncState store = new FeedSyncState(dir);
        assert store.read() == null && !Files.exists(dir) : "a read of an absent file reports absent and creates nothing";
        FeedSyncState.Snapshot first = snap(current());
        store.create(first);
        rejects(() -> store.create(snap()));
        FeedSyncState reopened = new FeedSyncState(dir);
        FeedSyncState.Snapshot read = reopened.read();
        assert Arrays.equals(FeedSyncState.encode(read), FeedSyncState.encode(first));
        assert Files.exists(dir.resolve("sync.lock"));

        byte[] saved = Files.readAllBytes(dir.resolve("sync.bin"));
        reopened.replace(read, snap(current()));
        assert Arrays.equals(saved, Files.readAllBytes(dir.resolve("sync.bin"))) : "an identical rewrite changes nothing";

        FeedSyncState.Snapshot second = snap(changed(), adopting());
        reopened.replace(read, second);
        byte[] after = Files.readAllBytes(dir.resolve("sync.bin"));
        assert after.length == 370;
        rejects(() -> store.replace(read, snap())); // stale expectation
        assert Arrays.equals(after, Files.readAllBytes(dir.resolve("sync.bin")));
        store.replace(store.read(), snap());
        assert store.read().records.isEmpty();
        try (java.util.stream.Stream<Path> names = Files.list(dir)) {
            for (Path p : (Iterable<Path>) names::iterator) assert p.getFileName().toString().equals("sync.bin") || p.getFileName().toString().equals("sync.lock") : p;
        }
        FeedSyncState missing = new FeedSyncState(dir.resolve("none"));
        rejects(() -> missing.replace(first, first));
        assert !Files.exists(dir.resolve("none/sync.bin"));
    }

    private static void failures(Path dir) throws Exception {
        new FeedSyncState(dir).create(snap(current()));
        Path file = dir.resolve("sync.bin");
        byte[] saved = Files.readAllBytes(file);
        FeedSyncState broken = new FeedSyncState(dir, (source, target) -> { throw new IOException("private path"); });
        try {
            broken.replace(broken.read(), snap());
            throw new AssertionError("expected failure");
        } catch (IOException safe) {
            assert !safe.getMessage().contains("private path");
        }
        assert Arrays.equals(saved, Files.readAllBytes(file)) : "a failed replace leaves the old file";

        byte[] future = Arrays.copyOf(saved, saved.length - 32);
        future[7] = 2;
        future = seal(future);
        Files.write(file, future);
        FeedSyncState store = new FeedSyncState(dir);
        rejects(store::read);
        rejects(() -> store.replace(snap(current()), snap()));
        rejects(() -> store.create(snap()));
        assert Arrays.equals(future, Files.readAllBytes(file)) : "unsupported data is never rewritten";
    }

    private static byte[] raw(UUID sync, UUID inbox, int following, String context, String feed, String stream, int count, byte[]... records) throws IOException {
        return rawBytes(sync, inbox, following, context.getBytes(StandardCharsets.UTF_8), feed.getBytes(StandardCharsets.UTF_8),
                stream.getBytes(StandardCharsets.UTF_8), count, records);
    }

    private static byte[] rawBytes(UUID sync, UUID inbox, int following, byte[] context, byte[] feed, byte[] stream, int count, byte[]... records) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x544d4d53);
        out.writeInt(1);
        uuid(out, sync);
        uuid(out, inbox);
        out.writeByte(following);
        for (byte[] text : new byte[][]{context, feed, stream}) { out.writeInt(text.length); out.write(text); }
        out.writeInt(count);
        for (byte[] r : records) out.write(r);
        return bytes.toByteArray();
    }

    private static byte[] rec(UUID device, UUID remote, UUID base, int kind, byte[] digest, UUID adoptNew, UUID adoptOld) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        uuid(out, device);
        uuid(out, remote);
        optional(out, base);
        out.writeByte(kind);
        if (kind == 2) out.write(digest);
        optional(out, adoptNew);
        optional(out, adoptOld);
        return bytes.toByteArray();
    }

    private static void optional(DataOutputStream out, UUID id) throws IOException {
        out.writeByte(id == null ? 0 : 1);
        if (id != null) uuid(out, id);
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static byte[] seal(byte[] body) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
        byte[] all = Arrays.copyOf(body, body.length + 32);
        System.arraycopy(digest, 0, all, body.length, 32);
        return all;
    }

    private static void rejectsBody(byte[] body) throws Exception { rejects(() -> FeedSyncState.decode(seal(body))); }

    private static UUID u(long suffix) { return new UUID(0x4000L, 0x8026000000000000L | suffix); }

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    private static void rejects(Action action) throws Exception {
        try { action.run(); }
        catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected refusal");
    }
}
