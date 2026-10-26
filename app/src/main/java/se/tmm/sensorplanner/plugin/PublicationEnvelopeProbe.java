package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.os.Bundle;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;
import java.util.function.BooleanSupplier;
import gov.tak.api.ui.IHostUIService;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.plugin.publication.*;
import com.atakmap.coremap.maps.time.CoordinatedTime;

/** Isolated synthetic SDK import experiment, never a saved-draft publishing adapter. */
@TargetApi(26)
final class PublicationEnvelopeProbe {
    private final MapView map;
    private final IHostUIService ui;
    private final BooleanSupplier allowed;
    private boolean closed;
    private AlertDialog dialog;
    private PublicationPreparationReview pending;

    PublicationEnvelopeProbe(MapView map, IHostUIService ui, BooleanSupplier allowed) {
        this.map = map; this.ui = ui; this.allowed = allowed;
    }

    synchronized void show() {
        if (closed || !allowed.getAsBoolean() || !ShareLabRoute.connected(map.getContext())) {
            ui.showToast("SDK-importprovet kräver isolerat loopback-labb och flygplansläge");
            return;
        }
        cancel();
        dialog = new AlertDialog.Builder(map.getContext()).setTitle("Produktionsformat · syntetiskt prov")
                .setItems(new String[]{"Mini", "Scout", "PIR", "Gateway"}, (d, which) -> {
                    if (!closed) review(new DeviceType[]{DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY}[which]);
                }).setNegativeButton("Avbryt", (d, w) -> cancel()).create();
        dialog.setOnCancelListener(d -> cancel()); dialog.show();
    }

    private synchronized void review(DeviceType type) {
        if (closed || !allowed.getAsBoolean() || !ShareLabRoute.connected(map.getContext())) return;
        cancel();
        TmmDraft fixture = PublicationEnvelopeFixtures.draft(type);
        PublicationPreparationReview review = new PublicationPreparationReview(fixture);
        pending = review;
        dialog = new AlertDialog.Builder(map.getContext()).setTitle("Granska syntetiskt SDK-importprov")
                .setMessage("ENDAST ISOLERAT LABB\nFast syntetisk enhet, inga sparade utkast läses.\n"
                        + "Typ: " + type.displayName + "\nFormat: tmmPublication v1\nUUID: " + fixture.uid + "\nIngen feed vald. Bild delas inte.\n\n"
                        + "Bekräftelse skapar eller uppdaterar ett vanligt ATAK-kartobjekt. "
                        + "Andra feeds kan publicera det automatiskt – detta ska mätas i labbet. "
                        + "Inget särskilt sändningsanrop görs och mottagande är obekräftat. "
                        + "Kartkopian kan finnas kvar efter Unload eller omstart.")
                .setNegativeButton("Avbryt", (d, w) -> cancel())
                .setPositiveButton("Importera syntetisk kartkopia", (d, w) -> confirm(review, fixture)).create();
        dialog.setOnCancelListener(d -> cancel());
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private synchronized void confirm(PublicationPreparationReview review, TmmDraft fixture) {
        if (closed || pending != review) return;
        pending = null;
        if (!allowed.getAsBoolean() || !ShareLabRoute.connected(map.getContext())) {
            review.close(); result("Inte importerat: labbvillkoren gäller inte längre."); return;
        }
        boolean attempted = false;
        try {
            PublicationPreparationReview.Prepared prepared = review.prepare(fixture);
            CotEvent event = PublicationCot.create(prepared, fixture.displayId, new CoordinatedTime());
            // Refuse to overwrite a different pre-existing fixture variant or an unrelated host object.
            MapItem existing = map.getMapItem(fixture.uid.toString());
            if (existing != null && !PublicationCot.matches(CotEventFactory.createCotEvent(existing), prepared)) {
                result("Inte importerat: ett avvikande kartobjekt har samma UUID. Utred det i labbet."); return;
            }
            CotImporterManager manager = CotImporterManager.getInstance();
            if (manager == null) throw new IllegalStateException();
            Bundle options = new Bundle();
            options.putString("from", "se.tmm.sensorplanner.plugin.publicationlab");
            attempted = true;
            ImportResult imported = manager.importData(event, options);
            int count = 0;
            for (MapItem item : map.getRootGroup().getItemsRecursive()) {
                if (fixture.uid.toString().equals(item.getUID())) count++;
            }
            MapItem actual = map.getMapItem(fixture.uid.toString());
            boolean verified = count == 1 && actual != null
                    && PublicationCot.matches(CotEventFactory.createCotEvent(actual), prepared);
            result("SDK-resultat: " + imported + "\nKartobjekt med UUID: " + count
                    + "\nExakt återexport verifierad: " + (verified ? "Ja" : "Nej")
                    + "\nFeedpublicering och mottagande är obekräftade. Kontrollera båda feeds och klient B. "
                    + "Ingen automatisk återställning eller omsändning görs.");
        } catch (RuntimeException failure) {
            result(attempted ? "Import försökt, resultat oklart. Kontrollera kartan och båda feeds före nytt försök."
                    : "Inte importerat: provet kunde inte förberedas eller befintligt objekt verifieras.");
        } finally { review.close(); }
    }

    private void result(String message) {
        if (dialog != null) dialog.dismiss();
        dialog = new AlertDialog.Builder(map.getContext()).setTitle("SDK-importprov – ingen leveransbekräftelse")
                .setMessage(message).setPositiveButton("Stäng", null).create();
        dialog.show();
    }

    private synchronized void cancel() {
        if (pending != null) { pending.close(); pending = null; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    synchronized void close() {
        closed = true;
        if (pending != null) { pending.close(); pending = null; }
        AlertDialog stopping = dialog;
        dialog = null;
        if (stopping != null) ui.queueEvent(stopping::dismiss);
    }
}
