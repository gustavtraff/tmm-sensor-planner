package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsLogger;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.*;
import gov.tak.api.ui.IHostUIService;
import se.tmm.sensorplanner.core.*;

/** Lab inspection only; never imports into DraftStore or acknowledges delivery. */
@TargetApi(26)
final class ShareLabReceiver implements CommsLogger {
    private static final String KEY = "se.tmm.sensorplanner.issue14.metadata";
    private final MapView map;
    private final IHostUIService ui;
    private volatile boolean closed;
    private ShareReview.Metadata received;
    private int count;
    private AlertDialog dialog;
    private final CotDetailHandler handler = new CotDetailHandler(ShareLabFields.TAG) {
        @Override public ImportResult toItemMetadata(MapItem item, CotEvent event, CotDetail detail) {
            try {
                if (closed || !ShareLabRoute.connected(map.getContext())) return ImportResult.FAILURE;
                item.setMetaString(KEY, ShareLabWire.create(ShareLabWire.read(event)).toString());
                return ImportResult.SUCCESS;
            } catch (RuntimeException invalid) { return ImportResult.FAILURE; }
        }
        @Override public boolean toCotDetail(MapItem item, CotEvent event, CotDetail root) {
            try {
                if (closed) return false;
                String saved = item.getMetaString(KEY, null);
                if (saved == null) return false;
                ShareReview.Metadata metadata = ShareLabWire.read(CotEvent.parse(saved));
                if (!metadata.uid.toString().equals(event.getUID())
                        || ShareLabFields.envelopePosition(metadata.position).latitude != event.getCotPoint().getLat()
                        || ShareLabFields.envelopePosition(metadata.position).longitude != event.getCotPoint().getLon()) return false;
                root.addChild(ShareLabWire.create(metadata).getDetail().getFirstChildByName(0, ShareLabFields.TAG));
                return true;
            } catch (RuntimeException invalid) { return false; }
        }
    };

    ShareLabReceiver(MapView map, IHostUIService ui) {
        this.map = map; this.ui = ui;
        CotDetailManager.getInstance().registerHandler(handler);
        CommsMapComponent.getInstance().registerCommsLogger(this);
    }

    void show() {
        if (closed) return;
        String message = "Inget verifierat metadataprov mottaget. Öppna CoT-labbet på mottagaren före sändning.";
        if (received != null) {
            try {
                int matches = 0;
                for (MapItem item : map.getRootGroup().getItemsRecursive()) {
                    if (received.uid.toString().equals(item.getUID())) matches++;
                }
                if (matches != 1) throw new IllegalStateException();
                CotEvent exported = CotEventFactory.createCotEvent(map.getMapItem(received.uid.toString()));
                ShareReview.Metadata actual = ShareLabWire.read(exported);
                if (!ShareLabWire.same(received, actual)) throw new IllegalStateException();
                message = "Ett faktiskt kartobjekt återexporterat och verifierat. Mottagna händelser: " + count
                        + "\n\n" + ShareLabWire.summary(actual) + "\nIngen import till lokala utkast har gjorts.";
            } catch (RuntimeException unavailable) {
                message = "Mottaget prov kunde inte verifieras genom kartans återexport. Ingen leveransbekräftelse ges.";
            }
        }
        TextView text = new TextView(map.getContext()); text.setText(message); text.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(map.getContext()); scroll.addView(text);
        if (dialog != null) dialog.dismiss();
        dialog = new AlertDialog.Builder(map.getContext()).setTitle("Mottagen enhetsmetadata i labbet")
                .setView(scroll).setPositiveButton("Stäng", null).create(); dialog.show();
    }

    @Override public void logReceive(CotEvent event, String rxid, String server) {
        if (event == null || event.getDetail() == null
                || event.getDetail().getFirstChildByName(0, ShareLabFields.TAG) == null) return;
        if (closed || !ShareLabRoute.STREAM.equals(server) || !ShareLabRoute.connected(map.getContext())) return;
        try {
            ShareReview.Metadata metadata = ShareLabWire.read(event);
            ui.queueEvent(() -> { if (!closed) { received = metadata; count++; } });
        } catch (RuntimeException ignored) { }
    }
    @Override public void logSend(CotEvent event, String destination) {}
    @Override public void logSend(CotEvent event, String[] recipients) {}
    @Override public void dispose() {}
    void close() {
        closed = true;
        CommsMapComponent.getInstance().unregisterCommsLogger(this);
        CotDetailManager.getInstance().unregisterHandler(handler);
        if (dialog != null) dialog.dismiss();
        received = null;
    }
}
