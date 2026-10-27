package se.tmm.sensorplanner.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.plugin.local.ImportCommitUi;
import se.tmm.sensorplanner.plugin.local.ImportDecisionUi;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26,35}, manifest = Config.NONE)
public class ImportCommitUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> work = new ArrayDeque<>(), ui = new ArrayDeque<>();
    private Activity activity;
    private Path root, file, image;
    private LocalV2Store local;
    private InboxStore inbox;
    private ImportCommitUi commits;
    private ImportDecisionUi flow;
    private TmmDraft draft;
    private InboxStore.Receipt receipt;
    private boolean available = true, source = true, closeOnChanged;
    private int changes, clockReads;
    private final Instant editTime = Instant.ofEpochSecond(30);
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get(); root = temporary.newFolder().toPath();
        file = root.resolve("local/drafts.bin"); local = new LocalV2Store(file.getParent()); local.initializeNew();
        inbox = new InboxStore(root.resolve("inbox")); inbox.initializeNew();
        draft = new TmmDraft(new UUID(0,15), "PIR SYNTHETIC", DeviceType.PIR, DeviceStatus.PLANNED, "ROLE",
                Instant.EPOCH, Instant.ofEpochSecond(10), null, "local", new Position(0,1), 45.0, "synthetic-image.png", 1);
        LocalV2.Snapshot s = local.load(); local.save(s.storeId, s.state(draft.uid), draft);
        image = root.resolve(draft.imageRef); Files.write(image, new byte[]{1,3,7});
        TmmDraft incoming = metadata(draft, "remote", DeviceStatus.ACTIVE);
        receipt = inbox.receive("SYNTHETIC SOURCE", incoming, draft, Instant.ofEpochSecond(100));
        endpoint(local);
    }
    private TmmDraft metadata(TmmDraft d, String comment, DeviceStatus status) {
        return new TmmDraft(d.uid, d.displayId, d.type, status, d.owner, d.createdAt, d.updatedAt,
                d.deployedAt, comment, d.position, d.bearingDeg, null, 1);
    }
    private void endpoint(LocalV2Store store) {
        commits = new ImportCommitUi(activity, work::add, new ImportCommitUi.Host() {
            public void post(Runnable r) { ui.add(r); }
            public boolean available() { return available; }
            public boolean sourceAvailable(UUID inboxId, InboxStore.Receipt selected) { return source && selected.id.equals(receipt.id); }
            public void changed(UUID target) { changes++; if (closeOnChanged) commits.close(); }
        }, store, inbox);
    }
    @After public void close() { if (flow!=null) flow.close(); commits.close(); activity.finish(); }
    private void run() { work.remove().run(); drain(); }
    private void drain() { while (!ui.isEmpty()) ui.remove().run(); shadowOf(Looper.getMainLooper()).idle(); }
    private AlertDialog dialog() { return (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog(); }
    private String title() { return shadowOf(dialog()).getTitle().toString(); }
    private String message() { return String.valueOf(shadowOf(dialog()).getMessage()); }
    private void click(int which) { dialog().getButton(which).performClick(); shadowOf(Looper.getMainLooper()).idle(); }
    private ImportDecisionPreparation.Prepared selected() throws Exception {
        InboxStore.Snapshot i = inbox.load(); LocalV2.Snapshot s = local.load();
        return ImportDecisionPreparation.useSelected(i, receipt.id, s, EnumSet.of(ImportDecisionPreparation.Field.COMMENT), editTime).prepare(i,s);
    }
    private ImportDecisionPreparation.Prepared keep() throws Exception {
        InboxStore.Snapshot i = inbox.load(); LocalV2.Snapshot s = local.load();
        return ImportDecisionPreparation.keepLocal(i, receipt.id, s).prepare(i,s);
    }
    private ImportDecisionPreparation.Prepared add() throws Exception {
        LocalV2.Snapshot s = local.load(); local.delete(s.storeId, s.state(draft.uid));
        InboxStore.Snapshot i = inbox.load(); s = local.load();
        return ImportDecisionPreparation.addLocally(i, receipt.id, s, true).prepare(i,s);
    }
    private void createFlow() {
        flow = new ImportDecisionUi(activity, work::add, new ImportDecisionUi.Host() {
            public void post(Runnable r) { ui.add(r); }
            public boolean available() { return available; }
            public ImportDecisionUi.Sources load() throws Exception { return new ImportDecisionUi.Sources(inbox.load(),local.load()); }
            public Instant editTime() { clockReads++; return editTime; }
        }, p -> "SYNTHETIC MGRS", commits);
    }
    private void previewComment() throws Exception {
        createFlow(); flow.show(inbox.load().inboxId,receipt); run();
        shadowOf(dialog()).clickOnItem(1); shadowOf(Looper.getMainLooper()).idle();
        for (View v : descendants(shadowOf(dialog()).getView())) {
            if (v instanceof CheckBox && ((CheckBox)v).getText().toString().startsWith("Kommentar:")) ((CheckBox)v).setChecked(true);
        }
        click(AlertDialog.BUTTON_POSITIVE); run();
    }
    @Test public void exactPreviewSavePersistsOnceWithImageAndReceiptPreserved() throws Exception {
        byte[] before = Files.readAllBytes(file), received = Files.readAllBytes(root.resolve("inbox/inbox.bin"));
        previewComment(); assertArrayEquals(before,Files.readAllBytes(file));
        assertEquals("Spara val lokalt",dialog().getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        AlertDialog old = dialog(); click(AlertDialog.BUTTON_POSITIVE); old.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(1,work.size());
        run(); assertArrayEquals(before,Files.readAllBytes(file)); assertTrue(flow.isBusy());
        run(); assertEquals("Valda värden sparade lokalt",title()); assertEquals(1,changes);
        LocalV2.Snapshot saved = local.load(); assertEquals(1,saved.decisions.size());
        assertEquals("remote",saved.state(draft.uid).draft.comment); assertEquals(2,saved.state(draft.uid).version);
        assertEquals(draft.imageRef,saved.state(draft.uid).draft.imageRef); assertEquals(editTime,saved.state(draft.uid).draft.updatedAt);
        assertEquals(1,clockReads); assertFalse(message().contains(draft.imageRef));
        assertArrayEquals(new byte[]{1,3,7},Files.readAllBytes(image)); assertArrayEquals(received,Files.readAllBytes(root.resolve("inbox/inbox.bin")));
        click(AlertDialog.BUTTON_POSITIVE); assertFalse(flow.isBusy());
    }
    @Test public void cancelBeforeFreshPreparationOrQueuedCommitNeverWrites() throws Exception {
        byte[] before = Files.readAllBytes(file); previewComment(); click(AlertDialog.BUTTON_NEGATIVE);
        assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(0,changes);
        previewComment(); click(AlertDialog.BUTTON_POSITIVE); run(); flow.cancel(); run();
        assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(0,changes);
    }
    @Test public void sourceLossAfterPreviewAndTargetChangeAfterPreparationBlockApply() throws Exception {
        byte[] before = Files.readAllBytes(file); previewComment(); click(AlertDialog.BUTTON_POSITIVE); run(); source=false; run();
        assertEquals("Källåtkomst saknas",title()); assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(0,changes);
        click(AlertDialog.BUTTON_POSITIVE); source=true;
        previewComment(); click(AlertDialog.BUTTON_POSITIVE); run();
        LocalV2.Snapshot s = local.load();
        local.save(s.storeId,s.state(draft.uid),draft.edit(draft.displayId,draft.type,draft.status,draft.owner,null,
                draft.comment,draft.position,draft.bearingDeg,"changed-image",draft.updatedAt));
        run(); assertEquals("Granskningen är inaktuell",title()); assertTrue(local.load().decisions.isEmpty());
        assertEquals("changed-image",local.load().state(draft.uid).draft.imageRef);
    }
    @Test public void keepAndEqualHaveDistinctNoMutationOutcomes() throws Exception {
        commits.commit(keep()); run(); assertEquals("Lokalt val registrerat",title());
        assertEquals(1,local.load().state(draft.uid).version); assertEquals(1,local.load().state(draft.uid).draft.revision);
        click(AlertDialog.BUTTON_POSITIVE);
        receipt = inbox.receive("SYNTHETIC SOURCE",metadata(draft,draft.comment,draft.status),draft,Instant.EPOCH);
        byte[] before = Files.readAllBytes(file); commits.commit(keep()); run();
        assertEquals("Samma metadata",title()); assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(1,changes);
    }
    @Test public void replayReportsHistoricalDecisionAndCurrentDeletedState() throws Exception {
        ImportDecisionPreparation.Prepared p=add(); commits.commit(p); run(); assertEquals("Enheten tillagd lokalt",title());
        click(AlertDialog.BUTTON_POSITIVE); LocalV2.Snapshot saved=local.load(); local.delete(saved.storeId,saved.state(draft.uid));
        byte[] before=Files.readAllBytes(file); commits.commit(p); run();
        assertEquals("Tidigare lokalt beslut verifierat",title()); assertTrue(message().contains("Raderat lokalt"));
        assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(1,local.load().decisions.size());
    }
    @Test public void unknownAfterReplacementReadsLocalHistoryWithoutInboxOrSource() throws Exception {
        ImportDecisionPreparation.Prepared p=selected();
        endpoint(new LocalV2Store(file.getParent(),(a,b)->{
            Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); throw new IOException("PRIVATE-PATH-MUST-NOT-APPEAR");
        }));
        commits.commit(p); run(); assertEquals("Sparandet är obekräftat",title()); assertEquals(0,changes);
        assertFalse(message().contains("PRIVATE-PATH")); source=false; Files.delete(root.resolve("inbox/inbox.bin"));
        click(AlertDialog.BUTTON_POSITIVE); run();
        assertEquals("Tidigare lokalt beslut verifierat",title()); assertEquals(1,changes);
        assertEquals(1,local.load().decisions.size());
    }
    @Test public void notCommittedRetryUsesSameCommandOnceAndRevalidatesState() throws Exception {
        AtomicBoolean fail=new AtomicBoolean(true); ImportDecisionPreparation.Prepared p=selected();
        endpoint(new LocalV2Store(file.getParent(),(a,b)->{
            if(fail.getAndSet(false)) throw new IOException("PRIVATE-FAILED-RENAME");
            Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }));
        commits.commit(p); run(); click(AlertDialog.BUTTON_POSITIVE); run(); assertEquals("Beslutet är inte sparat",title());
        AlertDialog retry=dialog(); click(AlertDialog.BUTTON_POSITIVE); retry.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(1,work.size()); run();
        assertEquals(p.plan.operationId,local.load().decisions.get(0).id); assertEquals(1,local.load().decisions.size());
    }
    @Test public void retryAfterStateChangedFailsInsteadOfRebasing() throws Exception {
        endpoint(new LocalV2Store(file.getParent(),(a,b)->{ throw new IOException("PRIVATE-FAILED-RENAME"); }));
        commits.commit(selected()); run(); click(AlertDialog.BUTTON_POSITIVE); run();
        LocalV2.Snapshot s=local.load(); local.delete(s.storeId,s.state(draft.uid));
        click(AlertDialog.BUTTON_POSITIVE); run(); assertEquals("Granskningen är inaktuell",title());
        assertEquals(LocalV2.Presence.DELETED,local.load().state(draft.uid).presence); assertTrue(local.load().decisions.isEmpty());
    }
    @Test public void missingDifferentAndCorruptReadbackStayUnknownWithoutInitialization() throws Exception {
        ImportDecisionPreparation.Prepared p=selected();
        endpoint(new LocalV2Store(file.getParent(),(a,b)->{throw new IOException("PRIVATE-FAILED-RENAME");}));
        commits.commit(p); run(); byte[] old=Files.readAllBytes(file); LocalV2.Snapshot saved=local.load();
        Files.delete(file); click(AlertDialog.BUTTON_POSITIVE); run(); assertEquals("Sparandet är obekräftat",title()); assertFalse(Files.exists(file));
        Files.write(file,new byte[]{1,2,3}); click(AlertDialog.BUTTON_POSITIVE); run(); assertEquals("Sparandet är obekräftat",title());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(file));
        Files.write(file,LocalV2Codec.encode(new LocalV2.Snapshot(UUID.randomUUID(),saved.slots,saved.decisions)));
        byte[] replaced=Files.readAllBytes(file); click(AlertDialog.BUTTON_POSITIVE); run(); assertEquals("Sparandet är obekräftat",title());
        assertArrayEquals(replaced,Files.readAllBytes(file)); Files.write(file,old);
    }
    @Test public void stopBeforeWriteAndAfterWriteSuppressLateResults() throws Exception {
        byte[] before=Files.readAllBytes(file); commits.commit(selected()); commits.stop(); run();
        assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(0,changes);
        commits.close(); endpoint(local); commits.commit(selected()); work.remove().run(); commits.stop(); drain();
        assertEquals(1,local.load().decisions.size()); assertEquals(0,changes);
    }
    @Test public void accessLossWhileQueuedAndOldCallbacksCannotAffectNextFlow() throws Exception {
        ImportDecisionPreparation.Prepared p=selected(); byte[] before=Files.readAllBytes(file);
        commits.commit(p); available=false; run(); assertArrayEquals(before,Files.readAllBytes(file)); available=true;
        commits.commit(p); AlertDialog old=dialog(); commits.cancel(); commits.commit(p);
        old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        run(); run(); assertEquals(1,local.load().decisions.size()); assertEquals(1,changes);
    }
    @Test public void callbackClosingControllerCannotResurrectOutcomeDialog() throws Exception {
        closeOnChanged=true; commits.commit(selected()); AlertDialog progress=dialog(); run();
        assertEquals(1,changes); assertFalse(progress.isShowing()); assertFalse(commits.isBusy());
        assertEquals(1,local.load().decisions.size());
    }
    @Test public void busyStoreCannotApplyAndShowsSafeReason() throws Exception {
        ImportDecisionPreparation.Prepared prepared=selected(); byte[] before=Files.readAllBytes(file);
        try (java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(file.getParent().resolve("drafts.lock"),StandardOpenOption.WRITE);
             java.nio.channels.FileLock lock=channel.lock()) {
            commits.commit(prepared); run(); assertEquals("Lagringen är upptagen",title());
        }
        assertArrayEquals(before,Files.readAllBytes(file)); assertEquals(0,changes);
    }
    @Test public void fullStoreRefusesAddWithoutEvictionOrImageChanges() throws Exception {
        LocalV2.Snapshot saved=local.load(); List<LocalV2.State> slots=new ArrayList<>();
        for(int i=0;i<1000;i++) slots.add(LocalV2.State.deleted(new UUID(99,i),1));
        Files.write(file,LocalV2Codec.encode(new LocalV2.Snapshot(saved.storeId,slots,Collections.emptyList())));
        InboxStore.Snapshot received=inbox.load(); LocalV2.Snapshot full=local.load();
        ImportDecisionPreparation.Prepared prepared=ImportDecisionPreparation.addLocally(received,receipt.id,full,false).prepare(received,full);
        byte[] before=Files.readAllBytes(file); commits.commit(prepared); run();
        assertEquals("Valet kunde inte sparas",title()); assertArrayEquals(before,Files.readAllBytes(file));
        assertArrayEquals(new byte[]{1,3,7},Files.readAllBytes(image)); assertEquals(0,changes);
    }
    private static List<View> descendants(View root) {
        List<View> result=new ArrayList<>(); result.add(root);
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) result.addAll(descendants(((ViewGroup)root).getChildAt(i)));
        return result;
    }
}
