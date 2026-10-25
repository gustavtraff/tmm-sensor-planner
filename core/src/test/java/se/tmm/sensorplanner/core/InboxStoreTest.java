package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class InboxStoreTest {
    private static final UUID UID = new UUID(0, 15);
    private static final Instant NOW = Instant.ofEpochSecond(100, 123);
    private interface Action { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) { child(args); return; }
        Path root = Files.createTempDirectory("tmm-inbox-test-");
        try {
            receiptAndImages(root.resolve("receipts"), root.resolve("drafts"));
            faultsAndInitialization(root.resolve("faults"));
            limits(root.resolve("limits"));
            processes(root.resolve("restart"));
            racingProcesses(root.resolve("same"), true);
            racingProcesses(root.resolve("different"), false);
            crashedCommit(root.resolve("crash-before"), false);
            crashedCommit(root.resolve("crash-after"), true);
            System.out.println("Inbox storage passed (deduplication, conflicts, images, reopen, processes, contention, crash points and write failures).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static TmmDraft sample(String comment, long revision, String image) {
        return new TmmDraft(UID, "PIR SYNTHETIC 15", DeviceType.PIR, DeviceStatus.PLANNED, "SYNTHETIC ROLE",
                Instant.EPOCH, Instant.ofEpochSecond(10, 456), null, comment, new Position(-0.0, 1.25), -0.0, image, revision);
    }

    private static void receiptAndImages(Path directory, Path draftsDirectory) throws Exception {
        InboxStore inbox = new InboxStore(directory);
        fails(inbox::load); fails(() -> inbox.find("SOURCE-A", sample("base", 1, null)));
        fails(() -> inbox.receive("SOURCE-A", sample("base", 1, null), null, NOW));
        assert !Files.exists(directory.resolve("inbox.bin"));
        InboxStore.Snapshot initialized = inbox.initializeNew();
        assert initialized.receipts.isEmpty() && Files.size(directory.resolve("inbox.bin")) == 60;
        DraftStore drafts = new DraftStore(draftsDirectory);
        TmmDraft local = sample("local", 1, "owned-original.png"); drafts.save(local);
        Path image = draftsDirectory.resolve(local.imageRef); Files.write(image, new byte[]{1, 3, 5, 7});
        byte[] draftBytes = bytes(draftsDirectory.resolve("drafts.bin")), imageBytes = bytes(image);
        TmmDraft incoming = sample("base", 99, null);
        InboxStore.Receipt first = inbox.receive("SOURCE-A", incoming, local, NOW);
        byte[] committed = bytes(directory.resolve("inbox.bin"));
        assert first.incoming.revision == 1 && first.incoming.imageRef == null;
        assert first.localObservations.get(0).revision == 1 && first.localObservations.get(0).imageRef == null;
        assert local.imageRef.equals("owned-original.png") && incoming.revision == 99;
        InboxStore.Receipt repeat = inbox.receive("SOURCE-A", sample("base", Long.MAX_VALUE, null), null, Instant.EPOCH);
        assert repeat.id.equals(first.id) && repeat.firstObservedAt.equals(NOW) && repeat.localObservations.size() == 1;
        assert Arrays.equals(committed, bytes(directory.resolve("inbox.bin")));
        TmmDraft zero = new TmmDraft(UID, incoming.displayId, incoming.type, incoming.status, incoming.owner,
                incoming.createdAt, incoming.updatedAt, null, incoming.comment, new Position(0.0, 1.25), 0.0, null, 1);
        assert inbox.receive("SOURCE-A", zero, null, NOW).id.equals(first.id);
        InboxStore.Receipt different = inbox.receive("SOURCE-A", sample("different", 1, null), local, NOW);
        assert !different.id.equals(first.id);
        assert !inbox.receive("SOURCE-B", incoming, null, NOW).id.equals(first.id);
        TmmDraft anotherUid = new TmmDraft(new UUID(0, 16), incoming.displayId, incoming.type, incoming.status,
                incoming.owner, incoming.createdAt, incoming.updatedAt, null, incoming.comment, incoming.position, incoming.bearingDeg, null, 1);
        inbox.receive("SOURCE-A", anotherUid, null, NOW);
        assert inbox.load().receipts.size() == 4;
        assert Arrays.equals(draftBytes, bytes(draftsDirectory.resolve("drafts.bin"))) && Arrays.equals(imageBytes, bytes(image));
        TmmDraft replaced = local.edit(local.displayId, local.type, local.status, local.owner, null,
                local.comment, local.position, local.bearingDeg, "owned-replacement.png", local.updatedAt);
        drafts.save(replaced); Path replacement = draftsDirectory.resolve(replaced.imageRef); Files.write(replacement, new byte[]{2, 4, 6});
        byte[] currentDrafts = bytes(draftsDirectory.resolve("drafts.bin"));
        assert ImportReview.assess(Collections.singletonList(replaced), sample("local", 1, null)).kind
                == ImportReview.Kind.SAME_METADATA;
        InboxStore.Receipt observed = inbox.retainLocalObservation(first.id, replaced);
        assert observed.localObservations.size() == 2 && observed.localObservations.get(1).revision == 2;
        assert observed.localObservations.get(1).imageRef == null;
        inbox.retainLocalObservation(first.id, null);
        byte[] all = bytes(directory.resolve("inbox.bin"));
        assert inbox.retainLocalObservation(first.id, null).localObservations.size() == 3;
        assert inbox.retainLocalObservation(first.id, replaced).localObservations.size() == 3;
        assert Arrays.equals(all, bytes(directory.resolve("inbox.bin")));
        InboxStore reopened = new InboxStore(directory);
        assert reopened.load().inboxId.equals(initialized.inboxId);
        assert reopened.find("SOURCE-A", incoming).id.equals(first.id);
        assert reopened.find("SOURCE-A", sample("absent", 1, null)) == null;
        InboxStore.Receipt restored = reopened.find("SOURCE-A", incoming);
        assert restored.firstObservedAt.equals(NOW) && restored.contextId.equals("SOURCE-A");
        assert InboxCodec.sameMetadata(restored.incoming, incoming);
        assert restored.localObservations.size() == 3;
        assert InboxCodec.sameObservation(restored.localObservations.get(0), sample("local", 1, null));
        assert InboxCodec.sameObservation(restored.localObservations.get(1), sample("local", 2, null));
        assert restored.localObservations.get(2) == null;
        assert reopened.find("SOURCE-A", sample("different", 1, null)).id.equals(different.id);
        assert Arrays.equals(currentDrafts, bytes(draftsDirectory.resolve("drafts.bin")));
        assert drafts.load().get(0).imageRef.equals(replaced.imageRef) && Arrays.equals(imageBytes, bytes(image));
        assert Arrays.equals(new byte[]{2, 4, 6}, bytes(replacement));
        assert !new String(all, StandardCharsets.ISO_8859_1).contains("owned-");
        illegal(() -> inbox.receive("SOURCE-A", sample("base", 1, "foreign-image"), null, NOW));
        illegal(() -> inbox.receive(" SOURCE-A", incoming, null, NOW));
        illegal(() -> inbox.retainLocalObservation(first.id, anotherUid));
        fails(() -> inbox.retainLocalObservation(UUID.randomUUID(), null));
        fails(() -> inbox.receive("SOURCE-A", sample("bad\uD800", 1, null), null, NOW));
        assert Arrays.equals(all, bytes(directory.resolve("inbox.bin")));
        try { reopened.load().receipts.clear(); throw new AssertionError("Mutable snapshot"); } catch (UnsupportedOperationException expected) { }
        try { first.localObservations.clear(); throw new AssertionError("Mutable receipt"); } catch (UnsupportedOperationException expected) { }
    }

    private static void faultsAndInitialization(Path directory) throws Exception {
        Path failedInit = directory.resolve("initialization");
        InboxStore initializing = new InboxStore(failedInit, (a, b) -> { throw new IOException("private-init-before"); });
        fails(initializing::initializeNew);
        assert !Files.exists(failedInit.resolve("inbox.bin")) && tempCount(failedInit) == 0;
        InboxStore ambiguousInit = new InboxStore(failedInit, (a, b) -> {
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("private-init-after");
        });
        fails(ambiguousInit::initializeNew);
        InboxStore initialized = new InboxStore(failedInit);
        UUID initializedId = initialized.load().inboxId;
        fails(initialized::initializeNew);
        assert initialized.load().inboxId.equals(initializedId);
        InboxStore good = new InboxStore(directory); good.initializeNew();
        Path data = directory.resolve("inbox.bin"); byte[] empty = bytes(data);
        fails(good::initializeNew); assert Arrays.equals(empty, bytes(data));
        InboxStore before = new InboxStore(directory, (a, b) -> { throw new AtomicMoveNotSupportedException("private-path", "private-target", "synthetic"); });
        fails(() -> before.receive("A", sample("before", 1, null), null, NOW));
        assert Arrays.equals(empty, bytes(data)) && good.find("A", sample("before", 1, null)) == null;
        assert tempCount(directory) == 0;
        InboxStore after = new InboxStore(directory, (a, b) -> { Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); throw new IOException("private-after-replace"); });
        fails(() -> after.receive("A", sample("after", 1, null), null, NOW));
        InboxStore.Receipt found = good.find("A", sample("after", 1, null)); assert found != null;
        assert good.receive("A", sample("after", 7, null), null, Instant.EPOCH).id.equals(found.id);
        byte[] saved = bytes(data);
        fails(() -> before.retainLocalObservation(found.id, sample("local", 2, null)));
        assert Arrays.equals(saved, bytes(data));
        fails(() -> after.retainLocalObservation(found.id, sample("local", 2, null)));
        assert good.find("A", sample("after", 1, null)).localObservations.size() == 2;
        byte[] withObservation = bytes(data);
        assert good.retainLocalObservation(found.id, sample("local", 2, null)).localObservations.size() == 2;
        assert Arrays.equals(withObservation, bytes(data));
        try (FileChannel channel = FileChannel.open(directory.resolve("inbox.lock"), StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            fails(good::load); fails(good::initializeNew);
            fails(() -> good.receive("A", sample("busy", 1, null), null, NOW));
        }
        for (byte[] invalid : new byte[][]{new byte[0], new byte[12], corrupt(withObservation), future(withObservation)}) {
            Files.write(data, invalid);
            fails(good::load); fails(good::initializeNew);
            fails(() -> good.receive("A", sample("attempt", 1, null), null, NOW));
            fails(() -> good.find("A", sample("after", 1, null)));
            assert Arrays.equals(invalid, bytes(data));
        }
        Files.delete(data); Files.write(directory.resolve("inbox-abandoned.tmp"), withObservation);
        fails(good::load); assert !Files.exists(data);
        Path target = directory.resolve("synthetic-target"); Files.write(target, withObservation);
        Files.createSymbolicLink(data, target);
        fails(good::load); fails(good::initializeNew);
        assert Arrays.equals(withObservation, bytes(target)); Files.delete(data);
        Files.createSymbolicLink(data, directory.resolve("missing-target")); fails(good::initializeNew); Files.delete(data);
        Files.delete(directory.resolve("inbox.lock")); Files.createSymbolicLink(directory.resolve("inbox.lock"), target);
        fails(good::initializeNew); assert Arrays.equals(withObservation, bytes(target));
    }

    private static void limits(Path directory) throws Exception {
        InboxStore store = new InboxStore(directory); InboxStore.Snapshot initial = store.initializeNew();
        Path data = directory.resolve("inbox.bin");
        List<InboxStore.Receipt> receipts = new ArrayList<>();
        for (int i = 0; i < 500; i++) receipts.add(new InboxStore.Receipt(new UUID(1, i), "A", NOW,
                sample("variant-" + i, 1, null), Collections.singletonList(null)));
        Files.write(data, InboxCodec.encode(new InboxStore.Snapshot(initial.inboxId, receipts)));
        byte[] full = bytes(data);
        assert store.receive("A", sample("variant-0", 99, null), null, NOW).id.equals(receipts.get(0).id);
        fails(() -> store.receive("A", sample("new", 1, null), null, NOW));
        fails(() -> store.retainLocalObservation(receipts.get(0).id, sample("local", 1, "local-image")));
        assert Arrays.equals(full, bytes(data));
        String large = String.join("", Collections.nCopies(65536, "x"));
        List<InboxStore.Receipt> oversized = new ArrayList<>();
        TmmDraft largeMetadata = new TmmDraft(UID, "PIR 15", DeviceType.PIR, DeviceStatus.ACTIVE, large,
                Instant.EPOCH, NOW, null, "synthetic", new Position(0, 1), null, null, 1);
        List<TmmDraft> locals = new ArrayList<>();
        for (int i = 1; i < 300; i++) locals.add(new TmmDraft(UID, largeMetadata.displayId, largeMetadata.type,
                largeMetadata.status, large, Instant.EPOCH, NOW, null, "synthetic", largeMetadata.position, null, null, i));
        oversized.add(new InboxStore.Receipt(new UUID(2, 1), "A", NOW, largeMetadata, locals));
        fails(() -> InboxCodec.encode(new InboxStore.Snapshot(initial.inboxId, oversized)));
        assert Arrays.equals(full, bytes(data));
        fails(() -> store.receive(large + "x", sample("base", 1, null), null, NOW));
        assert Arrays.equals(full, bytes(data));
        try (OutputStream out = Files.newOutputStream(data)) {
            byte[] block = new byte[65536]; for (int i = 0; i < 257; i++) out.write(block);
        }
        byte[] digest = fileDigest(data); fails(store::load); fails(store::initializeNew);
        assert Arrays.equals(digest, fileDigest(data));
    }

    private static void processes(Path directory) throws Exception {
        new InboxStore(directory).initializeNew();
        Process first = start("once", directory, "base"); done(first, 0);
        String id = new String(readAll(first.getInputStream()), StandardCharsets.US_ASCII).trim();
        Process repeat = start("once", directory, "base"); done(repeat, 0);
        assert id.equals(new String(readAll(repeat.getInputStream()), StandardCharsets.US_ASCII).trim());
        Process different = start("once", directory, "other"); done(different, 0);
        assert new InboxStore(directory).load().receipts.size() == 2;
    }

    private static void racingProcesses(Path directory, boolean same) throws Exception {
        new InboxStore(directory).initializeNew();
        Process a = null, b = null;
        try {
            try (FileChannel channel = FileChannel.open(directory.resolve("inbox.lock"), StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                a = start("race", directory, "0", "base"); b = start("race", directory, "1", same ? "base" : "other");
                awaitFile(directory.resolve("ready-0")); awaitFile(directory.resolve("ready-1"));
                Files.createFile(directory.resolve("start"));
                awaitFile(directory.resolve("busy-0")); awaitFile(directory.resolve("busy-1"));
            }
            done(a, 0); done(b, 0);
            String one = new String(readAll(a.getInputStream()), StandardCharsets.US_ASCII).trim();
            String two = new String(readAll(b.getInputStream()), StandardCharsets.US_ASCII).trim();
            assert one.equals(two) == same;
            assert new InboxStore(directory).load().receipts.size() == (same ? 1 : 2);
        } finally { if (a != null) a.destroyForcibly(); if (b != null) b.destroyForcibly(); }
    }

    private static void crashedCommit(Path directory, boolean after) throws Exception {
        InboxStore store = new InboxStore(directory); store.initializeNew(); byte[] original = bytes(directory.resolve("inbox.bin"));
        Process child = start(after ? "halt-after" : "halt-before", directory, "crashed"); done(child, after ? 18 : 17);
        InboxStore.Receipt found = store.find("A", sample("crashed", 1, null));
        if (after) {
            assert found != null;
            assert store.receive("A", sample("crashed", 9, null), null, NOW).id.equals(found.id);
        } else {
            assert found == null && Arrays.equals(original, bytes(directory.resolve("inbox.bin")));
            assert tempCount(directory) == 1;
        }
    }

    private static void child(String[] args) throws Exception {
        Path directory = Paths.get(args[1]); String mode = args[0];
        if (mode.startsWith("halt-")) {
            InboxStore store = new InboxStore(directory, (a, b) -> {
                if (mode.equals("halt-after")) Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                Runtime.getRuntime().halt(mode.equals("halt-after") ? 18 : 17);
            });
            store.receive("A", sample(args[2], 1, null), null, NOW); throw new AssertionError("Halt hook returned");
        }
        String variant = args[2];
        if (mode.equals("race")) {
            String tag = args[2]; variant = args[3]; Files.createFile(directory.resolve("ready-" + tag)); awaitFile(directory.resolve("start"));
            for (int i = 0; i < 2000; i++) {
                try { System.out.println(new InboxStore(directory).receive("A", sample(variant, 1, null), null, NOW).id); return; }
                catch (IOException e) {
                    if (!e.getMessage().equals("Inbox is busy; retry explicitly")) throw e;
                    if (!Files.exists(directory.resolve("busy-" + tag))) Files.createFile(directory.resolve("busy-" + tag));
                    Thread.sleep(5);
                }
            }
            throw new AssertionError("Contended receipt did not finish");
        }
        System.out.println(new InboxStore(directory).receive("A", sample(variant, 1, null), null, NOW).id);
    }

    private static Process start(String... args) throws IOException {
        List<String> command = new ArrayList<>(Arrays.asList(System.getProperty("java.home") + "/bin/java", "-ea", "-Xmx64m",
                "-cp", System.getProperty("java.class.path"), InboxStoreTest.class.getName()));
        command.addAll(Arrays.asList(args)); return new ProcessBuilder(command).redirectErrorStream(true).start();
    }
    private static Process start(String mode, Path directory, String... args) throws IOException {
        List<String> values = new ArrayList<>(Arrays.asList(mode, directory.toString())); values.addAll(Arrays.asList(args));
        return start(values.toArray(new String[0]));
    }
    private static void done(Process process, int expected) throws Exception {
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("Test child timed out"); }
        if (process.exitValue() != expected) throw new AssertionError("Child failed: " + new String(readAll(process.getInputStream()), StandardCharsets.UTF_8));
    }
    private static void awaitFile(Path path) throws Exception {
        for (int i = 0; i < 2000; i++) { if (Files.exists(path)) return; Thread.sleep(5); }
        throw new AssertionError("Test rendezvous timed out");
    }
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] block = new byte[8192]; int n;
        while ((n = in.read(block)) != -1) out.write(block, 0, n); return out.toByteArray();
    }
    private static byte[] bytes(Path path) throws IOException { return Files.readAllBytes(path); }
    private static byte[] fileDigest(Path path) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256"); try (InputStream in = Files.newInputStream(path)) {
            byte[] block = new byte[8192]; int n; while ((n = in.read(block)) != -1) d.update(block, 0, n);
        } return d.digest();
    }
    private static long tempCount(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) { return files.filter(p -> p.toString().endsWith(".tmp")).count(); }
    }
    private static byte[] corrupt(byte[] bytes) { byte[] changed = bytes.clone(); changed[40] ^= 1; return changed; }
    private static byte[] future(byte[] bytes) throws Exception {
        byte[] changed = bytes.clone(); java.nio.ByteBuffer.wrap(changed).putInt(4, 2);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(changed, changed.length - 32));
        System.arraycopy(digest, 0, changed, changed.length - 32, 32); return changed;
    }
    private static void fails(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected failed inbox operation"); }
        catch (IOException expected) { assert expected.getCause() == null; assert !expected.getMessage().contains("private-"); }
    }
    private static void illegal(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected invalid inbox argument"); } catch (IllegalArgumentException expected) { }
    }
}
