package se.tmm.sensorplanner.core;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static se.tmm.sensorplanner.core.ImportReview.Kind.*;

public final class InboxReviewTest {
    private static final UUID UID = new UUID(0, 15), OTHER = new UUID(0, 16);
    private interface Action { void run(); }

    public static void main(String[] args) throws Exception {
        goldenFixtureAndGrouping();
        invalidLocalSnapshots();
        localChangesAndStoragePreservation();
        System.out.println("Inbox review passed (grouping, provenance, current versus historical state, edits, deletion, images and read-only preservation).");
    }

    private static TmmDraft copy(TmmDraft value, UUID uid, String comment, long revision, String image) {
        return new TmmDraft(uid, value.displayId, value.type, value.status, value.owner,
                value.createdAt, value.updatedAt, value.deployedAt, comment, value.position,
                value.bearingDeg, image, revision);
    }

    private static InboxStore.Snapshot fixture() throws Exception {
        byte[] source = Files.readAllBytes(Paths.get("test-fixtures/synthetic/inbox/candidate-v1.base64"));
        return InboxCodec.decode(Base64.getMimeDecoder().decode(source));
    }

    private static void goldenFixtureAndGrouping() throws Exception {
        InboxStore.Snapshot inbox = fixture();
        TmmDraft base = inbox.receipts.get(0).incoming;
        TmmDraft current = copy(base, base.uid, base.comment, Long.MAX_VALUE, "synthetic-current-image");
        List<TmmDraft> locals = new ArrayList<>(Arrays.asList(current,
                copy(base, OTHER, "unrelated local", 1, null)));
        InboxReview review = InboxReview.assess(inbox, locals);
        assert review.inboxId.equals(inbox.inboxId) && review.groups.size() == 1;
        InboxReview.Group group = review.groups.get(0);
        assert group.uid.equals(base.uid) && group.currentLocal == current && group.variants.size() == 3;
        assert group.variants.get(0).kind == SAME_METADATA;
        assert group.variants.get(1).kind == REVIEW_REQUIRED;
        assert group.variants.get(2).kind == SAME_METADATA; // Different source and signed-zero bits remain separate.
        for (int i = 0; i < 3; i++) {
            assert group.variants.get(i).receipt == inbox.receipts.get(i);
            assert group.variants.get(i).kind == ImportReview.assess(locals, inbox.receipts.get(i).incoming).kind;
        }
        assert !group.variants.get(0).receipt.contextId.equals(group.variants.get(2).receipt.contextId);
        assert group.variants.get(0).receipt.localObservations.get(0).revision == 7;
        assert group.variants.get(0).receipt.localObservations.get(1).revision == 8;
        assert group.variants.get(1).receipt.localObservations.equals(Collections.singletonList(null));
        assert !ImportReview.sameMetadata(group.variants.get(0).receipt.localObservations.get(0), current);
        locals.clear();
        assert group.currentLocal == current && group.currentLocal.imageRef.equals("synthetic-current-image");
        immutable(() -> review.groups.clear()); immutable(() -> group.variants.clear());

        // Interleaved receipt order and equal visible labels must not merge UUIDs or select a winner.
        List<InboxStore.Receipt> interleaved = new ArrayList<>(inbox.receipts);
        TmmDraft other = copy(base, OTHER, base.comment, 1, null);
        InboxStore.Receipt secondDevice = new InboxStore.Receipt(new UUID(0, 99), "SYNTHETIC-CONTEXT-A",
                Instant.EPOCH, other, Collections.singletonList(null));
        interleaved.add(1, secondDevice);
        InboxReview absent = InboxReview.assess(new InboxStore.Snapshot(inbox.inboxId, interleaved), Collections.emptyList());
        assert absent.groups.size() == 2 && absent.groups.get(0).uid.equals(base.uid) && absent.groups.get(1).uid.equals(OTHER);
        assert absent.groups.get(0).variants.size() == 3 && absent.groups.get(1).variants.size() == 1;
        assert absent.groups.get(0).variants.get(1).receipt == inbox.receipts.get(1);
        for (InboxReview.Group item : absent.groups) {
            assert item.currentLocal == null;
            for (InboxReview.Variant variant : item.variants) assert variant.kind == NEW_UID;
        }
    }

    private static void invalidLocalSnapshots() throws Exception {
        InboxStore.Snapshot inbox = fixture(), empty = new InboxStore.Snapshot(inbox.inboxId, Collections.emptyList());
        TmmDraft unrelated = copy(inbox.receipts.get(0).incoming, OTHER, "unrelated", 1, null);
        for (InboxStore.Snapshot snapshot : Arrays.asList(inbox, empty)) {
            invalid(() -> InboxReview.assess(snapshot, null));
            invalid(() -> InboxReview.assess(snapshot, Collections.singletonList(null)));
            invalid(() -> InboxReview.assess(snapshot, Arrays.asList(unrelated, unrelated)));
        }
        invalid(() -> InboxReview.assess(null, Collections.emptyList()));
        assert InboxReview.assess(empty, Collections.singletonList(unrelated)).groups.isEmpty();
        // A missing inbox must be rejected by load, not fabricated as an empty successful review.
    }

    private static void localChangesAndStoragePreservation() throws Exception {
        Path root = Files.createTempDirectory("tmm-inbox-review-");
        try {
            InboxStore inbox = new InboxStore(root.resolve("inbox"));
            try { inbox.load(); throw new AssertionError("Missing inbox accepted"); } catch (java.io.IOException expected) { }
            inbox.initializeNew();
            DraftStore drafts = new DraftStore(root.resolve("drafts"));
            TmmDraft local = new TmmDraft(UID, "PIR SYNTHETIC 15", DeviceType.PIR, DeviceStatus.PLANNED,
                    "SYNTHETIC ROLE", Instant.EPOCH, Instant.EPOCH, null, "base", new Position(0, 1), null, "owned-original", 1);
            drafts.save(local);
            Path image = root.resolve("drafts").resolve(local.imageRef); Files.write(image, new byte[]{1, 2, 3});
            TmmDraft incoming = copy(local, UID, "base", 1, null);
            InboxStore.Receipt receipt = inbox.receive("SYNTHETIC-CONTEXT", incoming, local, Instant.EPOCH);
            InboxStore.Snapshot snapshot = inbox.load();
            byte[] inboxBytes = Files.readAllBytes(root.resolve("inbox/inbox.bin"));
            byte[] originalDrafts = Files.readAllBytes(root.resolve("drafts/drafts.bin"));
            InboxReview initial = InboxReview.assess(snapshot, drafts.load());
            assert initial.groups.get(0).variants.get(0).kind == SAME_METADATA;
            assert Arrays.equals(originalDrafts, Files.readAllBytes(root.resolve("drafts/drafts.bin")));

            TmmDraft imageOnly = local.edit(local.displayId, local.type, local.status, local.owner, null,
                    local.comment, local.position, null, "owned-replacement", Instant.EPOCH);
            drafts.save(imageOnly);
            Path replacement = root.resolve("drafts").resolve(imageOnly.imageRef); Files.write(replacement, new byte[]{4, 5});
            InboxReview images = InboxReview.assess(snapshot, drafts.load());
            assert images.groups.get(0).variants.get(0).kind == SAME_METADATA;
            assert images.groups.get(0).currentLocal.imageRef.equals("owned-replacement");
            assert images.groups.get(0).currentLocal.revision == 2;
            assert initial.groups.get(0).currentLocal.imageRef.equals("owned-original");
            assert receipt.localObservations.size() == 1 && receipt.localObservations.get(0).revision == 1;
            assert receipt.localObservations.get(0).imageRef == null;

            TmmDraft detached = imageOnly.edit(imageOnly.displayId, imageOnly.type, imageOnly.status, imageOnly.owner, null,
                    imageOnly.comment, imageOnly.position, null, null, Instant.EPOCH);
            drafts.save(detached);
            InboxReview removedImage = InboxReview.assess(snapshot, drafts.load());
            assert removedImage.groups.get(0).variants.get(0).kind == SAME_METADATA;
            assert removedImage.groups.get(0).currentLocal.imageRef == null;
            assert images.groups.get(0).currentLocal.imageRef.equals("owned-replacement");

            TmmDraft edited = detached.edit(detached.displayId, detached.type, detached.status, detached.owner, null,
                    "changed locally", detached.position, null, detached.imageRef, Instant.EPOCH);
            drafts.save(edited);
            InboxReview changed = InboxReview.assess(snapshot, drafts.load());
            assert changed.groups.get(0).variants.get(0).kind == REVIEW_REQUIRED;
            assert initial.groups.get(0).currentLocal.comment.equals("base");
            assert changed.groups.get(0).currentLocal.comment.equals("changed locally");
            assert receipt.localObservations.get(0).comment.equals("base");

            drafts.delete(UID, 4);
            InboxReview deleted = InboxReview.assess(snapshot, drafts.load());
            assert deleted.groups.get(0).currentLocal == null && deleted.groups.get(0).variants.get(0).kind == NEW_UID;
            assert deleted.groups.get(0).variants.get(0).receipt.id.equals(receipt.id);
            // Explicit local recreation can reuse a revision; review makes no generation/freshness claim.
            TmmDraft recreated = copy(local, UID, "recreated locally", 1, "owned-replacement");
            drafts.save(recreated);
            byte[] latestDrafts = Files.readAllBytes(root.resolve("drafts/drafts.bin"));
            InboxReview reopened = InboxReview.assess(new InboxStore(root.resolve("inbox")).load(), new DraftStore(root.resolve("drafts")).load());
            assert reopened.groups.get(0).currentLocal.revision == 1;
            assert reopened.groups.get(0).currentLocal.comment.equals("recreated locally");
            assert reopened.groups.get(0).variants.get(0).kind == REVIEW_REQUIRED;
            assert Arrays.equals(latestDrafts, Files.readAllBytes(root.resolve("drafts/drafts.bin")));
            assert Arrays.equals(inboxBytes, Files.readAllBytes(root.resolve("inbox/inbox.bin")));
            assert Arrays.equals(new byte[]{1, 2, 3}, Files.readAllBytes(image));
            assert Arrays.equals(new byte[]{4, 5}, Files.readAllBytes(replacement));
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }

    private static void invalid(Action action) {
        try { action.run(); throw new AssertionError("Invalid snapshot accepted"); }
        catch (NullPointerException | IllegalArgumentException expected) { }
    }
    private static void immutable(Action action) {
        try { action.run(); throw new AssertionError("Mutable review result"); }
        catch (UnsupportedOperationException expected) { }
    }
}
