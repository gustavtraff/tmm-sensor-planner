package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import java.util.UUID;
import java.util.concurrent.Executor;
import se.tmm.sensorplanner.core.*;

/** Optional local-only commit endpoint, called after the exact preview's explicit Save confirmation. */
@TargetApi(26)
public final class ImportCommitUi {
    public interface Host {
        void post(Runnable action);
        /** Fast thread-safe current session/local access policy. */
        boolean available();
        /** Fast thread-safe trusted source policy, checked again immediately before a new attempt. */
        boolean sourceAvailable(UUID inboxId, InboxStore.Receipt receipt);
        /** UI thread: enqueue a storage refresh after known commit; never broadcast or infer delivery. */
        void changed(UUID target);
    }
    private final Context context;
    private final Executor worker;
    private final Host host;
    private final LocalV2Store local;
    private final InboxStore inbox;
    private final Object lifecycle = new Object();
    private long generation;
    private boolean closed;
    private volatile boolean busy;
    private ImportDecisionPreparation.Prepared command;
    private boolean notified;
    private AlertDialog dialog;

    public ImportCommitUi(Context context, Executor worker, Host host, LocalV2Store local, InboxStore inbox) {
        this.context = java.util.Objects.requireNonNull(context); this.worker = java.util.Objects.requireNonNull(worker);
        this.host = java.util.Objects.requireNonNull(host); this.local = java.util.Objects.requireNonNull(local);
        this.inbox = java.util.Objects.requireNonNull(inbox);
    }
    public boolean isBusy() { return busy; }

    /** UI thread, once per explicit confirmation. Prepared remains memory-only and is discarded on close. */
    public void commit(ImportDecisionPreparation.Prepared prepared) {
        java.util.Objects.requireNonNull(prepared);
        final long token;
        synchronized (lifecycle) {
            if (closed || busy || !host.available()) return;
            command = prepared; notified = false; busy = true; token = ++generation;
        }
        attempt(prepared, token);
    }
    private void attempt(ImportDecisionPreparation.Prepared prepared, long token) {
        synchronized (lifecycle) { if (!valid(token)) return; progress("Sparar granskat val lokalt"); }
        worker.execute(() -> {
            try {
                if (!valid(token)) return;
                if (!host.available()) { post(token, this::cancel); return; }
                if (!sourceAvailable(prepared)) {
                    post(token, () -> info("Källåtkomst saknas", "Ingen ny sparning utfördes. Källåtkomsten kunde inte verifieras. Läs om och granska på nytt.")); return;
                }
                if (!valid(token)) return;
                LocalV2Store.Result result = local.commit(inbox, prepared);
                post(token, () -> {
                    if (result.effect == LocalV2Store.Effect.ALREADY_EQUAL) {
                        info("Samma metadata", "Ingen utkaständring eller ny beslutshistorik sparades. Ingen bild ändrades. Inget delades.");
                    } else known(result.decision, result.current, result.effect == LocalV2Store.Effect.ALREADY_COMMITTED);
                });
            } catch (LocalV2Store.Failure failure) {
                post(token, () -> {
                    switch (failure.reason) {
                        case BUSY: info("Lagringen är upptagen", "Försöket kunde inte starta. Läs om och granska på nytt; inget återförsöks automatiskt."); break;
                        case STALE: info("Granskningen är inaktuell", "Underlaget eller det lokala tillståndet har ändrats. Dina val har inte applicerats på en nyare version. Läs om och granska på nytt."); break;
                        case INVALID: case CAPACITY:
                            info("Valet kunde inte sparas", "Valet eller lagringsgränsen tillåter inte ändringen. Inget sparades i detta försök. Befintliga enheter och historik behålls; ingen automatisk rensning görs."); break;
                        default: unknown(prepared, token);
                    }
                });
            } catch (Exception unavailable) { post(token, () -> unknown(prepared, token)); }
        });
    }
    private void unknown(ImportDecisionPreparation.Prepared prepared, long token) {
        replace(new AlertDialog.Builder(context).setTitle("Sparandet är obekräftat")
                .setMessage("Beslutet kan redan vara sparat. Kontrollera samma beslut i det lokala lagret innan ett nytt försök. "
                        + "Ingen återställning eller automatisk upprepning görs. Ingen feed eller leverans har bekräftats.")
                .setNegativeButton("Stäng", (d,w) -> { if (active(token,d)) cancel(); })
                .setPositiveButton("Kontrollera sparat beslut", (d,w) -> { if (active(token,d)) readback(prepared, token); }).create());
    }
    private void readback(ImportDecisionPreparation.Prepared prepared, long token) {
        progress("Läser lokal beslutshistorik");
        worker.execute(() -> {
            if (!valid(token)) return;
            if (!host.available()) { post(token, this::cancel); return; }
            LocalV2Store.Readback found = local.readback(prepared);
            post(token, () -> {
                switch (found.status) {
                    case COMMITTED: known(found.decision, found.current, true); break;
                    case NOT_COMMITTED:
                        AlertDialog.Builder next = new AlertDialog.Builder(context).setTitle("Beslutet är inte sparat")
                                .setMessage("Ett giltigt lokalt lager med samma identitet saknar detta beslut. "
                                        + "Ett uttryckligt nytt försök använder samma beslut och kontrollerar allt på nytt. "
                                        + "Ändrade uppgifter kräver en ny granskning.")
                                .setNegativeButton("Stäng", (d,w) -> { if (active(token,d)) cancel(); });
                        if (sourceAvailable(prepared)) {
                            next.setPositiveButton("Försök samma val igen", (d,w) -> {
                                if (!active(token,d)) return;
                                final long retry;
                                synchronized (lifecycle) { if (!valid(token)) return; retry = ++generation; }
                                attempt(prepared, retry);
                            });
                        }
                        replace(next.create()); break;
                    default: unknown(prepared, token);
                }
            });
        });
    }
    private void known(LocalV2.Decision decision, LocalV2.State current, boolean historical) {
        String title = historical ? "Tidigare lokalt beslut verifierat"
                : decision.outcome == LocalV2.Outcome.KEPT ? "Lokalt val registrerat"
                : decision.outcome == LocalV2.Outcome.ADDED ? "Enheten tillagd lokalt" : "Valda värden sparade lokalt";
        String text = "Sparat beslut: " + (decision.outcome == LocalV2.Outcome.ADDED ? "Tillagd lokalt" : decision.outcome == LocalV2.Outcome.UPDATED ? "Valda värden sparade lokalt" : "Lokalt val registrerat") + "\nBesluts-ID: " + decision.id
                + "\nUUID: " + decision.target + "\nDåvarande tillståndsversion: " + decision.afterVersion
                + " · revision " + decision.afterRevision + "\n\nLokalt tillstånd vid kontrollen: ";
        if (current.presence == LocalV2.Presence.DELETED) text += "Raderat lokalt · version " + current.version;
        else text += current.draft.displayId + " · " + InboxReviewText.status(current.draft.status) + "\nAktivt lokalt · version " + current.version + " · revision " + current.draft.revision
                + (current.version == decision.afterVersion ? "\nBeslutets resultatversion är fortfarande aktuell." : "\nDet lokala tillståndet har ändrats efter beslutet.");
        text += "\n\nIngen bild importerades eller rensades. Inget delades, ingen kvittens skickades och ingen konflikt avgjordes globalt.";
        long token = generation;
        if (!notified) {
            notified = true;
            try { host.changed(decision.target); }
            catch (RuntimeException refreshFailed) { text += "\nVyn kunde inte uppdateras. Öppna den lokala listan igen."; }
        }
        if (valid(token)) info(title, text);
    }
    private boolean sourceAvailable(ImportDecisionPreparation.Prepared prepared) {
        try { return host.sourceAvailable(prepared.plan.inboxId, prepared.plan.receipt); }
        catch (RuntimeException unavailable) { return false; }
    }
    private void progress(String title) {
        final long token;
        synchronized (lifecycle) { token = generation; }
        replace(new AlertDialog.Builder(context).setTitle(title).setMessage("Endast lokal lagring. Resultatet måste bekräftas av lagret.")
                .setNegativeButton("Stäng vyn", (d,w) -> { if (active(token,d)) cancel(); }).create());
    }
    private void info(String title, String message) {
        final long token;
        synchronized (lifecycle) { token = generation; }
        replace(new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Stäng", (d,w) -> { if (active(token,d)) cancel(); }).create());
    }
    private boolean valid(long token) { synchronized (lifecycle) { return !closed && generation == token && command != null; } }
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
    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss(); dialog = next;
        next.setCanceledOnTouchOutside(false); next.setOnCancelListener(d -> { if (dialog == d) cancel(); }); next.show();
    }
    public void cancel() {
        synchronized (lifecycle) { generation++; command = null; busy = false; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }
    public void stop() { synchronized (lifecycle) { closed = true; generation++; command = null; } }
    public void close() { stop(); cancel(); }
}
