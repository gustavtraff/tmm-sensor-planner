package se.tmm.sensorplanner.plugin;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapItem;
import com.atakmap.commoncommo.CoTSendMethod;
import com.atakmap.comms.CommsLogger;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import gov.tak.api.ui.IHostUIService;

/** Synthetic loopback-only transport experiment. It never publishes a real draft. */
final class CotRoundTripProbe implements CommsLogger {
    private static final String LAB = "127.0.0.1:8089:ssl";
    private final Runnable showMetadata;
    private final Context context;
    private final MapView map;
    private final IHostUIService ui;
    private boolean closed;
    private AlertDialog dialog;
    private CotEvent received;
    private int receivedCount;
    private final AtomicIntegerArray checks = new AtomicIntegerArray(5);
    private volatile String problem = "Ingen avvisning";
    private final CotProbeDetailHandler detailHandler = new CotProbeDetailHandler();

    CotRoundTripProbe(MapView map, IHostUIService ui, Runnable showMetadata) {
        this.showMetadata = showMetadata;
        context = map.getContext();
        this.map = map;
        this.ui = ui;
        CotProbeFixture.selfTest();
        CotDetailManager.getInstance().registerHandler(detailHandler);
        CommsMapComponent.getInstance().registerCommsLogger(this);
    }

    void show() {
        if (closed) return;
        String[] actions = {"Nytt Mini-prov", "Nytt Scout-prov", "Nytt PIR-prov",
                "Nytt Gateway-prov", "Visa mottaget prov", "Återsänd mottaget prov", "Visa labbdiagnostik", "Visa mottagen enhetsmetadata"};
        replace(new AlertDialog.Builder(context)
                .setTitle("CoT-labb · mottagna: " + receivedCount)
                .setItems(actions, (ignored, index) -> {
                    if (index == 7) showMetadata.run();
                    else if (index == 6) message("Labbdiagnostik", "Trafik: " + checks.get(0)
                            + "\nTesttagg: " + checks.get(1) + "\nKanal: " + checks.get(2)
                            + "\nLabbvillkor: " + checks.get(3) + "\nValiderade: " + checks.get(4)
                            + "\nSenaste kontroll: " + problem);
                    else if (index < 4) selectRecipient(CotProbeFixture.create(CotProbeFixture.TYPES[index]));
                    else if (received == null) ui.showToast("Inget verifierat prov mottaget");
                    else {
                        try {
                            int matches = 0;
                            for (MapItem item : map.getRootGroup().getItemsRecursive()) {
                                if (received.getUID().equals(item.getUID())) matches++;
                            }
                            if (matches != 1) throw new IllegalStateException("Expected one imported fixture");
                            CotEvent exported = CotEventFactory.createCotEvent(map.getMapItem(received.getUID()));
                            CotEvent verified = CotProbeFixture.forReturn(exported);
                            if (index == 4) message("Kartobjekt återexporterat och verifierat",
                                    CotProbeFixture.summary(verified));
                            else selectRecipient(verified);
                        } catch (RuntimeException unavailable) {
                            ui.showToast("Inte skickat: kartobjektets återexport kunde inte verifieras");
                        }
                    }
                }).create());
    }

    private void selectRecipient(CotEvent event) {
        if (!labConnected()) {
            ui.showToast("Inte skickat: kräver isolerat loopback-labb och flygplansläge");
            return;
        }
        IndividualContact self = CotMapComponent.getInstance().getSelfContact(false);
        List<Contact> choices = new ArrayList<>();
        for (Contact contact : Contacts.getInstance().getAllContacts()) {
            if (contact instanceof IndividualContact && contact.getUID() != null
                    && !contact.getUID().isEmpty() && !ChatManagerMapComponent.isSpecialGroup(contact.getUID())
                    && (self == null || !contact.getUID().equals(self.getUID()))) {
                choices.add(contact);
            }
        }
        if (choices.isEmpty()) {
            ui.showToast("Inte skickat: ingen mottagare finns i labbet");
            return;
        }
        String[] names = new String[choices.size()];
        for (int i = 0; i < names.length; i++) {
            Contact contact = choices.get(i);
            names[i] = contact.getName() + "\n" + contact.getUID();
        }
        replace(new AlertDialog.Builder(context).setTitle("Välj en labbmottagare")
                .setItems(names, (ignored, index) -> review(event, choices.get(index)))
                .setNegativeButton("Avbryt", null).create());
    }

    private void review(CotEvent event, Contact contact) {
        replace(new AlertDialog.Builder(context).setTitle("Granska före sändning")
                .setView(scrolling("Mottagare: " + contact.getName() + "\nUID: " + contact.getUID()
                        + "\nKanal: endast labbets TAK Server\n\n" + CotProbeFixture.summary(event)))
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Skicka syntetiskt prov", (ignored, button) -> send(event, contact.getUID()))
                .create());
    }

    private void send(CotEvent event, String uid) {
        if (closed) return;
        Contact current = Contacts.getInstance().getContactByUuid(uid);
        if (!labConnected() || uid == null || uid.isEmpty() || ChatManagerMapComponent.isSpecialGroup(uid) || !(current instanceof IndividualContact)) {
            ui.showToast("Inte skickat: anslutning eller mottagare saknas");
            return;
        }
        try {
            CotProbeFixture.validate(event);
            CotProbeFixture.refreshTime(event);
            Bundle destination = new Bundle();
            destination.putStringArray("toUIDs", new String[] {uid});
            // SDK return values are not delivery acknowledgments (see Issue #5 report).
            CotMapComponent.getExternalDispatcher().dispatch(event, destination, CoTSendMethod.TAK_SERVER);
            ui.showToast("Sändningsanrop utfört – leverans obekräftad");
        } catch (RuntimeException failure) {
            // Do not expose coordinates, recipient data or raw events in error logs.
            ui.showToast("Sändningsfel – leverans obekräftad");
        }
    }

    private boolean labConnected() {
        try {
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 1
                    || Settings.Global.getInt(context.getContentResolver(), Settings.Global.WIFI_ON, 0) != 0) {
                return false;
            }
            TAKServer[] servers = TAKServerListener.getInstance().getServers();
            int enabled = 0;
            if (servers == null) return false;
            for (TAKServer server : servers) {
                if (!server.isEnabled() || !server.isStream()) continue;
                if (!server.isConnected() || !LAB.equals(server.getConnectString())) return false;
                enabled++;
            }
            return enabled == 1;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public void logReceive(CotEvent event, String rxid, String server) {
        checks.incrementAndGet(0);
        if (!CotProbeFixture.isProbe(event)) return;
        checks.incrementAndGet(1);
        if (!LAB.equals(server)) {
            problem = server == null ? "Kanal saknas" : "Annan kanal";
            return;
        }
        checks.incrementAndGet(2);
        if (!labConnected()) {
            problem = "Labbvillkor saknas";
            return;
        }
        checks.incrementAndGet(3);
        final CotEvent validated;
        try {
            validated = CotProbeFixture.forReturn(event);
        } catch (RuntimeException invalid) {
            problem = invalid instanceof CotProbeFixture.Invalid
                    ? ((CotProbeFixture.Invalid) invalid).field : "Ogiltigt SDK-innehåll";
            return;
        }
        checks.incrementAndGet(4);
        ui.queueEvent(() -> {
            if (closed) return;
            received = validated;
            receivedCount++;
            ui.showToast("CoT-prov mottaget – UUID och metadata verifierade");
        });
    }

    @Override public void logSend(CotEvent event, String destination) { }
    @Override public void logSend(CotEvent event, String[] recipients) { }
    @Override public void dispose() { }

    void close() {
        closed = true;
        CommsMapComponent.getInstance().unregisterCommsLogger(this);
        CotDetailManager.getInstance().unregisterHandler(detailHandler);
        if (dialog != null) dialog.dismiss();
        received = null;
    }

    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        next.show();
    }

    private ScrollView scrolling(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(view);
        return scroll;
    }

    private void message(String title, String text) {
        replace(new AlertDialog.Builder(context).setTitle(title).setView(scrolling(text))
                .setPositiveButton("Stäng", null).create());
    }
}
