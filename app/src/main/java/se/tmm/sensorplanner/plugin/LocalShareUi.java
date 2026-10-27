package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.commoncommo.CoTSendMethod;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import gov.tak.api.ui.IHostUIService;
import se.tmm.sensorplanner.core.*;

/** Explicit one-recipient lab sharing, serialized with the planner's local writes. */
@TargetApi(26)
final class LocalShareUi {
    private final Context context;
    private final IHostUIService ui;
    private final LocalV2Store store;
    private final ExecutorService worker;
    private final BooleanSupplier available;
    private final AtomicLong generation = new AtomicLong();
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private volatile boolean busy;
    private boolean confirmed;
    private AlertDialog dialog;

    LocalShareUi(Context context, IHostUIService ui, LocalV2Store store, ExecutorService worker, BooleanSupplier available) {
        this.context = context; this.ui = ui; this.store = store; this.worker = worker; this.available = available;
    }

    boolean isBusy() { return busy; }

    void show(TmmDraft draft) {
        if (closed || busy || !available.getAsBoolean()) return;
        if (!ShareLabRoute.connected(context)) {
            ui.showToast("Inte skickat: kräver isolerat loopback-labb, flygplansläge och en ansluten labbserver");
            return;
        }
        busy = true; confirmed = false;
        long token = generation.incrementAndGet();
        replace(new AlertDialog.Builder(context).setTitle("Läser sparad enhet")
                .setMessage("Ingen metadata skickas innan du har granskat och bekräftat.")
                .setNegativeButton("Avbryt", (d, w) -> cancel()).create());
        worker.execute(() -> {
            try {
                LocalV2.Selection current = find(draft.uid);
                if (current == null) throw new IllegalStateException();
                post(token, () -> choose(current, token));
            } catch (Exception unavailable) {
                post(token, () -> result("Inte skickat: enheten kunde inte läsas. Läs om den lokala listan."));
            }
        });
    }

    private void choose(LocalV2.Selection selection, long token) {
        TmmDraft draft = selection.draft;
        if (!ShareLabRoute.connected(context)) { result("Inte skickat: labbanslutningen saknas."); return; }
        List<Contact> choices = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Contact contact : Contacts.getInstance().getAllContacts()) {
            if (ShareLabRoute.recipient(contact.getUID()) != null && seen.add(contact.getUID())) choices.add(contact);
        }
        choices.sort(Comparator.comparing(Contact::getUID));
        if (choices.isEmpty()) { result("Inte skickat: ingen individuell mottagare finns i labbet."); return; }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).getName() + "\n" + choices.get(i).getUID();
        replace(new AlertDialog.Builder(context).setTitle("Välj en labbmottagare")
                .setItems(labels, (d, which) -> {
                    try {
                        Contact selected = choices.get(which);
                        ShareReview review = new ShareReview(draft, ShareLabRoute.destination(selected.getUID()), selected.getName());
                        review(review, selection, selected.getUID(), token);
                    } catch (RuntimeException invalid) { result("Inte skickat: mottagarvalet kunde inte verifieras."); }
                }).setNegativeButton("Avbryt", (d, w) -> cancel()).create());
    }

    private void review(ShareReview review, LocalV2.Selection selection, String uid, long token) {
        String text = "ENDAST SYNTETISKT ISOLERAT LABB\nMottagare: " + review.destinationLabel
                + "\nMottagar-UID: " + uid + "\nKanal: endast labbets TAK Server\n\n"
                + ShareLabWire.summary(review.metadata)
                + (review.imageStatus == ShareReview.ImageStatus.NO_LOCAL_IMAGE ? "\nIngen lokal bild är bifogad." : "\nBilden delas inte.")
                + "\n\nMottagaren får ett labbmärkt kartobjekt. Lokala utkast synkroniseras inte."
                + "\nUtkastet förblir lokalt. Sändningsanropet är ingen leveransbekräftelse.";
        TextView view = new TextView(context); view.setText(text); view.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(context); scroll.addView(view);
        replace(new AlertDialog.Builder(context).setTitle("Granska metadata före sändning")
                .setView(scroll).setNegativeButton("Avbryt", (d, w) -> cancel())
                .setPositiveButton("Skicka metadata i labbet", (d, w) -> {
                    if (!valid(token) || confirmed) return;
                    confirmed = true;
                    worker.execute(() -> send(review, selection, uid, token));
                }).create());
    }

    private void send(ShareReview review, LocalV2.Selection selection, String uid, long token) {
        String outcome;
        boolean dispatchStarted = false;
        try {
            LocalV2.Snapshot snapshot = store.load();
            if (!selection.isCurrent(snapshot)) throw new IllegalStateException();
            TmmDraft current = snapshot.state(review.metadata.uid).draft;
            com.atakmap.coremap.cot.event.CotEvent event = ShareLabWire.create(review.metadata);
            synchronized (lifecycle) {
                if (!valid(token)) return;
                Contact recipient = ShareLabRoute.recipient(uid);
                if (!available.getAsBoolean() || !ShareLabRoute.connected(context) || recipient == null) {
                    outcome = "Inte skickat: anslutning eller mottagare saknas. Granska på nytt.";
                } else if (!review.isCurrent(current, ShareLabRoute.destination(recipient.getUID()), recipient.getName())) {
                    outcome = "Inte skickat: enheten eller mottagaren har ändrats. Läs om och granska på nytt.";
                } else {
                    Bundle destination = new Bundle();
                    destination.putStringArray("toUIDs", new String[]{uid});
                    dispatchStarted = true;
                    CotMapComponent.getExternalDispatcher().dispatch(event, destination, CoTSendMethod.TAK_SERVER);
                    outcome = "Sändningsanrop utfört – leverans obekräftad. Ingen bild har skickats. Utkastet är lokalt.";
                }
            }
        } catch (Exception failure) {
            outcome = dispatchStarted ? "Sändningsfel – leverans obekräftad. Utkastet är lokalt."
                    : "Inte skickat: sparad enhet eller metadata kunde inte verifieras. Läs om och granska på nytt.";
        }
        String message = outcome;
        post(token, () -> result(message));
    }

    private LocalV2.Selection find(UUID uid) throws java.io.IOException {
        LocalV2.Selection selected = store.load().select(uid);
        return selected.draft == null ? null : selected;
    }

    private boolean valid(long token) { return !closed && generation.get() == token; }
    private void post(long token, Runnable action) { ui.queueEvent(() -> { if (valid(token)) action.run(); }); }
    private void result(String text) {
        replace(new AlertDialog.Builder(context).setTitle("Metadata i labbet").setMessage(text)
                .setPositiveButton("Stäng", (d, w) -> cancel()).create());
    }
    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next; dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> cancel()); dialog.show();
    }
    private void cancel() {
        synchronized (lifecycle) { generation.incrementAndGet(); }
        busy = false;
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }
    void stopSending() { synchronized (lifecycle) { closed = true; generation.incrementAndGet(); } }
    void close() { stopSending(); cancel(); }
}
