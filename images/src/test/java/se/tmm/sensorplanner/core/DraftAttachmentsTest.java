package se.tmm.sensorplanner.core;

import android.graphics.Bitmap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.DraftAttachments;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DraftAttachmentsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Instant now = Instant.EPOCH;

    @Test public void attachReplaceDetachAndDeleteCommitBeforeCleanup() throws Exception {
        DraftStore store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft original = draft();
        store.save(original);
        TmmDraft first = attach(attachments, images, original);
        assertEquals(original.uid, first.uid);
        assertEquals(original.createdAt, first.createdAt);
        assertEquals(original.deployedAt, first.deployedAt);
        assertEquals(original.comment, first.comment);
        assertEquals(original.bearingDeg, first.bearingDeg);
        assertEquals(original.displayId, first.displayId);
        assertEquals(original.type, first.type);
        assertEquals(original.owner, first.owner);
        assertEquals(original.status, first.status);
        assertEquals(original.position.latitude, first.position.latitude, 0);
        assertEquals(original.position.longitude, first.position.longitude, 0);
        assertEquals(2, first.revision);
        assertNotNull(new LocalImages(imagePath()).read(first.imageRef));
        TmmDraft replaced = attach(attachments, images, first);
        assertThrows(IOException.class, () -> images.read(first.imageRef));
        DraftAttachments.Result detached = attachments.detach(replaced, now.plusSeconds(1));
        assertFalse(detached.cleanupFailed);
        assertNull(detached.drafts.get(0).imageRef);
        assertThrows(IOException.class, () -> images.read(replaced.imageRef));
        TmmDraft last = attach(attachments, images, detached.drafts.get(0));
        assertTrue(attachments.delete(last).drafts.isEmpty());
        assertThrows(IOException.class, () -> images.read(last.imageRef));
        assertTrue(new DraftStore(storePath()).load().isEmpty());
    }

    @Test public void cancelAndStaleMetadataNeverRemoveExistingAttachment() throws Exception {
        DraftStore store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft original = draft();
        store.save(original);
        TmmDraft attached = attach(attachments, images, original);
        try (LocalImages.Pending cancelled = pending(images)) { assertNotNull(cancelled.preview()); }
        assertEquals(1, countImages());
        try (LocalImages.Pending stale = pending(images)) {
            assertThrows(IOException.class, () -> attachments.attach(original, stale, now.plusSeconds(1)));
        }
        assertEquals(2, countImages());
        assertThrows(IOException.class, () -> attachments.detach(original, now.plusSeconds(1)));
        assertThrows(IOException.class, () -> attachments.delete(original));
        assertEquals(attached.imageRef, store.load().get(0).imageRef);
        assertNotNull(images.read(attached.imageRef));
    }

    @Test public void sharedImageSurvivesReplacementAndDeletionUntilLastDetach() throws Exception {
        DraftStore store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft first = draft();
        store.save(first);
        first = attach(attachments, images, first);
        String shared = first.imageRef;
        TmmDraft second = draft();
        second = new TmmDraft(second.uid, second.displayId, second.type, second.status, second.owner,
                second.createdAt, second.updatedAt, second.deployedAt, second.comment,
                second.position, second.bearingDeg, shared, 1);
        store.save(second);
        TmmDraft replaced = attach(attachments, images, first);
        assertNotNull(images.read(shared));
        attachments.delete(replaced);
        assertNotNull(images.read(shared));
        attachments.detach(second, now.plusSeconds(1));
        assertThrows(IOException.class, () -> images.read(shared));
    }

    @Test public void deletionOfSharedRecordRetainsRemainingImage() throws Exception {
        DraftStore store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft first = draft();
        store.save(first);
        first = attach(attachments, images, first);
        TmmDraft second = draft();
        store.save(new TmmDraft(second.uid, second.displayId, second.type, second.status, second.owner,
                second.createdAt, second.updatedAt, null, second.comment, second.position,
                second.bearingDeg, first.imageRef, 1));
        attachments.delete(first);
        assertNotNull(images.read(first.imageRef));
        attachments.delete(store.load().get(0));
        String reference = first.imageRef;
        assertThrows(IOException.class, () -> images.read(reference));
    }

    @Test public void cleanupFailureReturnsCommittedSnapshotWithoutRollback() throws Exception {
        DraftStore store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft original = draft();
        store.save(original);
        TmmDraft attached = attach(attachments, images, original);
        Path old = imagePath().resolve(attached.imageRef);
        Files.delete(old);
        Files.createSymbolicLink(old, imagePath().resolve("synthetic-missing.png"));
        DraftAttachments.Result result = attachments.detach(attached, now.plusSeconds(1));
        assertTrue(result.cleanupFailed);
        assertNull(result.drafts.get(0).imageRef);
        assertNull(new DraftStore(storePath()).load().get(0).imageRef);
        assertTrue(Files.isSymbolicLink(old));
    }

    @Test public void ambiguousErrorAfterReplacementPreservesNewAndPreviousImages() throws Exception {
        DraftStore normal = store();
        LocalImages images = images();
        TmmDraft original = draft();
        normal.save(original);
        TmmDraft first = attach(new DraftAttachments(normal, images), images, original);
        DraftStore failing = new DraftStore(storePath(), (source, destination) -> {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Synthetic error after metadata replacement");
        });
        try (LocalImages.Pending replacement = pending(images)) {
            assertThrows(IOException.class, () -> new DraftAttachments(failing, images)
                    .attach(first, replacement, now.plusSeconds(1)));
        }
        assertEquals(2, countImages());
        assertNotNull(images.read(first.imageRef));
        TmmDraft saved = new DraftStore(storePath()).load().get(0);
        assertNotEquals(first.imageRef, saved.imageRef);
        assertNotNull(images.read(saved.imageRef));
    }

    @Test public void failureOnFreshReadAfterSuccessfulSaveRetainsBothImages() throws Exception {
        DraftStore normal = store();
        LocalImages images = images();
        TmmDraft original = draft();
        normal.save(original);
        TmmDraft first = attach(new DraftAttachments(normal, images), images, original);
        DraftStore failing = new DraftStore(storePath(), (source, destination) -> {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // The save completed, but the following fresh read cannot open its lock file.
            Files.delete(storePath().resolve("drafts.lock"));
            Files.createDirectory(storePath().resolve("drafts.lock"));
        });
        try (LocalImages.Pending replacement = pending(images)) {
            assertThrows(IOException.class, () -> new DraftAttachments(failing, images)
                    .attach(first, replacement, now.plusSeconds(1)));
        }
        assertEquals(2, countImages());
        assertNotNull(images.read(first.imageRef));
        Files.delete(storePath().resolve("drafts.lock"));
        TmmDraft saved = normal.load().get(0);
        assertNotEquals(first.imageRef, saved.imageRef);
        assertNotNull(new LocalImages(imagePath()).read(saved.imageRef));
    }

    private TmmDraft attach(DraftAttachments attachments, LocalImages images, TmmDraft draft) throws Exception {
        try (LocalImages.Pending image = pending(images)) {
            DraftAttachments.Result result = attachments.attach(draft, image, now.plusSeconds(1));
            assertFalse(result.cleanupFailed);
            return result.drafts.stream().filter(d -> d.uid.equals(draft.uid)).findFirst().get();
        }
    }

    private LocalImages.Pending pending(LocalImages images) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(new int[]{0xff00ff00}, 1, 1, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes));
        bitmap.recycle();
        return images.prepare(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private TmmDraft draft() {
        return new TmmDraft(java.util.UUID.randomUUID(), "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE,
                "Synthetic group", now, now, now.minusSeconds(1), "Synthetic comment",
                new Position(0, 0), 270.0, null, 1);
    }
    private Path storePath() { return temporary.getRoot().toPath().resolve("drafts"); }
    private Path imagePath() { return temporary.getRoot().toPath().resolve("images"); }
    private DraftStore store() { return new DraftStore(storePath()); }
    private LocalImages images() { return new LocalImages(imagePath()); }
    private long countImages() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(imagePath())) { return files.count(); }
    }
}
