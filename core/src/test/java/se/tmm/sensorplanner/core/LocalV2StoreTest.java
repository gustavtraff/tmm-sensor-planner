package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static se.tmm.sensorplanner.core.LocalV2.*;
import static se.tmm.sensorplanner.core.LocalV2Store.*;

public final class LocalV2StoreTest {
    private static final UUID UID = new UUID(0, 115);
    private static final Instant TIME = Instant.ofEpochSecond(100, 123);
    private interface Task { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) { child(args); return; }
        Path root = Files.createTempDirectory("tmm-local-v2-");
        try {
            transactions(root.resolve("transactions"));
            staleAndNormalWrites(root.resolve("stale"));
            invalidAndCapacity(root.resolve("invalid"));
            projectedByteLimit(root.resolve("bytes"));
            failures(root.resolve("faults"));
            locksAndRaces(root.resolve("locks"));
            crash(root.resolve("before"), false); crash(root.resolve("after"), true);
            System.out.println("Local v2 store passed (atomic import, expected states, history, replay, restart, locks, crash and faults).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
    private static TmmDraft sample(UUID uid, String comment, long revision, String image) {
        return new TmmDraft(uid, "PIR SYNTHETIC", DeviceType.PIR, DeviceStatus.PLANNED, "SYNTHETIC ROLE",
                Instant.EPOCH, TIME, null, comment, new Position(-0.0, 1.25), 0.0, image, revision);
    }
    private static TmmDraft sample(String comment) { return sample(UID, comment, 1, null); }
    private static TmmDraft edit(TmmDraft draft, String comment, String image) {
        return draft.edit(draft.displayId, draft.type, draft.status, draft.owner, draft.deployedAt, comment,
                draft.position, draft.bearingDeg, image, draft.updatedAt.plusSeconds(1));
    }
    private static final class Fixture {
        final Path directory; final LocalV2Store local; final InboxStore inbox; final UUID storeId;
        Fixture(Path directory) throws Exception {
            this.directory = directory; local = new LocalV2Store(directory.resolve("local"));
            inbox = new InboxStore(directory.resolve("inbox"));
            storeId = local.initializeNew().storeId; inbox.initializeNew();
        }
        InboxStore.Receipt receive(String comment) throws Exception {
            return inbox.receive("SYNTHETIC SOURCE", sample(comment), null, TIME);
        }
        ImportDecisionPreparation.Prepared add(InboxStore.Receipt receipt, boolean reAdd) throws Exception {
            InboxStore.Snapshot i = inbox.load(); Snapshot l = local.load();
            return ImportDecisionPreparation.addLocally(i, receipt.id, l, reAdd).prepare(i, l);
        }
        ImportDecisionPreparation.Prepared choose(InboxStore.Receipt receipt, ImportDecisionPreparation.Field field) throws Exception {
            InboxStore.Snapshot i = inbox.load(); Snapshot l = local.load();
            return ImportDecisionPreparation.useSelected(i, receipt.id, l, EnumSet.of(field), TIME.plusSeconds(10)).prepare(i, l);
        }
        ImportDecisionPreparation.Prepared keep(InboxStore.Receipt receipt) throws Exception {
            InboxStore.Snapshot i = inbox.load(); Snapshot l = local.load();
            return ImportDecisionPreparation.keepLocal(i, receipt.id, l).prepare(i, l);
        }
        Path file() { return directory.resolve("local/drafts.bin"); }
        State current() throws Exception { return local.load().slots.get(0); }
    }
    private static void transactions(Path root) throws Exception {
        Fixture f = new Fixture(root); InboxStore.Receipt receipt = f.receive("incoming");
        ImportDecisionPreparation.Prepared add = f.add(receipt, false);
        assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.NOT_COMMITTED;
        byte[] inboxBytes = Files.readAllBytes(root.resolve("inbox/inbox.bin"));
        Result added = f.local.commit(f.inbox, add);
        assert added.effect == Effect.COMMITTED && added.decision.outcome == Outcome.ADDED;
        assert added.current.version == 1 && added.current.draft.revision == 1 && added.current.draft.imageRef == null;
        assert Arrays.equals(inboxBytes, Files.readAllBytes(root.resolve("inbox/inbox.bin")));
        byte[] bytes = Files.readAllBytes(f.file());
        Result repeat = f.local.commit(f.inbox, add);
        assert repeat.effect == Effect.ALREADY_COMMITTED && Arrays.equals(bytes, Files.readAllBytes(f.file()));
        ImportDecisionPreparation.Prepared equal = f.keep(receipt);
        assert f.local.commit(f.inbox, equal).effect == Effect.ALREADY_EQUAL;
        assert Arrays.equals(bytes, Files.readAllBytes(f.file()));
        assert f.local.readback(f.storeId, equal.plan.operationId).status == CommitStatus.NOT_COMMITTED;
        State withImage = f.local.save(f.storeId, added.current, edit(added.current.draft, "local", "synthetic-image.png"));
        Path image = root.resolve("local/synthetic-image.png"); Files.write(image, new byte[]{7, 3, 1});
        InboxStore.Receipt update = f.receive("changed elsewhere");
        ImportDecisionPreparation.Prepared selected = f.choose(update, ImportDecisionPreparation.Field.COMMENT);
        f.inbox.retainLocalObservation(update.id, withImage.draft);
        f.receive("unrelated variant");
        Result changed = f.local.commit(f.inbox, selected);
        assert changed.current.version == 3 && changed.current.draft.revision == 3;
        assert changed.current.draft.comment.equals("changed elsewhere") && changed.current.draft.updatedAt.equals(TIME.plusSeconds(10));
        assert changed.current.draft.imageRef.equals("synthetic-image.png");
        assert changed.decision.before.draft.imageRef == null && changed.decision.before.draft.comment.equals("local");
        assert changed.decision.before.draft.revision == 2 && changed.decision.result.imageRef == null;
        assert Arrays.equals(new byte[]{7, 3, 1}, Files.readAllBytes(image));
        ImportDecisionPreparation.Prepared keep = f.keep(receipt);
        State before = f.current(); Result kept = f.local.commit(f.inbox, keep);
        assert kept.decision.outcome == Outcome.KEPT && kept.current.version == before.version;
        assert kept.current.draft.revision == before.draft.revision && kept.current.draft.imageRef.equals(before.draft.imageRef);
        ImportDecisionPreparation.Prepared unchanged = f.choose(receipt, ImportDecisionPreparation.Field.STATUS);
        Result noChange = f.local.commit(f.inbox, unchanged);
        assert noChange.decision.outcome == Outcome.KEPT && noChange.decision.fieldMask == 4 && noChange.decision.editTime == null;
        assert noChange.current.version == before.version && f.local.load().decisions.size() == 4;
        State deleted = f.local.delete(f.storeId, f.current());
        Readback historical = new LocalV2Store(root.resolve("local")).readback(f.storeId, selected.plan.operationId);
        assert historical.status == CommitStatus.COMMITTED && historical.decision.result.comment.equals("changed elsewhere");
        assert historical.current.presence == Presence.DELETED;
        Result oldReplay = f.local.commit(f.inbox, selected);
        assert oldReplay.effect == Effect.ALREADY_COMMITTED && oldReplay.current.presence == Presence.DELETED;
        assert f.current().version == deleted.version;
        ImportDecisionPreparation.Prepared readd = f.add(receipt, true);
        Result readded = f.local.commit(f.inbox, readd);
        assert readded.current.version == deleted.version + 1 && readded.current.draft.revision == 1 && readded.current.draft.imageRef == null;
        assert f.local.load().slots.size() == 1;
        assert Arrays.equals(new byte[]{7, 3, 1}, Files.readAllBytes(image));
        Files.delete(root.resolve("inbox/inbox.bin"));
        assert f.local.readback(f.storeId, readd.plan.operationId).status == CommitStatus.COMMITTED;
        expect(Reason.UNKNOWN, () -> f.local.commit(f.inbox, readd));
        assert f.local.readback(UUID.randomUUID(), add.plan.operationId).status == CommitStatus.UNKNOWN;
    }
    private static void staleAndNormalWrites(Path root) throws Exception {
        Fixture f = new Fixture(root); InboxStore.Receipt r = f.receive("received");
        ImportDecisionPreparation.Prepared beforeCreateDelete = f.add(r, false);
        State created = f.local.save(f.storeId, State.neverSeen(UID), sample("local"));
        State deleted = f.local.delete(f.storeId, created);
        expect(Reason.STALE, () -> f.local.commit(f.inbox, beforeCreateDelete));
        expect(Reason.STALE, () -> f.local.save(f.storeId, State.neverSeen(UID), sample("local")));
        State recreated = f.local.save(f.storeId, deleted, sample("local"));
        assert recreated.version == 3 && recreated.draft.revision == 1;
        ImportDecisionPreparation.Prepared old = f.choose(r, ImportDecisionPreparation.Field.COMMENT);
        State imageOnly = f.local.save(f.storeId, recreated, edit(recreated.draft, "local", "synthetic-new.png"));
        expect(Reason.STALE, () -> f.local.commit(f.inbox, old));
        expect(Reason.STALE, () -> f.local.delete(f.storeId, recreated));
        expect(Reason.STALE, () -> f.local.save(UUID.randomUUID(), imageOnly, edit(imageOnly.draft, "new", null)));
        expect(Reason.INVALID, () -> f.local.save(f.storeId, imageOnly, sample("bad revision")));
        ImportDecisionPreparation.Prepared fresh = f.choose(r, ImportDecisionPreparation.Field.COMMENT);
        UUID other = new UUID(0, 116);
        f.local.save(f.storeId, State.neverSeen(other), sample(other, "unrelated", 1, null));
        f.local.commit(f.inbox, fresh);
        assert f.local.load().slots.get(1).uid.equals(other);
        ImportDecisionPreparation.Prepared staleSameBytes = f.keep(r);
        State current = f.current(); State gone = f.local.delete(f.storeId, current);
        f.local.save(f.storeId, gone, LocalV2Binary.local(current.draft, current.draft.imageRef, 1));
        expect(Reason.STALE, () -> f.local.commit(f.inbox, staleSameBytes));
        // The immutable command ID is deliberately forged in committed test evidence, never through a public command constructor.
        ImportDecisionPreparation.Prepared bound = f.keep(f.receive("different"));
        Result committed = f.local.commit(f.inbox, bound);
        Snapshot s = f.local.load(); List<Decision> records = new ArrayList<>(s.decisions);
        Decision d = committed.decision;
        records.set(records.size() - 1, new Decision(d.id, d.action, d.target, d.inboxId, d.receiptId,
                "OTHER SYNTHETIC CONTEXT", d.firstObservedAt, d.incoming, d.before, d.reviewedFingerprint(),
                d.fieldMask, d.editTime, d.outcome, d.afterVersion, d.afterRevision, d.result));
        Files.write(f.file(), LocalV2Codec.encode(new Snapshot(s.storeId, s.slots, records)));
        expect(Reason.INVALID, () -> f.local.commit(f.inbox, bound));
        byte[] prior = Files.readAllBytes(f.file());
        Files.write(f.file(), LocalV2Codec.encode(new Snapshot(UUID.randomUUID(), s.slots, Collections.emptyList())));
        expect(Reason.STALE, () -> f.local.commit(f.inbox, bound));
        assert f.local.readback(f.storeId, bound.plan.operationId).status == CommitStatus.UNKNOWN;
        Files.write(f.file(), prior);
        ImportDecisionPreparation.Prepared beforeInboxReplacement = f.keep(f.receive("new source binding"));
        InboxStore.Snapshot oldInbox = f.inbox.load();
        Files.write(root.resolve("inbox/inbox.bin"), InboxCodec.encode(new InboxStore.Snapshot(UUID.randomUUID(), oldInbox.receipts)));
        expect(Reason.STALE, () -> f.local.commit(f.inbox, beforeInboxReplacement));
        ImportDecisionPreparation.Prepared oldInboxCommand = bound;
        assert f.local.readback(f.storeId, oldInboxCommand.plan.operationId).status == CommitStatus.COMMITTED;
    }
    private static void invalidAndCapacity(Path root) throws Exception {
        Fixture f = new Fixture(root); InboxStore.Receipt r = f.receive("incoming");
        ImportDecisionPreparation.Prepared add = f.add(r, false); byte[] initial = Files.readAllBytes(f.file());
        expect(Reason.INVALID, f.local::initializeNew);
        byte[] future = initial.clone(); java.nio.ByteBuffer.wrap(future).putInt(4, 3);
        System.arraycopy(LocalV2Binary.sha(future, future.length - 32), 0, future, future.length - 32, 32);
        byte[] corrupt = initial.clone(); corrupt[0] ^= 1;
        byte[][] invalid = {new byte[0], new byte[]{1, 2, 3}, future, corrupt,
                Base64.getDecoder().decode(new String(Files.readAllBytes(Paths.get("test-fixtures/synthetic/storage/v1.base64")), java.nio.charset.StandardCharsets.UTF_8).trim())};
        for (byte[] bytes : invalid) {
            Files.write(f.file(), bytes);
            expect(Reason.UNKNOWN, f.local::load); expect(Reason.UNKNOWN, () -> f.local.commit(f.inbox, add));
            expect(Reason.INVALID, f.local::initializeNew);
            assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.UNKNOWN;
            assert Arrays.equals(bytes, Files.readAllBytes(f.file()));
        }
        Files.delete(f.file()); expect(Reason.UNKNOWN, f.local::load);
        expect(Reason.UNKNOWN, () -> f.local.commit(f.inbox, add));
        assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.UNKNOWN && !Files.exists(f.file());
        Files.write(f.file(), initial);
        List<State> slots = new ArrayList<>();
        for (int i = 0; i < 1000; i++) slots.add(State.deleted(new UUID(1, i), 1));
        Files.write(f.file(), LocalV2Codec.encode(new Snapshot(f.storeId, slots, Collections.emptyList())));
        byte[] full = Files.readAllBytes(f.file());
        expect(Reason.CAPACITY, () -> f.local.commit(f.inbox, add));
        assert Arrays.equals(full, Files.readAllBytes(f.file()));
        assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.NOT_COMMITTED;
        // Existing slots still support explicit re-add at the slot cap.
        State known = slots.get(0); f.local.save(f.storeId, known, sample(known.uid, "reuse", 1, null));
        assert f.local.load().slots.size() == 1000;
        Files.write(f.file(), initial); f.local.commit(f.inbox, add);
        List<Decision> history = new ArrayList<>(); Decision first = f.local.load().decisions.get(0);
        history.add(first); State active = f.current();
        InboxStore.Receipt conflict = f.receive("other");
        ImportDecisionPreparation.Prepared keep = f.keep(conflict);
        for (int i = 1; i < 1000; i++) {
            history.add(new Decision(new UUID(3, i), Action.KEEP_LOCAL, UID, keep.plan.inboxId, conflict.id,
                    conflict.contextId, conflict.firstObservedAt, conflict.incoming,
                    State.active(active.version, LocalV2Binary.local(active.draft, null, active.draft.revision)),
                    keep.plan.reviewedFingerprint(), 0, null, Outcome.KEPT, active.version, active.draft.revision,
                    LocalV2Binary.local(active.draft, null, 1)));
        }
        Files.write(f.file(), LocalV2Codec.encode(new Snapshot(f.storeId, Collections.singletonList(active), history)));
        byte[] historyFull = Files.readAllBytes(f.file());
        expect(Reason.CAPACITY, () -> f.local.commit(f.inbox, keep));
        assert Arrays.equals(historyFull, Files.readAllBytes(f.file()));
        assert f.local.commit(f.inbox, add).effect == Effect.ALREADY_COMMITTED;
        assert f.local.commit(f.inbox, f.keep(r)).effect == Effect.ALREADY_EQUAL;
        f.local.save(f.storeId, active, edit(active.draft, "ordinary edit", null));
        State exhausted = State.active(Long.MAX_VALUE, sample("exhausted"));
        Files.write(f.file(), LocalV2Codec.encode(new Snapshot(f.storeId, Collections.singletonList(exhausted), Collections.emptyList())));
        expect(Reason.INVALID, () -> f.local.save(f.storeId, exhausted, edit(exhausted.draft, "overflow", null)));
        expect(Reason.INVALID, () -> f.local.delete(f.storeId, exhausted));
    }
    private static void projectedByteLimit(Path root) throws Exception {
        Fixture f = new Fixture(root); List<State> slots = new ArrayList<>();
        String large = String.join("", Collections.nCopies(65536, "x"));
        for (int i = 0; i < 255; i++) {
            TmmDraft d = sample(new UUID(8, i), "", 1, null);
            slots.add(State.active(1, new TmmDraft(d.uid, large, d.type, d.status, d.owner, d.createdAt,
                    d.updatedAt, null, d.comment, d.position, d.bearingDeg, null, 1)));
        }
        State last = State.active(1, sample(new UUID(8, 255), "", 1, null)); slots.add(last);
        int padding = LocalV2Codec.MAX_BYTES - LocalV2Codec.encode(new Snapshot(f.storeId, slots, Collections.emptyList())).length;
        TmmDraft d = last.draft;
        last = State.active(1, new TmmDraft(d.uid, String.join("", Collections.nCopies(d.displayId.length() + padding, "x")),
                d.type, d.status, d.owner, d.createdAt, d.updatedAt, null, d.comment, d.position, d.bearingDeg, null, 1));
        slots.set(255, last); byte[] full = LocalV2Codec.encode(new Snapshot(f.storeId, slots, Collections.emptyList()));
        assert full.length == LocalV2Codec.MAX_BYTES; Files.write(f.file(), full);
        State expected = last;
        LocalV2Store noWrite = new LocalV2Store(root.resolve("local"), (a, b) -> { throw new AssertionError("Oversize reached replacement"); });
        expect(Reason.INVALID, () -> noWrite.save(f.storeId, expected, edit(expected.draft, "one byte too many", null)));
        assert Arrays.equals(full, Files.readAllBytes(f.file())) && tempCount(root.resolve("local")) == 0;
        assert f.local.load().slots.size() == 256;
    }
    private static void failures(Path root) throws Exception {
        Fixture f = new Fixture(root); ImportDecisionPreparation.Prepared add = f.add(f.receive("incoming"), false);
        byte[] old = Files.readAllBytes(f.file());
        for (boolean partial : new boolean[]{true, false}) {
            LocalV2Store failedWrite = new LocalV2Store(root.resolve("local"), new LocalV2Store.Committer() {
                public void writeAndSync(Path temporary, byte[] bytes) throws IOException {
                    Files.write(temporary, partial ? Arrays.copyOf(bytes, bytes.length / 2) : bytes);
                    throw new IOException(partial ? "private-partial-write" : "private-sync-failure");
                }
                public void replace(Path a, Path b) { throw new AssertionError("Failed write reached replacement"); }
            });
            expect(Reason.UNKNOWN, () -> failedWrite.commit(f.inbox, add));
            assert Arrays.equals(old, Files.readAllBytes(f.file())) && tempCount(root.resolve("local")) == 0;
            assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.NOT_COMMITTED;
        }
        LocalV2Store unsupported = new LocalV2Store(root.resolve("local"), (a, b) -> {
            throw new AtomicMoveNotSupportedException("private-source", "private-destination", "unsupported");
        });
        expect(Reason.UNKNOWN, () -> unsupported.commit(f.inbox, add));
        assert Arrays.equals(old, Files.readAllBytes(f.file()));
        assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.NOT_COMMITTED;
        Path failedInit = root.resolve("failed-init");
        expect(Reason.UNKNOWN, () -> new LocalV2Store(failedInit, (a, b) -> { throw new IOException("private-init"); }).initializeNew());
        assert !Files.exists(failedInit.resolve("drafts.bin")) && tempCount(failedInit) == 0;
        LocalV2Store after = new LocalV2Store(root.resolve("local"), (a, b) -> {
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("private-after-replacement");
        });
        expect(Reason.UNKNOWN, () -> after.commit(f.inbox, add));
        assert f.local.readback(f.storeId, add.plan.operationId).status == CommitStatus.COMMITTED;
        assert f.local.commit(f.inbox, add).effect == Effect.ALREADY_COMMITTED;
        assert tempCount(root.resolve("local")) == 0;
        ImportDecisionPreparation.Prepared keep = f.keep(f.receive("cleanup failure"));
        LocalV2Store cleanup = new LocalV2Store(root.resolve("local"), (a, b) -> {
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectory(a); Files.write(a.resolve("synthetic"), new byte[]{1});
        });
        expect(Reason.UNKNOWN, () -> cleanup.commit(f.inbox, keep));
        assert f.local.readback(f.storeId, keep.plan.operationId).status == CommitStatus.COMMITTED;
        try (java.util.stream.Stream<Path> paths = Files.list(root.resolve("local"))) {
            for (Path p : (Iterable<Path>) paths.filter(Files::isDirectory)::iterator) {
                Files.delete(p.resolve("synthetic")); Files.delete(p);
            }
        }
        Path abandoned = root.resolve("local/drafts-v2-abandoned.tmp"); Files.write(abandoned, old);
        assert f.local.load().decisions.size() == 2 && Files.exists(abandoned);
        Path real = root.resolve("real"); Files.move(f.file(), real);
        Files.createSymbolicLink(f.file(), real);
        expect(Reason.UNKNOWN, f.local::load); assert Files.isSymbolicLink(f.file());
        Files.delete(f.file()); Files.move(real, f.file());
        Path lock = root.resolve("local/drafts.lock"); Files.delete(lock); Files.createSymbolicLink(lock, real);
        expect(Reason.UNKNOWN, f.local::load); Files.delete(lock);
        Path alias = root.resolve("alias"); Files.createSymbolicLink(alias, root.resolve("local"));
        expect(Reason.UNKNOWN, () -> new LocalV2Store(alias).load());
    }
    private static void locksAndRaces(Path root) throws Exception {
        Fixture f = new Fixture(root); InboxStore.Receipt a = f.receive("A"), b = f.receive("B");
        ImportDecisionPreparation.Prepared one = f.add(a, false), two = f.add(b, false);
        try (FileChannel channel = FileChannel.open(root.resolve("local/drafts.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            expect(Reason.BUSY, () -> f.local.commit(f.inbox, one));
            assert f.local.readback(f.storeId, one.plan.operationId).status == CommitStatus.UNKNOWN;
            assert f.inbox.load().receipts.size() == 2;
        }
        try (FileChannel channel = FileChannel.open(root.resolve("inbox/inbox.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            expect(Reason.BUSY, () -> f.local.commit(f.inbox, one));
            assert f.local.load().slots.isEmpty();
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Reason>> futures = new ArrayList<>();
            for (ImportDecisionPreparation.Prepared p : Arrays.asList(one, two)) {
                futures.add(executor.submit(() -> { start.await();
                    try { new LocalV2Store(root.resolve("local")).commit(new InboxStore(root.resolve("inbox")), p); return null; }
                    catch (Failure failure) { return failure.reason; }
                }));
            }
            start.countDown(); int committed = 0;
            for (Future<Reason> future : futures) { Reason reason = future.get(10, TimeUnit.SECONDS);
                if (reason == null) committed++; else assert reason == Reason.BUSY || reason == Reason.STALE;
            }
            assert committed == 1 && f.local.load().decisions.size() == 1;
            ImportDecisionPreparation.Prepared loser = f.current().draft.comment.equals("A") ? two : one;
            expect(Reason.STALE, () -> f.local.commit(f.inbox, loser));
        } finally { executor.shutdownNow(); }
        Path ready = root.resolve("ready");
        Process holder = process(root, "hold-local");
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && System.nanoTime() < deadline && holder.isAlive()) Thread.sleep(10);
            assert Files.exists(ready);
            expect(Reason.BUSY, () -> f.local.commit(f.inbox, one));
            assert f.inbox.load().receipts.size() == 2;
        } finally { holder.destroyForcibly(); assert holder.waitFor(10, TimeUnit.SECONDS); }
    }
    private static void crash(Path root, boolean after) throws Exception {
        Fixture f = new Fixture(root); f.receive("crash");
        Path evidence = root.resolve("operation.txt");
        Process process = process(root, after ? "crash-after" : "crash-before");
        assert process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 77;
        UUID operation = UUID.fromString(new String(Files.readAllBytes(evidence), java.nio.charset.StandardCharsets.UTF_8));
        Readback readback = f.local.readback(f.storeId, operation);
        assert readback.status == (after ? CommitStatus.COMMITTED : CommitStatus.NOT_COMMITTED);
        Snapshot loaded = f.local.load(); assert loaded.slots.size() == (after ? 1 : 0) && loaded.decisions.size() == (after ? 1 : 0);
        assert tempCount(root.resolve("local")) == (after ? 0 : 1);
    }
    private static void child(String[] args) throws Exception {
        Path root = Paths.get(args[1]); InboxStore inbox = new InboxStore(root.resolve("inbox"));
        if (args[0].equals("hold-local")) {
            try (FileChannel channel = FileChannel.open(root.resolve("local/drafts.lock"), StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                Files.write(root.resolve("ready"), new byte[]{1});
                Thread.sleep(20000);
            }
            return;
        }
        LocalV2Store local = new LocalV2Store(root.resolve("local"), (a, b) -> {
            if (args[0].equals("crash-after")) Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Runtime.getRuntime().halt(77);
        });
        InboxStore.Snapshot i = inbox.load(); Snapshot l = local.load();
        ImportDecisionPreparation.Prepared prepared = ImportDecisionPreparation.addLocally(i, i.receipts.get(0).id, l, false).prepare(i, l);
        Files.write(root.resolve("operation.txt"), prepared.plan.operationId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        local.commit(inbox, prepared); throw new AssertionError("Expected process halt");
    }
    private static Process process(Path root, String action) throws IOException {
        return new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-cp", System.getProperty("java.class.path"), LocalV2StoreTest.class.getName(), action, root.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("child.log").toFile()).start();
    }
    private static long tempCount(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(".tmp")).count();
        }
    }
    private static void expect(Reason reason, Task task) throws Exception {
        try { task.run(); throw new AssertionError("Expected local v2 failure: " + reason); }
        catch (Failure expected) {
            assert expected.reason == reason : expected.reason;
            assert expected.getCause() == null && expected.getSuppressed().length == 0;
            assert !expected.getMessage().contains("private-");
        }
    }
}
