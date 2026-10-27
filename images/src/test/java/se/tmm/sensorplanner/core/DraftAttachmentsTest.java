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
        LocalV2Store store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft original = draft();
        save(store, original);
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
        DraftAttachments.Result detached = attachments.detach(selection(replaced), now.plusSeconds(1));
        assertFalse(detached.cleanupFailed);
        assertNull(detached.drafts.get(0).imageRef);
        assertThrows(IOException.class, () -> images.read(replaced.imageRef));
        TmmDraft detachedDraft = detached.drafts.get(0); selections.put(detachedDraft, detached.storage.snapshot.select(detachedDraft.uid));
        TmmDraft last = attach(attachments, images, detachedDraft);
        assertTrue(attachments.delete(selection(last)).drafts.isEmpty());
        assertThrows(IOException.class, () -> images.read(last.imageRef));
        assertTrue(new LocalV2Store(storePath()).load().activeDrafts().isEmpty());
    }

    @Test public void cancelAndStaleMetadataNeverRemoveExistingAttachment() throws Exception {
        LocalV2Store store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft original = draft();
        save(store, original);
        TmmDraft attached = attach(attachments, images, original);
        try (LocalImages.Pending cancelled = pending(images)) { assertNotNull(cancelled.preview()); }
        assertEquals(1, countImages());
        try (LocalImages.Pending stale = pending(images)) {
            assertThrows(IOException.class, () -> attachments.attach(selection(original), stale, now.plusSeconds(1)));
        }
        assertEquals(2, countImages());
        assertThrows(IOException.class, () -> attachments.detach(selection(original), now.plusSeconds(1)));
        assertThrows(IOException.class, () -> attachments.delete(selection(original)));
        assertEquals(attached.imageRef, store.load().activeDrafts().get(0).imageRef);
        assertNotNull(images.read(attached.imageRef));
    }

    @Test public void sharedImageSurvivesReplacementAndDeletionUntilLastDetach() throws Exception {
        LocalV2Store store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft first = draft();
        save(store, first);
        first = attach(attachments, images, first);
        String shared = first.imageRef;
        TmmDraft second = draft();
        second = new TmmDraft(second.uid, second.displayId, second.type, second.status, second.owner,
                second.createdAt, second.updatedAt, second.deployedAt, second.comment,
                second.position, second.bearingDeg, shared, 1);
        save(store, second);
        TmmDraft replaced = attach(attachments, images, first);
        assertNotNull(images.read(shared));
        attachments.delete(selection(replaced));
        assertNotNull(images.read(shared));
        attachments.detach(selection(second), now.plusSeconds(1));
        assertThrows(IOException.class, () -> images.read(shared));
    }

    @Test public void deletionOfSharedRecordRetainsRemainingImage() throws Exception {
        LocalV2Store store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft first = draft();
        save(store, first);
        first = attach(attachments, images, first);
        TmmDraft second = draft();
        save(store, new TmmDraft(second.uid, second.displayId, second.type, second.status, second.owner,
                second.createdAt, second.updatedAt, null, second.comment, second.position,
                second.bearingDeg, first.imageRef, 1));
        attachments.delete(selection(first));
        assertNotNull(images.read(first.imageRef));
        LocalV2.Selection remaining = store.load().select(second.uid);
        attachments.delete(remaining);
        String reference = first.imageRef;
        assertThrows(IOException.class, () -> images.read(reference));
    }

    @Test public void cleanupFailureReturnsCommittedSnapshotWithoutRollback() throws Exception {
        LocalV2Store store = store();
        LocalImages images = images();
        DraftAttachments attachments = new DraftAttachments(store, images);
        TmmDraft original = draft();
        save(store, original);
        TmmDraft attached = attach(attachments, images, original);
        Path old = imagePath().resolve(attached.imageRef);
        Files.delete(old);
        Files.createSymbolicLink(old, imagePath().resolve("synthetic-missing.png"));
        DraftAttachments.Result result = attachments.detach(selection(attached), now.plusSeconds(1));
        assertTrue(result.cleanupFailed);
        assertNull(result.drafts.get(0).imageRef);
        assertNull(new LocalV2Store(storePath()).load().activeDrafts().get(0).imageRef);
        assertTrue(Files.isSymbolicLink(old));
    }

    @Test public void ambiguousErrorAfterReplacementPreservesNewAndPreviousImages() throws Exception {
        LocalV2Store normal = store();
        LocalImages images = images();
        TmmDraft original = draft();
        save(normal, original);
        TmmDraft first = attach(new DraftAttachments(normal, images), images, original);
        LocalV2Store failing = new LocalV2Store(storePath(), (source, destination) -> {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Synthetic error after metadata replacement");
        });
        try (LocalImages.Pending replacement = pending(images)) {
            assertThrows(IOException.class, () -> new DraftAttachments(failing, images)
                    .attach(selection(first), replacement, now.plusSeconds(1)));
        }
        assertEquals(2, countImages());
        assertNotNull(images.read(first.imageRef));
        TmmDraft saved = new LocalV2Store(storePath()).load().activeDrafts().get(0);
        assertNotEquals(first.imageRef, saved.imageRef);
        assertNotNull(images.read(saved.imageRef));
    }

    @Test public void failureOnFreshReadAfterSuccessfulSaveRetainsBothImages() throws Exception {
        LocalV2Store normal = store();
        LocalImages images = images();
        TmmDraft original = draft();
        save(normal, original);
        TmmDraft first = attach(new DraftAttachments(normal, images), images, original);
        LocalV2Store failing = new LocalV2Store(storePath(), (source, destination) -> {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // The save completed, but the following fresh read cannot open its lock file.
            Files.delete(storePath().resolve("drafts.lock"));
            Files.createDirectory(storePath().resolve("drafts.lock"));
        });
        try (LocalImages.Pending replacement = pending(images)) {
            assertThrows(IOException.class, () -> new DraftAttachments(failing, images)
                    .attach(selection(first), replacement, now.plusSeconds(1)));
        }
        assertEquals(2, countImages());
        assertNotNull(images.read(first.imageRef));
        Files.delete(storePath().resolve("drafts.lock"));
        TmmDraft saved = normal.load().activeDrafts().get(0);
        assertNotEquals(first.imageRef, saved.imageRef);
        assertNotNull(new LocalImages(imagePath()).read(saved.imageRef));
    }

    @Test public void identicalDeleteReaddAndNewDatasetRejectOldAttachmentSelection() throws Exception {
        LocalV2Store store = store(); LocalImages images = images();
        TmmDraft original = draft(); save(store, original); LocalV2.Selection old = selection(original);
        LocalV2.State deleted = store.delete(old.storeId, old.state);
        store.save(old.storeId, deleted, original);
        DraftAttachments attachments = new DraftAttachments(store, images);
        try (LocalImages.Pending p = pending(images)) {
            assertThrows(IOException.class, () -> attachments.attach(old, p, now.plusSeconds(1)));
        }
        assertThrows(IOException.class, () -> attachments.delete(old));
        assertThrows(IOException.class, () -> attachments.detach(old, now.plusSeconds(1)));
        LocalV2.Snapshot saved = store.load(); LocalV2.Selection previousDataset = saved.select(original.uid);
        Files.write(storePath().resolve("drafts.bin"), LocalV2Codec.encode(new LocalV2.Snapshot(java.util.UUID.randomUUID(), saved.slots, saved.decisions)));
        assertThrows(IOException.class, () -> attachments.delete(previousDataset));
        assertEquals(original.revision, store.load().state(original.uid).draft.revision);
    }

    @Test public void datasetChangeBeforeCleanupPreservesBothCopies() throws Exception {
        LocalV2Store normal = store(); LocalImages images = images(); TmmDraft original = draft(); save(normal, original);
        TmmDraft first = attach(new DraftAttachments(normal, images), images, original);
        LocalV2Store replaced = new LocalV2Store(storePath(), (source, target) -> {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            LocalV2.Snapshot saved = LocalV2Codec.decode(Files.readAllBytes(target));
            Files.write(target, LocalV2Codec.encode(new LocalV2.Snapshot(java.util.UUID.randomUUID(), saved.slots, saved.decisions)));
        });
        try (LocalImages.Pending pending = pending(images)) {
            assertThrows(IOException.class, () -> new DraftAttachments(replaced, images).attach(selection(first), pending, now.plusSeconds(1)));
        }
        assertEquals(2, countImages()); assertNotNull(images.read(first.imageRef));
        assertNotNull(images.read(normal.load().state(first.uid).draft.imageRef));
    }

    private TmmDraft attach(DraftAttachments attachments, LocalImages images, TmmDraft draft) throws Exception {
        try (LocalImages.Pending image = pending(images)) {
            DraftAttachments.Result result = attachments.attach(selection(draft), image, now.plusSeconds(1));
            assertFalse(result.cleanupFailed);
            TmmDraft saved = result.drafts.stream().filter(d -> d.uid.equals(draft.uid)).findFirst().get();
            selections.put(saved, result.storage.snapshot.select(saved.uid));
            return saved;
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
    private final java.util.Map<TmmDraft, LocalV2.Selection> selections = new java.util.IdentityHashMap<>();
    private LocalV2Store store() throws Exception { LocalV2Store store = new LocalV2Store(storePath()); store.initializeNew(); return store; }
    private void save(LocalV2Store store, TmmDraft draft) throws Exception {
        LocalV2.Snapshot snapshot = store.load(); store.save(snapshot.storeId, snapshot.state(draft.uid), draft);
        selections.put(draft, store.load().select(draft.uid));
    }
    private LocalV2.Selection selection(TmmDraft draft) throws Exception {
        LocalV2.Selection captured = selections.get(draft);
        if (captured == null) throw new AssertionError("Uncaptured test selection");
        return captured;
    }
    private LocalImages images() { return new LocalImages(imagePath()); }
    private long countImages() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(imagePath())) { return files.count(); }
    }
}
