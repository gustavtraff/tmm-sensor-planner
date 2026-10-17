package se.tmm.sensorplanner.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ShareReviewTest {
    private static final UUID UID = new UUID(0, 1);
    private static final Instant CREATED = Instant.EPOCH;
    private static final Instant UPDATED = CREATED.plusSeconds(10);
    private static final String TARGET = "synthetic-recipient-1";
    private static final String LABEL = "Synthetic recipient A";

    public static void main(String[] args) throws Exception {
        metadataAndImages();
        changedDraftNeedsReview();
        changedDestinationNeedsReview();
        invalidDestinations();
        reviewDoesNotWrite();
        System.out.println("Share review tests passed (field allowlist, images, freshness and preservation).");
    }

    private static TmmDraft sample(long revision, String image) {
        return new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, image, revision);
    }

    private static void metadataAndImages() {
        TmmDraft draft = sample(7, "synthetic-owned-image");
        ShareReview review = new ShareReview(draft, TARGET, LABEL);
        ShareReview.Metadata metadata = review.metadata;
        assert metadata.uid.equals(draft.uid) && metadata.displayId.equals(draft.displayId);
        assert metadata.type == draft.type && metadata.status == draft.status && metadata.owner.equals(draft.owner);
        assert metadata.createdAt.equals(CREATED) && metadata.updatedAt.equals(UPDATED);
        assert metadata.deployedAt.equals(draft.deployedAt) && metadata.comment.equals(draft.comment);
        assert metadata.position.latitude == 0 && metadata.position.longitude == 1 && metadata.bearingDeg == 45.0;
        assert review.imageStatus == ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED;
        assert review.destinationId.equals(TARGET) && review.destinationLabel.equals(LABEL);
        assert review.isCurrent(sample(7, "synthetic-owned-image"), TARGET, LABEL);
        ShareReview withoutImage = new ShareReview(sample(7, null), TARGET, LABEL);
        assert withoutImage.imageStatus == ShareReview.ImageStatus.NO_LOCAL_IMAGE;
        Set<String> expected = new HashSet<>(Arrays.asList("uid", "displayId", "type", "status", "owner",
                "createdAt", "updatedAt", "deployedAt", "comment", "position", "bearingDeg"));
        Set<String> actual = new HashSet<>();
        for (Field field : ShareReview.Metadata.class.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            assert Modifier.isPublic(field.getModifiers()) && Modifier.isFinal(field.getModifiers());
            assert !TmmDraft.class.isAssignableFrom(field.getType());
            actual.add(field.getName());
        }
        assert actual.equals(expected) : "Unreviewed metadata field change";
        TmmDraft empty = TmmDraft.create("Mini 1", DeviceType.MINI, "TEST ROLE", new Position(0, 0), CREATED);
        ShareReview optional = new ShareReview(empty, TARGET, LABEL);
        assert optional.metadata.deployedAt == null && optional.metadata.bearingDeg == null;
        assert optional.metadata.comment.isEmpty() && optional.imageStatus == ShareReview.ImageStatus.NO_LOCAL_IMAGE;
    }

    private static void changedDraftNeedsReview() {
        TmmDraft original = sample(7, "owned-local-image");
        ShareReview review = new ShareReview(original, TARGET, LABEL);
        assert !review.isCurrent(null, TARGET, LABEL);
        assert !review.isCurrent(sample(6, original.imageRef), TARGET, LABEL);
        assert !review.isCurrent(sample(8, original.imageRef), TARGET, LABEL);
        assert !review.isCurrent(sample(Long.MAX_VALUE, original.imageRef), TARGET, LABEL);
        assert !review.isCurrent(sample(7, null), TARGET, LABEL);
        assert !review.isCurrent(sample(7, "replaced-image"), TARGET, LABEL);
        TmmDraft addedImage = sample(7, "added-image");
        assert !new ShareReview(sample(7, null), TARGET, LABEL).isCurrent(addedImage, TARGET, LABEL);
        TmmDraft[] changesAtSameRevision = {
            new TmmDraft(new UUID(0, 2), original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, "PIR 135", original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, DeviceType.SCOUT, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, DeviceStatus.CANCELLED, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, "OTHER ROLE",
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED.plusNanos(1), UPDATED, original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED.plusNanos(1), original.deployedAt, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, null, original.comment, original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, "Changed", original.position, 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, new Position(0.1, 1), 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, new Position(0, 1.1), 45.0, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, null, original.imageRef, 7),
            new TmmDraft(UID, original.displayId, original.type, original.status, original.owner,
                    CREATED, UPDATED, original.deployedAt, original.comment, original.position, 45.5, original.imageRef, 7)
        };
        for (TmmDraft changed : changesAtSameRevision) assert !review.isCurrent(changed, TARGET, LABEL);
        TmmDraft edited = original.edit(original.displayId, original.type, original.status, original.owner,
                original.deployedAt, original.comment, original.position, original.bearingDeg, original.imageRef, UPDATED);
        assert !review.isCurrent(edited, TARGET, LABEL);
        assert new ShareReview(edited, TARGET, LABEL).isCurrent(edited, TARGET, LABEL);
        assert review.metadata.displayId.equals("PIR 134") && review.metadata.status == DeviceStatus.ACTIVE;
        assert review.metadata.position.latitude == 0 && review.imageStatus == ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED;
    }

    private static void changedDestinationNeedsReview() {
        TmmDraft draft = sample(1, null);
        ShareReview review = new ShareReview(draft, TARGET, LABEL);
        assert !review.isCurrent(draft, "synthetic-recipient-2", LABEL);
        assert !review.isCurrent(draft, TARGET, "Synthetic recipient B");
        assert !review.isCurrent(draft, null, LABEL) && !review.isCurrent(draft, TARGET, null);
        assert !review.isCurrent(draft, " " + TARGET, LABEL);
        assert !review.isCurrent(draft, TARGET.toUpperCase(java.util.Locale.ROOT), LABEL);
        ShareReview reselection = new ShareReview(draft, "synthetic-recipient-2", "Synthetic recipient B");
        assert reselection.isCurrent(draft, "synthetic-recipient-2", "Synthetic recipient B");
        assert review.destinationId.equals(TARGET) && review.destinationLabel.equals(LABEL);
    }

    private static void invalidDestinations() {
        TmmDraft draft = sample(1, null);
        for (String bad : new String[]{null, "", " ", " padded", "padded ", "line\nbreak", "tab\tvalue", "control\u0000",
                "\u00a0", "\u00a0padded", "padded\u2003", "line\u2028break", "line\u2029break",
                "hidden\u200b", "bidi\u202evalue", "unpaired\ud800"}) {
            try { new ShareReview(draft, bad, LABEL); throw new AssertionError("Invalid destination accepted"); }
            catch (IllegalArgumentException expected) { }
            try { new ShareReview(draft, TARGET, bad); throw new AssertionError("Invalid label accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        ShareReview unicode = new ShareReview(draft, "synthetic-recipient-å", "Syntetisk mottagare 🌲");
        assert unicode.isCurrent(draft, "synthetic-recipient-å", "Syntetisk mottagare 🌲");
        try { new ShareReview(null, TARGET, LABEL); throw new AssertionError("Null draft accepted"); }
        catch (NullPointerException expected) { }
    }

    private static void reviewDoesNotWrite() throws Exception {
        Path directory = Files.createTempDirectory("tmm-share-review-test-");
        try {
            DraftStore store = new DraftStore(directory);
            TmmDraft draft = sample(1, "owned-local-image");
            store.save(draft);
            Path data = directory.resolve("drafts.bin");
            byte[] original = Files.readAllBytes(data);
            ShareReview review = new ShareReview(store.load().get(0), TARGET, LABEL);
            assert review.isCurrent(new DraftStore(directory).load().get(0), TARGET, LABEL);
            // Discarding a review has no confirmation, write or sharing-state side effect.
            assert Arrays.equals(original, Files.readAllBytes(data));
            store.delete(draft.uid, draft.revision);
            assert !review.isCurrent(null, TARGET, LABEL);
            assert review.metadata.uid.equals(draft.uid) && review.metadata.comment.equals(draft.comment);
        } finally {
            Files.deleteIfExists(directory.resolve("drafts.bin"));
            Files.deleteIfExists(directory.resolve("drafts.lock"));
            Files.delete(directory);
        }
    }
}
