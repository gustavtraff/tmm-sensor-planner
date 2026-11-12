package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImagePublicationReviewTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String DESTINATION = "synthetic-selected-feed", LABEL = "Synthetic feed";
    private static final String HANDLE = "00000000-0000-4000-8000-000000000193.png";

    @Test public void actualFrozenPreviewDescriptorAndRecipientVerifierAgreeWithoutWrites() throws Exception {
        Fixture f = fixture(); byte[] original = Files.readAllBytes(f.path);
        ImagePublicationReview review = f.review(); byte[] preview = review.preview();
        byte[] expected = preview.clone(); preview[0] = 0;
        ImagePublicationReview.Prepared confirmed = review.confirm(f.snapshot, DESTINATION, LABEL, f.images);
        ReceivedImagePayload received = ReceivedImagePayload.read(new ByteArrayInputStream(confirmed.descriptor()),
                new ByteArrayInputStream(confirmed.png()));
        assertArrayEquals(expected, received.image.bytes());
        ByteArrayOutputStream container = new ByteArrayOutputStream(); confirmed.writeContainer(container);
        ReceivedImagePayload contained = ImageContainerCodec.read(new ByteArrayInputStream(container.toByteArray()));
        assertTrue(confirmed.offer.sameBinding(contained.offer));
        assertEquals(confirmed.offer.deliveryId, contained.offer.deliveryId);
        assertArrayEquals(expected, contained.image.bytes());
        assertTrue(review.offer.sameBinding(received.offer));
        assertEquals(review.offer.associationId, received.offer.associationId);
        assertEquals(review.offer.deliveryId, received.offer.deliveryId);
        assertEquals(f.draft.uid, received.offer.metadata.uid);
        assertArrayEquals(original, Files.readAllBytes(f.path));
        try (java.util.stream.Stream<Path> files = Files.list(f.path.getParent())) { assertEquals(1, files.count()); }
        assertFalse(new String(confirmed.descriptor(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(HANDLE));
        confirmed.png()[0] = 0; confirmed.descriptor()[0] = 0; confirmed.offer.sha256()[0] ^= 1;
        assertArrayEquals(expected, confirmed.png());
        assertTrue(ImageOfferCodec.decode(new ByteArrayInputStream(confirmed.descriptor())).sameBinding(review.offer));
        assertThrows(IOException.class, review::preview);
        assertThrows(IOException.class, () -> review.confirm(f.snapshot, DESTINATION, LABEL, f.images));
        review.close(); assertArrayEquals(expected, confirmed.png());
    }

    @Test public void cancelReturnsNoCandidateAndPreservesSource() throws Exception {
        Fixture f = fixture(); byte[] before = Files.readAllBytes(f.path);
        ImagePublicationReview review = f.review(); review.close(); review.close();
        assertThrows(IOException.class, review::preview);
        assertThrows(IOException.class, () -> review.confirm(f.snapshot, DESTINATION, LABEL, f.images));
        assertArrayEquals(before, Files.readAllBytes(f.path));
    }

    @Test public void changedDestinationIdOrLabelConsumesReview() throws Exception {
        for (boolean id : new boolean[]{true, false}) {
            Fixture f = fixture(); ImagePublicationReview review = f.review();
            assertThrows(IOException.class, () -> review.confirm(f.snapshot,
                    id ? "another-feed" : DESTINATION, id ? LABEL : "Changed label", f.images));
            assertThrows(IOException.class, () -> review.confirm(f.snapshot, DESTINATION, LABEL, f.images));
        }
    }

    @Test public void unavailableOrDifferentDatasetRefuses() throws Exception {
        Fixture f = fixture();
        for (LocalV2.Snapshot current : new LocalV2.Snapshot[]{null,
                snapshot(UUID.randomUUID(), LocalV2.State.active(1, f.draft))}) {
            ImagePublicationReview review = f.review();
            assertThrows(IOException.class, () -> review.confirm(current, DESTINATION, LABEL, f.images));
        }
    }

    @Test public void deletionRecreationAndStateVersionChangesRefuseEvenWhenMetadataMatches() throws Exception {
        Fixture f = fixture();
        for (LocalV2.State state : new LocalV2.State[]{LocalV2.State.neverSeen(f.draft.uid),
                LocalV2.State.deleted(f.draft.uid, 2), LocalV2.State.active(3, f.draft)}) {
            ImagePublicationReview review = f.review();
            assertThrows(IOException.class, () -> review.confirm(snapshot(f.snapshot.storeId, state), DESTINATION, LABEL, f.images));
        }
    }

    @Test public void everyLocalBindingMattersBeyondStateVersion() throws Exception {
        Fixture f = fixture(); TmmDraft d = f.draft;
        for (TmmDraft changed : new TmmDraft[]{draft(d, d.displayId + " changed", HANDLE, d.revision),
                draft(d, d.displayId, null, d.revision),
                draft(d, d.displayId, "00000000-0000-4000-8000-000000000194.png", d.revision),
                draft(d, d.displayId, HANDLE, d.revision + 1)}) {
            ImagePublicationReview review = f.review();
            assertThrows(IOException.class, () -> review.confirm(snapshot(f.snapshot.storeId,
                    LocalV2.State.active(1, changed)), DESTINATION, LABEL, f.images));
        }
    }

    @Test public void sameHandleWithDifferentValidPngRefusesWithoutReplacingFrozenPreview() throws Exception {
        Fixture f = fixture(); ImagePublicationReview review = f.review();
        Files.write(f.path, resource("/image-transfer/h2.png"));
        assertThrows(IOException.class, () -> review.confirm(f.snapshot, DESTINATION, LABEL, f.images));
        assertThrows(IOException.class, review::preview);
        assertArrayEquals(resource("/image-transfer/h2.png"), Files.readAllBytes(f.path));
    }

    @Test public void missingCorruptAndSymlinkImagesRefuseAtConfirmationAndCannotBeRetried() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            Fixture f = fixture(); ImagePublicationReview review = f.review();
            if (kind == 0) Files.delete(f.path);
            if (kind == 1) Files.write(f.path, new byte[]{1, 2, 3});
            if (kind == 2) {
                Path target = temp.newFile().toPath(); Files.write(target, resource("/image-transfer/h1.png"));
                Files.delete(f.path); Files.createSymbolicLink(f.path, target);
            }
            assertThrows(IOException.class, () -> review.confirm(f.snapshot, DESTINATION, LABEL, f.images));
            Files.deleteIfExists(f.path); Files.write(f.path, resource("/image-transfer/h1.png"));
            assertThrows(IOException.class, () -> review.confirm(f.snapshot, DESTINATION, LABEL, f.images));
        }
    }

    @Test public void cannotPrepareAnAbsentInactiveOrMissingImageSelection() throws Exception {
        Fixture f = fixture();
        for (LocalV2.State state : new LocalV2.State[]{LocalV2.State.neverSeen(f.draft.uid),
                LocalV2.State.deleted(f.draft.uid, 2), LocalV2.State.active(1, draft(f.draft, f.draft.displayId, null, 1))}) {
            assertThrows(IOException.class, () -> new ImagePublicationReview(
                    snapshot(f.snapshot.storeId, state).select(f.draft.uid), DESTINATION, LABEL, f.images));
        }
        Files.delete(f.path); assertThrows(IOException.class, f::review);
        Files.write(f.path, new byte[]{1}); assertThrows(IOException.class, f::review);
    }

    @Test public void unrelatedDeviceChangesDoNotInvalidateReviewedDevice() throws Exception {
        Fixture f = fixture(); ImagePublicationReview review = f.review();
        LocalV2.Snapshot now = new LocalV2.Snapshot(f.snapshot.storeId,
                Arrays.asList(f.snapshot.state(f.draft.uid), LocalV2.State.deleted(UUID.randomUUID(), 4)), Collections.emptyList());
        assertNotNull(review.confirm(now, DESTINATION, LABEL, f.images));
    }

    @Test public void newReviewsUseDistinctAssociationAndDeliveryIdentities() throws Exception {
        Fixture f = fixture(); ImagePublicationReview a = f.review(), b = f.review();
        assertNotEquals(a.offer.associationId, b.offer.associationId);
        assertNotEquals(a.offer.deliveryId, b.offer.deliveryId); assertTrue(a.offer.sameBinding(b.offer));
        a.close(); b.close();
    }

    private Fixture fixture() throws Exception {
        Path directory = temp.newFolder().toPath(); Path path = directory.resolve(HANDLE);
        Files.write(path, resource("/image-transfer/h1.png"));
        ShareReview.Metadata m = ImageOfferCodec.decode(new ByteArrayInputStream(resource("/offer-h1.bin"))).metadata;
        TmmDraft d = new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt,
                m.updatedAt, m.deployedAt, m.comment, m.position, m.bearingDeg, HANDLE, 1);
        return new Fixture(path, new LocalImages(directory), d, snapshot(UUID.randomUUID(), LocalV2.State.active(1, d)));
    }
    private static LocalV2.Snapshot snapshot(UUID id, LocalV2.State state) {
        return new LocalV2.Snapshot(id, Collections.singletonList(state), Collections.emptyList());
    }
    private static TmmDraft draft(TmmDraft d, String label, String image, long revision) {
        return new TmmDraft(d.uid, label, d.type, d.status, d.owner, d.createdAt, d.updatedAt,
                d.deployedAt, d.comment, d.position, d.bearingDeg, image, revision);
    }
    private static byte[] resource(String path) throws IOException {
        try (InputStream in = ImagePublicationReviewTest.class.getResourceAsStream(path)) {
            assertNotNull(path, in); ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
    private static final class Fixture {
        final Path path; final LocalImages images; final TmmDraft draft; final LocalV2.Snapshot snapshot;
        Fixture(Path path, LocalImages images, TmmDraft draft, LocalV2.Snapshot snapshot) {
            this.path = path; this.images = images; this.draft = draft; this.snapshot = snapshot;
        }
        ImagePublicationReview review() throws IOException {
            return new ImagePublicationReview(snapshot.select(draft.uid), DESTINATION, LABEL, images);
        }
    }
}
