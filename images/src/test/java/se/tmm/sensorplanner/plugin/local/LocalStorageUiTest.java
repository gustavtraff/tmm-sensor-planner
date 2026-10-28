package se.tmm.sensorplanner.plugin.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
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
@Config(sdk = {26,35}, manifest = Config.NONE)
public class LocalStorageUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> work = new ArrayDeque<>(), posted = new ArrayDeque<>();
    private Activity activity;
    private Path directory;
    private LocalV2Store store;
    private LocalStorageUi controller;
    private LocalV2Store.Inspection opened;
    private String error;
    private boolean available = true;
    private boolean showList;
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        directory = temporary.newFolder().toPath(); store = new LocalV2Store(directory);
        controller = new LocalStorageUi(activity, store, work::add, new LocalStorageUi.Host() {
            public boolean available() { return available; }
            public void post(Runnable action) { posted.add(action); }
            public void loaded(LocalV2Store.Inspection state, boolean interactive) { opened = state; showList = interactive; }
            public void unavailable(String message, boolean interactive) { error = message; }
        });
    }
    @After public void close() { controller.close(); activity.finish(); }
    private void run() { work.remove().run(); while (!posted.isEmpty()) posted.remove().run(); }
    private AlertDialog dialog() { return (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog(); }
    private void click(int button) { dialog().getButton(button).performClick(); shadowOf(Looper.getMainLooper()).idle(); }
    private TmmDraft legacy() throws Exception {
        TmmDraft d = TmmDraft.create("PIR SYNTHETIC", DeviceType.PIR, "ROLE", new Position(0, 1), Instant.EPOCH);
        new DraftStore(directory).save(d); return d;
    }
    @Test public void restoreNeverInitializesOrMigrates() throws Exception {
        controller.load(false); run(); assertNotNull(error); assertNull(opened); assertFalse(Files.exists(directory.resolve("drafts.bin")));
        legacy(); byte[] bytes = Files.readAllBytes(directory.resolve("drafts.bin"));
        controller.load(false); run(); assertNull(opened); assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("drafts.bin")));
    }
    @Test public void initializationRequiresExplicitChoiceAndIsOneUse() throws Exception {
        controller.load(true); run(); assertNull(opened);
        assertTrue(shadowOf(dialog()).getMessage().toString().contains("tidigare data saknas"));
        AlertDialog old = dialog(); click(AlertDialog.BUTTON_POSITIVE); old.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(1, work.size()); run(); assertNotNull(opened); assertTrue(showList); assertEquals(0, opened.snapshot.slots.size());
        UUID id = opened.snapshot.storeId;
        controller.load(false); run(); assertEquals(id, opened.snapshot.storeId); assertFalse(showList);
    }
    @Test public void legacyUpgradePreservesDataAndWarnsAboutHistoryAndDowngrade() throws Exception {
        TmmDraft d = legacy(); byte[] old = Files.readAllBytes(directory.resolve("drafts.bin"));
        controller.load(true); run(); assertNull(opened); assertArrayEquals(old, Files.readAllBytes(directory.resolve("drafts.bin")));
        String message = shadowOf(dialog()).getMessage().toString();
        assertTrue(message.contains("Äldre raderings-") && message.contains("Äldre pluginversioner"));
        click(AlertDialog.BUTTON_POSITIVE); run();
        assertEquals(d.uid, opened.snapshot.activeDrafts().get(0).uid); assertEquals(1, opened.snapshot.state(d.uid).version);
    }
    @Test public void cancelBackAndOldButtonsCannotActivateOrCancelNewFlow() throws Exception {
        controller.load(true); run(); AlertDialog old = dialog(); click(AlertDialog.BUTTON_NEGATIVE);
        assertFalse(controller.isBusy()); assertFalse(Files.exists(directory.resolve("drafts.bin")));
        controller.load(true); run(); old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertTrue(controller.isBusy()); dialog().cancel(); shadowOf(Looper.getMainLooper()).idle(); assertFalse(controller.isBusy());
        assertFalse(Files.exists(directory.resolve("drafts.bin")));
    }
    @Test public void stopBeforeQueuedConfirmationNeverWrites() throws Exception {
        legacy(); byte[] before = Files.readAllBytes(directory.resolve("drafts.bin"));
        controller.load(true); run(); click(AlertDialog.BUTTON_POSITIVE); controller.stop(); run();
        assertNull(opened); assertArrayEquals(before, Files.readAllBytes(directory.resolve("drafts.bin")));
    }
    @Test public void accessLossAndQueuedReadNeverOpenOrInitialize() throws Exception {
        controller.load(true); available = false; run(); assertFalse(controller.isBusy()); assertNull(opened);
        available = true; controller.load(true); run(); click(AlertDialog.BUTTON_POSITIVE); available = false; run();
        assertNull(opened); assertFalse(Files.exists(directory.resolve("drafts.bin")));
    }
    @Test public void transientAccessLossBeforeQueuedReadReleasesBusyAndRequiresNewOpen() throws Exception {
        controller.load(true);
        available = false; work.remove().run();
        available = true; posted.remove().run();
        assertFalse(controller.isBusy()); assertNull(opened);
        assertFalse(Files.exists(directory.resolve("drafts.bin")));
        controller.load(true); run();
        assertTrue(dialog().isShowing()); assertTrue(controller.isBusy());
        assertNull(opened); assertFalse(Files.exists(directory.resolve("drafts.bin")));
    }
    @Test public void transientAccessLossBeforeQueuedConfirmationDiscardsConsentAndAllowsRetry() throws Exception {
        legacy(); byte[] before = Files.readAllBytes(directory.resolve("drafts.bin"));
        controller.load(true); run(); click(AlertDialog.BUTTON_POSITIVE);
        available = false; work.remove().run();
        available = true; posted.remove().run();
        assertFalse(controller.isBusy()); assertNull(opened);
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("drafts.bin")));
        controller.load(true); run();
        assertTrue(dialog().isShowing()); assertNull(opened);
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("drafts.bin")));
        click(AlertDialog.BUTTON_POSITIVE); run();
        assertNotNull(opened); assertEquals(LocalV2Store.Format.V2, opened.format);
    }
    @Test public void transientAccessLossBeforeInitializationNeverResumesOldConsent() throws Exception {
        controller.load(true); run(); AlertDialog old = dialog(); click(AlertDialog.BUTTON_POSITIVE);
        available = false; work.remove().run();
        available = true; posted.remove().run();
        assertFalse(controller.isBusy()); assertNull(opened);
        assertFalse(Files.exists(directory.resolve("drafts.bin")));
        controller.load(true); run();
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(work.isEmpty()); assertTrue(controller.isBusy()); assertTrue(dialog().isShowing());
        click(AlertDialog.BUTTON_POSITIVE); run();
        assertNotNull(opened); assertEquals(0, opened.snapshot.slots.size());
    }
    @Test public void transientAccessLossAtConfirmationDismissesOldChoiceAndAllowsNewOne() throws Exception {
        controller.load(true); run();
        available = false; click(AlertDialog.BUTTON_POSITIVE);
        available = true; posted.remove().run();
        assertFalse(controller.isBusy()); assertTrue(work.isEmpty()); assertNull(opened);
        assertFalse(Files.exists(directory.resolve("drafts.bin")));
        controller.load(true); run();
        assertTrue(dialog().isShowing()); assertTrue(controller.isBusy());
        assertNull(opened); assertFalse(Files.exists(directory.resolve("drafts.bin")));
    }
    @Test public void corruptSourceHasNoInitializationChoice() throws Exception {
        Files.write(directory.resolve("drafts.bin"), new byte[]{1,2,3}); controller.load(true); run();
        assertNotNull(error); assertNull(opened); assertFalse(controller.isBusy()); assertTrue(work.isEmpty());
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(directory.resolve("drafts.bin")));
    }
    @Test public void competingCreationCannotBeOverwrittenByMissingConfirmation() throws Exception {
        controller.load(true); run(); legacy(); byte[] bytes = Files.readAllBytes(directory.resolve("drafts.bin"));
        click(AlertDialog.BUTTON_POSITIVE); run(); assertNotNull(error); assertNull(opened);
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("drafts.bin")));
    }
    @Test public void interactiveRequestCanJoinPendingRestoreAndShowsChoice() throws Exception {
        legacy(); controller.load(false); controller.load(true); assertEquals(1, work.size()); run();
        assertTrue(controller.isBusy()); assertTrue(dialog().isShowing()); assertNull(opened);
    }
    @Test public void capacityWarningDistinguishesDeletedSlotsAndHistoryAndBytes() throws Exception {
        store.initializeNew(); LocalV2.Snapshot s = store.load();
        assertEquals("", LocalStorageUi.warning(store.inspect()));
        List<LocalV2.State> slots = new ArrayList<>();
        for (int i=0; i<800; i++) slots.add(LocalV2.State.deleted(new UUID(1,i), 1));
        Files.write(directory.resolve("drafts.bin"), LocalV2Codec.encode(new LocalV2.Snapshot(s.storeId, slots, Collections.emptyList())));
        String warning = LocalStorageUi.warning(store.inspect());
        assertTrue(warning.contains("800/1000") && warning.contains("0 aktiva, 800 raderade") && warning.contains("Beslut: 0/1000"));
        assertTrue(warning.contains("byte") && warning.contains("Radering frigör ingen plats"));
    }
    @Test public void fileByteBudgetAloneTriggersWarning() throws Exception {
        store.initializeNew(); LocalV2.Snapshot snapshot = store.load(); List<LocalV2.State> slots = new ArrayList<>();
        String large = String.join("", Collections.nCopies(65536, "x"));
        for (int i=0; i<205; i++) {
            TmmDraft d = new TmmDraft(new UUID(5,i), "SYNTHETIC", DeviceType.MINI, DeviceStatus.PLANNED,
                    large, Instant.EPOCH, Instant.EPOCH, null, "", new Position(0,1), null, null, 1);
            slots.add(LocalV2.State.active(1,d));
        }
        Files.write(directory.resolve("drafts.bin"), LocalV2Codec.encode(new LocalV2.Snapshot(snapshot.storeId, slots, Collections.emptyList())));
        String warning = LocalStorageUi.warning(store.inspect());
        assertTrue(warning.contains("205/1000") && warning.contains("Beslut: 0/1000") && warning.contains("byte"));
    }

}
