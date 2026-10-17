package se.tmm.sensorplanner.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static se.tmm.sensorplanner.core.ImportReview.Kind.*;

public final class ImportReviewTest {
    private static final UUID UID = new UUID(0, 1);
    private static final Instant CREATED = Instant.EPOCH;
    private static final Instant UPDATED = CREATED.plusSeconds(10);

    public static void main(String[] args) throws Exception {
        identityAndRepeats();
        everyMetadataFieldRequiresReview();
        localCountersAndImages();
        invalidInputs();
        storedSnapshotIsPreserved();
        System.out.println("Import review tests passed (UUIDs, metadata, revisions, images and preservation).");
    }

    private static TmmDraft sample(UUID uid, long revision, String image) {
        return new TmmDraft(uid, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1),
                45.0, image, revision);
    }

    private static void identityAndRepeats() {
        TmmDraft local = sample(UID, 7, null);
        TmmDraft incoming = sample(UID, 7, null);
        assert local != incoming;
        ImportReview empty = ImportReview.assess(Collections.emptyList(), incoming);
        assert empty.kind == NEW_UID && empty.local == null && empty.incoming == incoming;
        TmmDraft sameLabel = sample(new UUID(0, 2), 7, null);
        assert ImportReview.assess(Collections.singletonList(sameLabel), incoming).kind == NEW_UID;
        List<TmmDraft> snapshot = Collections.unmodifiableList(Arrays.asList(sameLabel, local));
        for (int i = 0; i < 3; i++) {
            ImportReview repeat = ImportReview.assess(snapshot, incoming);
            assert repeat.kind == SAME_METADATA && repeat.local == local && repeat.incoming == incoming;
        }
        assert snapshot.size() == 2 && snapshot.get(0) == sameLabel && snapshot.get(1) == local;
        assert ImportReview.assess(Arrays.asList(local, sameLabel), incoming).local == local;
    }

    private static void everyMetadataFieldRequiresReview() {
        TmmDraft local = sample(UID, 7, "owned-local-image");
        TmmDraft[] differences = {
            new TmmDraft(UID, "pir 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.SCOUT, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.CANCELLED, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "OTHER ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED.plusNanos(1), UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED.plusNanos(1), CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED.minusSeconds(1), CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, null, "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5).plusNanos(1), "Synthetic åäö", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "", new Position(0, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0.000001, 1), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1.000001), 45.0, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), null, null, 7),
            new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                    CREATED, UPDATED, CREATED.plusSeconds(5), "Synthetic åäö", new Position(0, 1), 45.5, null, 7)
        };
        for (TmmDraft incoming : differences) {
            ImportReview review = ImportReview.assess(Collections.singletonList(local), incoming);
            assert review.kind == REVIEW_REQUIRED;
            assert review.local == local && review.incoming == incoming;
        }
        TmmDraft noBearing = differences[12];
        assert ImportReview.assess(Collections.singletonList(noBearing), sample(UID, 7, null)).kind == REVIEW_REQUIRED;
        assert ImportReview.assess(Collections.singletonList(noBearing), noBearing).kind == SAME_METADATA;
    }

    private static void localCountersAndImages() {
        for (long localRevision : new long[]{1, 7, Long.MAX_VALUE}) {
            TmmDraft local = sample(UID, localRevision, "owned-local-image");
            for (long incomingRevision : new long[]{1, 7, Long.MAX_VALUE}) {
                TmmDraft incoming = sample(UID, incomingRevision, null);
                assert ImportReview.assess(Collections.singletonList(local), incoming).kind == SAME_METADATA;
                TmmDraft changed = new TmmDraft(UID, incoming.displayId, incoming.type, incoming.status,
                        incoming.owner, incoming.createdAt, incoming.updatedAt, incoming.deployedAt,
                        "Changed elsewhere", incoming.position, incoming.bearingDeg, null, incomingRevision);
                ImportReview review = ImportReview.assess(Collections.singletonList(local), changed);
                assert review.kind == REVIEW_REQUIRED && review.local == local && review.incoming == changed;
            }
            assert local.imageRef.equals("owned-local-image") && local.revision == localRevision;
        }
        TmmDraft positiveZero = new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE,
                "TEST ROLE", CREATED, UPDATED, null, "", new Position(0.0, 0.0), 0.0, null, 1);
        TmmDraft negativeZero = new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE,
                "TEST ROLE", CREATED, UPDATED, null, "", new Position(-0.0, -0.0), -0.0, null, 2);
        assert ImportReview.assess(Collections.singletonList(positiveZero), negativeZero).kind == SAME_METADATA;
    }

    private static void invalidInputs() {
        TmmDraft incoming = sample(UID, 1, null);
        expect(NullPointerException.class, () -> ImportReview.assess(null, incoming));
        expect(NullPointerException.class, () -> ImportReview.assess(Collections.emptyList(), null));
        expect(NullPointerException.class, () -> ImportReview.assess(Arrays.asList(incoming, null), incoming));
        expect(IllegalArgumentException.class, () -> ImportReview.assess(Arrays.asList(incoming, incoming), incoming));
        TmmDraft unrelated = sample(new UUID(0, 2), 1, null);
        expect(IllegalArgumentException.class,
                () -> ImportReview.assess(Arrays.asList(incoming, unrelated, unrelated), incoming));
        for (String handle : new String[]{"owned-local-image", "../private.png", "content://synthetic/image"}) {
            TmmDraft withHandle = sample(UID, 1, handle);
            expect(IllegalArgumentException.class, () -> ImportReview.assess(Collections.emptyList(), withHandle));
            expect(IllegalArgumentException.class, () -> ImportReview.assess(Collections.singletonList(incoming), withHandle));
        }
    }

    private static void storedSnapshotIsPreserved() throws Exception {
        Path directory = Files.createTempDirectory("tmm-import-review-test-");
        try {
            DraftStore store = new DraftStore(directory);
            TmmDraft local = sample(UID, 1, "owned-local-image");
            store.save(local);
            Path data = directory.resolve("drafts.bin");
            byte[] original = Files.readAllBytes(data);
            TmmDraft incoming = local.edit(local.displayId, local.type, DeviceStatus.CANCELLED,
                    local.owner, null, "Changed elsewhere", new Position(1, 1), null, null, UPDATED);
            List<TmmDraft> snapshot = store.load();
            ImportReview review = ImportReview.assess(snapshot, incoming);
            assert review.kind == REVIEW_REQUIRED && review.local == snapshot.get(0) && review.incoming == incoming;
            assert Arrays.equals(original, Files.readAllBytes(data));
            TmmDraft reopened = new DraftStore(directory).load().get(0);
            assert reopened.revision == 1 && reopened.status == DeviceStatus.ACTIVE;
            assert reopened.imageRef.equals("owned-local-image") && reopened.uid.equals(local.uid);
        } finally {
            Files.deleteIfExists(directory.resolve("drafts.bin"));
            Files.deleteIfExists(directory.resolve("drafts.lock"));
            Files.delete(directory);
        }
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected rejected review input");
        } catch (RuntimeException error) {
            if (!type.isInstance(error)) throw error;
        }
    }
}
