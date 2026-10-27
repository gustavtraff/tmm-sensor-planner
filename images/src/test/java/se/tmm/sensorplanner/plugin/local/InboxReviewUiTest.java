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
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
public class InboxReviewUiTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Activity activity;
    private InboxReviewUi browser;
    private InboxStore inbox;
    private DraftStore drafts;
    private TmmDraft local;
    private InboxReview review;
    private Path inboxFile, draftsFile, image;
    @Before public void setup() throws Exception {
        activity = org.robolectric.Robolectric.buildActivity(Activity.class).setup().get();
        Path root = temporary.newFolder().toPath();
        inboxFile = root.resolve("inbox/inbox.bin"); draftsFile = root.resolve("drafts/drafts.bin");
        inbox = new InboxStore(inboxFile.getParent()); inbox.initializeNew(); drafts = new DraftStore(draftsFile.getParent());
        local = draft(new UUID(0, 15), "CURRENT-COMMENT", "SYNTHETIC-IMAGE-MUST-NOT-RENDER.png", 1, DeviceStatus.PLANNED);
        drafts.save(local);
        for (int revision = 2; revision <= 7; revision++) {
            local = draft(local.uid, local.comment, local.imageRef, revision, local.status); drafts.save(local);
        }
        image = draftsFile.getParent().resolve(local.imageRef); Files.write(image, new byte[]{1, 2, 3});
        TmmDraft incoming = draft(local.uid, local.comment, null, 99, local.status);
        TmmDraft historical = draft(local.uid, "HISTORICAL-COMMENT", "HISTORICAL-IMAGE-MUST-NOT-RENDER.png", 3, local.status);
        InboxStore.Receipt same = inbox.receive("SYNTHETIC-CONTEXT-A", incoming, historical, Instant.EPOCH);
        inbox.retainLocalObservation(same.id, null);
        inbox.receive("SYNTHETIC-CONTEXT-A", draft(local.uid, "INCOMING-COMMENT", null, 1, DeviceStatus.ACTIVE), local, Instant.EPOCH.plusSeconds(10));
        inbox.receive("SYNTHETIC-CONTEXT-B", incoming, local, Instant.EPOCH.plusSeconds(20));
        inbox.receive("SYNTHETIC-CONTEXT-A", draft(new UUID(0, 16), "Other UUID", null, 1, local.status), null, Instant.EPOCH);
        review = InboxReview.assess(inbox.load(), drafts.load());
        browser = new InboxReviewUi(activity, p -> "SYNTHETIC-MGRS");
    }
    @After public void teardown() { browser.close(); activity.finish(); }
    private TmmDraft draft(UUID uid, String comment, String imageRef, long revision, DeviceStatus status) {
        return new TmmDraft(uid, "PIR 134", DeviceType.PIR, status, "Synthetic role", Instant.EPOCH,
                Instant.EPOCH.plusNanos(42), null, comment, new Position(0, 1), 45.0, imageRef, revision);
    }
    @Test public void sameLabelsRemainSeparateGroupsAndSourceVariantsRemainVisible() throws Exception {
        browser.show(review);
        assertEquals(2, dialog().getListView().getAdapter().getCount());
        String first = dialog().getListView().getAdapter().getItem(0).toString();
        assertTrue(first.contains(local.uid.toString()) && first.contains("3 mottagna varianter"));
        item(0); assertEquals(3, dialog().getListView().getAdapter().getCount());
        String a = dialog().getListView().getAdapter().getItem(0).toString(), b = dialog().getListView().getAdapter().getItem(2).toString();
        assertTrue(a.contains("Samma metadata") && b.contains("Samma metadata"));
        assertTrue(a.contains("SYNTHETIC-CONTEXT-A") && b.contains("SYNTHETIC-CONTEXT-B"));
        assertTrue(dialog().getListView().getAdapter().getItem(1).toString().contains("Avvikande metadata"));
    }
    @Test public void differenceViewPrioritizesChangedFieldsAndExpandsExactUnchangedValues() throws Exception {
        browser.show(review); item(0); item(1);
        String visible = visibleText(shadowOf(dialog()).getView());
        assertTrue(visible.contains("Ändrat: Status") && visible.contains("Ändrat: Kommentar"));
        assertTrue(visible.contains("Planerad") && visible.contains("Aktiv"));
        assertTrue(visible.contains("CURRENT-COMMENT") && visible.contains("INCOMING-COMMENT"));
        assertFalse(visible.contains("Exakta lat/lon"));
        Button expand = findButton(shadowOf(dialog()).getView(), "Visa oförändrade metadatafält");
        assertNotNull(expand); expand.performClick();
        visible = visibleText(shadowOf(dialog()).getView());
        assertTrue(visible.contains("SYNTHETIC-MGRS") && visible.contains("Exakta lat/lon: 0.0, 1.0°"));
        assertTrue(visible.contains("800 mils (6400)") && visible.contains("1970-01-01T00:00:00.000000042Z"));
        assertTrue(visible.contains("Källkontexten autentiseras inte"));
        assertFalse(allText(shadowOf(dialog()).getView()).contains("SYNTHETIC-IMAGE-MUST-NOT-RENDER"));
    }
    @Test public void sameMetadataDoesNotClaimImageDeliveryOrUseForeignRevision() throws Exception {
        browser.show(review); item(0); item(0);
        String text = visibleText(shadowOf(dialog()).getView());
        assertTrue(text.contains("Samma metadata") && text.contains("Lokal revision: 7"));
        assertTrue(text.contains("Lokal bildassociation finns; bildfilen har inte kontrollerats"));
        assertTrue(text.contains("Mottagna metadata innehåller ingen bild"));
        assertFalse(text.contains("Mottagen revision:"));
        assertFalse(text.contains("Ändrat:"));
        assertEquals("Till varianter", dialog().getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
    }
    @Test public void differingCreationTimeGetsExplicitIdentityWarning() throws Exception {
        TmmDraft different = new TmmDraft(local.uid, local.displayId, local.type, local.status, local.owner,
                Instant.EPOCH.plusNanos(1), local.updatedAt, local.deployedAt, local.comment, local.position, local.bearingDeg, null, 1);
        inbox.receive("SYNTHETIC-CONTEXT-A", different, local, Instant.EPOCH.plusSeconds(30));
        browser.show(InboxReview.assess(inbox.load(), drafts.load())); item(0); item(3);
        String text = visibleText(shadowOf(dialog()).getView());
        assertTrue(text.contains("Identiteten behöver granskas separat") && text.contains("Ändrat: Skapad UTC"));
    }
    @Test public void historicalPresentAndAbsentObservationsNeverReplaceCurrentLocal() throws Exception {
        browser.show(review); item(0); item(0); click(AlertDialog.BUTTON_NEUTRAL);
        assertEquals("Historik · inte nuläge", shadowOf(dialog()).getTitle().toString());
        assertEquals(2, dialog().getListView().getAdapter().getCount()); item(0);
        String text = visibleText(shadowOf(dialog()).getView());
        assertTrue(text.contains("HISTORICAL-COMMENT") && text.contains("inte aktuellt lokalt tillstånd"));
        assertTrue(text.contains("Historiken sparar inga bildreferenser"));
        assertFalse(text.contains("CURRENT-COMMENT") || text.contains("IMAGE-MUST-NOT-RENDER"));
        click(AlertDialog.BUTTON_POSITIVE); item(1);
        text = visibleText(shadowOf(dialog()).getView()); assertTrue(text.contains("Lokalt observerades frånvaro"));
        assertFalse(text.contains("Lokal bildassociation finns"));
        dialog().cancel(); idle(); dialog().cancel(); idle();
        assertEquals("Jämför variant · skrivskyddat", shadowOf(dialog()).getTitle().toString());
        assertTrue(visibleText(shadowOf(dialog()).getView()).contains("Lokal bildassociation finns"));
    }
    @Test public void absentLocalIsNotPresentedAsNeverExistedOrAutomaticallyAdded() throws Exception {
        browser.show(review); item(1); item(0);
        String text = visibleText(shadowOf(dialog()).getView());
        assertTrue(text.contains("Saknas lokalt i underlaget") && text.contains("Det visar inte om den tidigare har raderats"));
        assertFalse(text.contains("Ny enhet")); assertFalse(text.contains("Lokal revision:"));
        assertTrue(text.contains("Mottaget:\nOther UUID"));
    }
    @Test public void backNavigationAndCloseDiscardOnlyUiState() throws Exception {
        browser.show(review); item(0); item(1); dialog().cancel(); idle();
        assertTrue(shadowOf(dialog()).getTitle().toString().startsWith("Varianter"));
        dialog().cancel(); idle(); assertTrue(shadowOf(dialog()).getTitle().toString().startsWith("Mottagna metadata"));
        dialog().cancel(); idle(); assertNull(dialog());
        browser.show(review); click(AlertDialog.BUTTON_NEGATIVE); assertNull(dialog());
    }
    @Test public void refreshUsesNewSuppliedLocalStateAndInvalidatesOldCallbacks() throws Exception {
        browser.show(review); item(0); item(1); AlertDialog old = dialog();
        drafts.delete(local.uid, local.revision);
        browser.show(InboxReview.assess(inbox.load(), drafts.load()));
        AlertDialog current = dialog(); old.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); idle();
        assertSame(current, dialog()); item(0); item(1);
        assertTrue(visibleText(shadowOf(dialog()).getView()).contains("Saknas lokalt i underlaget"));
        assertNotNull(review.groups.get(0).currentLocal);
    }
    @Test public void stopThenCloseCannotResumeOldSelectionOrAnotherSnapshot() throws Exception {
        browser.show(review); item(0); item(0); AlertDialog old = dialog(); browser.stop();
        old.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); idle(); assertSame(old, dialog());
        browser.close(); assertNull(dialog()); browser.show(review); assertNull(dialog());
    }
    @Test public void emptyRequiresAValidSuppliedReviewAndNullDoesNotFabricateOne() throws Exception {
        Path other = temporary.newFolder().toPath(); InboxStore validEmpty = new InboxStore(other); validEmpty.initializeNew();
        browser.show(InboxReview.assess(validEmpty.load(), drafts.load()));
        assertTrue(shadowOf(dialog()).getMessage().toString().contains("Underlaget innehåller inga mottagna metadata"));
        AlertDialog old = dialog();
        try { browser.show(null); fail("Null accepted"); } catch (NullPointerException expected) { }
        assertSame(old, dialog());
    }
    @Test public void navigationAndClosePreserveInboxDraftAndImageBytes() throws Exception {
        byte[] a = Files.readAllBytes(inboxFile), b = Files.readAllBytes(draftsFile), c = Files.readAllBytes(image);
        browser.show(review); item(0); item(1); click(AlertDialog.BUTTON_NEUTRAL); item(0);
        browser.close();
        assertArrayEquals(a, Files.readAllBytes(inboxFile)); assertArrayEquals(b, Files.readAllBytes(draftsFile));
        assertArrayEquals(c, Files.readAllBytes(image));
        assertEquals(4, new InboxStore(inboxFile.getParent()).load().receipts.size());
        assertEquals(local.imageRef, new DraftStore(draftsFile.getParent()).load().get(0).imageRef);
    }
    private void item(int index) throws Exception {
        ListView list = dialog().getListView(); list.performItemClick(list.getAdapter().getView(index, null, list), index, index); idle();
    }
    private void click(int which) throws Exception { dialog().getButton(which).performClick(); idle(); }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private AlertDialog dialog() throws Exception { Field f = InboxReviewUi.class.getDeclaredField("dialog"); f.setAccessible(true); return (AlertDialog) f.get(browser); }
    private Button findButton(View v, String prefix) {
        if (v instanceof Button && ((Button) v).getText().toString().startsWith(prefix)) return (Button) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            Button b = findButton(((ViewGroup) v).getChildAt(i), prefix); if (b != null) return b;
        }
        return null;
    }
    private String visibleText(View v) { return v.getVisibility() == View.VISIBLE ? allText(v, true) : ""; }
    private String allText(View v) { return allText(v, false); }
    private String allText(View v, boolean visible) {
        if (visible && v.getVisibility() != View.VISIBLE) return "";
        if (v instanceof TextView) return ((TextView) v).getText().toString();
        StringBuilder text = new StringBuilder();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) text.append(allText(((ViewGroup) v).getChildAt(i), visible)).append('\n');
        return text.toString();
    }
}
