package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import se.tmm.sensorplanner.core.*;

/** Private in-memory preparation only; no file, MapItem, feed or transport operation. */
@TargetApi(26)
public final class PublicationPreparationUi {
    public interface Host {
        void post(Runnable action);
        boolean available();
        String summary(ShareReview.Metadata metadata);
    }

    private final Context context;
    private final LocalV2Store store;
    private final Executor worker;
    private final Host host;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;
    private volatile boolean busy;
    private volatile PublicationPreparationReview pending;
    private ShareReview.Metadata prepared;
    private AlertDialog dialog;

    public PublicationPreparationUi(Context context, LocalV2Store store, Executor worker, Host host) {
        this.context = context; this.store = store; this.worker = worker; this.host = host;
    }

    public boolean isBusy() { return busy; }

    /** UI thread; read and confirmation work use the same worker as edits/deletes. */
    public void show(UUID uid) {
        if (closed || busy || !host.available()) return;
        busy = true;
        long token = generation.incrementAndGet();
        replace(new AlertDialog.Builder(context).setTitle("Läser sparad enhet")
                .setMessage("Ingen feed väljs och inget skickas. Granska först den sparade informationen.")
                .setNegativeButton("Avbryt", (d, w) -> cancel()).create());
        worker.execute(() -> {
            if (!valid(token)) return;
            try {
                LocalV2.Selection selected = find(uid);
                if (selected == null) throw new IllegalStateException();
                post(token, () -> review(selected, token));
            } catch (Exception unavailable) {
                post(token, () -> failure("Den sparade enheten kunde inte läsas. Läs om enheten och försök igen."));
            }
        });
    }

    private void review(LocalV2.Selection selected, long token) {
        TmmDraft saved = selected.draft;
        PublicationPreparationReview review = new PublicationPreparationReview(saved);
        pending = review;
        replace(new AlertDialog.Builder(context).setTitle("Granska metadatakopia")
                .setView(scrolling(host.summary(review.metadata) + imageText(review.imageStatus)
                        + "\n\nIngen feed vald. Inte skickat av TMM. Kopian förbereds enbart i minnet "
                        + "och försvinner när du stänger granskningen eller avlastar pluginet."))
                .setNegativeButton("Avbryt", (d, w) -> cancel())
                .setPositiveButton("Förbered lokalt", (d, w) -> {
                    if (!valid(token) || pending != review) return;
                    if (!host.available()) { cancel(); return; }
                    pending = null;
                    worker.execute(() -> prepare(review, selected, token));
                }).create());
    }

    private void prepare(PublicationPreparationReview review, LocalV2.Selection selected, long token) {
        try {
            if (!valid(token)) return;
            if (!host.available()) { post(token, this::cancel); return; }
            LocalV2.Snapshot snapshot = store.load();
            if (!selected.isCurrent(snapshot)) throw new IllegalStateException();
            TmmDraft current = snapshot.state(review.metadata.uid).draft;
            if (!valid(token)) return;
            byte[] bytes = PublicationCodec.encode(review.prepare(current));
            ShareReview.Metadata decoded = PublicationCodec.decode(new ByteArrayInputStream(bytes));
            post(token, () -> {
                prepared = decoded;
                replace(new AlertDialog.Builder(context).setTitle("Metadatakopia förberedd")
                        .setView(scrolling(host.summary(prepared) + imageText(review.imageStatus)
                                + "\n\nFörberedd i minnet. Ingen feed vald. Inte skickat av TMM. "
                                + "Kopian försvinner när du stänger. Ingen fil har skapats."))
                        .setPositiveButton("Stäng", (d, w) -> cancel()).create());
            });
        } catch (Exception unavailable) {
            post(token, () -> failure("Ingen kopia har förberetts. Enheten har ändrats, tagits bort eller kunde inte "
                    + "läsas/verifieras. Läs om enheten och granska på nytt."));
        } finally { review.close(); }
    }

    private LocalV2.Selection find(UUID uid) throws java.io.IOException {
        LocalV2.Selection selected = store.load().select(uid);
        return selected.draft == null ? null : selected;
    }

    private boolean valid(long token) { return !closed && generation.get() == token; }
    private void post(long token, Runnable action) {
        host.post(() -> {
            if (!valid(token)) return;
            if (!host.available()) { cancel(); return; }
            action.run();
        });
    }
    private String imageText(ShareReview.ImageStatus image) {
        return image == ShareReview.ImageStatus.LOCAL_IMAGE_NOT_SHARED ? "\n\nBilden delas inte."
                : "\n\nIngen lokal bild är bifogad. Ingen bild delas.";
    }
    private ScrollView scrolling(String message) {
        TextView text = new TextView(context); text.setText(message); text.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(context); scroll.addView(text); return scroll;
    }
    private void failure(String message) {
        prepared = null;
        replace(new AlertDialog.Builder(context).setTitle("Kopian kunde inte förberedas").setMessage(message)
                .setPositiveButton("Stäng", (d, w) -> cancel()).create());
    }
    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next; dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> cancel()); dialog.show();
    }

    /** UI thread; invalidate callbacks as well as the pending core review. */
    public void cancel() {
        generation.incrementAndGet();
        PublicationPreparationReview review = pending; pending = null;
        if (review != null) review.close();
        prepared = null; busy = false;
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    /** May be called before the UI-thread close during plugin shutdown. */
    public void stop() {
        closed = true; generation.incrementAndGet();
        PublicationPreparationReview review = pending;
        if (review != null) review.close();
    }

    /** UI thread; no background work can publish a result after stop. */
    public void close() { stop(); cancel(); }
}
