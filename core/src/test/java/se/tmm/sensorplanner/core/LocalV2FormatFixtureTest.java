package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Test-only inspector and fingerprint oracle; no production v2 codec, writer or migrator. */
public final class LocalV2FormatFixtureTest {
    private static final Path ROOT = Paths.get("test-fixtures/synthetic/storage-v2");
    private static final UUID STORE = id(100), A = id(201), B = id(202), C = id(203), D = id(204), E = id(205);
    private static final int MAX = 16 * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        byte[] history = fixture("history-v2.base64", "71a43709bb7b339fa3325c91f4957e2ab65f3c66f23ca0448865c4a54540dd43");
        Frame frame = inspect(history);
        golden(frame);
        production(history);
        assert !new String(history, StandardCharsets.ISO_8859_1).contains("owned-old");
        Frame empty = inspect(fixture("empty-v2.base64", "abd92ed4d4e0ca3ba3cbf285bb04e799b8721218f41b2d6421e5db7e7fe3429a"));
        assert empty.slots.isEmpty() && empty.operations.isEmpty() && empty.store.equals(STORE);
        production(fixture("empty-v2.base64", "abd92ed4d4e0ca3ba3cbf285bb04e799b8721218f41b2d6421e5db7e7fe3429a"));
        fingerprints();
        migration();
        malformed(history);
        System.out.println("Local v2 proposal fixtures passed (independent frame/fingerprints, history, deleted versions, v1 expected migration and malformed inputs; exact production codec/fingerprints; no file writer).");
    }

    private static UUID id(int value) { return UUID.fromString(String.format(Locale.ROOT, "00000000-0000-4000-8015-%012d", value)); }
    private static TmmDraft sample(UUID uid, String comment, Instant updated, double latitude, Double bearing, String image, long revision) {
        return new TmmDraft(uid, "PIR V2 SYNTHETIC", DeviceType.PIR, DeviceStatus.ACTIVE, "SYNTHETIC ROLE",
                Instant.ofEpochSecond(0, 123), updated, Instant.ofEpochSecond(5, 6), comment,
                new Position(latitude, 1.25), bearing, image, revision);
    }
    private static TmmDraft before() { return sample(A, "Local before choice", Instant.ofEpochSecond(10, 456), -0.0, 45.25, null, 1); }
    private static TmmDraft with(TmmDraft m, UUID uid, Instant updated, String owner) {
        return new TmmDraft(uid, m.displayId, m.type, m.status, owner, m.createdAt, updated, m.deployedAt,
                m.comment, m.position, m.bearingDeg, null, 1);
    }
    private static void golden(Frame f) throws Exception {
        assert f.store.equals(STORE) && f.slots.size() == 4 && f.operations.size() == 5;
        Iterator<Slot> ordered = f.slots.values().iterator();
        for (UUID target : Arrays.asList(A, B, C, D)) assert ordered.next().uid.equals(target);
        Slot active = f.slots.get(A);
        assert active.version == 6 && active.tag == 1 && active.draft.revision == 9;
        assert active.draft.imageRef.equals("owned-new");
        TmmDraft result = sample(A, "Chosen remote åäö 🌲", Instant.ofEpochSecond(20, 123), -0.0, 45.25, null, 1);
        assert same(active.draft, result);
        assert Double.doubleToRawLongBits(active.draft.position.latitude) == Long.MIN_VALUE;
        assert f.slots.get(B).tag == 2 && f.slots.get(B).version == 3 && f.slots.get(B).draft == null;
        assert f.slots.get(C).tag == 2 && f.slots.get(C).version == 2;
        assert f.slots.get(D).version == 9 && f.slots.get(D).draft.revision == 1 && f.slots.get(D).draft.imageRef == null;
        assert same(f.slots.get(D).draft, sample(D, "Explicit re-add", Instant.ofEpochSecond(10, 456), -0.0, -0.0, null, 1));
        for (int i = 0; i < 5; i++) {
            Op o = f.operations.get(i);
            assert o.id.equals(id(300 + i)) && o.inbox.equals(id(101)) && o.receipt.equals(id(400 + i));
            assert o.context.equals("SYNTHETIC-CONTEXT-A") && o.observed.equals(Instant.ofEpochSecond(100 + i, i));
        }
        Op use = f.operations.get(0);
        assert use.action == 2 && use.mask == 16 && use.beforeTag == 1 && use.beforeVersion == 4 && use.beforeRevision == 7;
        assert same(use.before, before()) && same(use.result, result);
        assert use.incoming.comment.equals(result.comment) && use.incoming.status == DeviceStatus.DEPLOYED;
        assert use.incoming.position.latitude == 0.25 && use.incoming.position.longitude == 1.5;
        assert use.incoming.bearingDeg == 90.0 && use.incoming.deployedAt == null;
        assert use.incoming.updatedAt.equals(Instant.ofEpochSecond(15, 987));
        assert Arrays.equals(use.fingerprint, fingerprint(STORE, A, 1, 4, before(), "owned-old", 7));
        Op add = f.operations.get(1), restore = f.operations.get(2), keep = f.operations.get(3), noop = f.operations.get(4);
        assert add.target.equals(C) && add.beforeTag == 0 && add.afterVersion == 1 && add.afterRevision == 1;
        assert same(add.incoming, sample(C, "Added then deleted", Instant.ofEpochSecond(10, 456), -0.0, null, null, 1));
        assert restore.target.equals(D) && restore.beforeTag == 2 && restore.beforeVersion == 8 && restore.afterVersion == 9;
        assert keep.action == 3 && keep.outcome == 3 && keep.afterVersion == 6 && keep.afterRevision == 9;
        assert same(keep.incoming, sample(A, "Other received variant", Instant.ofEpochSecond(30, 999), -0.0, 45.25, null, 1));
        assert same(keep.before, result) && same(keep.result, result);
        assert noop.action == 2 && noop.mask == 64 && noop.outcome == 3 && noop.editTime == null;
        assert same(noop.incoming, sample(A, "Unselected remote comment", Instant.ofEpochSecond(40, 999), 0.0, 45.25, null, 1));
        assert !ImportReview.sameMetadata(noop.incoming, noop.before); // Unselected remote fields remain retained.
        assert !f.slots.containsKey(E); // Never-seen absence has no serialized slot.
    }

    private static Frame inspect(byte[] bytes) throws Exception {
        if (bytes.length < 64 || bytes.length > MAX) throw invalid();
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(sha(payload), Arrays.copyOfRange(bytes, payload.length, bytes.length))) throw invalid();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != 0x544d4d44 || in.readInt() != 2) throw invalid();
            Frame f = new Frame(); f.store = uuid(in); int slots = count(in);
            for (int i = 0; i < slots; i++) {
                Slot s = new Slot(); s.uid = uuid(in); s.version = positive(in.readLong()); s.tag = tag(in, 1, 2);
                if (s.tag == 1) {
                    TmmDraft m = metadataBody(in, s.uid);
                    String image = flag(in) ? required(in) : null;
                    s.draft = local(m, image, positive(in.readLong()));
                }
                if (f.slots.put(s.uid, s) != null) throw invalid();
            }
            int operations = count(in); Set<UUID> ids = new HashSet<>();
            Map<UUID, Op> last = new HashMap<>(); Map<String, Op> receipts = new HashMap<>();
            for (int i = 0; i < operations; i++) {
                Op o = new Op(); o.id = uuid(in); if (!ids.add(o.id)) throw invalid();
                o.action = tag(in, 1, 3); o.target = uuid(in); o.inbox = uuid(in); o.receipt = uuid(in);
                o.context = required(in); o.observed = instant(in); o.incoming = metadata(in);
                o.beforeTag = tag(in, 0, 2); o.beforeVersion = in.readLong();
                if (o.beforeTag == 0 ? o.beforeVersion != 0 : o.beforeVersion < 1) throw invalid();
                if (o.beforeTag == 1) { o.beforeRevision = positive(in.readLong()); o.before = metadata(in); }
                o.fingerprint = new byte[32]; in.readFully(o.fingerprint);
                o.mask = in.readInt(); if (o.mask < 0 || o.mask > 255) throw invalid();
                o.editTime = flag(in) ? instant(in) : null; o.outcome = tag(in, 1, 3);
                o.afterVersion = positive(in.readLong()); o.afterRevision = positive(in.readLong()); o.result = metadata(in);
                validate(f, o);
                Op previous = last.put(o.target, o);
                if (previous != null && (o.beforeVersion < previous.afterVersion
                        || o.beforeVersion == previous.afterVersion && (o.beforeTag != 1
                        || o.beforeRevision != previous.afterRevision || !same(o.before, previous.result)))) throw invalid();
                Op provenance = receipts.put(o.inbox.toString() + o.receipt, o);
                if (provenance != null && (!provenance.context.equals(o.context) || !provenance.observed.equals(o.observed)
                        || !same(provenance.incoming, o.incoming))) throw invalid();
                f.operations.add(o);
            }
            if (in.available() != 0) throw invalid();
            return f;
        } catch (IllegalArgumentException | java.time.DateTimeException e) { throw invalid(); }
    }

    private static void validate(Frame f, Op o) throws Exception {
        if (!o.target.equals(o.incoming.uid) || !o.target.equals(o.result.uid)
                || o.before != null && !o.target.equals(o.before.uid)) throw invalid();
        Slot current = f.slots.get(o.target);
        if (current == null || current.version < o.afterVersion) throw invalid();
        if (o.action == 1) {
            if (o.beforeTag == 1 || o.mask != 0 || o.editTime != null || o.outcome != 1
                    || o.beforeVersion == Long.MAX_VALUE || o.afterVersion != o.beforeVersion + 1
                    || o.afterRevision != 1 || !same(o.result, o.incoming)) throw invalid();
        } else {
            if (o.beforeTag != 1 || same(o.before, o.incoming)) throw invalid();
            boolean changed = false;
            TmmDraft selected = o.before;
            if (o.action == 2) {
                if (o.mask == 0 || !o.incoming.createdAt.equals(o.before.createdAt)) throw invalid();
                selected = select(o.before, o.incoming, o.mask, o.before.updatedAt);
                changed = !same(selected, o.before);
            } else if (o.mask != 0) throw invalid();
            if (changed) {
                if (o.editTime == null || o.editTime.isBefore(o.before.updatedAt) || o.outcome != 2
                        || o.beforeVersion == Long.MAX_VALUE || o.beforeRevision == Long.MAX_VALUE
                        || o.afterVersion != o.beforeVersion + 1 || o.afterRevision != o.beforeRevision + 1
                        || !same(o.result, select(o.before, o.incoming, o.mask, o.editTime))) throw invalid();
            } else if (o.editTime != null || o.outcome != 3 || o.afterVersion != o.beforeVersion
                    || o.afterRevision != o.beforeRevision || !same(o.result, o.before)) throw invalid();
        }
        if (o.beforeTag != 1 && !MessageDigest.isEqual(o.fingerprint,
                fingerprint(f.store, o.target, o.beforeTag, o.beforeVersion, null, null, 0))) throw invalid();
        if (current.version == o.afterVersion) {
            if (current.tag != 1 || current.draft.revision != o.afterRevision || !same(current.draft, o.result)) throw invalid();
            if (o.action == 1 && current.draft.imageRef != null) throw invalid();
            if (o.beforeTag == 1 && !MessageDigest.isEqual(o.fingerprint,
                    fingerprint(f.store, o.target, 1, o.beforeVersion, o.before, current.draft.imageRef, o.beforeRevision))) throw invalid();
        }
    }
    private static TmmDraft select(TmmDraft b, TmmDraft r, int mask, Instant updated) {
        return new TmmDraft(b.uid, (mask & 1) != 0 ? r.displayId : b.displayId,
                (mask & 2) != 0 ? r.type : b.type, (mask & 4) != 0 ? r.status : b.status,
                (mask & 8) != 0 ? r.owner : b.owner, b.createdAt, updated,
                (mask & 32) != 0 ? r.deployedAt : b.deployedAt, (mask & 16) != 0 ? r.comment : b.comment,
                (mask & 64) != 0 ? r.position : b.position, (mask & 128) != 0 ? r.bearingDeg : b.bearingDeg, null, 1);
    }

    private static void fingerprints() throws Exception {
        List<String> lines = Files.readAllLines(ROOT.resolve("fingerprints.tsv"), StandardCharsets.US_ASCII);
        assert lines.size() == 18;
        Set<String> distinct = new HashSet<>(), names = new HashSet<>(); String first = null, allZero = null;
        for (String line : lines) {
            String[] values = line.split("\t"); assert values.length == 3;
            String name = values[0]; assert names.add(name); UUID store = STORE, target = A; int tag = 1;
            long version = 4, revision = 7; TmmDraft m = before(); String image = "owned-old";
            switch (name) {
                case "active": break;
                case "positive-zero": m = sample(A, m.comment, m.updatedAt, 0.0, 45.25, null, 1); break;
                case "all-negative-zero":
                case "all-positive-zero":
                    double zero = name.equals("all-negative-zero") ? -0.0 : 0.0;
                    m = new TmmDraft(A, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                            m.deployedAt, m.comment, new Position(zero, zero), zero, null, 1); break;
                case "bearing-absent": m = sample(A, m.comment, m.updatedAt, -0.0, null, null, 1); break;
                case "image-replaced": image = "owned-new"; break;
                case "image-absent": image = null; break;
                case "revision": revision = 8; break;
                case "state-version": version = 5; break;
                case "store-identity": store = id(999); break;
                case "device-identity": target = E; m = with(m, E, m.updatedAt, m.owner); break;
                case "nanoseconds": m = with(m, A, m.updatedAt.plusNanos(1), m.owner); break;
                case "text-case": m = with(m, A, m.updatedAt, "Synthetic ROLE"); break;
                case "exhausted": version = Long.MAX_VALUE; revision = Long.MAX_VALUE; break;
                case "never-seen": target = E; tag = 0; version = 0; break;
                case "never-other-uid": target = B; tag = 0; version = 0; break;
                case "deleted": target = B; tag = 2; version = 3; break;
                case "deleted-version": target = B; tag = 2; version = 4; break;
                default: throw new AssertionError("Unknown fingerprint vector");
            }
            byte[] preimage = preimage(store, target, tag, version, m, image, revision);
            assert Arrays.equals(preimage, Base64.getDecoder().decode(values[1]));
            String digest = hex(sha(preimage)); assert digest.equals(values[2]);
            LocalV2.State state = tag == 0 ? LocalV2.State.neverSeen(target) : tag == 2
                    ? LocalV2.State.deleted(target, version) : LocalV2.State.active(version, local(m, image, revision));
            assert Arrays.equals(LocalStateFingerprint.digest(store, state), sha(preimage));
            if (name.equals("active")) first = digest;
            if (name.equals("all-negative-zero")) allZero = digest;
            if (name.equals("positive-zero")) assert digest.equals(first);
            else if (name.equals("all-positive-zero")) assert digest.equals(allZero);
            else assert distinct.add(digest);
        }
    }

    private static byte[] fingerprint(UUID store, UUID target, int tag, long version, TmmDraft m, String image, long revision) throws Exception {
        return sha(preimage(store, target, tag, version, m, image, revision));
    }
    private static byte[] preimage(UUID store, UUID target, int tag, long version, TmmDraft m, String image, long revision) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x544d4d52); out.writeInt(1); uuid(out, store); uuid(out, target); out.writeByte(tag); out.writeLong(version);
            if (tag == 1) {
                if (!target.equals(m.uid)) throw invalid();
                text(out, m.displayId); text(out, m.type.name()); text(out, m.status.name()); text(out, m.owner);
                instant(out, m.createdAt); instant(out, m.updatedAt); out.writeBoolean(m.deployedAt != null);
                if (m.deployedAt != null) instant(out, m.deployedAt);
                text(out, m.comment); out.writeDouble(m.position.latitude == 0 ? 0.0 : m.position.latitude);
                out.writeDouble(m.position.longitude == 0 ? 0.0 : m.position.longitude); out.writeBoolean(m.bearingDeg != null);
                if (m.bearingDeg != null) out.writeDouble(m.bearingDeg == 0 ? 0.0 : m.bearingDeg);
                out.writeBoolean(image != null); if (image != null) text(out, image); out.writeLong(revision);
            }
        }
        return bytes.toByteArray();
    }

    private static void migration() throws Exception {
        byte[] expected = fixture("migrated-v1-v2.base64", "a83aed3f900845346acd8de5b03054c79d99741dd20549e89fd31ea7ecb4ebfb");
        production(expected);
        Frame migrated = inspect(expected);
        assert migrated.store.equals(STORE) && migrated.slots.size() == 1 && migrated.operations.isEmpty();
        byte[] source = Base64.getDecoder().decode(new String(Files.readAllBytes(Paths.get("test-fixtures/synthetic/storage/v1.base64")), StandardCharsets.US_ASCII).trim());
        assert hex(sha(source)).equals("f980a436617a2616fc93b33e5b1491b8718259515a50c3ab77b6c10007f55b14");
        Path directory = Files.createTempDirectory("tmm-v2-fixture-");
        try {
            Path data = directory.resolve("drafts.bin"); Files.write(data, source);
            TmmDraft v1 = new DraftStore(directory).load().get(0);
            Slot slot = migrated.slots.get(v1.uid);
            assert slot.version == 1 && slot.tag == 1 && slot.draft.revision == v1.revision;
            assert same(slot.draft, v1) && Objects.equals(slot.draft.imageRef, v1.imageRef);
            assert Double.doubleToRawLongBits(slot.draft.position.latitude) == Double.doubleToRawLongBits(v1.position.latitude);
            assert Double.doubleToRawLongBits(slot.draft.position.longitude) == Double.doubleToRawLongBits(v1.position.longitude);
            assert Double.doubleToRawLongBits(slot.draft.bearingDeg) == Double.doubleToRawLongBits(v1.bearingDeg);
            assert Arrays.equals(source, Files.readAllBytes(data));
            Files.write(data, expected);
            try { new DraftStore(directory).load(); throw new AssertionError("Legacy v1 reader accepted v2"); }
            catch (IOException wanted) { }
            assert Arrays.equals(expected, Files.readAllBytes(data));
            byte[] emptyV1 = Base64.getDecoder().decode(new String(Files.readAllBytes(ROOT.resolve("empty-v1.base64")), StandardCharsets.US_ASCII).trim());
            assert emptyV1.length == 44 && Arrays.equals(sha(Arrays.copyOf(emptyV1, 12)), Arrays.copyOfRange(emptyV1, 12, 44));
            Files.write(data, emptyV1); assert new DraftStore(directory).load().isEmpty();
            Frame emptyV2 = inspect(Base64.getDecoder().decode(new String(Files.readAllBytes(ROOT.resolve("empty-v2.base64")), StandardCharsets.US_ASCII).trim()));
            assert emptyV2.slots.isEmpty() && emptyV2.operations.isEmpty() && emptyV2.store.equals(STORE);
            assert Arrays.equals(emptyV1, Files.readAllBytes(data));
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static void malformed(byte[] source) throws Exception {
        Properties offsets = new Properties();
        try (Reader reader = Files.newBufferedReader(ROOT.resolve("offsets.properties"), StandardCharsets.US_ASCII)) { offsets.load(reader); }
        byte[] b = source.clone(); b[40] ^= 1; reject(b);
        reject(Arrays.copyOf(source, source.length - 1)); reject(new byte[MAX + 1]);
        reject(seal(Arrays.copyOf(source, source.length - 33)));
        reject(seal(Arrays.copyOf(source, source.length - 31)));
        for (int[] v : new int[][]{{0, 0}, {4, 1}, {4, 3}, {24, -1}, {24, 1001}, {at(offsets, "operationCount"), -1},
                {at(offsets, "operationCount"), 1001}, {at(offsets, "slot0.body"), 65537}, {at(offsets, "slot0.body"), -1},
                {at(offsets, "op0.mask"), 256}, {at(offsets, "op0.mask"), 0}, {at(offsets, "op3.mask"), 16}}) {
            b = source.clone(); ByteBuffer.wrap(b).putInt(v[0], v[1]); reject(reseal(b));
        }
        for (String key : Arrays.asList("slot0.version", "slot1.version", "op0.beforeRevision", "op0.afterRevision", "op0.afterVersion")) {
            b = source.clone(); ByteBuffer.wrap(b).putLong(at(offsets, key), 0); reject(reseal(b));
        }
        for (Object[] v : new Object[][]{{"slot1.tag", 0}, {"slot0.tag", 3}, {"op0.action", 0}, {"op0.action", 4},
                {"op0.beforeTag", 3}, {"op0.outcome", 1}, {"op3.fingerprint", 255}, {"op1.fingerprint", 255},
                {"op0.editTime", 2}}) {
            b = source.clone(); b[at(offsets, (String) v[0])] = ((Integer) v[1]).byteValue(); reject(reseal(b));
        }
        b = source.clone(); b[at(offsets, "op0.context") + 4] = 32; reject(reseal(b));
        b = source.clone(); b[at(offsets, "slot0.body") + 4] = (byte) 255; reject(reseal(b));
        for (String key : Arrays.asList("slot1.uid", "op1.id")) {
            b = source.clone(); System.arraycopy(source, at(offsets, key.equals("slot1.uid") ? "slot0.uid" : "op0.id"), b, at(offsets, key), 16); reject(reseal(b));
        }
        b = source.clone(); b[at(offsets, "op0.target") + 15] ^= 1; reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putLong(at(offsets, "op0.beforeVersion"), Long.MAX_VALUE); reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putLong(at(offsets, "slot0.version"), 4); reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putInt(at(offsets, "op0.observed") + 8, 1000000000); reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putLong(at(offsets, "op0.observed"), Long.MAX_VALUE); reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putDouble(at(offsets, "slot0.latitude"), Double.NaN); reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putDouble(at(offsets, "op0.incoming.latitude"), 91); reject(reseal(b));
        b = source.clone(); b[at(offsets, "slot0.deployedFlag")] = 2; reject(reseal(b));
        b = source.clone(); b[at(offsets, "slot0.bearingFlag")] = 2; reject(reseal(b));
        b = source.clone(); b[at(offsets, "slot0.type")] = 'X'; reject(reseal(b));
        b = source.clone(); b[at(offsets, "slot0.owner")] = ' '; reject(reseal(b));
        b = source.clone(); ByteBuffer.wrap(b).putInt(at(offsets, "slot0.updatedNanos"), -1); reject(reseal(b));
        // Both operations remain well formed, but moving a later target decision first breaks its version chain.
        int start = at(offsets, "op0.id"), later = at(offsets, "op3.id"), end = at(offsets, "op4.id");
        ByteArrayOutputStream reordered = new ByteArrayOutputStream();
        reordered.write(source, 0, start); reordered.write(source, later, end - later);
        reordered.write(source, start, later - start); reordered.write(source, end, source.length - 32 - end);
        reject(seal(reordered.toByteArray()));
    }

    private static int at(Properties offsets, String key) { return Integer.parseInt(offsets.getProperty(key)); }
    private static TmmDraft local(TmmDraft m, String image, long revision) {
        return new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt, m.deployedAt,
                m.comment, m.position, m.bearingDeg, image, revision);
    }
    private static TmmDraft metadata(DataInputStream in) throws IOException { return metadataBody(in, uuid(in)); }
    private static TmmDraft metadataBody(DataInputStream in, UUID uid) throws IOException {
        String label = required(in); DeviceType type = DeviceType.valueOf(required(in)); DeviceStatus status = DeviceStatus.valueOf(required(in));
        String owner = required(in); Instant created = instant(in), updated = instant(in), deployed = flag(in) ? instant(in) : null;
        String comment = text(in); Position position = new Position(in.readDouble(), in.readDouble()); Double bearing = flag(in) ? in.readDouble() : null;
        return new TmmDraft(uid, label, type, status, owner, created, updated, deployed, comment, position, bearing, null, 1);
    }
    private static String text(DataInputStream in) throws IOException {
        int size = in.readInt(); if (size < 0 || size > 65536 || size > in.available()) throw invalid();
        byte[] raw = new byte[size]; in.readFully(raw);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();
    }
    private static String required(DataInputStream in) throws IOException {
        String s = text(in); if (s.isEmpty() || !s.equals(s.trim())) throw invalid(); return s;
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
        if (encoded.remaining() > 65536) throw invalid(); byte[] raw = new byte[encoded.remaining()]; encoded.get(raw);
        out.writeInt(raw.length); out.write(raw);
    }
    private static Instant instant(DataInputStream in) throws IOException {
        long seconds = in.readLong(); int nanos = in.readInt(); if (nanos < 0 || nanos > 999999999) throw invalid(); return Instant.ofEpochSecond(seconds, nanos);
    }
    private static void instant(DataOutputStream out, Instant value) throws IOException { out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    private static boolean flag(DataInputStream in) throws IOException { return tag(in, 0, 1) == 1; }
    private static int tag(DataInputStream in, int min, int max) throws IOException { int n = in.readUnsignedByte(); if (n < min || n > max) throw invalid(); return n; }
    private static int count(DataInputStream in) throws IOException { int n = in.readInt(); if (n < 0 || n > 1000) throw invalid(); return n; }
    private static long positive(long n) throws IOException { if (n < 1) throw invalid(); return n; }
    private static boolean same(TmmDraft a, TmmDraft b) { return a.uid.equals(b.uid) && ImportReview.sameMetadata(a, b); }
    private static byte[] fixture(String name, String hash) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(new String(Files.readAllBytes(ROOT.resolve(name)), StandardCharsets.US_ASCII).trim());
        assert hex(sha(bytes)).equals(hash); return bytes;
    }
    private static byte[] sha(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private static byte[] seal(byte[] payload) throws Exception { byte[] bytes = Arrays.copyOf(payload, payload.length + 32); System.arraycopy(sha(payload), 0, bytes, payload.length, 32); return bytes; }
    private static byte[] reseal(byte[] bytes) throws Exception { return seal(Arrays.copyOf(bytes, bytes.length - 32)); }
    private static void reject(byte[] bytes) throws Exception {
        byte[] original = bytes.clone();
        try { inspect(bytes); throw new AssertionError("Malformed local v2 fixture accepted"); } catch (IOException expected) { }
        try { LocalV2Codec.decode(bytes); throw new AssertionError("Production codec accepted malformed v2"); }
        catch (IOException expected) { assert expected.getCause() == null; assert expected.getMessage().equals("Invalid or unsupported local v2 data"); }
        assert Arrays.equals(original, bytes);
    }
    private static void production(byte[] bytes) throws Exception {
        byte[] original = bytes.clone();
        LocalV2.Snapshot value = LocalV2Codec.decode(bytes);
        byte[] encoded = LocalV2Codec.encode(value);
        assert Arrays.equals(original, encoded) && Arrays.equals(original, bytes);
        Frame oracle = inspect(encoded);
        assert oracle.store.equals(value.storeId) && oracle.slots.size() == value.slots.size()
                && oracle.operations.size() == value.decisions.size();
        Iterator<Slot> slots = oracle.slots.values().iterator();
        for (LocalV2.State state : value.slots) {
            Slot expected = slots.next();
            assert state.uid.equals(expected.uid) && state.version == expected.version && state.presence.code == expected.tag;
            assert expected.draft == null ? state.draft == null : exactMetadata(expected.draft, state.draft)
                    && expected.draft.revision == state.draft.revision && Objects.equals(expected.draft.imageRef, state.draft.imageRef);
        }
        for (int i = 0; i < value.decisions.size(); i++) {
            LocalV2.Decision d = value.decisions.get(i); Op expected = oracle.operations.get(i);
            assert d.id.equals(expected.id) && d.action.code == expected.action && d.target.equals(expected.target);
            assert d.inboxId.equals(expected.inbox) && d.receiptId.equals(expected.receipt);
            assert d.contextId.equals(expected.context) && d.firstObservedAt.equals(expected.observed);
            assert exactMetadata(d.incoming, expected.incoming) && d.incoming.imageRef == null && d.incoming.revision == 1;
            assert d.before.presence.code == expected.beforeTag && d.before.version == expected.beforeVersion;
            assert d.before.draft == null ? expected.before == null : exactMetadata(d.before.draft, expected.before)
                    && d.before.draft.revision == expected.beforeRevision && d.before.draft.imageRef == null;
            assert Arrays.equals(d.reviewedFingerprint(), expected.fingerprint) && d.fieldMask == expected.mask;
            assert Objects.equals(d.editTime, expected.editTime) && d.outcome.code == expected.outcome;
            assert d.afterVersion == expected.afterVersion && d.afterRevision == expected.afterRevision;
            assert exactMetadata(d.result, expected.result) && d.result.imageRef == null && d.result.revision == 1;
        }
    }
    private static boolean exactMetadata(TmmDraft a, TmmDraft b) {
        return same(a, b) && Double.doubleToRawLongBits(a.position.latitude) == Double.doubleToRawLongBits(b.position.latitude)
                && Double.doubleToRawLongBits(a.position.longitude) == Double.doubleToRawLongBits(b.position.longitude)
                && (a.bearingDeg == null ? b.bearingDeg == null : b.bearingDeg != null
                && Double.doubleToRawLongBits(a.bearingDeg) == Double.doubleToRawLongBits(b.bearingDeg));
    }
    private static IOException invalid() { return new IOException("Invalid local v2 proposal fixture"); }
    private static String hex(byte[] bytes) { StringBuilder text = new StringBuilder(); for (byte b : bytes) text.append(String.format(Locale.ROOT, "%02x", b & 255)); return text.toString(); }
    private static final class Frame { UUID store; Map<UUID, Slot> slots = new LinkedHashMap<>(); List<Op> operations = new ArrayList<>(); }
    private static final class Slot { UUID uid; long version; int tag; TmmDraft draft; }
    private static final class Op {
        UUID id, target, inbox, receipt; String context; Instant observed, editTime; int action, beforeTag, mask, outcome;
        long beforeVersion, beforeRevision, afterVersion, afterRevision; TmmDraft incoming, before, result; byte[] fingerprint;
    }
}
