package se.tmm.sensorplanner.plugin.publication;

import android.annotation.TargetApi;
import android.os.Bundle;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.core.PublicationPreparationReview;

/** UI-thread lab handoff; refuses to change any existing host copy. */
@TargetApi(26)
public final class PublicationMapCopy {
    interface Port {
        boolean available();
        int count(String uid);
        CotEvent export(String uid);
        String importEvent(CotEvent event);
    }
    private final Port port;
    public PublicationMapCopy(MapView map, BooleanSupplier allowed) {
        this(new Port() {
            @Override public boolean available() { return allowed.getAsBoolean(); }
            @Override public int count(String uid) {
                int count = 0;
                for (MapItem item : map.getRootGroup().getItemsRecursive()) if (uid.equals(item.getUID())) count++;
                return count;
            }
            @Override public CotEvent export(String uid) {
                MapItem item = map.getMapItem(uid);
                return item == null ? null : CotEventFactory.createCotEvent(item);
            }
            @Override public String importEvent(CotEvent event) {
                CotImporterManager manager = CotImporterManager.getInstance();
                if (manager == null) throw new IllegalStateException();
                Bundle options = new Bundle(); options.putString("from", "se.tmm.sensorplanner.plugin.publicationlab");
                return manager.importData(event, options).toString();
            }
        });
    }
    PublicationMapCopy(Port port) { this.port = port; }

    public String create(PublicationPreparationReview.Prepared prepared, String label) {
        boolean attempted = false;
        try {
            if (!port.available()) return "Inte skapat: kräver isolerat loopback-labb, flygplansläge och ansluten labbserver.";
            CotEvent candidate = PublicationCot.create(prepared, label, new CoordinatedTime());
            String uid = candidate.getUID();
            int before = port.count(uid);
            CotEvent existing = port.export(uid);
            if (before != 0 || existing != null) {
                if (before == 1 && exact(existing, prepared, label)) {
                    return "En oförändrad befintlig kartkopia är verifierad. Inget SDK-importanrop har gjorts. "
                            + "Befintliga feedkopplingar är inte kontrollerade eller återkallade.";
                }
                return "Inte skapat: en avvikande eller oklar kartkopia har samma UUID. Inget befintligt objekt har skrivits över. Utred kartkopian separat.";
            }
            if (!port.available()) return "Inte skapat: labbvillkoren gäller inte längre. Granska på nytt.";
            attempted = true;
            String sdk = port.importEvent(candidate);
            int count = port.count(uid);
            boolean verified = count == 1 && exact(port.export(uid), prepared, label);
            return "SDK-resultat: " + sdk + "\nKartobjekt med UUID: " + count
                    + "\nGranskad kartkopia verifierad: " + (verified ? "Ja" : "Nej")
                    + "\nKontrollera kartan och feeds vid oklart resultat. Ingen automatisk återställning eller omsändning görs.";
        } catch (RuntimeException failure) {
            return attempted ? "SDK-import försökt – resultat oklart. Kontrollera kartan och feeds före nytt försök. Ingen automatisk återställning görs."
                    : "Inte skapat: metadata, kartetikett eller befintlig kartkopia kunde inte verifieras. Etiketten ID + revision måste vara högst 128 tecken utan styrtecken. Granska på nytt.";
        }
    }
    private static boolean exact(CotEvent event, PublicationPreparationReview.Prepared prepared, String label) {
        if (!PublicationCot.matches(event, prepared)) return false;
        CotDetail contact = event.getDetail().getFirstChildByName(0, "contact");
        return contact != null && label.equals(contact.getAttribute("callsign"));
    }
}
