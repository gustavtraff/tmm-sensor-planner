package se.tmm.sensorplanner.plugin.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public class ImportDecisionUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> work = new ArrayDeque<>(), ui = new ArrayDeque<>();
    private Activity activity;
    private ImportDecisionUi flow;
    private InboxStore inbox;
    private DraftStore legacy;
    private Path v2File, inboxFile, legacyFile, image;
    private final UUID storeId = new UUID(0, 7001);
    private TmmDraft local;
    private InboxStore.Receipt receipt;
    private Instant clock = Instant.ofEpochSecond(30);
    private int clockReads, loadReads;
    private boolean available = true, failLoad;
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Path root = temporary.newFolder().toPath();
        inboxFile = root.resolve("inbox/inbox.bin"); inbox = new InboxStore(inboxFile.getParent()); inbox.initializeNew();
        legacyFile = root.resolve("legacy/drafts.bin"); legacy = new DraftStore(legacyFile.getParent());
        local = draft(DeviceType.PIR, DeviceStatus.PLANNED, "LOCAL-COMMENT", 45.0, "SYNTHETIC-PRIVATE-IMAGE.png", 1);
        legacy.save(local);
        for (int r = 2; r <= 7; r++) { local = draft(local.type, local.status, local.comment, local.bearingDeg, local.imageRef, r); legacy.save(local); }
        image = legacyFile.getParent().resolve(local.imageRef); Files.write(image, new byte[]{1, 2, 3});
        receipt = inbox.receive("SYNTHETIC-CONTEXT-A", draft(DeviceType.PIR, DeviceStatus.ACTIVE, "INCOMING-COMMENT", 45.0, null, 1), local, Instant.ofEpochSecond(100));
        v2File = root.resolve("synthetic-v2-fixture.bin");
        // Fixture identity/version are explicit independent literals, not a production migration from v1.
        fixture(LocalV2.State.active(12, local));
        flow = new ImportDecisionUi(activity, work::add, new ImportDecisionUi.Host() {
            @Override public void post(Runnable action) { ui.add(action); }
            @Override public boolean available() { return available; }
            @Override public ImportDecisionUi.Sources load() throws Exception {
                loadReads++;
                if (failLoad) throw new java.io.IOException("PRIVATE-PATH-MUST-NOT-APPEAR");
                return new ImportDecisionUi.Sources(inbox.load(), LocalV2Codec.decode(Files.readAllBytes(v2File)));
            }
            @Override public Instant editTime() { clockReads++; return clock; }
        }, p -> "SYNTHETIC-MGRS");
    }
    @After public void teardown() { flow.close(); activity.finish(); }
    private TmmDraft draft(DeviceType type, DeviceStatus status, String comment, Double bearing, String imageRef, long revision) {
        return new TmmDraft(new UUID(0, 15), "PIR 134", type, status, "Synthetic role", Instant.EPOCH,
                Instant.ofEpochSecond(10), null, comment, new Position(0, 1), bearing, imageRef, revision);
    }
    private void fixture(LocalV2.State... states) throws Exception {
        Files.write(v2File, LocalV2Codec.encode(new LocalV2.Snapshot(storeId, Arrays.asList(states), Collections.emptyList())));
    }
    @Test public void selectedPreviewAndFreshPreparationKeepExactTimeImageAndBytes() throws Exception {
        byte[] a = Files.readAllBytes(inboxFile), b = Files.readAllBytes(v2File), c = Files.readAllBytes(legacyFile), d = Files.readAllBytes(image);
        choices(); chooseFields("Status", "Kommentar");
        ImportDecisionPreparation.Plan plan = pending().preview;
        assertEquals(ImportDecisionPreparation.Effect.UPDATE_LOCAL, plan.effect);
        assertEquals(20, plan.fieldMask); assertEquals(clock, plan.editTime);
        assertEquals(8, plan.result.draft.revision); assertEquals(13, plan.result.version);
        assertEquals(local.imageRef, plan.result.draft.imageRef);
        String text = text(); assertTrue(text.contains("Föreslaget:\nINCOMING-COMMENT") && text.contains("Den lokala bildassociationen bevaras"));
        assertTrue(text.contains("SYNTHETIC-MGRS") && text.contains("800 mils"));
        assertFalse(text.contains(local.imageRef));
        AlertDialog preview = dialog(); click(AlertDialog.BUTTON_POSITIVE);
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); assertEquals(1, work.size());
        clock = Instant.ofEpochSecond(999); runWork();
        ImportDecisionPreparation.Prepared prepared = prepared(); assertNotNull(prepared);
        assertEquals(Instant.ofEpochSecond(30), prepared.plan.editTime); assertEquals(1, clockReads);
        assertEquals("Importbeslut förberett i minnet", shadowOf(dialog()).getTitle().toString());
        assertTrue(text().contains("Inget har sparats eller applicerats"));
        click(AlertDialog.BUTTON_POSITIVE); assertNull(prepared()); assertFalse(flow.isBusy());
        assertArrayEquals(a, Files.readAllBytes(inboxFile)); assertArrayEquals(b, Files.readAllBytes(v2File));
        assertArrayEquals(c, Files.readAllBytes(legacyFile)); assertArrayEquals(d, Files.readAllBytes(image));
    }
    @Test public void emptySelectionStaysInSelectorAndSelectsNothingImplicitly() throws Exception {
        choices(); item(1); click(AlertDialog.BUTTON_POSITIVE);
        assertEquals("Välj mottagna fält", shadowOf(dialog()).getTitle().toString());
        assertTrue(text().contains("Välj minst ett mottaget fält")); assertTrue(work.isEmpty());
        for (CheckBox c : checks()) assertFalse(c.isChecked());
    }
    @Test public void allEightFieldLabelsMapToTheirExactMasksAndNullTimeIsExplicit() throws Exception {
        String[] names = {"ID", "Typ", "Status", "Ägare", "Kommentar", "Utplacerad UTC", "Position", "Bäring"};
        ImportDecisionPreparation.Field[] fields = ImportDecisionPreparation.Field.values();
        for (int i = 0; i < fields.length; i++) {
            choices(); chooseFields(names[i]); assertEquals(fields[i].mask, pending().preview.fieldMask); click(AlertDialog.BUTTON_NEGATIVE);
        }
        local = new TmmDraft(local.uid, local.displayId, local.type, local.status, local.owner, local.createdAt, local.updatedAt,
                Instant.ofEpochSecond(5), local.comment, local.position, local.bearingDeg, local.imageRef, local.revision);
        fixture(LocalV2.State.active(12, local)); choices(); chooseFields(names);
        assertEquals(255, pending().preview.fieldMask); assertNull(pending().preview.result.draft.deployedAt);
        assertEquals(local.imageRef, pending().preview.result.draft.imageRef);
    }
    @Test public void oldCancelAndQueuedPreparedResultCannotAffectANewFlow() throws Exception {
        choices(); chooseFields("Status"); AlertDialog old = dialog(); click(AlertDialog.BUTTON_NEGATIVE);
        choices(); AlertDialog current = dialog(); old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle(); assertSame(current, dialog());
        item(0); runWork(); click(AlertDialog.BUTTON_POSITIVE); work.remove().run();
        flow.cancel(); Runnable r; while ((r = ui.poll()) != null) r.run(); assertNull(prepared()); assertFalse(flow.isBusy());
    }
    @Test public void keepLocalAndUnchangedSelectedValuesDoNotAdvanceCounters() throws Exception {
        choices(); item(0); runWork();
        assertEquals(ImportDecisionPreparation.Effect.KEEP_LOCAL, pending().preview.effect);
        assertEquals(7, pending().preview.result.draft.revision); assertEquals(12, pending().preview.result.version);
        assertEquals(0, clockReads); click(AlertDialog.BUTTON_NEGATIVE);
        choices(); chooseFields("Position");
        assertEquals(ImportDecisionPreparation.Effect.KEEP_LOCAL, pending().preview.effect);
        assertEquals(64, pending().preview.fieldMask); assertNull(pending().preview.editTime);
        assertEquals(local.imageRef, pending().preview.result.draft.imageRef);
    }
    @Test public void equalMetadataNeedsNoPreparationPrompt() throws Exception {
        receipt = inbox.receive("SYNTHETIC-EQUAL", draft(local.type, local.status, local.comment, local.bearingDeg, null, 1), local, Instant.EPOCH);
        choices(); assertEquals("Samma metadata", shadowOf(dialog()).getTitle().toString());
        assertNull(pending()); assertNull(prepared()); assertTrue(work.isEmpty()); assertEquals(0, clockReads);
    }
    @Test public void absentAndKnownDeletedUseDistinctExplicitAddFlow() throws Exception {
        fixture(); choices(); assertTrue(shadowOf(dialog()).getTitle().toString().startsWith("Tillägg"));
        click(AlertDialog.BUTTON_POSITIVE); runWork();
        assertEquals(1, pending().preview.result.version); assertEquals(1, pending().preview.result.draft.revision);
        assertNull(pending().preview.result.draft.imageRef); click(AlertDialog.BUTTON_NEGATIVE);
        fixture(LocalV2.State.deleted(local.uid, 12)); choices(); click(AlertDialog.BUTTON_POSITIVE);
        assertTrue(text().contains("Bekräfta uttryckligen återläggning")); assertTrue(work.isEmpty());
        checks().get(0).performClick(); click(AlertDialog.BUTTON_POSITIVE); runWork();
        assertTrue(pending().preview.isReAdd()); assertEquals(13, pending().preview.result.version);
        assertTrue(text().contains("Uttrycklig återläggning"));
    }
    @Test public void creationMismatchAndBackwardClockHaveSafeSpecificErrors() throws Exception {
        TmmDraft incoming = new TmmDraft(local.uid, local.displayId, local.type, local.status, local.owner,
                Instant.EPOCH.plusNanos(1), local.updatedAt, null, "INCOMING-COMMENT", local.position, local.bearingDeg, null, 1);
        InboxStore.Receipt original = receipt;
        receipt = inbox.receive("SYNTHETIC-IDENTITY", incoming, local, Instant.EPOCH);
        choices(); chooseFields("Kommentar"); assertTrue(shadowOf(dialog()).getMessage().toString().contains("Skapandetiden skiljer"));
        click(AlertDialog.BUTTON_POSITIVE); receipt = original; clock = Instant.EPOCH;
        choices(); chooseFields("Status"); assertTrue(shadowOf(dialog()).getMessage().toString().contains("klockan ligger före"));
        assertNull(prepared());
    }
    @Test public void gatewayRequiresExplicitBearingRemoval() throws Exception {
        receipt = inbox.receive("SYNTHETIC-GATEWAY", draft(DeviceType.GATEWAY, DeviceStatus.ACTIVE, "INCOMING", null, null, 1), local, Instant.EPOCH);
        choices(); chooseFields("Typ"); assertTrue(shadowOf(dialog()).getMessage().toString().contains("Rensa bäring"));
        click(AlertDialog.BUTTON_POSITIVE); choices(); chooseFields("Typ", "Bäring");
        assertEquals(DeviceType.GATEWAY, pending().preview.result.draft.type); assertNull(pending().preview.result.draft.bearingDeg);
        assertEquals(local.imageRef, pending().preview.result.draft.imageRef); assertEquals(130, pending().preview.fieldMask);
    }
    @Test public void staleImageVersionAndDeleteReAddAreRejectedAtConfirmation() throws Exception {
        for (int change = 0; change < 3; change++) {
            fixture(LocalV2.State.active(12, local)); choices(); chooseFields("Status");
            if (change == 0) fixture(LocalV2.State.active(12, draft(local.type, local.status, local.comment, local.bearingDeg, "REPLACED-PRIVATE-IMAGE", 7)));
            if (change == 1) fixture(LocalV2.State.active(13, local));
            if (change == 2) fixture(LocalV2.State.deleted(local.uid, 14));
            click(AlertDialog.BUTTON_POSITIVE); runWork(); assertNull(prepared());
            assertTrue(shadowOf(dialog()).getMessage().toString().contains("underlaget har ändrats")); click(AlertDialog.BUTTON_POSITIVE);
        }
    }
    @Test public void cancelBackAndStopSuppressQueuedReadsPreviewsAndResults() throws Exception {
        flow.show(inbox.load().inboxId, receipt); dialog().cancel(); idle(); runWork(); assertFalse(flow.isBusy());
        choices(); chooseFields("Status"); click(AlertDialog.BUTTON_NEGATIVE); assertNull(pending());
        choices(); item(0); flow.cancel(); runWork(); assertNull(pending()); assertNull(prepared());
        choices(); item(0); runWork(); click(AlertDialog.BUTTON_POSITIVE); flow.stop(); runWork();
        assertNull(prepared()); flow.close(); assertFalse(flow.isBusy());
    }
    @Test public void sourceIdentityAvailabilityAndLoadFailuresNeverBecomeEmptyState() throws Exception {
        available = false; flow.show(inbox.load().inboxId, receipt); assertTrue(work.isEmpty());
        assertTrue(shadowOf(dialog()).getMessage().toString().contains("Versionsbundet underlag")); click(AlertDialog.BUTTON_POSITIVE);
        available = true; failLoad = true; flow.show(inbox.load().inboxId, receipt); runWork();
        assertFalse(shadowOf(dialog()).getMessage().toString().contains("PRIVATE-PATH")); click(AlertDialog.BUTTON_POSITIVE);
        failLoad = false; flow.show(new UUID(0, 999), receipt); runWork();
        assertTrue(shadowOf(dialog()).getMessage().toString().contains("vald mottagning kunde inte verifieras"));
    }
    @Test public void revokedAccessBeforeQueuedReadOrBuildSuppressesIoAndClock() throws Exception {
        flow.show(inbox.load().inboxId, receipt); available = false; runWork();
        assertEquals(0, loadReads); assertFalse(flow.isBusy());
        available = true; choices(); item(1);
        checks().stream().filter(c -> c.getText().toString().startsWith("Status")).findFirst().get().performClick();
        click(AlertDialog.BUTTON_POSITIVE); available = false; runWork();
        assertEquals(0, clockReads); assertNull(pending()); assertNull(prepared());
    }
    @Test public void lostAccessAtPrepareDiscardsResultAndNewReceiptEvidenceDoesNotRebase() throws Exception {
        choices(); chooseFields("Status"); inbox.retainLocalObservation(receipt.id, null);
        click(AlertDialog.BUTTON_POSITIVE); runWork(); assertNotNull(prepared()); click(AlertDialog.BUTTON_POSITIVE);
        choices(); chooseFields("Kommentar"); click(AlertDialog.BUTTON_POSITIVE); available = false; runWork();
        assertNull(prepared()); assertFalse(flow.isBusy());
    }
    @Test public void optionalBrowserTransitionKeepsSelectionAndDefaultBrowserStaysReadOnly() throws Exception {
        InboxReview review = InboxReview.assess(inbox.load(), Collections.singletonList(local));
        InboxReviewUi browser = new InboxReviewUi(activity, p -> "SYNTHETIC-MGRS"); browser.show(review);
        browserItem(browser, 0); browserItem(browser, 0);
        assertNull(findButton(shadowOf(browserDialog(browser)).getView(), "Förhandsvisa importval")); browser.close();
        browser = new InboxReviewUi(activity, p -> "SYNTHETIC-MGRS", flow); browser.show(review);
        browserItem(browser, 0); browserItem(browser, 0);
        findButton(shadowOf(browserDialog(browser)).getView(), "Förhandsvisa importval").performClick(); idle();
        assertNull(browserDialog(browser)); runWork(); item(0); runWork();
        assertEquals(receipt.id, pending().preview.receipt.id); assertEquals(inbox.load().inboxId, pending().preview.inboxId); browser.close();
    }
    private void choices() throws Exception { flow.show(inbox.load().inboxId, receipt); runWork(); }
    private void chooseFields(String... prefixes) throws Exception {
        item(1);
        for (String prefix : prefixes) {
            CheckBox c = checks().stream().filter(v -> v.getText().toString().startsWith(prefix)).findFirst().get(); c.performClick();
        }
        click(AlertDialog.BUTTON_POSITIVE); runWork();
    }
    private void runWork() { work.remove().run(); Runnable r; while ((r = ui.poll()) != null) r.run(); idle(); }
    private void item(int i) throws Exception { ListView list = dialog().getListView(); list.performItemClick(list.getAdapter().getView(i, null, list), i, i); idle(); }
    private void click(int which) throws Exception { dialog().getButton(which).performClick(); idle(); }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private Object field(String name) throws Exception { Field f = ImportDecisionUi.class.getDeclaredField(name); f.setAccessible(true); return f.get(flow); }
    private AlertDialog dialog() throws Exception { return (AlertDialog) field("dialog"); }
    private ImportDecisionPreparation pending() throws Exception { return (ImportDecisionPreparation) field("pending"); }
    private ImportDecisionPreparation.Prepared prepared() throws Exception { return (ImportDecisionPreparation.Prepared) field("prepared"); }
    private String text() throws Exception { return text(shadowOf(dialog()).getView()); }
    private String text(View v) { if (v instanceof TextView) return ((TextView) v).getText().toString(); StringBuilder s = new StringBuilder(); if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) s.append(text(((ViewGroup)v).getChildAt(i))).append('\n'); return s.toString(); }
    private List<CheckBox> checks() throws Exception { List<CheckBox> result = new ArrayList<>(); collectChecks(shadowOf(dialog()).getView(), result); return result; }
    private void collectChecks(View v, List<CheckBox> list) { if (v instanceof CheckBox) list.add((CheckBox)v); if (v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) collectChecks(((ViewGroup)v).getChildAt(i),list); }
    private Button findButton(View v,String prefix) { if(v instanceof Button&&((Button)v).getText().toString().startsWith(prefix))return (Button)v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=findButton(((ViewGroup)v).getChildAt(i),prefix);if(b!=null)return b;}return null; }
    private AlertDialog browserDialog(InboxReviewUi browser) throws Exception { Field f=InboxReviewUi.class.getDeclaredField("dialog");f.setAccessible(true);return(AlertDialog)f.get(browser); }
    private void browserItem(InboxReviewUi browser,int i)throws Exception{ListView list=browserDialog(browser).getListView();list.performItemClick(list.getAdapter().getView(i,null,list),i,i);idle();}
}
