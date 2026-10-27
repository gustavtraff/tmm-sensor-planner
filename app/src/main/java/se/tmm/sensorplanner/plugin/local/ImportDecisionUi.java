package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.core.ImportDecisionPreparation.Field;

/** Exact import preview; default is memory-only, with an explicitly injected local commit endpoint. */
@TargetApi(26)
public final class ImportDecisionUi {
    public static final class Sources {
        public final InboxStore.Snapshot inbox;
        public final LocalV2.Snapshot local;
        public Sources(InboxStore.Snapshot inbox, LocalV2.Snapshot local) {
            this.inbox = Objects.requireNonNull(inbox); this.local = Objects.requireNonNull(local);
        }
    }
    public interface Host {
        void post(Runnable action);
        /** Fast thread-safe access/source/v2 policy; a context string alone is not authentication. */
        boolean available();
        /** Worker thread: genuine successfully loaded snapshots; never synthesize v2 from v1. */
        Sources load() throws Exception;
        /** Worker thread: local clock value captured for this preview, never clamped to remote/local times. */
        Instant editTime();
    }
    private final Context context;
    private final Executor worker;
    private final Host host;
    private final Function<Position, String> coordinates;
    private final ImportCommitUi commits;
    private final Object lifecycle = new Object();
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed, busy;
    private ImportDecisionPreparation pending;
    private ImportDecisionPreparation.Prepared prepared;
    private AlertDialog dialog;

    public ImportDecisionUi(Context context, Executor worker, Host host, Function<Position, String> coordinates) {
        this(context, worker, host, coordinates, null);
    }
    public ImportDecisionUi(Context context, Executor worker, Host host, Function<Position, String> coordinates, ImportCommitUi commits) {
        this.commits = commits;
        this.context = Objects.requireNonNull(context); this.worker = Objects.requireNonNull(worker);
        this.host = Objects.requireNonNull(host); this.coordinates = Objects.requireNonNull(coordinates);
    }
    public boolean isBusy() { return busy || commits != null && commits.isBusy(); }
    public boolean allowsLocalCommit() { return commits != null; }
    /** UI thread: preserve selection identity across the transition out of the read-only browser. */
    public void show(UUID inboxId, InboxStore.Receipt selected) {
        Objects.requireNonNull(inboxId); Objects.requireNonNull(selected);
        final long token;
        synchronized (lifecycle) {
            if (closed || isBusy()) return;
            busy = true; token = generation.incrementAndGet();
            if (!host.available()) { failure("Versionsbundet underlag eller källåtkomst saknas. Ingen förhandsvisning har skapats."); return; }
            progress("Läser underlag för importval");
        }
        worker.execute(() -> {
            try {
                if (!valid(token)) return;
                if (!host.available()) { post(token, this::cancel); return; }
                Sources sources = host.load();
                LocalV2Codec.encode(sources.local);
                InboxStore.Receipt current = selected(sources.inbox, inboxId, selected);
                LocalV2.State state = state(sources.local, current.incoming.uid);
                post(token, () -> choose(sources, current, state, token));
            } catch (Exception unavailable) { post(token, () -> failure("Underlaget eller vald mottagning kunde inte verifieras. Läs om och välj varianten på nytt.")); }
        });
    }
    private void choose(Sources sources, InboxStore.Receipt receipt, LocalV2.State state, long token) {
        if (state.presence != LocalV2.Presence.ACTIVE) {
            LinearLayout body = column();
            body.addView(text("UUID: " + receipt.incoming.uid + "\nMottaget ID: " + receipt.incoming.displayId
                    + (commits == null ? "\nEndast förhandsvisning. Inget sparas eller läggs till av detta flöde." : "\nGranska först. Inget sparas före uttrycklig bekräftelse.")));
            boolean deleted = state.presence == LocalV2.Presence.DELETED;
            CheckBox reAdd = new CheckBox(context);
            if (deleted) {
                body.addView(text("Denna UUID har en känd lokal radering. Ett tillägg är en uttrycklig återläggning."));
                reAdd.setText("Jag vill uttryckligen förhandsvisa återläggning efter lokal radering"); reAdd.setMinHeight(dp(48)); body.addView(reAdd);
            } else body.addView(text("UUID saknas sedan lagrets start/migrering. Det bevisar inte att enheten aldrig har funnits."));
            TextView error = text(""); body.addView(error);
            AlertDialog next = new AlertDialog.Builder(context).setTitle(deleted ? "Återläggning · inget sparas" : "Tillägg · inget sparas")
                    .setView(scroll(body)).setNegativeButton("Avbryt", (d, w) -> { if (dialog == d) cancel(); }).setPositiveButton("Förhandsvisa", null).create();
            replace(next);
            next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!active(token, next)) return;
                if (deleted && !reAdd.isChecked()) { error.setText("Bekräfta uttryckligen återläggning för att fortsätta."); return; }
                build(sources, receipt, LocalV2.Action.ADD_LOCAL, Collections.emptySet(), deleted && reAdd.isChecked(), token);
            });
            return;
        }
        if (ImportReview.assess(Collections.singletonList(state.draft), receipt.incoming).kind == ImportReview.Kind.SAME_METADATA) {
            info("Samma metadata", "Mottagna och lokala metadata är redan lika. Inget beslut behöver förberedas här. "
                    + "Lokal bild och revision ingår inte i metadatajämförelsen. Inget sparas."); return;
        }
        replace(new AlertDialog.Builder(context).setTitle("Välj importförslag · inget sparas")
                .setItems(new String[]{"Behåll lokalt", "Använd valda mottagna värden"}, (d, which) -> {
                    if (!active(token, d)) return;
                    if (which == 0) build(sources, receipt, LocalV2.Action.KEEP_LOCAL, Collections.emptySet(), false, token);
                    else fields(sources, receipt, token);
                }).setNegativeButton("Avbryt", (d, w) -> { if (dialog == d) cancel(); }).create());
    }
    private void fields(Sources sources, InboxStore.Receipt receipt, long token) {
        LinearLayout body = column();
        body.addView(text("Välj uttryckligen värden från denna enda mottagning. Position väljer latitud och longitud tillsammans. "
                + "Rensa tid/bäring är också ett uttryckligt fältval. Inget sparas."));
        Map<Field, CheckBox> checks = new LinkedHashMap<>();
        List<InboxReviewText.Row> rows = InboxReviewText.rows(null, receipt.incoming, coordinates);
        Field[] fields = Field.values();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i]; InboxReviewText.Row row = rows.get(rowIndex(field));
            String value = row.incoming;
            if (field == Field.DEPLOYED_AT && receipt.incoming.deployedAt == null) value = "Rensa utplaceringstid";
            if (field == Field.BEARING && receipt.incoming.bearingDeg == null) value = "Rensa bäring";
            CheckBox check = new CheckBox(context); check.setText(row.name + ": " + InboxReviewText.preview(value)); check.setMinHeight(dp(48));
            checks.put(field, check); body.addView(check);
        }
        TextView error = text(""); body.addView(error);
        AlertDialog next = new AlertDialog.Builder(context).setTitle("Välj mottagna fält").setView(scroll(body))
                .setNegativeButton("Till val", (d, w) -> { if (active(token, d)) choose(sources, receipt, state(sources.local, receipt.incoming.uid), token); })
                .setPositiveButton("Förhandsvisa", null).create();
        replace(next);
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!active(token, next)) return;
            Set<Field> selected = EnumSet.noneOf(Field.class);
            for (Map.Entry<Field, CheckBox> e : checks.entrySet()) if (e.getValue().isChecked()) selected.add(e.getKey());
            if (selected.isEmpty()) { error.setText("Välj minst ett mottaget fält."); return; }
            build(sources, receipt, LocalV2.Action.USE_SELECTED, selected, false, token);
        });
    }
    private void build(Sources sources, InboxStore.Receipt receipt, LocalV2.Action action, Set<Field> fields, boolean reAdd, long token) {
        progress("Förbereder exakt förhandsvisning");
        worker.execute(() -> {
            ImportDecisionPreparation review = null;
            try {
                if (!valid(token)) return;
                if (!host.available()) { post(token, this::cancel); return; }
                switch (action) {
                    case ADD_LOCAL: review = ImportDecisionPreparation.addLocally(sources.inbox, receipt.id, sources.local, reAdd); break;
                    case KEEP_LOCAL: review = ImportDecisionPreparation.keepLocal(sources.inbox, receipt.id, sources.local); break;
                    case USE_SELECTED: review = ImportDecisionPreparation.useSelected(sources.inbox, receipt.id, sources.local, fields, host.editTime()); break;
                    default: throw new IllegalArgumentException();
                }
                final ImportDecisionPreparation owned = review;
                synchronized (lifecycle) {
                    if (!valid(token)) { owned.close(); return; }
                    pending = owned;
                }
                post(token, () -> preview(owned, token));
            } catch (Exception rejected) {
                if (review != null) review.close();
                String message = rejection(rejected); post(token, () -> failure(message));
            }
        });
    }
    private void preview(ImportDecisionPreparation review, long token) {
        ImportDecisionPreparation.Plan plan = review.preview;
        LinearLayout body = summary(plan);
        replace(new AlertDialog.Builder(context).setTitle("Exakt importförslag · inget sparas").setView(scroll(body))
                .setNegativeButton("Avbryt", (d, w) -> { if (dialog == d) cancel(); })
                .setPositiveButton(commits == null ? "Förbered i minnet" : "Spara val lokalt", (d, w) -> {
                    synchronized (lifecycle) {
                        if (!active(token, d) || pending != review) return;
                        pending = null;
                    }
                    progress("Kontrollerar färskt underlag");
                    worker.execute(() -> prepare(review, token));
                }).create());
    }
    private void prepare(ImportDecisionPreparation review, long token) {
        try {
            if (!valid(token)) return;
            if (!host.available()) { post(token, this::cancel); return; }
            Sources fresh = host.load();
            if (!valid(token)) return;
            ImportDecisionPreparation.Prepared result = review.prepare(fresh.inbox, fresh.local);
            post(token, () -> {
                if (commits != null) { cancel(); commits.commit(result); return; }
                prepared = result;
                LinearLayout body = summary(result.plan);
                body.addView(text("Förberett i minnet. Inget har sparats eller applicerats. Ingen historik eller kvittens har skrivits. "
                        + "Resultatet släpps när du stänger. En framtida lagringsåtgärd måste kontrollera aktuellt tillstånd igen under rätt lås."));
                replace(new AlertDialog.Builder(context).setTitle("Importbeslut förberett i minnet").setView(scroll(body))
                        .setPositiveButton("Stäng", (d, w) -> { if (dialog == d) cancel(); }).create());
            });
        } catch (Exception stale) { post(token, () -> failure("Ingen förberedelse slutförd: underlaget har ändrats eller kunde inte verifieras. Läs om och granska på nytt.")); }
        finally { review.close(); }
    }
    private LinearLayout summary(ImportDecisionPreparation.Plan p) {
        LinearLayout body = column();
        body.addView(text((commits == null ? "Endast förhandsvisning/minnesförberedelse – ingen applicering." : "Granskat lokalt val – sparas först när du bekräftar Spara val lokalt.") + "\nFörslag: " + effect(p.effect)
                + (p.isReAdd() ? "\nUttrycklig återläggning efter känd lokal radering." : "")
                + "\nUUID: " + p.receipt.incoming.uid + "\nKällkontext: " + p.receipt.contextId
                + "\nFörst registrerad lokalt UTC: " + p.receipt.firstObservedAt + "\nMottagnings-ID: " + p.receipt.id
                + "\nInkorg-ID: " + p.inboxId + "\nLokalt lager-ID: " + p.storeId
                + "\nGranskad tillståndsversion: " + p.reviewed.version + "\nFöreslagen tillståndsversion: " + p.result.version));
        if (p.editTime != null) body.addView(text("Föreslagen lokal ändringstid UTC: " + p.editTime + ". Den byts inte vid förberedelse eller sparning."));
        else body.addView(text(p.action == LocalV2.Action.ADD_LOCAL ? "Mottagna skapande-/ändringstider behålls; de är inte lokal författaridentitet." : "Ingen lokal ändringstid föreslås."));
        List<String> selected = new ArrayList<>();
        for (Field f : Field.values()) if ((p.fieldMask & f.mask) != 0) selected.add(fieldName(f));
        body.addView(text("Valda mottagna fält: " + (selected.isEmpty() ? "Inga" : String.join(", ", selected))));
        if (p.result.draft != null) {
            body.addView(text("Föreslagen lokal revision: " + p.result.draft.revision
                    + (p.result.draft.imageRef == null ? "\nIngen bild läggs till från mottagna metadata." : "\nDen lokala bildassociationen bevaras; bildfilen har inte kontrollerats.")));
            for (InboxReviewText.Row row : InboxReviewText.rows(p.reviewed.draft, p.result.draft, coordinates)) {
                body.addView(text((row.changed ? "Föreslagen ändring: " : "") + row.name
                        + (p.reviewed.draft == null ? "" : "\nLokalt i underlaget:\n" + row.local) + "\nFöreslaget:\n" + row.incoming));
            }
        }
        body.addView(text("Detta är ett bundet förslag från underlaget, inte ett livevärde eller en sparad ändring. "
                + "Källauktoritet avgörs av anroparen, inte av ägartext eller källkontextens namn."));
        return body;
    }
    private static int rowIndex(Field f) {
        switch (f) {
            case DISPLAY_ID: return 0; case TYPE: return 1; case STATUS: return 2; case OWNER: return 3;
            case COMMENT: return 7; case DEPLOYED_AT: return 6; case POSITION: return 8; case BEARING: return 9;
            default: throw new IllegalArgumentException("Unsupported preview field");
        }
    }
    private static String fieldName(Field f) {
        switch (f) {
            case DISPLAY_ID: return "ID"; case TYPE: return "Typ"; case STATUS: return "Status"; case OWNER: return "Ägare";
            case COMMENT: return "Kommentar"; case DEPLOYED_AT: return "Utplacerad UTC"; case POSITION: return "Position"; case BEARING: return "Bäring";
            default: return f.name();
        }
    }
    private static String effect(ImportDecisionPreparation.Effect effect) {
        switch (effect) {
            case ADD_LOCAL: return "Lägg till lokalt"; case UPDATE_LOCAL: return "Använd valda mottagna värden";
            case KEEP_LOCAL: return "Behåll lokalt – ingen utkaständring föreslås"; case ALREADY_EQUAL: return "Redan samma metadata – ingen ändring";
            default: return effect.name();
        }
    }
    private static String rejection(Exception e) {
        String m = e.getMessage();
        if ("Received creation identity differs; review identity".equals(m)) return "Skapandetiden skiljer. Identiteten behöver granskas separat; mottagna värden kan inte användas här.";
        if ("Local edit time required".equals(m) || "edit time cannot precede the previous update".equals(m)) return "Lokal ändringstid saknas eller klockan ligger före senaste lokala ändring. Kontrollera klockan och granska på nytt.";
        if ("bearingDeg requires PIR/Scout and a finite value in [0, 360)".equals(m)) return "Vald typ och bäring passar inte ihop. Om bäring ska tas bort måste även Rensa bäring väljas uttryckligen.";
        return "Förhandsvisningen kunde inte skapas. Kontrollera fältval, lokalt tillstånd och tid; läs om underlaget. Inget har sparats.";
    }
    private static LocalV2.State state(LocalV2.Snapshot local, UUID uid) {
        for (LocalV2.State s : local.slots) if (s.uid.equals(uid)) return s;
        return LocalV2.State.neverSeen(uid);
    }
    private static InboxStore.Receipt selected(InboxStore.Snapshot inbox, UUID id, InboxStore.Receipt expected) {
        if (!inbox.inboxId.equals(id)) throw new IllegalStateException();
        for (InboxStore.Receipt r : inbox.receipts) if (r.id.equals(expected.id)) {
            if (!r.contextId.equals(expected.contextId) || !r.firstObservedAt.equals(expected.firstObservedAt)
                    || !r.incoming.uid.equals(expected.incoming.uid)
                    || ImportReview.assess(Collections.singletonList(expected.incoming), r.incoming).kind != ImportReview.Kind.SAME_METADATA) throw new IllegalStateException();
            return r;
        }
        throw new IllegalStateException();
    }
    private boolean valid(long token) { return !closed && generation.get() == token; }
    private boolean active(long token, android.content.DialogInterface d) { return valid(token) && dialog == d; }
    private void post(long token, Runnable action) {
        host.post(() -> {
            synchronized (lifecycle) {
                if (!valid(token)) return;
                if (!host.available()) { cancel(); return; }
                action.run();
            }
        });
    }
    private void progress(String title) { replace(new AlertDialog.Builder(context).setTitle(title).setMessage(commits == null ? "Inget sparas eller appliceras." : "Inget har sparats i detta steg. Ett val sparas först efter uttrycklig bekräftelse och nya kontroller.").setNegativeButton("Avbryt", (d, w) -> { if (dialog == d) cancel(); }).create()); }
    private void failure(String message) { info("Importförslag kunde inte förberedas", message); }
    private void info(String title, String message) {
        replace(new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Stäng", (d, w) -> { if (dialog == d) cancel(); }).create());
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value) { TextView t = new TextView(context); t.setText(value); t.setTextSize(16); t.setPadding(dp(16), dp(8), dp(16), dp(8)); return t; }
    private ScrollView scroll(LinearLayout v) { ScrollView scroll = new ScrollView(context); scroll.addView(v); return scroll; }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss(); dialog = next;
        next.setCanceledOnTouchOutside(false); next.setOnCancelListener(d -> { if (dialog == d) cancel(); }); next.show();
    }
    public void cancel() {
        synchronized (lifecycle) { generation.incrementAndGet(); if (pending != null) pending.close(); pending = null; prepared = null; busy = false; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (commits != null) commits.cancel();
    }
    public void stop() { synchronized (lifecycle) { closed = true; generation.incrementAndGet(); if (pending != null) pending.close(); pending = null; prepared = null; } if (commits != null) commits.stop(); }
    public void close() { stop(); cancel(); }
}
