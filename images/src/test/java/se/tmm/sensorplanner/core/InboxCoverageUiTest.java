package se.tmm.sensorplanner.core;

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
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.plugin.local.InboxReviewUi;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26,35}, manifest = Config.NONE)
public class InboxCoverageUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Activity activity;
    private InboxReviewUi browser;
    private InboxStore inbox;
    private LocalV2Store local;
    private InboxStore.Receipt receipt;
    private Path root, image;
    private TmmDraft draft;
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = temporary.newFolder().toPath();
        inbox = new InboxStore(root.resolve("inbox")); inbox.initializeNew();
        local = new LocalV2Store(root.resolve("local")); LocalV2.Snapshot initial = local.initializeNew();
        draft = new TmmDraft(new UUID(0,15), "PIR SYNTHETIC", DeviceType.PIR, DeviceStatus.PLANNED, "SYNTHETIC ROLE",
                Instant.EPOCH, Instant.ofEpochSecond(10), null, "LOCAL-COMMENT", new Position(0,1), 45.0,
                "IMAGE-HANDLE-MUST-NOT-RENDER.png", 1);
        local.save(initial.storeId, LocalV2.State.neverSeen(draft.uid), draft);
        image = root.resolve(draft.imageRef); Files.write(image, new byte[]{1,3,7});
        TmmDraft incoming = new TmmDraft(draft.uid, draft.displayId, draft.type, DeviceStatus.ACTIVE, draft.owner,
                draft.createdAt, Instant.ofEpochSecond(20), null, "RECEIVED-COMMENT", draft.position, draft.bearingDeg, null, 1);
        receipt = inbox.receive("SYNTHETIC-CONTEXT-A", incoming, draft, Instant.ofEpochSecond(100));
        inbox.receive("SYNTHETIC-CONTEXT-B", incoming, null, Instant.ofEpochSecond(101));
        inbox.receive("SYNTHETIC-CONTEXT-A", TmmDraft.create(draft.displayId, DeviceType.PIR, draft.owner,
                draft.position, Instant.EPOCH), null, Instant.EPOCH);
        keep();
        browser = new InboxReviewUi(activity, p -> "SYNTHETIC-MGRS");
    }
    @After public void teardown() { browser.close(); activity.finish(); }
    private void keep() throws Exception {
        InboxStore.Snapshot received = inbox.load(); LocalV2.Snapshot state = local.load();
        local.commit(inbox, ImportDecisionPreparation.keepLocal(received, receipt.id, state).prepare(received, state));
    }
    private void show() throws Exception { show(inbox.load(), local.load()); }
    private void show(InboxStore.Snapshot received, LocalV2.Snapshot state) throws Exception {
        browser.show(InboxReview.assess(received, state.activeDrafts()), InboxDecisionCoverage.assess(received, state));
    }
    private void edit(String image, String comment) throws Exception {
        LocalV2.Snapshot state = local.load(); LocalV2.State expected = state.state(draft.uid); TmmDraft before = expected.draft;
        TmmDraft changed = before.edit(before.displayId, before.type, before.status, before.owner, before.deployedAt,
                comment, before.position, before.bearingDeg, image, before.updatedAt);
        local.save(state.storeId, expected, changed);
    }
    @Test public void coveredConflictAndOtherContextRemainVisibleAndDistinct() throws Exception {
        show(); assertEquals(2, dialog().getListView().getAdapter().getCount()); item(0);
        assertEquals(2, dialog().getListView().getAdapter().getCount());
        assertTrue(label(0).contains("Granskat mot aktuellt lokalt tillstånd"));
        assertTrue(label(0).contains("Avvikande metadata · redan granskat mot nuläget"));
        assertFalse(label(0).contains("Avvikande metadata · granskning behövs"));
        assertTrue(label(1).contains("Inget matchande granskningsbeslut"));
        item(0); assertTrue(text().contains("Tillståndsversion: 1") && text().contains("Lokal revision: 1"));
        assertTrue(text().contains("inte en ny sparbekräftelse"));
        assertFalse(text().contains(draft.imageRef));
    }
    @Test public void imageOnlyChangeRequiresFreshReviewEvenIfMetadataIsRestored() throws Exception {
        edit("NEW-IMAGE-MUST-NOT-RENDER.png", draft.comment); show(); item(0);
        assertTrue(label(0).contains("Äldre beslut · ny granskning")); item(0);
        assertTrue(text().contains("Ny granskning krävs före ett nytt importval"));
        assertTrue(text().contains("Tillståndsversion: 2"));
        assertFalse(text().contains("Granskat mot aktuellt lokalt tillstånd"));
        assertFalse(text().contains("NEW-IMAGE-MUST-NOT-RENDER"));
        edit(draft.imageRef, draft.comment); show(); item(0); item(0);
        assertTrue(text().contains("Tillståndsversion: 3") && text().contains("Äldre beslut"));
    }
    @Test public void laterEqualMetadataKeepsOldDecisionSeparateWithoutDemandingAnotherChoice() throws Exception {
        LocalV2.Snapshot state = local.load(); LocalV2.State before = state.state(draft.uid); TmmDraft m = receipt.incoming;
        TmmDraft equal = new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                m.deployedAt, m.comment, m.position, m.bearingDeg, draft.imageRef, 2);
        local.save(state.storeId, before, equal); show(); item(0); item(0);
        assertTrue(text().contains("Samma metadata") && text().contains("Äldre beslut"));
        assertTrue(text().contains("Samma metadata kräver inget nytt val enbart"));
        assertTrue(text().contains("Tillståndsversion: 2"));
        assertFalse(text().contains("Granskat mot aktuellt lokalt tillstånd"));
    }
    @Test public void deletionAndReAddNeverReviveOldChoice() throws Exception {
        LocalV2.Snapshot state = local.load(); local.delete(state.storeId, state.state(draft.uid));
        show(); item(0); item(0);
        assertTrue(text().contains("Känd lokal radering") && text().contains("Tillståndsversion: 2"));
        assertTrue(text().contains("ingen automatisk återläggning"));
        assertFalse(text().contains("Det visar inte om den tidigare har raderats"));
        state = local.load(); local.save(state.storeId, state.state(draft.uid), draft);
        show(); item(0); item(0);
        assertTrue(text().contains("Tillståndsversion: 3") && text().contains("Lokal revision: 1"));
        assertTrue(text().contains("Äldre beslut · ny granskning"));
    }
    @Test public void earlierAndCurrentDecisionsAndObservationsHaveSeparateHistory() throws Exception {
        edit(draft.imageRef, "SECOND-LOCAL-COMMENT"); keep();
        show(); item(0); item(0);
        button("Granskningsbeslut (2)").performClick(); idle();
        assertTrue(label(0).contains("Äldre beslut") && label(1).contains("Aktuellt granskningsbeslut"));
        item(0); assertTrue(text().contains("Äldre beslut · täcker inte nuläget"));
        assertTrue(text().contains("Sparat resultat: Behållet lokalt · version 1 · revision 1"));
        assertTrue(text().contains("RECEIVED-COMMENT") && text().contains("LOCAL-COMMENT"));
        assertFalse(text().contains(draft.imageRef)); click(AlertDialog.BUTTON_POSITIVE);
        item(1); assertTrue(text().contains("Aktuellt granskningsbeslut") && text().contains("SECOND-LOCAL-COMMENT"));
        click(AlertDialog.BUTTON_POSITIVE); click(AlertDialog.BUTTON_NEGATIVE);
        click(AlertDialog.BUTTON_NEUTRAL);
        assertEquals("Historik · inte nuläge", shadowOf(dialog()).getTitle().toString());
        assertTrue(label(0).contains("Observation"));
    }
    @Test public void selectedUpdateHistoryShowsExactChoiceAndBeforeResultVersions() throws Exception {
        InboxStore.Snapshot received = inbox.load(); LocalV2.Snapshot state = local.load();
        local.commit(inbox, ImportDecisionPreparation.useSelected(received, receipt.id, state,
                EnumSet.of(ImportDecisionPreparation.Field.COMMENT), Instant.ofEpochSecond(30)).prepare(received, state));
        show(); item(0); item(0); button("Granskningsbeslut (2)").performClick(); idle(); item(1);
        assertTrue(text().contains("Använd valda mottagna uppgifter") && text().contains("Val av mottagna uppgifter: Kommentar"));
        assertTrue(text().contains("Granskat före: Aktiv lokal enhet · version 1 · revision 1"));
        assertTrue(text().contains("Sparat resultat: Uppdaterat lokalt · version 2 · revision 2"));
        assertFalse(text().contains(draft.imageRef));
    }
    @Test public void explicitReAddHistoryShowsKnownDeletionAndNewActiveResult() throws Exception {
        LocalV2.Snapshot state = local.load(); local.delete(state.storeId, state.state(draft.uid));
        state = local.load(); InboxStore.Snapshot received = inbox.load();
        local.commit(inbox, ImportDecisionPreparation.addLocally(received, receipt.id, state, true).prepare(received, state));
        show(); item(0); item(0); button("Granskningsbeslut (2)").performClick(); idle(); item(1);
        assertTrue(text().contains("Aktuellt granskningsbeslut") && text().contains("Val av mottagna uppgifter: Hela mottagna metadatavarianten"));
        assertTrue(text().contains("Granskat före: Känd lokal radering · version 2"));
        assertTrue(text().contains("Sparat resultat: Tillagt lokalt · version 3 · revision 1"));
    }
    @Test public void missingReceiptAndEmptyInboxKeepAllSavedDecisionsAccessible() throws Exception {
        InboxStore.Snapshot received = new InboxStore.Snapshot(UUID.randomUUID(), Collections.emptyList());
        show(received, local.load()); click(AlertDialog.BUTTON_NEUTRAL);
        assertEquals(1, dialog().getListView().getAdapter().getCount());
        assertTrue(label(0).contains("ingen exakt mottagning i denna inkorg")); item(0);
        assertTrue(text().contains(receipt.id.toString()) && text().contains("SYNTHETIC-CONTEXT-A"));
        assertFalse(text().contains("Aktuellt granskningsbeslut"));
    }
    @Test public void legacyRefreshClearsCoverageAndOldCallbacksCannotRestoreIt() throws Exception {
        show(); item(0); item(0); Button old = button("Granskningsbeslut");
        browser.show(InboxReview.assess(inbox.load(), local.load().activeDrafts()));
        AlertDialog root = dialog(); old.performClick(); idle(); assertSame(root, dialog());
        item(0); item(0); assertTrue(text().contains("Beslutstäckning saknas i underlaget"));
        assertFalse(text().contains("Granskat mot aktuellt lokalt tillstånd"));
        assertNull(buttonOrNull("Granskningsbeslut"));
    }
    @Test public void mismatchedPairedModelsAreRejectedWithoutReplacingDialog() throws Exception {
        InboxStore.Snapshot received = inbox.load(); LocalV2.Snapshot state = local.load();
        InboxReview review = InboxReview.assess(received, state.activeDrafts());
        show(); AlertDialog original = dialog();
        InboxDecisionCoverage wrongInbox = InboxDecisionCoverage.assess(new InboxStore.Snapshot(UUID.randomUUID(), received.receipts), state);
        try { browser.show(review, wrongInbox); fail("Mismatched inbox accepted"); } catch (IllegalArgumentException expected) { }
        assertSame(original, dialog());
        InboxStore.Receipt r = received.receipts.get(0);
        List<InboxStore.Receipt> changed = new ArrayList<>(received.receipts);
        changed.set(0, new InboxStore.Receipt(r.id, "OTHER-SYNTHETIC-CONTEXT", r.firstObservedAt, r.incoming, r.localObservations));
        try { browser.show(review, InboxDecisionCoverage.assess(new InboxStore.Snapshot(received.inboxId, changed), state));
            fail("Mismatched source context accepted"); } catch (IllegalArgumentException expected) { }
        InboxStore.Snapshot duplicate = new InboxStore.Snapshot(received.inboxId,
                Arrays.asList(received.receipts.get(0), received.receipts.get(0), received.receipts.get(2)));
        try { browser.show(InboxReview.assess(duplicate, state.activeDrafts()), InboxDecisionCoverage.assess(received, state));
            fail("Duplicate review rows accepted"); } catch (IllegalArgumentException expected) { }
        assertSame(original, dialog());
        edit("CHANGED-ASSOCIATION.png", draft.comment);
        try { browser.show(review, InboxDecisionCoverage.assess(received, local.load())); fail("Mismatched local fields accepted"); }
        catch (IllegalArgumentException expected) { }
        assertSame(original, dialog());
    }
    @Test public void navigatingAndClosingDoesNotWriteOrCommitAnything() throws Exception {
        Path inboxFile = root.resolve("inbox/inbox.bin"), localFile = root.resolve("local/drafts.bin");
        byte[] a = Files.readAllBytes(inboxFile), b = Files.readAllBytes(localFile), c = Files.readAllBytes(image);
        show(); click(AlertDialog.BUTTON_NEUTRAL); item(0); click(AlertDialog.BUTTON_POSITIVE); dialog().cancel(); idle();
        item(0); item(0); button("Granskningsbeslut").performClick(); idle(); dialog().cancel(); idle(); browser.close();
        assertArrayEquals(a, Files.readAllBytes(inboxFile)); assertArrayEquals(b, Files.readAllBytes(localFile)); assertArrayEquals(c, Files.readAllBytes(image));
        assertEquals(1, local.load().decisions.size()); assertEquals(3, inbox.load().receipts.size());
    }
    private String label(int index) throws Exception { return dialog().getListView().getAdapter().getItem(index).toString(); }
    private void item(int index) throws Exception {
        ListView list = dialog().getListView(); list.performItemClick(list.getAdapter().getView(index, null, list), index, index); idle();
    }
    private void click(int which) throws Exception { dialog().getButton(which).performClick(); idle(); }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private AlertDialog dialog() throws Exception { Field f = InboxReviewUi.class.getDeclaredField("dialog"); f.setAccessible(true); return (AlertDialog) f.get(browser); }
    private String text() throws Exception { return text(shadowOf(dialog()).getView()); }
    private String text(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return "";
        if (view instanceof TextView) return ((TextView) view).getText().toString();
        StringBuilder text = new StringBuilder();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) text.append(text(((ViewGroup) view).getChildAt(i))).append('\n');
        return text.toString();
    }
    private Button button(String prefix) throws Exception { Button button = buttonOrNull(prefix); assertNotNull(button); return button; }
    private Button buttonOrNull(String prefix) throws Exception { return find(shadowOf(dialog()).getView(), prefix); }
    private Button find(View view, String prefix) {
        if (view instanceof Button && ((Button) view).getText().toString().startsWith(prefix)) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button button = find(((ViewGroup) view).getChildAt(i), prefix); if (button != null) return button;
        }
        return null;
    }
}
