package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gov.tak.api.ui.IHostUIService;
import se.tmm.sensorplanner.core.*;

/** Offline local prototype; kept behind the entry point's API 26 runtime guard. */
@TargetApi(26)
final class LocalPlanner {
    private static final DeviceType[] TYPES = {DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY};
    private static final String[] STATUSES = {"Planerad", "Utplacerad", "Aktiv", "Intagen", "Avbruten"};
    private final MapView map;
    private final Context context;
    private final IHostUIService ui;
    private final DraftStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LocalDraftLayer layer;
    private List<TmmDraft> drafts = Collections.emptyList();
    private AlertDialog dialog;
    private AlertDialog confirmation;
    private boolean closed;
    private boolean loading;

    LocalPlanner(MapView map, IHostUIService ui) throws Exception {
        this.map = map;
        this.context = map.getContext();
        this.ui = ui;
        // The plugin executes as ATAK's UID; no-backup host storage avoids shared files and backups.
        store = new DraftStore(new java.io.File(context.getNoBackupFilesDir(),
                "se.tmm.sensorplanner/local-drafts-v1").toPath());
        layer = new LocalDraftLayer(map, this::selected);
    }

    private void selected(UUID uid) {
        if (!closed && !loading && (dialog == null || !dialog.isShowing()) && OfflineMode.isOffline(context)) {
            for (TmmDraft draft : drafts) if (draft.uid.equals(uid)) {
                review(draft);
                break;
            }
        }
    }

    void show() {
        if (!offline() || closed || loading) return;
        if (dialog != null && dialog.isShowing()) return;
        loading = true;
        dismiss();
        worker.execute(() -> {
            try {
                List<TmmDraft> loaded = store.load();
                ui.queueEvent(() -> {
                    if (closed) return;
                    loading = false;
                    refresh(loaded);
                    list();
                });
            } catch (Exception failure) {
                ui.queueEvent(() -> {
                    loading = false;
                    if (closed) return;
                    drafts = Collections.emptyList();
                    if (layer != null) layer.close();
                    layer = null;
                    alert("Lagringen kunde inte öppnas",
                            "Befintliga data har inte återställts eller skrivits över. Försök öppna igen. "
                            + "Om felet kvarstår behövs felsökning innan du fortsätter.");
                });
            }
        });
    }

    private boolean offline() {
        if (OfflineMode.isOffline(context)) return true;
        ui.showToast("Prototypen kräver flygplansläge och avstängt nätverk");
        return false;
    }

    private void list() {
        if (!offline() || closed) return;
        String[] rows = new String[drafts.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = title(drafts.get(i)) + "\nLokalt utkast";
        dismiss();
        dialog = new AlertDialog.Builder(context).setTitle("TMM – lokala utkast (" + rows.length + ")")
                .setItems(rows, (ignored, index) -> review(drafts.get(index)))
                .setPositiveButton("Skapa enhet", (ignored, which) -> form(null))
                .setNeutralButton("Läs om", (ignored, which) -> { dismiss(); show(); })
                .setNegativeButton("Stäng", null).create();
        dialog.show();
    }

    private void review(TmmDraft draft) {
        if (closed || !offline()) return;
        StringBuilder text = new StringBuilder("Lokalt utkast – delas inte\nEndast syntetiska testdata i denna prototyp.\n\n");
        text.append(title(draft)).append("\nÄgare: ").append(draft.owner)
                .append("\nLatitud: ").append(draft.position.latitude)
                .append("\nLongitud: ").append(draft.position.longitude)
                .append("\nSkapad: ").append(localTime(draft.createdAt))
                .append("\nÄndrad: ").append(localTime(draft.updatedAt))
                .append("\nUtplacerad: ").append(draft.deployedAt == null ? "Ej angivet" : localTime(draft.deployedAt))
                .append("\nBäring från sann nord: ").append(draft.bearingDeg == null ? "Ej angivet" : draft.bearingDeg + "°")
                .append("\nKommentar: ").append(draft.comment.isEmpty() ? "Ingen" : draft.comment)
                .append("\nBild: ").append(draft.imageRef == null ? "Ingen (bildstöd kommer senare)" : "Lokal koppling bevarad; bildvisning saknas")
                .append("\nRevision: ").append(draft.revision);
        if (DraftForm.hasDuplicateId(draft.displayId, draft.uid, drafts)) {
            text.append("\n\nVarning: samma synliga ID finns på en annan enhet.");
        }
        Optional<DistanceCalculator.Distance> nearest = DistanceCalculator.nearestOther(draft, drafts);
        text.append("\n\nNärmaste annan TMM: ").append(nearest.isPresent() ? distance(nearest.get()) : "Ingen annan behörig enhet");
        if (draft.type == DeviceType.GATEWAY) {
            text.append("\nAndra gateways:");
            List<DistanceCalculator.Distance> gateways = DistanceCalculator.otherGateways(draft, drafts);
            if (gateways.isEmpty()) text.append(" Inga");
            for (DistanceCalculator.Distance other : gateways) text.append("\n").append(distance(other));
        }
        text.append("\nKartpilen är 100 m lång och visar riktning från sann nord, inte sensorernas räckvidd.");
        text.append("\nAvstånd avrundas till hela meter och visar inte radiotäckning.");
        LinearLayout content = column();
        content.addView(label(text.toString()));
        Button center = button("Visa på kartan", () -> {
            try { if (layer == null) throw new IllegalStateException(); layer.center(draft); dismiss(); }
            catch (RuntimeException failure) { ui.showToast("Kartan kunde inte centreras"); }
        });
        content.addView(center);
        content.addView(button("Ta bort lokalt utkast", () -> confirmDelete(draft)));
        dismiss();
        dialog = new AlertDialog.Builder(context).setTitle(draft.displayId)
                .setView(scroll(content)).setPositiveButton("Redigera", (ignored, which) -> form(draft))
                .setNegativeButton("Till listan", (ignored, which) -> list()).create();
        dialog.show();
    }

    private void form(TmmDraft original) {
        if (closed || !offline()) return;
        LinearLayout fields = column();
        fields.addView(label("Lokalt utkast – delas inte. Använd endast syntetiska testdata.\n"
                + "Positionen anges i decimalgrader; ny enhet använder kartans mittpunkt."));
        Map<DraftForm.Field, EditText> inputs = new EnumMap<>(DraftForm.Field.class);
        EditText id = field(fields, inputs, DraftForm.Field.DISPLAY_ID, "Enhetens ID", original == null ? "" : original.displayId, false);
        Spinner type = spinner(fields, "Typ", new String[]{"Mini", "Scout", "PIR", "Gateway"});
        if (original != null) type.setSelection(Arrays.asList(TYPES).indexOf(original.type));
        Spinner status = spinner(fields, "Status", STATUSES);
        status.setSelection(original == null ? 0 : original.status.ordinal());
        EditText owner = field(fields, inputs, DraftForm.Field.OWNER, "Ägare (grupp/roll)", original == null ? "" : original.owner, false);
        EditText deployed = field(fields, inputs, DraftForm.Field.DEPLOYED_AT,
                "Utplacerad UTC (valfri), t.ex. 2026-10-17T12:30:00Z", original == null || original.deployedAt == null ? "" : original.deployedAt.toString(), false);
        EditText comment = field(fields, inputs, DraftForm.Field.COMMENT, "Kommentar (max 2000)", original == null ? "" : original.comment, false);
        comment.setSingleLine(false);
        comment.setMinLines(2);
        GeoPoint center = map.getPoint().get();
        EditText latitude = field(fields, inputs, DraftForm.Field.LATITUDE, "Latitud (−90 till 90)", DraftForm.decimalText(original == null ? center.getLatitude() : original.position.latitude), true);
        EditText longitude = field(fields, inputs, DraftForm.Field.LONGITUDE, "Longitud (−180 till 180)", DraftForm.decimalText(original == null ? center.getLongitude() : original.position.longitude), true);
        EditText bearing = field(fields, inputs, DraftForm.Field.BEARING, "Bäring från sann nord (valfri, 0 till <360°; endast PIR/Scout)", original == null || original.bearingDeg == null ? "" : DraftForm.decimalText(original.bearingDeg), true);
        View bearingLabel = fields.getChildAt(fields.indexOfChild(bearing) - 1);
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
                boolean visible = TYPES[position].supportsBearing() || !value(bearing).trim().isEmpty();
                bearing.setVisibility(visible ? View.VISIBLE : View.GONE);
                bearingLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        TextView formError = label("");
        fields.addView(formError);
        dismiss();
        AlertDialog editor = new AlertDialog.Builder(context).setTitle(original == null ? "Skapa enhet" : "Redigera " + original.displayId)
                .setView(scroll(fields)).setPositiveButton("Spara lokalt", null)
                .setNegativeButton("Avbryt", (ignored, which) -> list()).create();
        dialog = editor;
        editor.setCanceledOnTouchOutside(false);
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (closed || !offline()) return;
            for (EditText input : inputs.values()) input.setError(null);
            formError.setText("");
            DraftForm.Result result = DraftForm.validate(original, value(id), TYPES[type.getSelectedItemPosition()],
                    DeviceStatus.values()[status.getSelectedItemPosition()], value(owner), value(deployed), value(comment),
                    value(latitude), value(longitude), value(bearing), Instant.now());
            if (!result.isValid()) {
                EditText firstError = null;
                for (Map.Entry<DraftForm.Field, DraftForm.Error> error : result.errors.entrySet()) {
                    EditText input = inputs.get(error.getKey());
                    if (input != null) {
                        input.setError(errorText(error.getValue()));
                        if (firstError == null) firstError = input;
                    } else showError(formError, errorText(error.getValue()));
                }
                if (firstError != null) firstError.requestFocus();
                return;
            }
            Runnable save = () -> save(result.draft, editor, fields, formError);
            if (DraftForm.hasDuplicateId(result.draft.displayId, result.draft.uid, drafts)) {
                confirmation = new AlertDialog.Builder(context).setTitle("Samma ID finns redan")
                        .setMessage("En annan lokal enhet har samma ID. De sparas som separata enheter. Vill du spara ändå?")
                        .setPositiveButton("Spara ändå", (ignored, which) -> save.run())
                        .setNegativeButton("Tillbaka", null).create();
                confirmation.show();
            } else save.run();
        });
    }

    private void save(TmmDraft draft, AlertDialog editor, LinearLayout fields, TextView error) {
        if (closed || !editor.isShowing() || !offline()) return;
        busy(editor, fields, true);
        worker.execute(() -> {
            try {
                store.save(draft);
                List<TmmDraft> loaded = store.load();
                ui.queueEvent(() -> {
                    if (closed) return;
                    refresh(loaded);
                    editor.dismiss();
                    review(draft);
                });
            } catch (Exception failure) {
                ui.queueEvent(() -> {
                    if (closed) return;
                    busy(editor, fields, false);
                    showError(error, "Sparandet kunde inte bekräftas. Dina inmatade fält finns kvar. "
                            + "Öppna listan med Läs om innan du försöker igen; lagringen kan ha ändrats.");
                    editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                });
            }
        });
    }

    private void confirmDelete(TmmDraft draft) {
        dismiss();
        dialog = new AlertDialog.Builder(context).setTitle("Ta bort lokalt utkast?")
                .setMessage("Detta tar bara bort det lokala utkastet. Ingen server eller mottagare påverkas.")
                .setNegativeButton("Avbryt", (ignored, which) -> review(draft))
                .setPositiveButton("Ta bort", (ignored, which) -> {
                    if (closed || !offline()) return;
                    worker.execute(() -> {
                        try {
                            store.delete(draft.uid, draft.revision);
                            List<TmmDraft> loaded = store.load();
                            ui.queueEvent(() -> { if (!closed) { refresh(loaded); list(); } });
                        } catch (Exception failure) {
                            ui.queueEvent(() -> { if (!closed) alert("Borttagningen kunde inte bekräftas", "Läs om listan innan du fortsätter."); });
                        }
                    });
                }).create();
        dialog.show();
    }

    private void refresh(List<TmmDraft> loaded) {
        drafts = loaded;
        try {
            if (layer == null) layer = new LocalDraftLayer(map, this::selected);
            layer.render(loaded);
        } catch (Exception failure) {
            if (layer != null) layer.close();
            layer = null;
            ui.showToast("Datan är inläst men kartvyn har stängts efter ett fel. Öppna verktyget igen.");
        }
    }

    private void alert(String title, String message) {
        dismiss();
        dialog = new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Stäng", null).create();
        dialog.show();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        layout.setPadding(padding, padding, padding, padding);
        return layout;
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(child);
        return scroll;
    }

    private TextView label(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(16);
        view.setPadding(0, dp(6), 0, dp(4));
        return view;
    }

    private EditText field(LinearLayout parent, Map<DraftForm.Field, EditText> fields,
                           DraftForm.Field key, String title, String initial, boolean decimal) {
        TextView label = label(title);
        parent.addView(label);
        EditText input = new EditText(context);
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setContentDescription(title);
        input.setMinHeight(dp(48));
        input.setSingleLine(true);
        input.setInputType(decimal ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (decimal) {
            // Preserve either decimal separator even when the host keyboard locale uses only one.
            input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789+-,."));
            input.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setText(initial);
        fields.put(key, input);
        parent.addView(input);
        return input;
    }

    private Spinner spinner(LinearLayout parent, String title, String[] values) {
        TextView label = label(title);
        parent.addView(label);
        Spinner spinner = new Spinner(context);
        spinner.setId(View.generateViewId());
        label.setLabelFor(spinner.getId());
        spinner.setContentDescription(title);
        spinner.setMinimumHeight(dp(48));
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, values));
        parent.addView(spinner);
        return spinner;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(context);
        button.setText(text);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void busy(AlertDialog editor, View fields, boolean busy) {
        enabled(fields, !busy);
        editor.setCancelable(!busy);
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
    }

    private static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enabled(group.getChildAt(i), enabled);
        }
    }

    private static void showError(TextView view, String message) {
        view.setText(message);
        view.post(() -> view.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()), false));
    }

    private static String errorText(DraftForm.Error error) {
        switch (error) {
            case REQUIRED: return "Fältet måste fyllas i";
            case INVALID_NUMBER: return "Ange decimalgrader med punkt eller komma";
            case OUT_OF_RANGE: return "Värdet ligger utanför intervallet i fältets rubrik";
            case BEARING_UNSUPPORTED: return "Endast PIR och Scout har bäring. Rensa fältet om du byter typ.";
            case INVALID_TIMESTAMP: return "Ange UTC, t.ex. 2026-10-17T12:30:00Z, eller lämna tomt";
            case COMMENT_TOO_LONG: return "Kommentaren är för lång (max 2000)";
            case CLOCK_BEFORE_UPDATE: return "Klockan är före senaste ändringen. Kontrollera enhetens tid.";
            case REVISION_EXHAUSTED: return "Revisionen kan inte ökas. Utkastet har inte ändrats.";
            default: return "Kontrollera fältet";
        }
    }

    private static String value(EditText input) { return input.getText().toString(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static String title(TmmDraft draft) { return draft.displayId + " · " + draft.type.displayName + " · " + STATUSES[draft.status.ordinal()]; }
    private static String distance(DistanceCalculator.Distance distance) { return distance.draft.displayId + " · " + Math.round(distance.meters) + " m"; }
    private static String localTime(Instant instant) { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(instant); }
    private void dismiss() {
        if (confirmation != null) { confirmation.dismiss(); confirmation = null; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    void close() {
        closed = true;
        dismiss();
        worker.shutdown();
        if (layer != null) layer.close();
    }
}
