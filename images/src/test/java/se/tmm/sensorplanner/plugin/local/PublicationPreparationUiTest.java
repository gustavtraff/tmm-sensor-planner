package se.tmm.sensorplanner.plugin.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
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
public class PublicationPreparationUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> work = new ArrayDeque<>(), ui = new ArrayDeque<>();
    private final List<ShareReview.Metadata> shown = new ArrayList<>();
    private Activity activity;
    private DraftStore store;
    private PublicationPreparationUi preparation;
    private boolean available = true;
    private TmmDraft draft;
    private Path file;

    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Path directory = temporary.newFolder().toPath(); file = directory.resolve("drafts.bin");
        store = new DraftStore(directory);
        draft = new TmmDraft(new UUID(0, 14), "PIR 134", DeviceType.PIR, DeviceStatus.PLANNED, "Synthetic role",
                Instant.EPOCH, Instant.EPOCH, null, "Synthetic", new Position(0, 1), 45.0,
                "synthetic-missing-local-image", 1);
        store.save(draft);
        preparation = new PublicationPreparationUi(activity, store, work::add, new PublicationPreparationUi.Host() {
            @Override public void post(Runnable action) { ui.add(action); }
            @Override public boolean available() { return available; }
            @Override public String summary(ShareReview.Metadata m) { shown.add(m); return m.displayId + " / " + m.status; }
        });
    }
    @After public void teardown() { preparation.close(); activity.finish(); }

    @Test public void prepareReadsFreshSavedDataAndLeavesBytesUnchanged() throws Exception {
        byte[] before = Files.readAllBytes(file);
        review();
        assertTrue(viewText(shadowOf(dialog()).getView()).contains("Bilden delas inte"));
        click(AlertDialog.BUTTON_POSITIVE); work.remove().run(); drainUi();
        assertEquals("Metadatakopia förberedd", shadowOf(dialog()).getTitle().toString());
        assertEquals(2, shown.size());
        assertEquals(draft.uid, shown.get(1).uid);
        assertEquals(draft.comment, shown.get(1).comment);
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(draft.imageRef, store.load().get(0).imageRef);
        assertNotNull(field("prepared"));
        click(AlertDialog.BUTTON_POSITIVE);
        assertFalse(preparation.isBusy()); assertNull(field("prepared"));
    }

    @Test public void changedMetadataImageOnlyEditAndDeletedRecordRequireNewReview() throws Exception {
        for (int change = 0; change < 3; change++) {
            review();
            if (change == 2) store.delete(draft.uid, draft.revision);
            else {
                draft = draft.edit(draft.displayId, draft.type, draft.status, draft.owner, draft.deployedAt,
                        change == 0 ? "Changed" : draft.comment, draft.position, draft.bearingDeg,
                        change == 1 ? "replaced-local-image" : draft.imageRef, draft.updatedAt);
                store.save(draft);
            }
            click(AlertDialog.BUTTON_POSITIVE); work.remove().run(); drainUi();
            assertEquals("Kopian kunde inte förberedas", shadowOf(dialog()).getTitle().toString());
            assertNull(field("prepared")); click(AlertDialog.BUTTON_POSITIVE);
        }
    }

    @Test public void cancelSuppressesQueuedReadsAndPreparedCallbacks() throws Exception {
        preparation.show(draft.uid); click(AlertDialog.BUTTON_NEGATIVE);
        work.remove().run(); drainUi(); assertFalse(preparation.isBusy()); assertTrue(shown.isEmpty());
        review(); click(AlertDialog.BUTTON_POSITIVE); work.remove().run();
        preparation.cancel(); drainUi(); assertNull(field("prepared")); assertFalse(preparation.isBusy());
    }

    @Test public void backAndImageAbsentHaveTruthfulLifecycle() throws Exception {
        draft = draft.edit(draft.displayId, draft.type, draft.status, draft.owner, draft.deployedAt,
                draft.comment, draft.position, draft.bearingDeg, null, draft.updatedAt);
        store.save(draft); review();
        assertTrue(viewText(shadowOf(dialog()).getView()).contains("Ingen lokal bild är bifogad"));
        dialog().cancel(); shadowOf(Looper.getMainLooper()).idle();
        assertFalse(preparation.isBusy()); assertNull(field("prepared"));
        review(); click(AlertDialog.BUTTON_POSITIVE); work.remove().run(); drainUi();
        assertTrue(viewText(shadowOf(dialog()).getView()).contains("Ingen fil har skapats"));
        assertNull(store.load().get(0).imageRef);
    }

    @Test public void stopSuppressesConfirmationAndDuplicateClicks() throws Exception {
        review(); AlertDialog review = dialog();
        click(AlertDialog.BUTTON_POSITIVE);
        review.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, work.size());
        preparation.stop(); work.remove().run(); drainUi();
        assertNull(field("prepared"));
        preparation.close(); assertFalse(preparation.isBusy());
    }

    @Test public void lostAccessAndUnreadableStoreNeverUseCapturedDraft() throws Exception {
        review(); click(AlertDialog.BUTTON_POSITIVE); available = false;
        work.remove().run(); drainUi(); assertFalse(preparation.isBusy()); assertNull(field("prepared"));
        available = true; review(); Files.write(file, new byte[]{1, 2, 3});
        click(AlertDialog.BUTTON_POSITIVE); work.remove().run(); drainUi();
        assertEquals("Kopian kunde inte förberedas", shadowOf(dialog()).getTitle().toString());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }

    private void review() throws Exception {
        preparation.show(draft.uid); work.remove().run(); drainUi();
        assertEquals("Granska metadatakopia", shadowOf(dialog()).getTitle().toString());
    }
    private void drainUi() { while (!ui.isEmpty()) ui.remove().run(); }
    private void click(int button) throws Exception {
        dialog().getButton(button).performClick(); shadowOf(Looper.getMainLooper()).idle();
    }
    private AlertDialog dialog() throws Exception { return (AlertDialog) field("dialog"); }
    private Object field(String name) throws Exception {
        Field f = PublicationPreparationUi.class.getDeclaredField(name); f.setAccessible(true); return f.get(preparation);
    }
    private String viewText(android.view.View view) {
        if (view instanceof android.widget.TextView) return ((android.widget.TextView) view).getText().toString();
        StringBuilder text = new StringBuilder();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) text.append(viewText(group.getChildAt(i)));
        }
        return text.toString();
    }
}
