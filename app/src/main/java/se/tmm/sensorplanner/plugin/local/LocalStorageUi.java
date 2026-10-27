package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import java.util.concurrent.Executor;
import se.tmm.sensorplanner.core.*;

/** Explicit local-format activation only, serialized with all planner writes. */
@TargetApi(26)
public final class LocalStorageUi {
    public interface Host {
        boolean available();
        void post(Runnable action);
        void loaded(LocalV2Store.Inspection storage, boolean showList);
        void unavailable(String message, boolean interactive);
    }
    private final Context context;
    private final LocalV2Store store;
    private final Executor worker;
    private final Host host;
    private volatile long generation;
    private volatile boolean stopped;
    private boolean busy;
    private boolean interactive;
    private AlertDialog dialog;

    public LocalStorageUi(Context context, LocalV2Store store, Executor worker, Host host) {
        this.context = context; this.store = store; this.worker = worker; this.host = host;
    }
    public boolean isBusy() { return busy; }
    public void load(boolean showList) {
        if (stopped || !host.available()) return;
        if (busy) { interactive |= showList; return; }
        busy = true; interactive = showList; long token = ++generation;
        worker.execute(() -> {
            if (!valid(token)) { post(token, () -> { }); return; }
            try {
                LocalV2Store.Inspection state = store.inspect();
                post(token, () -> {
                    if (state.format == LocalV2Store.Format.V2) { busy = false; host.loaded(state, interactive); }
                    else if (interactive) { host.unavailable("Lokal lagring kräver ett uttryckligt val.", false); choose(state, token); }
                    else { busy = false; host.unavailable("Öppna TMM för att välja lokal lagring. Ingen uppgradering eller tom samling har skapats.", false); }
                });
            } catch (Exception unavailable) { post(token, () -> failed("Lagringen kunde inte läsas. Inga data har återställts eller initierats. Öppna igen för felsökning.")); }
        });
    }
    private void choose(LocalV2Store.Inspection state, long token) {
        boolean upgrade = state.format == LocalV2Store.Format.V1;
        String text = upgrade
                ? "Befintliga enheter, revisioner och bildkopplingar bevaras. Äldre raderings- och ändringshistorik saknas i det gamla formatet och kan inte återskapas. "
                    + "Skyddet mot gamla ändringar börjar efter uppgraderingen. Äldre pluginversioner kan sedan inte öppna lagringen. Inget skickas."
                : "Ingen lokal lagringsfil finns. Det går inte att avgöra om detta är en ny installation eller om tidigare data saknas. "
                    + "Om du haft utkast: avbryt och utred innan du fortsätter. Detta startar uttryckligen utan enhets-, raderings- eller beslutshistorik. Ingen återställning görs.";
        dialog = new AlertDialog.Builder(context).setTitle(upgrade ? "Uppgradera lokal lagring?" : "Starta utan lokal historik?")
                .setMessage(text).setNegativeButton("Avbryt", (d, w) -> { if (generation == token) cancel(); })
                .setPositiveButton(upgrade ? "Uppgradera och öppna" : "Starta utan historik", (d, w) -> {
                    if (!valid(token)) { post(token, () -> { }); return; }
                    long operation = ++generation;
                    worker.execute(() -> {
                        if (!valid(operation)) { post(operation, () -> { }); return; }
                        try {
                            LocalV2.Snapshot activated = upgrade ? store.migrateV1() : store.initializeNew();
                            LocalV2Store.Inspection loaded = store.inspect();
                            if (!loaded.requireV2().storeId.equals(activated.storeId)) throw new IllegalStateException();
                            post(operation, () -> { busy = false; host.loaded(loaded, true); });
                        } catch (Exception uncertain) {
                            post(operation, () -> failed("Ändringen kunde inte bekräftas. Öppna TMM igen för att läsa det faktiskt sparade formatet. Ingen automatisk återställning görs."));
                        }
                    });
                }).create();
        dialog.setCanceledOnTouchOutside(false); dialog.setOnCancelListener(d -> { if (generation == token) cancel(); }); dialog.show();
    }
    private boolean valid(long token) { return !stopped && generation == token && host.available(); }
    private void post(long token, Runnable action) {
        host.post(() -> { if (!stopped && generation == token) { if (host.available()) action.run(); else { cancel(); host.unavailable("Lokal åtkomst avslutades. Inga nya uppgifter visas.", false); } } });
    }
    private void failed(String message) { busy = false; host.unavailable(message, interactive); }
    public void cancel() { generation++; busy = false; if (dialog != null) { dialog.dismiss(); dialog = null; } }
    public void stop() { stopped = true; }
    public void close() { stop(); cancel(); }

    public static String warning(LocalV2Store.Inspection storage) {
        LocalV2.Snapshot s = storage.snapshot;
        if (s == null) return "Lagringsutrymme: okänt";
        if (s.slots.size() < 800 && s.decisions.size() < 800 && storage.frameBytes * 5L < LocalV2Codec.MAX_BYTES * 4L) return "";
        int active = s.activeDrafts().size();
        return "Lokal lagring nära eller vid gränsen\nEnhetsplatser: " + s.slots.size() + "/1000 (" + active + " aktiva, "
                + (s.slots.size() - active) + " raderade). Beslut: " + s.decisions.size() + "/1000. Fil: " + storage.frameBytes + "/" + LocalV2Codec.MAX_BYTES
                + " byte. Radering frigör ingen plats; historik tas inte bort automatiskt. Läsning och granskning fungerar. "
                + "En ändring sparas bara om den ryms. Starta inte om med tom lagring för att lösa detta.";
    }
}
