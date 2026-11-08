package se.tmm.sensorplanner.plugin.local;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import se.tmm.sensorplanner.core.ConfiguredPublicationTrial;

/** Source selection and the four separate trial statements; performs no network request or host write. */
public final class ServerTrialConsentUi {
    public interface Host {
        boolean available();
        List<String> profiles();
        void confirmed(String stream, boolean[] statements);
    }

    static final String[] STATEMENTS = {
            "Endast syntetiska data finns på denna enhet och i denna feed.",
            "Serverägaren har godkänt detta prov på denna server.",
            "Profilen har en server och en testfeed, och Auto-publicera är av.",
            "Jag förstår att TMM inte kan verifiera eller begränsa destinationen."};

    private final Context context;
    private final Host host;
    private AlertDialog dialog;
    private boolean closed;

    public ServerTrialConsentUi(Context context, Host host) { this.context = context; this.host = host; }

    public boolean isBusy() { return dialog != null && dialog.isShowing(); }

    public void show() {
        if (closed || isBusy() || !host.available()) return;
        List<String> streams;
        try { streams = new ArrayList<>(host.profiles()); }
        catch (RuntimeException unavailable) { streams = new ArrayList<>(); }
        if (streams.isEmpty()) {
            replace(new AlertDialog.Builder(context).setTitle("Ingen ansluten TLS-server")
                    .setMessage("Anslut exakt en SSL-server i ATAK först. Ingenting har skrivits eller skickats.")
                    .setPositiveButton("Stäng", (d, w) -> cancel()).create());
            return;
        }
        final List<String> choices = streams;
        AlertDialog next = new AlertDialog.Builder(context).setTitle("Välj server för publiceringsprov")
                .setItems(choices.toArray(new String[0]), null).setNegativeButton("Avbryt", (d, w) -> cancel()).create();
        replace(next);
        next.getListView().setOnItemClickListener((parent, view, n, id) -> {
            if (!closed && dialog == next && host.available()) consent(choices.get(n));
        });
    }

    private void consent(String stream) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(24, 16, 24, 16);
        TextView message = new TextView(context);
        message.setText("Vald server: " + stream
                + "\n\nATAK:s egna funktioner kan publicera kartkopian. TMM kan inte verifiera eller begränsa feed. "
                + "Nej i Data Sync-frågan ångrar inte SDK-ändringen. Bilder delas inte.\n\n"
                + "Godkännandet gäller bara denna session. Det upphör vid Avbryt, Unload, omstart och ändrad anslutning.");
        body.addView(message);
        List<CheckBox> boxes = new ArrayList<>();
        for (String statement : STATEMENTS) {
            CheckBox box = new CheckBox(context);
            box.setText(statement);
            box.setMinHeight(64);
            boxes.add(box);
            body.addView(box);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        AlertDialog next = new AlertDialog.Builder(context).setTitle("Publiceringsprov · syntetiska data").setView(scroll)
                .setNegativeButton("Avbryt", (d, w) -> cancel()).setPositiveButton("Fortsätt till granskning", null).create();
        replace(next);
        Button proceed = next.getButton(AlertDialog.BUTTON_POSITIVE);
        proceed.setEnabled(false);
        for (CheckBox box : boxes) box.setOnCheckedChangeListener((button, checked) -> proceed.setEnabled(all(boxes)));
        proceed.setOnClickListener(v -> {
            if (closed || dialog != next) return;
            if (!host.available() || !all(boxes)) { cancel(); return; }
            boolean[] statements = new boolean[ConfiguredPublicationTrial.STATEMENTS];
            for (int i = 0; i < statements.length; i++) statements[i] = boxes.get(i).isChecked();
            cancel();
            host.confirmed(stream, statements);
        });
    }

    private static boolean all(List<CheckBox> boxes) {
        for (CheckBox box : boxes) if (!box.isChecked()) return false;
        return true;
    }

    private void replace(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> cancel());
        dialog.show();
    }

    public void cancel() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    public void close() { closed = true; cancel(); }
}
