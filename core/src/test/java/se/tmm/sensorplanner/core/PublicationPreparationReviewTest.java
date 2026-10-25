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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class PublicationPreparationReviewTest {
    private static final Instant CREATED = Instant.EPOCH.plusNanos(123);

    public static void main(String[] args) throws Exception {
        projectionAndOneUse();
        staleAttemptIsConsumed();
        cancellation();
        concurrentAttempts();
        savedDraftIsUntouched();
        System.out.println("Private preparation tests passed (projection, one-use freshness, cancellation, concurrency and preservation).");
    }

    private static TmmDraft sample(long revision, String image) {
        return new TmmDraft(new UUID(0, 1), "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                CREATED, CREATED.plusSeconds(10), CREATED.plusSeconds(5), "Synthetic åäö 🌲",
                new Position(0.123456789, 1.987654321), 0.0, image, revision);
    }

    private static void projectionAndOneUse() {
        TmmDraft draft = sample(7, "nonexistent-local-image");
        PublicationPreparationReview review = new PublicationPreparationReview(draft);
        PublicationPreparationReview.Prepared prepared = review.prepare(sample(7, draft.imageRef));
        assert prepared.metadata == review.metadata;
        ShareReview.Metadata m = prepared.metadata;
        assert m.uid.equals(draft.uid) && m.displayId.equals("PIR 134");
        assert m.type == draft.type && m.status == draft.status && m.owner.equals(draft.owner);
        assert m.createdAt.equals(CREATED) && m.updatedAt.equals(draft.updatedAt);
        assert m.deployedAt.equals(draft.deployedAt) && m.comment.equals(draft.comment);
        assert m.position.latitude == draft.position.latitude && m.position.longitude == draft.position.longitude;
        assert m.bearingDeg.equals(draft.bearingDeg);
        assert prepared.imageStatus == ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED;
        rejected(review, draft);
        review.close();
        assert prepared.metadata == m && prepared.imageStatus == ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED;
        Set<String> fields = new HashSet<>();
        for (Field field : PublicationPreparationReview.Prepared.class.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            assert Modifier.isPublic(field.getModifiers()) && Modifier.isFinal(field.getModifiers());
            fields.add(field.getName());
        }
        assert fields.equals(new HashSet<>(Arrays.asList("metadata", "imageStatus")));
        TmmDraft optional = TmmDraft.create("Mini 1", DeviceType.MINI, "TEST ROLE", new Position(0, 0), CREATED);
        PublicationPreparationReview.Prepared empty = new PublicationPreparationReview(optional).prepare(optional);
        assert empty.metadata.deployedAt == null && empty.metadata.bearingDeg == null;
        assert empty.metadata.comment.isEmpty() && empty.imageStatus == ShareReview.ImageStatus.NO_LOCAL_IMAGE;
        try { new PublicationPreparationReview(null); throw new AssertionError("Null draft accepted"); }
        catch (NullPointerException expected) { }
    }

    private static void staleAttemptIsConsumed() {
        TmmDraft original = sample(7, "original-image");
        TmmDraft changedMetadata = new TmmDraft(original.uid, "PIR 135", original.type, original.status,
                original.owner, CREATED, original.updatedAt, original.deployedAt, original.comment,
                original.position, original.bearingDeg, original.imageRef, original.revision);
        TmmDraft changedUid = new TmmDraft(new UUID(0, 2), original.displayId, original.type, original.status,
                original.owner, CREATED, original.updatedAt, original.deployedAt, original.comment,
                original.position, original.bearingDeg, original.imageRef, original.revision);
        // ShareReviewTest exercises every metadata field through the shared freshness comparison.
        for (TmmDraft changed : new TmmDraft[]{null, changedUid, changedMetadata,
                sample(6, original.imageRef), sample(8, original.imageRef), sample(7, null), sample(7, "replacement")}) {
            PublicationPreparationReview review = new PublicationPreparationReview(original);
            rejected(review, changed);
            rejected(review, original);
        }
        rejected(new PublicationPreparationReview(sample(7, null)), original);
    }

    private static void cancellation() {
        TmmDraft draft = sample(1, null);
        PublicationPreparationReview review = new PublicationPreparationReview(draft);
        review.close();
        review.close();
        rejected(review, draft);
        assert new PublicationPreparationReview(draft).prepare(draft).metadata.uid.equals(draft.uid);
    }

    private static void concurrentAttempts() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            TmmDraft draft = sample(1, null);
            PublicationPreparationReview review = new PublicationPreparationReview(draft);
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = pool.submit(() -> attempt(start, review, draft));
            Future<Boolean> second = pool.submit(() -> attempt(start, review, draft));
            start.countDown();
            assert first.get(5, TimeUnit.SECONDS) != second.get(5, TimeUnit.SECONDS);
            rejected(review, draft);

            PublicationPreparationReview cancelled = new PublicationPreparationReview(draft);
            CountDownLatch race = new CountDownLatch(1);
            Future<Boolean> prepare = pool.submit(() -> attempt(race, cancelled, draft));
            Future<?> close = pool.submit(() -> { race.await(); cancelled.close(); return null; });
            race.countDown();
            prepare.get(5, TimeUnit.SECONDS); // Either ordering is valid; failures propagate through Future.
            close.get(5, TimeUnit.SECONDS);
            rejected(cancelled, draft);
        } finally {
            pool.shutdownNow();
            assert pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static boolean attempt(CountDownLatch start, PublicationPreparationReview review, TmmDraft draft)
            throws InterruptedException {
        start.await();
        try { return review.prepare(draft) != null; }
        catch (IllegalStateException expected) { return false; }
    }

    private static void savedDraftIsUntouched() throws Exception {
        Path directory = Files.createTempDirectory("tmm-private-preparation-test-");
        try {
            DraftStore store = new DraftStore(directory);
            TmmDraft draft = sample(1, "nonexistent-local-image");
            store.save(draft);
            byte[] before = Files.readAllBytes(directory.resolve("drafts.bin"));
            PublicationPreparationReview review = new PublicationPreparationReview(store.load().get(0));
            PublicationPreparationReview.Prepared prepared = review.prepare(new DraftStore(directory).load().get(0));
            review.close();
            assert prepared.metadata.uid.equals(draft.uid);
            assert Arrays.equals(before, Files.readAllBytes(directory.resolve("drafts.bin")));
            TmmDraft reopened = new DraftStore(directory).load().get(0);
            assert reopened.revision == draft.revision && reopened.imageRef.equals(draft.imageRef);
            PublicationPreparationReview deleted = new PublicationPreparationReview(reopened);
            store.delete(draft.uid, draft.revision);
            rejected(deleted, null);
            assert store.load().isEmpty();
        } finally {
            Files.deleteIfExists(directory.resolve("drafts.bin"));
            Files.deleteIfExists(directory.resolve("drafts.lock"));
            Files.delete(directory);
        }
    }

    private static void rejected(PublicationPreparationReview review, TmmDraft current) {
        try { review.prepare(current); throw new AssertionError("Invalid preparation accepted"); }
        catch (IllegalStateException expected) { }
    }
}
