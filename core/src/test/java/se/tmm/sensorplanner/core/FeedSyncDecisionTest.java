package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import se.tmm.sensorplanner.core.FeedSyncDecision.ImageState;
import static se.tmm.sensorplanner.core.FeedSyncDecision.Image;
import static se.tmm.sensorplanner.core.FeedSyncDecision.Metadata;

public final class FeedSyncDecisionTest {
    private static final UUID UID = UUID.fromString("00000000-0000-4000-8026-000000000001");
    private static final TmmDraft M1 = draft("one", Instant.EPOCH.plusSeconds(10), 1);
    private static final TmmDraft M2 = draft("two", Instant.EPOCH.plusSeconds(10), 1);
    private static final TmmDraft M3 = draft("three", Instant.EPOCH.plusSeconds(10), 1);
    private static final byte[] H1 = digest(1);
    private static final byte[] H2 = digest(2);
    private static final byte[] H3 = digest(3);
    private static final List<byte[]> NO_IMAGES = Collections.emptyList();

    public static void main(String[] args) {
        metadataRows();
        metadataEquality();
        imageRows();
        remoteImageSelection();
        independentDecisions();
        invalidInputs();
        System.out.println("Feed sync decision tests passed (U3 rows, U4 selection, independence).");
    }

    private static TmmDraft draft(String comment, Instant updatedAt, long revision) {
        return new TmmDraft(UID, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                Instant.EPOCH, updatedAt, null, comment, new Position(0, 1), null, null, revision);
    }

    private static byte[] digest(int seed) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) seed);
        return bytes;
    }

    private static ImageState img(byte[] digest) {
        return ImageState.of(digest);
    }

    private static Metadata meta(TmmDraft r, TmmDraft b, TmmDraft l) {
        return FeedSyncDecision.decide(r, b, l, NO_IMAGES, ImageState.UNKNOWN, ImageState.NONE).metadata;
    }

    private static Image image(List<byte[]> remote, TmmDraft b, TmmDraft l, ImageState base, ImageState local) {
        return FeedSyncDecision.decide(M1, b, l, remote, base, local).image;
    }

    private static List<byte[]> one(byte[] h) {
        return Collections.singletonList(h);
    }

    private static void metadataRows() {
        assert meta(null, M1, M1) == Metadata.NOTHING;           // missing from feed
        assert meta(null, null, null) == Metadata.NOTHING;
        assert meta(M1, M1, M2) == Metadata.NOTHING;             // R = B, even with local changes
        assert meta(M1, M1, M1) == Metadata.NOTHING;
        assert meta(M2, M1, M2) == Metadata.ADOPT_BASE;          // R = L
        assert meta(M2, M1, M1) == Metadata.APPLY;               // L = B, R differs
        assert meta(M3, M1, M2) == Metadata.CONFLICT;            // all three differ
        assert meta(M1, null, null) == Metadata.ADD;             // not local, no base
        assert meta(M1, M1, null) == Metadata.NOTHING;           // deleted locally, R = B
        assert meta(M2, M1, null) == Metadata.CONFLICT;          // deleted locally, R differs
        assert meta(M1, null, M1) == Metadata.ADOPT_BASE;        // local, no base, R = L
        assert meta(M1, null, M2) == Metadata.CONFLICT;          // local, no base, R differs
    }

    private static void metadataEquality() {
        TmmDraft later = draft("one", Instant.EPOCH.plusSeconds(11), 1);
        TmmDraft otherRevision = draft("one", Instant.EPOCH.plusSeconds(10), 9);
        assert meta(later, M1, M1) == Metadata.APPLY;            // updatedAt alone makes a variant
        assert meta(later, M1, M2) == Metadata.CONFLICT;
        assert meta(otherRevision, M1, M2) == Metadata.NOTHING;  // revision is not metadata
    }

    private static void imageRows() {
        ImageState none = ImageState.NONE;
        ImageState unknown = ImageState.UNKNOWN;
        assert image(one(H1), M1, M1, img(H1), img(H2)) == Image.NOTHING;     // H = base
        assert image(one(H2), M1, M1, img(H1), img(H2)) == Image.ADOPT_BASE;  // H = local
        assert image(one(H2), M1, M1, img(H1), img(H1)) == Image.APPLY;       // local = base
        assert image(one(H2), M1, M1, none, none) == Image.APPLY;             // no image yet
        assert image(one(H3), M1, M1, img(H1), img(H2)) == Image.CONFLICT;
        assert image(one(H3), M1, M1, img(H1), none) == Image.CONFLICT;
        assert image(one(H1), null, null, unknown, none) == Image.APPLY;      // added device
        assert image(one(H1), M1, null, img(H1), none) == Image.NOTHING;      // deleted, H = base
        assert image(one(H2), M1, null, img(H1), none) == Image.CONFLICT;     // deleted, H differs
        assert image(one(H2), M1, null, none, none) == Image.CONFLICT;
        assert image(one(H2), M1, null, unknown, none) == Image.NOTHING;      // deleted, no image base
        assert image(one(H1), M1, M1, unknown, img(H1)) == Image.ADOPT_BASE;  // unknown base, H = L
        assert image(one(H1), M1, M1, unknown, img(H2)) == Image.CONFLICT;    // unknown acts as no base
        assert image(one(H1), M1, M1, unknown, none) == Image.CONFLICT;
        assert image(one(H1), null, M1, unknown, img(H1)) == Image.ADOPT_BASE;
        assert image(one(H1), null, M1, unknown, img(H2)) == Image.CONFLICT;
        assert FeedSyncDecision.decide(null, M1, M1, one(H2), img(H1), img(H1)).image == Image.NOTHING;
    }

    private static void remoteImageSelection() {
        ImageState base = img(H1);
        assert image(NO_IMAGES, M1, M1, base, img(H2)) == Image.NOTHING;      // missing image never clears
        assert image(NO_IMAGES, M1, M1, base, ImageState.NONE) == Image.NOTHING;
        assert image(NO_IMAGES, null, null, ImageState.UNKNOWN, ImageState.NONE) == Image.NOTHING;
        assert image(Arrays.asList(H2, digest(2)), M1, M1, base, base) == Image.APPLY;  // duplicates count once
        assert image(Arrays.asList(H1, H2), M1, M1, base, base) == Image.APPLY;         // H2 differs from base
        assert image(Arrays.asList(H2, H1), M1, M1, base, img(H2)) == Image.ADOPT_BASE; // normal U3 path
        assert image(Arrays.asList(H2, H3), M1, M1, base, base) == Image.AMBIGUOUS;     // both differ
        assert image(Arrays.asList(H2, H3), M1, M1, ImageState.UNKNOWN, ImageState.NONE) == Image.AMBIGUOUS;
        assert image(Arrays.asList(H2, H3), M1, M1, ImageState.NONE, ImageState.NONE) == Image.AMBIGUOUS;
        assert image(Arrays.asList(H1, H2, H3), M1, M1, base, base) == Image.AMBIGUOUS; // two differ
        assert image(Arrays.asList(H1, H1, H2), M1, null, base, ImageState.NONE) == Image.CONFLICT; // H2 vs base, deleted
    }

    private static void independentDecisions() {
        // Metadata changed remotely, image unchanged: metadata applies, image stays.
        FeedSyncDecision a = FeedSyncDecision.decide(M2, M1, M1, one(H1), img(H1), img(H1));
        assert a.metadata == Metadata.APPLY && a.image == Image.NOTHING;
        // Image changed remotely while local metadata also changed: metadata conflicts, image applies.
        FeedSyncDecision b = FeedSyncDecision.decide(M3, M1, M2, one(H2), img(H1), img(H1));
        assert b.metadata == Metadata.CONFLICT && b.image == Image.APPLY;
        // Metadata equal to base while the image changed locally: image conflicts, metadata does not.
        FeedSyncDecision c = FeedSyncDecision.decide(M1, M1, M1, one(H3), img(H1), img(H2));
        assert c.metadata == Metadata.NOTHING && c.image == Image.CONFLICT;
        FeedSyncDecision d = FeedSyncDecision.decide(M1, null, null, one(H1), ImageState.UNKNOWN, ImageState.NONE);
        assert d.metadata == Metadata.ADD && d.image == Image.APPLY;
        FeedSyncDecision e = FeedSyncDecision.decide(null, M1, M1, one(H1), img(H1), img(H1));
        assert e.metadata == Metadata.NOTHING && e.image == Image.NOTHING;
    }

    private static void invalidInputs() {
        expectFailure(() -> ImageState.of(new byte[31]));
        expectFailure(() -> FeedSyncDecision.decide(M1, M1, M1, NO_IMAGES, null, ImageState.NONE));
        expectFailure(() -> FeedSyncDecision.decide(M1, M1, M1, NO_IMAGES, ImageState.NONE, ImageState.UNKNOWN));
        expectFailure(() -> FeedSyncDecision.decide(M1, M1, M1, one(new byte[5]), ImageState.NONE, ImageState.NONE));
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
