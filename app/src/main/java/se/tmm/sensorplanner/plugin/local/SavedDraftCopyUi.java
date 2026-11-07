package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import se.tmm.sensorplanner.core.*;

/** Reviewed saved-state handoff; caller keeps normal edits busy-blocked on the same worker. */
@TargetApi(26)
public final class SavedDraftCopyUi {
    public interface PendingUpdate extends AutoCloseable {
        ShareReview.Metadata previous();
        String previousLabel();
        /** UI thread; one-use, freshly checked host mutation. No destination/delivery claim. */
        String commit();
        @Override void close();
    }
    public interface Host {
        void post(Runnable action);
        boolean available();
        String summary(ShareReview.Metadata metadata);
        /** UI thread; must enforce lab guards and return an honest host-copy outcome. */
        String create(PublicationPreparationReview.Prepared prepared, String label);
        default boolean supportsUpdates() { return false; }
        default PendingUpdate inspectUpdate(PublicationPreparationReview.Prepared prepared, String label) { throw new UnsupportedOperationException(); }
    }
    private final Context context;
    private final LocalV2Store store;
    private final Executor worker;
    private final Host host;
    private final Object lifecycle = new Object();
    private volatile boolean busy;
    private boolean closed;
    private long generation;
    private PublicationPreparationReview pending;
    private Handoff waiting;
    private PendingUpdate pendingUpdate;
    private AlertDialog dialog;

    public SavedDraftCopyUi(Context context, LocalV2Store store, Executor worker, Host host) {
        this.context = context; this.store = store; this.worker = worker; this.host = host;
    }
    public boolean isBusy() { return busy; }

    public void show(UUID uid) {
        final long token;
        synchronized (lifecycle) {
            if (closed || busy || !host.available()) return;
            busy = true; token = ++generation;
        }
        replace(new AlertDialog.Builder(context).setTitle("Läser sparad enhet för kartkopieprov")
                .setMessage("Endast syntetiska data i isolerat labb. Ingen kartkopia skapas före bekräftelse.")
                .setNegativeButton("Avbryt", (d, w) -> cancel()).create());
        worker.execute(() -> {
            try {
                if (!valid(token)) return;
                LocalV2.Selection selected = find(uid);
                if (selected == null) throw new IllegalStateException();
                post(token, () -> review(selected, token));
            } catch (Exception unavailable) { post(token, () -> result("Inte skapat: den sparade enheten kunde inte läsas. Granska på nytt.")); }
        });
    }
    private void review(LocalV2.Selection selected, long token) {
        TmmDraft saved = selected.draft;
        PublicationPreparationReview review = new PublicationPreparationReview(saved);
        synchronized (lifecycle) {
            if (!valid(token)) { review.close(); return; }
            pending = review;
        }
        String label = saved.displayId + " · r" + saved.revision;
        String warning = "\n\nENDAST SYNTETISKT ISOLERAT LABB\nKartetikett: " + label
                + "\nDen lokala revisionen är ingen gemensam versionsräknare."
                + (saved.imageRef == null ? "\nIngen lokal bild är bifogad. Ingen bild delas." : "\nBilden delas inte.")
                + "\n\nIngen feed vald. Bekräftelse skapar en vanlig ATAK-kartkopia. "
                + "Feeder kan publicera vanligt kartinnehåll automatiskt. Kartkopian kan finnas kvar efter Unload/omstart. "
                + "Utkastet förblir lokalt och senare ändringar följer inte med. "
                + "Välj och kontrollera eventuell feed separat i Data Sync; TMM kan inte verifiera native Done eller leverans. "
                + "Skapa-funktionen skriver inte över en avvikande befintlig kartkopia med samma UUID. Uppdatering har en separat granskning.";
        TextView text = new TextView(context); text.setText(host.summary(review.metadata) + warning); text.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(context); scroll.addView(text);
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle("Granska kartkopia · isolerat labb").setView(scroll)
                .setNegativeButton("Avbryt", (d, w) -> cancel())
                .setPositiveButton("Skapa kartkopia i labbet", (d, w) -> {
                    synchronized (lifecycle) {
                        if (!valid(token) || pending != review) return;
                        pending = null;
                    }
                    replace(new AlertDialog.Builder(context).setTitle("Kontrollerar sparad enhet")
                            .setMessage("Ingen feed vald. Åtgärden kan avbrytas innan SDK-importen börjar.")
                            .setNegativeButton("Avbryt", (ignored, which) -> cancel()).create());
                    worker.execute(() -> prepare(review, selected, label, token, false));
                });
        if (host.supportsUpdates()) builder.setNeutralButton("Granska uppdatering", (d,w) -> {
            synchronized (lifecycle) {
                if (!valid(token) || pending != review) return;
                pending = null;
            }
            replace(new AlertDialog.Builder(context).setTitle("Läser befintlig kartkopia")
                    .setMessage("Ingen kartkopia ändras före en separat uppdateringsbekräftelse.")
                    .setNegativeButton("Avbryt", (ignored,which)->cancel()).create());
            worker.execute(() -> prepare(review, selected, label, token, true));
        });
        replace(builder.create());
    }
    private void prepare(PublicationPreparationReview review, LocalV2.Selection selected, String label, long token, boolean update) {
        try {
            if (!valid(token)) return;
            LocalV2.Snapshot snapshot = store.load();
            if (!selected.isCurrent(snapshot)) throw new IllegalStateException();
            TmmDraft current = snapshot.state(review.metadata.uid).draft;
            if (!valid(token)) return;
            PublicationPreparationReview.Prepared prepared = review.prepare(current);
            PublicationCodec.decode(new ByteArrayInputStream(PublicationCodec.encode(prepared)));
            String outcome = handoff(() -> {
                if (!update) return host.create(prepared, label);
                PendingUpdate inspected = host.inspectUpdate(prepared, label);
                if (inspected == null) throw new IllegalStateException();
                pendingUpdate = inspected;
                return "";
            }, token, update ? "Inte uppdaterat: kräver en enda giltig kartkopia utan värdbilagor. Ändrad typ, position, bäring eller skapandetid stöds inte. Ingen uppdatering utfördes. Granska på nytt."
                    : "Skapande försökt – resultat oklart. Kontrollera kartan och feeds före nytt försök. Ingen automatisk återställning görs.");
            if (outcome != null) post(token, () -> {
                if (update && pendingUpdate != null) reviewUpdate(pendingUpdate, prepared.metadata, selected, label, token);
                else result(outcome);
            });
        } catch (Exception unavailable) {
            post(token, () -> result((update ? "Inte uppdaterat" : "Inte skapat") + ": enheten har ändrats, tagits bort eller kunde inte verifieras. Läs om och granska på nytt."));
        } finally { review.close(); }
    }
    private void reviewUpdate(PendingUpdate update, ShareReview.Metadata proposed, LocalV2.Selection selected, String label, long token) {
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(24,16,24,16);
        TextView text = new TextView(context);
        text.setText("BEFINTLIG KARTKOPIA\nEtikett: " + update.previousLabel() + "\n" + host.summary(update.previous())
                + "\n\nFÖRESLAGEN METADATA\nEtikett: " + label + "\n" + host.summary(proposed)
                + "\n\nEndast metadata. Ändrad typ, position, bäring eller skapandetid stöds inte i detta prov. Privata TMM-bilder läses eller delas inte av uppdateringskoden. Befintliga värdbilagor måste saknas enligt SDK-kontrollen."
                + "\n\nTMM väljer ingen feed. Befintliga Data Sync-kopplingar och andra värdfunktioner kan publicera ändringen direkt eller senare. "
                + "Kontrollera alla berörda labbfeeds före bekräftelse. Okända destinationer är inte godkända. Endast syntetiska data i isolerat loopback-labb.");
        body.addView(text);
        CheckBox consent = new CheckBox(context); consent.setText("Jag har kontrollerat berörda labbfeeds och godkänner uppdateringens möjliga publicering dit.");
        consent.setMinHeight(64); body.addView(consent);
        ScrollView scroll = new ScrollView(context); scroll.addView(body);
        AlertDialog next = new AlertDialog.Builder(context).setTitle("Granska befintlig uppdatering · labb").setView(scroll)
                .setNegativeButton("Avbryt", (d,w)->cancel()).setPositiveButton("Uppdatera kartkopian", null).create();
        replace(next); next.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        consent.setOnCheckedChangeListener((button,checked)->next.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            synchronized (lifecycle) {
                if (!valid(token) || pendingUpdate != update || !consent.isChecked()) return;
                pendingUpdate = null;
            }
            replace(new AlertDialog.Builder(context).setTitle("Kontrollerar sparad enhet och kartkopia")
                    .setMessage("Ingen feedleverans bekräftas av SDK-resultatet.").setNegativeButton("Avbryt", (d,w)->cancel()).create());
            worker.execute(()->{
                try {
                    if (!valid(token)) return;
                    LocalV2.Snapshot fresh = store.load();
                    if (!selected.isCurrent(fresh)) throw new IllegalStateException();
                    String outcome = handoff(update::commit, token, "SDK-uppdatering försökt – resultat oklart. Kontrollera kartan och feeds före nytt försök. Ingen automatisk återställning görs.");
                    if (outcome != null) post(token,()->result(outcome));
                } catch (Exception stale) { post(token,()->result("Inte uppdaterat: den sparade enheten har ändrats eller kunde inte läsas. Granska på nytt.")); }
                finally { update.close(); }
            });
        });
    }
    private String handoff(Supplier<String> operation, long token, String failureMessage) {
        Handoff h = new Handoff();
        synchronized (lifecycle) {
            if (!valid(token)) return null;
            waiting = h;
            try {
                host.post(() -> {
                    synchronized (lifecycle) {
                        if (!h.pending || !valid(token)) { h.done.countDown(); return; }
                        h.pending = false;
                        try {
                            h.outcome = host.available() ? java.util.Objects.requireNonNull(operation.get())
                                    : "Inte skapat: labbvillkoren gäller inte längre. Granska på nytt.";
                        } catch (RuntimeException uncertain) {
                            h.outcome = failureMessage;
                        } finally { h.done.countDown(); }
                    }
                });
            } catch (RuntimeException unavailable) {
                h.pending = false; h.done.countDown();
            }
        }
        // Hold the planner worker until the UI operation completes; ordinary writes cannot overtake it.
        try { h.done.await(10, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        synchronized (lifecycle) {
            h.pending = false;
            if (waiting == h) waiting = null;
            if (!valid(token)) return null;
            return h.outcome == null ? "Inte skapat: UI-åtgärden kunde inte startas. Granska på nytt." : h.outcome;
        }
    }
    private LocalV2.Selection find(UUID uid) throws java.io.IOException {
        LocalV2.Selection selected = store.load().select(uid);
        return selected.draft == null ? null : selected;
    }

    private boolean valid(long token) { synchronized (lifecycle) { return !closed && generation == token; } }
    private void post(long token, Runnable action) {
        host.post(() -> {
            if (!valid(token)) return;
            if (!host.available()) { cancel(); return; }
            action.run();
        });
    }
    private void result(String message) {
        replace(new AlertDialog.Builder(context).setTitle("Kartkopieprov · resultat").setMessage(message
                + "\n\nIngen feed vald av TMM. Feedpublicering och mottagande är obekräftade. Utkastet är fortsatt lokalt.")
                .setPositiveButton("Stäng", (d, w) -> cancel()).create());
    }
    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next; dialog.setCanceledOnTouchOutside(false); dialog.setOnCancelListener(d -> cancel()); dialog.show();
    }
    public void cancel() {
        synchronized (lifecycle) { invalidate(); busy = false; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }
    private void invalidate() {
        generation++;
        if (pending != null) { pending.close(); pending = null; }
        if (pendingUpdate != null) { pendingUpdate.close(); pendingUpdate = null; }
        if (waiting != null) { waiting.pending = false; waiting.done.countDown(); waiting = null; }
    }
    public void stop() { synchronized (lifecycle) { closed = true; invalidate(); } }
    public void close() { stop(); cancel(); }
    private static final class Handoff {
        final CountDownLatch done = new CountDownLatch(1);
        boolean pending = true;
        String outcome;
    }
}
