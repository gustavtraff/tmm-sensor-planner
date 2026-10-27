package se.tmm.sensorplanner.plugin.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public class SavedDraftCopyUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> work = new ArrayDeque<>(), ui = new ConcurrentLinkedQueue<>();
    private Activity activity;
    private DraftStore store;
    private SavedDraftCopyUi copy;
    private TmmDraft draft;
    private Path file;
    private boolean available = true, throwOnCreate;
    private int imports;
    private PublicationPreparationReview.Prepared handed;
    private String label;
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Path directory = temporary.newFolder().toPath(); file = directory.resolve("drafts.bin"); store = new DraftStore(directory);
        draft = new TmmDraft(new UUID(0, 19), "PIR 134", DeviceType.PIR, DeviceStatus.PLANNED, "Synthetic role",
                Instant.EPOCH, Instant.EPOCH, null, "Synthetic", new Position(0, 1), 45.0, "synthetic-image", 1);
        store.save(draft);
        copy = new SavedDraftCopyUi(activity, store, work::add, new SavedDraftCopyUi.Host() {
            @Override public void post(Runnable action) { ui.add(action); }
            @Override public boolean available() { return available; }
            @Override public String summary(ShareReview.Metadata m) { return m.displayId + " / " + m.status; }
            @Override public String create(PublicationPreparationReview.Prepared prepared, String hostLabel) {
                imports++; handed = prepared; label = hostLabel;
                if (throwOnCreate) throw new IllegalStateException("PRIVATE-PATH-MUST-NOT-APPEAR");
                return "Granskad kartkopia verifierad: Ja";
            }
        });
    }
    @After public void teardown() { copy.close(); activity.finish(); }

    @Test public void freshConfirmationHandsExactMetadataOnceWithoutChangingSnapshot() throws Exception {
        byte[] before = Files.readAllBytes(file); review();
        String text = text(shadowOf(dialog()).getView());
        assertTrue(text.contains("Bilden delas inte") && text.contains("Ingen feed vald") && text.contains("PIR 134 · r1"));
        AlertDialog reviewed = dialog(); click(AlertDialog.BUTTON_POSITIVE);
        reviewed.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); assertEquals(1, work.size());
        executeHandoff(); assertEquals(1, imports); assertEquals("PIR 134 · r1", label);
        assertEquals(draft.uid, handed.metadata.uid); assertEquals(draft.comment, handed.metadata.comment);
        assertEquals(ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED, handed.imageStatus);
        assertArrayEquals(before, Files.readAllBytes(file)); assertEquals(draft.imageRef, store.load().get(0).imageRef);
        assertEquals("Kartkopieprov · resultat", shadowOf(dialog()).getTitle().toString());
        assertTrue(shadowOf(dialog()).getMessage().toString().contains("mottagande är obekräftade"));
        click(AlertDialog.BUTTON_POSITIVE); assertFalse(copy.isBusy());
    }
    @Test public void staleMetadataImageOnlyChangeAndDeletionNeverReachHost() throws Exception {
        for (int change = 0; change < 3; change++) {
            review();
            if (change == 2) store.delete(draft.uid, draft.revision);
            else {
                draft = draft.edit(draft.displayId, draft.type, draft.status, draft.owner, draft.deployedAt,
                        change == 0 ? "Changed" : draft.comment, draft.position, draft.bearingDeg,
                        change == 1 ? "new-image" : draft.imageRef, draft.updatedAt);
                store.save(draft);
            }
            click(AlertDialog.BUTTON_POSITIVE); executeHandoff();
            assertEquals(0, imports); assertTrue(shadowOf(dialog()).getMessage().toString().contains("Inte skapat"));
            click(AlertDialog.BUTTON_POSITIVE);
        }
    }
    @Test public void cancelAndBackSuppressReadsAndPendingReviews() throws Exception {
        copy.show(draft.uid); click(AlertDialog.BUTTON_NEGATIVE); work.remove().run(); drainUi();
        assertFalse(copy.isBusy()); assertEquals(0, imports);
        review(); dialog().cancel(); shadowOf(Looper.getMainLooper()).idle();
        assertFalse(copy.isBusy()); assertEquals(0, imports);
        review(); click(AlertDialog.BUTTON_NEGATIVE); assertFalse(copy.isBusy()); assertEquals(0, imports);
    }
    @Test public void cancellationReleasesWorkerAndPreventsLateUiImport() throws Exception {
        review(); click(AlertDialog.BUTTON_POSITIVE);
        Thread t = startWaitingHandoff(); assertTrue(t.isAlive());
        copy.cancel(); t.join(3000); assertFalse(t.isAlive()); drainUi();
        assertFalse(copy.isBusy()); assertEquals(0, imports);
    }
    @Test public void interruptedWorkerRevokesQueuedImportPermit() throws Exception {
        review(); click(AlertDialog.BUTTON_POSITIVE); Thread t = startWaitingHandoff();
        t.interrupt(); t.join(3000); assertFalse(t.isAlive()); drainUi();
        assertEquals(0, imports); assertTrue(shadowOf(dialog()).getMessage().toString().contains("Inte skapat"));
    }
    @Test public void stopInvalidatesHandoffBeforeQueuedUiCallback() throws Exception {
        review(); click(AlertDialog.BUTTON_POSITIVE); Thread t = startWaitingHandoff();
        copy.stop(); t.join(3000); assertFalse(t.isAlive()); drainUi();
        copy.close(); assertFalse(copy.isBusy()); assertEquals(0, imports);
    }
    @Test public void lostRouteAndCorruptStoreDoNotUseCapturedData() throws Exception {
        review(); click(AlertDialog.BUTTON_POSITIVE); Thread t = startWaitingHandoff();
        available = false; drainUi(); t.join(3000); assertFalse(t.isAlive()); drainUi();
        assertEquals(0, imports); assertFalse(copy.isBusy());
        available = true; review(); Files.write(file, new byte[]{1, 2, 3});
        click(AlertDialog.BUTTON_POSITIVE); executeHandoff(); assertEquals(0, imports);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }
    @Test public void ambiguousHostFailureDoesNotClaimRollbackOrExposeCause() throws Exception {
        throwOnCreate = true; review(); click(AlertDialog.BUTTON_POSITIVE); executeHandoff();
        String message = shadowOf(dialog()).getMessage().toString();
        assertEquals(1, imports); assertTrue(message.contains("resultat oklart"));
        assertFalse(message.contains("PRIVATE-PATH"));
    }
    @Test public void cancellationAfterImportOnlyDiscardsResultNotTheHostSideEffect() throws Exception {
        review(); click(AlertDialog.BUTTON_POSITIVE); Thread t = startWaitingHandoff();
        ui.remove().run(); t.join(3000); assertFalse(t.isAlive()); assertEquals(1, imports);
        copy.cancel(); drainUi(); assertFalse(copy.isBusy()); assertEquals(1, imports);
    }
    @Test public void absentImageAndUnavailableEntryAreTruthful() throws Exception {
        available = false; copy.show(draft.uid); assertTrue(work.isEmpty()); assertFalse(copy.isBusy());
        available = true; draft = draft.edit(draft.displayId, draft.type, draft.status, draft.owner, draft.deployedAt,
                draft.comment, draft.position, draft.bearingDeg, null, draft.updatedAt); store.save(draft);
        review(); assertTrue(text(shadowOf(dialog()).getView()).contains("Ingen lokal bild är bifogad"));
    }
    private void review() throws Exception {
        copy.show(draft.uid); work.remove().run(); drainUi();
        assertEquals("Granska kartkopia · isolerat labb", shadowOf(dialog()).getTitle().toString());
    }
    private Thread startWaitingHandoff() throws Exception {
        Thread t = new Thread(work.remove(), "saved-copy-test-worker"); t.start();
        await(() -> !ui.isEmpty() || !t.isAlive()); assertTrue("Expected queued UI handoff", t.isAlive()); return t;
    }
    private void executeHandoff() throws Exception {
        Thread t = new Thread(work.remove(), "saved-copy-test-worker"); t.start();
        long until = System.nanoTime() + 5000000000L;
        while (t.isAlive() && System.nanoTime() < until) { drainUi(); Thread.sleep(1); }
        t.join(100); assertFalse("Worker must finish", t.isAlive()); drainUi();
    }
    private void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(1);
        assertTrue("Condition not reached", condition.getAsBoolean());
    }
    private void drainUi() { Runnable r; while ((r = ui.poll()) != null) r.run(); }
    private void click(int which) throws Exception { dialog().getButton(which).performClick(); shadowOf(Looper.getMainLooper()).idle(); }
    private AlertDialog dialog() throws Exception {
        Field f = SavedDraftCopyUi.class.getDeclaredField("dialog"); f.setAccessible(true); return (AlertDialog) f.get(copy);
    }
    private String text(android.view.View view) {
        if (view instanceof android.widget.TextView) return ((android.widget.TextView) view).getText().toString();
        StringBuilder result = new StringBuilder();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result.append(text(group.getChildAt(i)));
        }
        return result.toString();
    }
}
