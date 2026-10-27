package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import se.tmm.sensorplanner.core.*;

/** Reviewed saved-state handoff; caller keeps normal edits busy-blocked on the same worker. */
@TargetApi(26)
public final class SavedDraftCopyUi {
    public interface Host {
        void post(Runnable action);
        boolean available();
        String summary(ShareReview.Metadata metadata);
        /** UI thread; must enforce lab guards and return an honest host-copy outcome. */
        String create(PublicationPreparationReview.Prepared prepared, String label);
    }
    private final Context context;
    private final DraftStore store;
    private final Executor worker;
    private final Host host;
    private final Object lifecycle = new Object();
    private volatile boolean busy;
    private boolean closed;
    private long generation;
    private PublicationPreparationReview pending;
    private Handoff waiting;
    private AlertDialog dialog;

    public SavedDraftCopyUi(Context context, DraftStore store, Executor worker, Host host) {
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
                TmmDraft saved = find(uid);
                if (saved == null) throw new IllegalStateException();
                post(token, () -> review(saved, token));
            } catch (Exception unavailable) { post(token, () -> result("Inte skapat: den sparade enheten kunde inte läsas. Granska på nytt.")); }
        });
    }
    private void review(TmmDraft saved, long token) {
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
                + "En avvikande befintlig kartkopia med samma UUID skrivs inte över.";
        TextView text = new TextView(context); text.setText(host.summary(review.metadata) + warning); text.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(context); scroll.addView(text);
        replace(new AlertDialog.Builder(context).setTitle("Granska kartkopia · isolerat labb").setView(scroll)
                .setNegativeButton("Avbryt", (d, w) -> cancel())
                .setPositiveButton("Skapa kartkopia i labbet", (d, w) -> {
                    synchronized (lifecycle) {
                        if (!valid(token) || pending != review) return;
                        pending = null;
                    }
                    replace(new AlertDialog.Builder(context).setTitle("Kontrollerar sparad enhet")
                            .setMessage("Ingen feed vald. Åtgärden kan avbrytas innan SDK-importen börjar.")
                            .setNegativeButton("Avbryt", (ignored, which) -> cancel()).create());
                    worker.execute(() -> prepare(review, label, token));
                }).create());
    }
    private void prepare(PublicationPreparationReview review, String label, long token) {
        try {
            if (!valid(token)) return;
            TmmDraft current = find(review.metadata.uid);
            if (!valid(token)) return;
            PublicationPreparationReview.Prepared prepared = review.prepare(current);
            PublicationCodec.decode(new ByteArrayInputStream(PublicationCodec.encode(prepared)));
            String outcome = handoff(prepared, label, token);
            if (outcome != null) post(token, () -> result(outcome));
        } catch (Exception unavailable) {
            post(token, () -> result("Inte skapat: enheten har ändrats, tagits bort eller kunde inte verifieras. Läs om och granska på nytt."));
        } finally { review.close(); }
    }
    private String handoff(PublicationPreparationReview.Prepared prepared, String label, long token) {
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
                            h.outcome = host.available() ? java.util.Objects.requireNonNull(host.create(prepared, label))
                                    : "Inte skapat: labbvillkoren gäller inte längre. Granska på nytt.";
                        } catch (RuntimeException uncertain) {
                            h.outcome = "Skapande försökt – resultat oklart. Kontrollera kartan och feeds före nytt försök. Ingen automatisk återställning görs.";
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
    private TmmDraft find(UUID uid) throws java.io.IOException {
        for (TmmDraft draft : store.load()) if (draft.uid.equals(uid)) return draft;
        return null;
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
