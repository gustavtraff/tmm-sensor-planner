package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import se.tmm.sensorplanner.core.*;

/** Read-only supplied-snapshot browser; no storage, source authentication, apply or transport capability. */
@TargetApi(26)
public final class InboxReviewUi {
    private final Context context;
    private final Function<Position, String> coordinates;
    private final ImportDecisionUi decisions;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;
    private InboxReview snapshot;
    private AlertDialog dialog;

    /** coordinates is the caller's MGRS formatter, not a store, image resolver or navigation callback. */
    public InboxReviewUi(Context context, Function<Position, String> coordinates) {
        this(context, coordinates, null);
    }
    /** Optional memory-preview transition only; default construction stays strictly read-only. */
    public InboxReviewUi(Context context, Function<Position, String> coordinates, ImportDecisionUi decisions) {
        this.context = Objects.requireNonNull(context); this.coordinates = Objects.requireNonNull(coordinates); this.decisions = decisions;
    }
    /** UI thread; caller must first load both stores successfully and assemble a validated review. */
    public void show(InboxReview review) {
        Objects.requireNonNull(review);
        if (closed) return;
        snapshot = review;
        groups(generation.incrementAndGet());
    }
    private void groups(long token) {
        AlertDialog.Builder b = new AlertDialog.Builder(context).setTitle("Mottagna metadata · skrivskyddad granskning");
        if (snapshot.groups.isEmpty()) b.setMessage("Underlaget innehåller inga mottagna metadata. "
                + "Detta är en ögonblicksbild, inte en livevy. Inga enheter läggs till eller ändras här.");
        else {
            String[] labels = new String[snapshot.groups.size()];
            for (int i = 0; i < labels.length; i++) {
                InboxReview.Group g = snapshot.groups.get(i);
                String id = g.currentLocal == null ? g.variants.get(0).receipt.incoming.displayId : g.currentLocal.displayId;
                labels[i] = InboxReviewText.preview(id) + "\nUUID: " + g.uid + "\n" + g.variants.size() + " mottagna varianter";
            }
            b.setItems(labels, (d, which) -> { if (active(token, d)) variants(snapshot.groups.get(which), token); });
        }
        b.setNegativeButton("Stäng", (d, w) -> { if (active(token, d)) cancel(); });
        replace(b.create(), token, this::cancel);
    }
    private void variants(InboxReview.Group group, long token) {
        String[] labels = new String[group.variants.size()];
        for (int i = 0; i < labels.length; i++) {
            InboxReview.Variant v = group.variants.get(i);
            labels[i] = "Variant " + (i + 1) + " · " + InboxReviewText.kind(v.kind)
                    + "\nID: " + InboxReviewText.preview(v.receipt.incoming.displayId)
                    + "\nRegistrerad UTC: " + v.receipt.firstObservedAt
                    + "\nKällkontext: " + InboxReviewText.preview(v.receipt.contextId);
        }
        replace(new AlertDialog.Builder(context).setTitle("Varianter · " + group.uid)
                .setItems(labels, (d, which) -> { if (active(token, d)) details(group, group.variants.get(which), token); })
                .setNegativeButton("Till enheter", (d, w) -> { if (active(token, d)) groups(token); })
                .setPositiveButton("Stäng", (d, w) -> { if (active(token, d)) cancel(); }).create(), token, () -> groups(token));
    }
    private void details(InboxReview.Group group, InboxReview.Variant variant, long token) {
        LinearLayout content = column();
        content.addView(text(InboxReviewText.kind(variant.kind), true));
        content.addView(text("Ögonblicksbild av tillhandahållet underlag. Vyn uppdateras inte automatiskt. "
                + "Inga värden appliceras här. Ordning och tidsstämplar utser ingen vinnare.", false));
        content.addView(text("UUID: " + group.uid + "\nMottaget ID: " + variant.receipt.incoming.displayId
                + "\nTyp: " + variant.receipt.incoming.type.displayName + " · " + InboxReviewText.status(variant.receipt.incoming.status), false));
        if (group.currentLocal == null) content.addView(text("Ingen lokal enhet med denna UUID i underlaget. "
                + "Det visar inte om den tidigare har raderats och tillåter inte automatisk återläggning.", false));
        else content.addView(text("Lokalt vid bedömning: " + group.currentLocal.displayId
                + "\nLokal revision: " + group.currentLocal.revision + " (lokal skrivräknare)"
                + (group.currentLocal.imageRef == null ? "\nIngen lokal bildassociation i underlaget."
                        : "\nLokal bildassociation finns; bildfilen har inte kontrollerats."), false));
        if (group.currentLocal != null && !group.currentLocal.createdAt.equals(variant.receipt.incoming.createdAt)) {
            content.addView(text("Skapandetiden skiljer. Identiteten behöver granskas separat före eventuell applicering.", true));
        }
        content.addView(text("Mottagna metadata innehåller ingen bild. Den lokala bildassociationen ändras inte. "
                + "Metadatajämförelsen omfattar inte lokal bild eller revision.", false));
        List<InboxReviewText.Row> rows = InboxReviewText.rows(group.currentLocal, variant.receipt.incoming, coordinates);
        LinearLayout unchanged = column(); int same = 0;
        for (InboxReviewText.Row r : rows) {
            LinearLayout target = group.currentLocal != null && !r.changed ? unchanged : content;
            if (target == unchanged) same++;
            target.addView(text((r.changed ? "Ändrat: " : "") + r.name, true));
            if (group.currentLocal != null) target.addView(text("Lokalt i underlaget:\n" + r.local, false));
            target.addView(text("Mottaget:\n" + r.incoming, false));
        }
        if (same > 0) {
            Button expand = new Button(context); expand.setMinHeight(dp(48));
            final String caption = "Visa oförändrade metadatafält (" + same + ")";
            expand.setText(caption); unchanged.setVisibility(android.view.View.GONE);
            expand.setOnClickListener(v -> {
                if (!current(token)) return;
                boolean show = unchanged.getVisibility() != android.view.View.VISIBLE;
                unchanged.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
                expand.setText(show ? "Dölj oförändrade metadatafält" : caption);
            });
            content.addView(expand); content.addView(unchanged);
        }
        content.addView(text("Först registrerad lokalt UTC: " + variant.receipt.firstObservedAt
                + "\nRegistrerad källkontext: " + variant.receipt.contextId
                + "\nMottagnings-ID: " + variant.receipt.id
                + "\nKällkontexten autentiseras inte av vyn. Ägare är redigerbar metadata, inte verifierad avsändaridentitet.", false));
        Button preview = null;
        if (decisions != null) {
            preview = new Button(context); preview.setText(decisions.allowsLocalCommit() ? "Granska importval · sparas först efter bekräftelse" : "Förhandsvisa importval · inget sparas"); preview.setMinHeight(dp(48)); content.addView(preview);
        }
        AlertDialog next = new AlertDialog.Builder(context).setTitle("Jämför variant · skrivskyddat").setView(scroll(content))
                .setNegativeButton("Till varianter", (d, w) -> { if (active(token, d)) variants(group, token); })
                .setNeutralButton("Historik (" + variant.receipt.localObservations.size() + ")", (d, w) -> {
                    if (active(token, d)) history(group, variant, token);
                }).setPositiveButton("Stäng", (d, w) -> { if (active(token, d)) cancel(); }).create();
        if (preview != null) preview.setOnClickListener(v -> {
            if (!active(token, next)) return;
            UUID inboxId = snapshot.inboxId; InboxStore.Receipt receipt = variant.receipt;
            cancel(); decisions.show(inboxId, receipt);
        });
        replace(next, token, () -> variants(group, token));
    }
    private void history(InboxReview.Group group, InboxReview.Variant variant, long token) {
        List<TmmDraft> observations = variant.receipt.localObservations;
        String[] labels = new String[observations.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = "Observation " + (i + 1) + " · "
                + (observations.get(i) == null ? "Lokalt observerades frånvaro" : "Lokal metadata, revision " + observations.get(i).revision);
        replace(new AlertDialog.Builder(context).setTitle("Historik · inte nuläge")
                .setItems(labels, (d, which) -> { if (active(token, d)) observation(group, variant, observations.get(which), token); })
                .setNegativeButton("Till jämförelsen", (d, w) -> { if (active(token, d)) details(group, variant, token); })
                .create(), token, () -> details(group, variant, token));
    }
    private void observation(InboxReview.Group group, InboxReview.Variant variant, TmmDraft observed, long token) {
        LinearLayout content = column();
        content.addView(text("Historisk lokal observation – inte aktuellt lokalt tillstånd. "
                + "Ingen separat observationstid är registrerad. Historiken sparar inga bildreferenser eller bildbevis.", true));
        if (observed == null) content.addView(text("Lokalt observerades frånvaro. Detta bevisar inte att enheten aldrig funnits och är inte nuläget.", false));
        else {
            content.addView(text("Historisk lokal revision: " + observed.revision, false));
            for (InboxReviewText.Row row : InboxReviewText.rows(null, observed, coordinates)) content.addView(text(row.name + ":\n" + row.incoming, false));
        }
        replace(new AlertDialog.Builder(context).setTitle("Historisk metadataobservation").setView(scroll(content))
                .setPositiveButton("Till historiken", (d, w) -> { if (active(token, d)) history(group, variant, token); })
                .create(), token, () -> history(group, variant, token));
    }
    private boolean current(long token) { return !closed && generation.get() == token && snapshot != null; }
    private boolean active(long token, DialogInterface from) { return current(token) && dialog == from; }
    private void replace(AlertDialog next, long token, Runnable back) {
        if (!current(token)) return;
        if (dialog != null) dialog.dismiss();
        dialog = next; dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> { if (active(token, d)) back.run(); }); dialog.show();
    }
    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL); return column;
    }
    private TextView text(String value, boolean heading) {
        TextView text = new TextView(context); text.setText(value); text.setTextSize(16); text.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (heading) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return text;
    }
    private ScrollView scroll(LinearLayout content) { ScrollView scroll = new ScrollView(context); scroll.addView(content); return scroll; }
    private int dp(int size) { return Math.round(size * context.getResources().getDisplayMetrics().density); }
    /** UI thread; releases references without changing any receipt, history or draft. */
    public void cancel() { generation.incrementAndGet(); snapshot = null; if (dialog != null) { dialog.dismiss(); dialog = null; } }
    /** Invalidates old callbacks before UI-thread disposal; no data or selection is resumed. */
    public void stop() { closed = true; generation.incrementAndGet(); }
    public void close() { stop(); cancel(); }
}
