package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class ImageAssociationRegistryTest {
    private static final String CONTEXT = "mission-api-v1|tak.example.invalid|synthetic";
    private static final Instant T1 = Instant.ofEpochSecond(1796731200, 123456789), T2 = T1.plusSeconds(60);
    private static final UUID STAGE_A = new UUID(0, 0x301), STAGE_B = new UUID(0, 0x302), STAGE_C = new UUID(0, 0x303);
    private static final ImageAssociationRegistry.StageProof READY = (id, offer) -> { };
    private interface Action { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("tmm-association-test-");
        try {
            goldens();
            lifecycle(root.resolve("life"));
            refusals(root.resolve("refuse"));
            failures(root.resolve("fail"));
            capacity(root.resolve("full"));
            stageGuard(root.resolve("guard"));
            System.out.println("Association registry passed (goldens, identity rules, refusals, failed commits, reopen and limits).");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static void goldens() throws Exception {
        for (String name : new String[]{"empty.bin", "one.bin", "two.bin"}) {
            byte[] golden = Files.readAllBytes(Paths.get("test-fixtures/synthetic/image-association-proposal/" + name));
            assert Arrays.equals(golden, ImageAssociationRegistry.encode(ImageAssociationRegistry.decode(golden))) : name;
        }
        byte[] two = Files.readAllBytes(Paths.get("test-fixtures/synthetic/image-association-proposal/two.bin"));
        ImageAssociationRegistry.Snapshot decoded = ImageAssociationRegistry.decode(two);
        assert decoded.records.size() == 2 && decoded.records.get(0).pngLength == 210 && decoded.records.get(1).pngLength == 220;
        assert decoded.records.get(0).verifiedAt.getNano() == 123456789;
        byte[] flipped = two.clone();
        flipped[50] ^= 1;
        byte[] truncated = Arrays.copyOf(two, two.length - 1), trailing = Arrays.copyOf(two, two.length + 1);
        for (byte[] bad : new byte[][]{flipped, truncated, trailing, new byte[0]}) rejects(() -> ImageAssociationRegistry.decode(bad));
    }

    private static void lifecycle(Path dir) throws Exception {
        InboxStore inbox = inbox(dir.resolve("inbox"));
        ImageOffer h1 = offer(1);
        inbox.receive(CONTEXT, ImageOffer.carrier(h1.metadata), null, T1);
        ImageAssociationRegistry registry = new ImageAssociationRegistry(dir.resolve("registry"));
        rejects(registry::load);
        assert !Files.exists(dir.resolve("registry/associations.bin")) : "a read must not create a registry";
        registry.initializeNew(inbox.load().inboxId);
        rejects(() -> registry.initializeNew(inbox.load().inboxId));

        ImageAssociationRegistry.Record first = registry.associate(inbox, CONTEXT, h1, STAGE_A, T1, READY);
        byte[] saved = bytes(dir);
        ImageAssociationRegistry.Record repeat = registry.associate(inbox, CONTEXT, h1, STAGE_B, T2, READY);
        assert repeat.verifiedAt.equals(T1) && repeat.stageLocalId.equals(STAGE_A) : "a repeat keeps the original";
        assert Arrays.equals(saved, bytes(dir));

        ImageOffer secondDelivery = copy(h1, h1.associationId, new UUID(0, 0x999), h1.sha256());
        registry.associate(inbox, CONTEXT, secondDelivery, STAGE_C, T2, READY);
        ImageAssociationRegistry reopened = new ImageAssociationRegistry(dir.resolve("registry"));
        assert reopened.load().records.size() == 2 && reopened.load().records.get(0).receiptId.equals(first.receiptId);
    }

    private static void refusals(Path dir) throws Exception {
        InboxStore inbox = inbox(dir.resolve("inbox"));
        ImageOffer h1 = offer(1), h2 = offer(2);
        inbox.receive(CONTEXT, ImageOffer.carrier(h1.metadata), null, T1);
        ImageAssociationRegistry registry = new ImageAssociationRegistry(dir.resolve("registry"));
        registry.initializeNew(inbox.load().inboxId);
        registry.associate(inbox, CONTEXT, h1, STAGE_A, T1, READY);
        byte[] saved = bytes(dir);
        Action[] refused = {
                () -> registry.associate(inbox, "other-context", h1, STAGE_B, T2, READY),
                () -> registry.associate(inbox, CONTEXT, copy(h1, h1.associationId, new UUID(0, 7), h2.sha256()), STAGE_B, T2, READY),
                () -> registry.associate(inbox, CONTEXT, h2, STAGE_A, T2, READY),
                () -> registry.associate(inbox, CONTEXT, h2, new UUID(0, 0), T2, READY),
                () -> registry.associate(inbox, " " + CONTEXT, h2, STAGE_B, T2, READY)};
        for (Action action : refused) {
            rejects(action);
            assert Arrays.equals(saved, bytes(dir)) : "a refusal changes nothing";
        }
        InboxStore other = inbox(dir.resolve("other"));
        other.receive(CONTEXT, ImageOffer.carrier(h2.metadata), null, T1);
        rejects(() -> registry.associate(other, CONTEXT, h2, STAGE_B, T2, READY));
        assert Arrays.equals(saved, bytes(dir));
        ImageAssociationRegistry.Record alternative = registry.associate(inbox, CONTEXT, h2, STAGE_B, T2, READY);
        assert alternative.receiptId.equals(registry.load().records.get(0).receiptId) && registry.load().records.size() == 2;
    }

    private static void failures(Path dir) throws Exception {
        InboxStore inbox = inbox(dir.resolve("inbox"));
        ImageOffer h1 = offer(1);
        inbox.receive(CONTEXT, ImageOffer.carrier(h1.metadata), null, T1);
        Path store = dir.resolve("registry");
        new ImageAssociationRegistry(store).initializeNew(inbox.load().inboxId);
        byte[] empty = bytes(dir);
        ImageAssociationRegistry broken = new ImageAssociationRegistry(store, (source, target) -> { throw new IOException("private path"); });
        try {
            broken.associate(inbox, CONTEXT, h1, STAGE_A, T1, READY);
            throw new AssertionError("expected failure");
        } catch (IOException safe) {
            assert !safe.getMessage().contains("private path");
        }
        assert Arrays.equals(empty, bytes(dir));
        byte[] future = empty.clone();
        future[7] = 2;
        Files.write(store.resolve("associations.bin"), future);
        ImageAssociationRegistry registry = new ImageAssociationRegistry(store);
        rejects(registry::load);
        rejects(() -> registry.associate(inbox, CONTEXT, h1, STAGE_A, T1, READY));
        assert Arrays.equals(future, Files.readAllBytes(store.resolve("associations.bin"))) : "unsupported data is never rewritten";
    }

    private static void capacity(Path dir) throws Exception {
        InboxStore inbox = inbox(dir.resolve("inbox"));
        ImageOffer h1 = offer(1);
        inbox.receive(CONTEXT, ImageOffer.carrier(h1.metadata), null, T1);
        ImageAssociationRegistry registry = new ImageAssociationRegistry(dir.resolve("registry"));
        registry.initializeNew(inbox.load().inboxId);
        for (int i = 1; i <= ImageAssociationRegistry.MAX_RECORDS; i++)
            registry.associate(inbox, CONTEXT, copy(h1, h1.associationId, new UUID(1, i), h1.sha256()), new UUID(2, i), T1, READY);
        assert registry.load().records.size() == ImageAssociationRegistry.MAX_RECORDS;
        byte[] full = bytes(dir);
        rejects(() -> registry.associate(inbox, CONTEXT, copy(h1, h1.associationId, new UUID(1, 999), h1.sha256()), new UUID(2, 999), T1, READY));
        assert Arrays.equals(full, bytes(dir));
        char[] padded = new char[ImageAssociationRegistry.MAX_CONTEXT_BYTES + 1];
        Arrays.fill(padded, 'x');
        String tooLong = new String(padded);
        InboxStore second = inbox(dir.resolve("long-inbox"));
        second.receive(tooLong, ImageOffer.carrier(h1.metadata), null, T1);
        ImageAssociationRegistry other = new ImageAssociationRegistry(dir.resolve("long-registry"));
        other.initializeNew(second.load().inboxId);
        rejects(() -> other.associate(second, tooLong, h1, STAGE_A, T1, READY));
    }

    private static void stageGuard(Path dir) throws Exception {
        InboxStore inbox = inbox(dir.resolve("inbox"));
        ImageOffer h1 = offer(1);
        inbox.receive(CONTEXT, ImageOffer.carrier(h1.metadata), null, T1);
        ImageAssociationRegistry registry = new ImageAssociationRegistry(dir.resolve("registry"));
        rejects(() -> registry.unlessReferenced(STAGE_A, () -> { throw new AssertionError("absent registry must refuse"); }));
        registry.initializeNew(inbox.load().inboxId);
        byte[] empty = bytes(dir);
        rejects(() -> registry.associate(inbox, CONTEXT, h1, STAGE_A, T1, (id, offer) -> { throw new IOException("private path"); }));
        assert Arrays.equals(empty, bytes(dir)) : "a failed stage proof commits nothing";

        registry.associate(inbox, CONTEXT, h1, STAGE_A, T1, READY);
        registry.associate(inbox, CONTEXT, h1, STAGE_B, T2, (id, offer) -> { throw new AssertionError("a repeat needs no live stage entry"); });
        rejects(() -> registry.unlessReferenced(STAGE_A, () -> { throw new AssertionError("referenced entry must be refused"); }));

        ImageAssociationRegistry second = new ImageAssociationRegistry(dir.resolve("registry"));
        assert registry.unlessReferenced(STAGE_B, () -> {
            try { second.load(); throw new AssertionError("the lock must be held"); } catch (IOException busy) { }
            return "ran";
        }).equals("ran") : "an unreferenced entry proceeds with the lock held";

        IOException own = new IOException("stage failure");
        try {
            registry.unlessReferenced(STAGE_B, () -> { throw own; });
            throw new AssertionError("expected failure");
        } catch (IOException thrown) { assert thrown == own : "the action's failure is not relabelled"; }
    }

    private static InboxStore inbox(Path dir) throws IOException {
        InboxStore inbox = new InboxStore(dir);
        inbox.initializeNew();
        return inbox;
    }

    private static byte[] bytes(Path dir) throws IOException {
        Path file = Files.exists(dir.resolve("registry/associations.bin")) ? dir.resolve("registry/associations.bin") : dir.resolve("associations.bin");
        return Files.readAllBytes(file);
    }

    private static ImageOffer offer(int n) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get("test-fixtures/synthetic/image-offer-proposal/offer-h" + n + ".bin"))) {
            return ImageOfferCodec.decode(in);
        }
    }

    private static ImageOffer copy(ImageOffer base, UUID association, UUID delivery, byte[] digest) {
        return new ImageOffer(association, delivery, base.metadata, digest, base.pngLength, base.width, base.height);
    }

    private static void rejects(Action action) throws Exception {
        try { action.run(); }
        catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected refusal");
    }
}
