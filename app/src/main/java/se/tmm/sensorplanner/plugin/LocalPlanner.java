package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import gov.tak.api.ui.IHostUIService;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.DraftAttachments;
import se.tmm.sensorplanner.plugin.images.LocalImagePreview;
import se.tmm.sensorplanner.plugin.local.BearingMilsEdit;
import se.tmm.sensorplanner.plugin.local.BearingDirectionView;

/** Local prototype; API 26 and session access are enforced by the entry point. */
@TargetApi(26)
final class LocalPlanner {
    private static final DeviceType[] TYPES = {DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY};
    private static final String[] STATUSES = {"Planerad", "Utplacerad", "Aktiv", "Intagen", "Avbruten"};
    private final MapView map;
    private final Context context;
    private final IHostUIService ui;
    private final DraftStore store;
    private final BooleanSupplier localAllowed;
    private final BooleanSupplier labAllowed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DraftAttachments attachments;
    private final LocalImageUi imagesUi;
    private final LocalShareUi sharing;
    private LocalDraftLayer layer;
    private List<TmmDraft> drafts = Collections.emptyList();
    private AlertDialog dialog;
    private AlertDialog confirmation;
    private MapPositionPicker positionPicker;
    private volatile boolean closed;
    private boolean loading;
    private boolean showAfterLoad;

    LocalPlanner(MapView map, IHostUIService ui, BooleanSupplier localAllowed, BooleanSupplier labAllowed,
                 String imageCopyNotice) throws Exception {
        this.localAllowed = localAllowed;
        this.labAllowed = labAllowed;
        this.map = map;
        this.context = map.getContext();
        this.ui = ui;
        // The plugin executes as ATAK's UID; no-backup host storage avoids shared files and backups.
        store = new DraftStore(new java.io.File(context.getNoBackupFilesDir(),
                "se.tmm.sensorplanner/local-drafts-v1").toPath());
        LocalImages images = new LocalImages(new java.io.File(context.getNoBackupFilesDir(),
                "se.tmm.sensorplanner/local-images-v1").toPath());
        attachments = new DraftAttachments(store, images);
        imagesUi = new LocalImageUi(context, imageCopyNotice, worker, images, attachments, new LocalImageUi.Listener() {
            @Override public boolean available() { return !closed && localAllowed.getAsBoolean(); }
            @Override public void committed(DraftAttachments.Result result, UUID uid) {
                if (closed) return;
                refresh(result.drafts);
                for (TmmDraft saved : drafts) if (saved.uid.equals(uid)) { review(saved); break; }
                if (result.cleanupFailed) ui.showToast("Ändringen är sparad men en äldre bildkopia kunde inte tas bort");
            }
            @Override public void failed(String message) { if (!closed) alert("Bilden kunde inte hanteras", message); }
        });
        sharing = new LocalShareUi(context, ui, store, worker, () -> !closed && labAllowed.getAsBoolean());
    }

    private void selected(UUID uid) {
        if (!closed && !loading && !isBusy() && (dialog == null || !dialog.isShowing()) && localAllowed.getAsBoolean()) {
            for (TmmDraft draft : drafts) if (draft.uid.equals(uid)) {
                review(draft);
                break;
            }
        }
    }

    void restore() { load(false); }

    void show() { load(true); }

    private void load(boolean showList) {
        if (closed) return;
        if (!localAllowed.getAsBoolean()) {
            if (showList) localAccess();
            return;
        }
        if (loading) { showAfterLoad |= showList; return; }
        if (isBusy() || (dialog != null && dialog.isShowing())) return;
        loading = true;
        showAfterLoad = showList;
        if (showList) dismiss();
        worker.execute(() -> {
            try {
                List<TmmDraft> loaded = store.load();
                ui.queueEvent(() -> {
                    if (closed) return;
                    loading = false;
                    boolean openList = showAfterLoad;
                    showAfterLoad = false;
                    // Session access may have ended while the worker read storage.
                    if (!localAllowed.getAsBoolean()) {
                        clearLayer();
                        if (openList) localAccess();
                        return;
                    }
                    refresh(loaded);
                    if (openList) list();
                });
            } catch (Exception failure) {
                ui.queueEvent(() -> {
                    loading = false;
                    if (closed) return;
                    boolean openList = showAfterLoad;
                    showAfterLoad = false;
                    clearLayer();
                    if (openList) {
                        alert("Lagringen kunde inte öppnas",
                                "Befintliga data har inte återställts eller skrivits över. Försök öppna igen. "
                                + "Om felet kvarstår behövs felsökning innan du fortsätter.");
                    } else {
                        ui.showToast("Lokala markörer kunde inte återställas. Sparade data har inte ändrats. Öppna TMM för att försöka igen.");
                    }
                });
            }
        });
    }

    private void clearLayer() {
        drafts = Collections.emptyList();
        if (layer != null) layer.close();
        layer = null;
    }

    private boolean localAccess() {
        if (localAllowed.getAsBoolean()) return true;
        ui.showToast("Öppna TMM och välj lokalt test, eller använd offline-läge");
        return false;
    }

    private void list() {
        if (!localAccess() || closed || isBusy()) return;
        String[] rows = new String[drafts.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = title(drafts.get(i)) + "\nLokalt utkast";
        dismiss();
        dialog = new AlertDialog.Builder(context).setTitle("TMM – lokala utkast (" + rows.length + ")"
                        + (labAllowed.getAsBoolean() ? "" : " · testläge"))
                .setItems(rows, (ignored, index) -> review(drafts.get(index)))
                .setPositiveButton("Skapa enhet", (ignored, which) -> form(null))
                .setNeutralButton("Uppdatera", (ignored, which) -> { dismiss(); show(); })
                .setNegativeButton("Stäng", null).create();
        dialog.show();
    }

    private void review(TmmDraft draft) {
        if (closed || !localAccess()) return;
        LinearLayout content = column();
        TextView status = label(draft.type.displayName + " · Status: " + STATUSES[draft.status.ordinal()]);
        status.setTextSize(18);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(status);
        content.addView(label("Lokalt utkast · delas inte automatiskt"));
        content.addView(label("Position · MGRS\n" + positionText(draft.position)));
        Button copy = button("Kopiera MGRS", () -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) throw new IllegalStateException();
                clipboard.setPrimaryClip(ClipData.newPlainText("MGRS", MgrsCoordinates.format(draft.position)));
                ui.showToast("MGRS kopierad");
            } catch (RuntimeException failure) { ui.showToast("MGRS kunde inte kopieras"); }
        });
        copy.setEnabled(draft.position.latitude >= -80 && draft.position.latitude <= 84);
        content.addView(copy);
        content.addView(buttonRow(button("Visa på kartan", () -> {
            try { if (layer == null) throw new IllegalStateException(); layer.center(draft); dismiss(); }
            catch (RuntimeException failure) { ui.showToast("Kartan kunde inte centreras"); }
        }), button("Flytta på kartan", () -> form(draft, true))));
        content.addView(label("Bild · " + (draft.imageRef == null ? "Ingen lokal bild" : "Sparad lokalt")));
        LocalImagePreview preview = draft.imageRef == null ? null : imagesUi.preview(draft);
        if (preview != null) {
            content.addView(preview);
            content.addView(buttonRow(button("Visa bild", () -> imagesUi.view(draft)),
                    button("Ersätt bild", () -> imagesUi.pick(draft))));
        } else content.addView(button("Lägg till bild", () -> imagesUi.pick(draft)));
        if (draft.type.supportsBearing()) {
            LinearLayout bearing = new LinearLayout(context);
            bearing.addView(label("Bäring: " + (draft.bearingDeg == null ? "Ej angiven" :
                    BearingMilsEdit.format(draft.bearingDeg) + " mils") + "\nSann nord · N uppåt"),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            bearing.addView(new BearingDirectionView(context, draft.bearingDeg), new LinearLayout.LayoutParams(dp(80), dp(80)));
            content.addView(bearing);
        }
        if (DraftForm.hasDuplicateId(draft.displayId, draft.uid, drafts)) {
            content.addView(label("Varning: samma synliga ID finns på en annan enhet."));
        }
        content.addView(label("Ägare: " + draft.owner + "\nKommentar: " + (draft.comment.isEmpty() ? "Ingen" : draft.comment)
                + "\nUtplacerad: " + (draft.deployedAt == null ? "Ej angivet" : localTime(draft.deployedAt))));
        Optional<DistanceCalculator.Distance> nearest = DistanceCalculator.nearestOther(draft, drafts);
        StringBuilder distances = new StringBuilder("Närmaste annan TMM: ")
                .append(nearest.isPresent() ? distance(nearest.get()) : "Ingen annan behörig enhet");
        if (draft.type == DeviceType.GATEWAY) {
            distances.append("\nAndra gateways:");
            List<DistanceCalculator.Distance> gateways = DistanceCalculator.otherGateways(draft, drafts);
            if (gateways.isEmpty()) distances.append(" Inga");
            for (DistanceCalculator.Distance other : gateways) distances.append("\n").append(distance(other));
        }
        content.addView(label(distances + "\nAvstånd är kartmått, inte radiotäckning."));
        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(label("Skapad: " + localTime(draft.createdAt) + "\nÄndrad: " + localTime(draft.updatedAt)
                + "\nRevision: " + draft.revision + "\nLokal = utkast på denna enhet. Serveranslutning innebär inte synkronisering eller bekräftad delning."
                + "\nEndast syntetiska testdata i denna prototyp.\nKartpilen är en 100 m riktningsmarkering från sann nord, inte sensorräckvidd."));
        if (draft.imageRef != null) details.addView(button("Ta bort bild", () -> imagesUi.detach(draft)));
        if (labAllowed.getAsBoolean()) details.addView(button("Dela metadata i isolerat labb", () -> sharing.show(draft)));
        details.addView(button("Ta bort lokalt utkast", () -> confirmDelete(draft)));
        details.setVisibility(View.GONE);
        Button expand = button("Fler uppgifter", () -> { });
        expand.setOnClickListener(view -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            expand.setText(show ? "Färre uppgifter" : "Fler uppgifter");
        });
        content.addView(expand);
        content.addView(details);
        TextView heading = label(draft.displayId);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(dp(16), dp(12), dp(16), dp(4));
        dismiss();
        dialog = new AlertDialog.Builder(context).setCustomTitle(heading)
                .setView(scroll(content)).setPositiveButton("Redigera", (ignored, which) -> form(draft))
                .setNegativeButton("Till listan", (ignored, which) -> list()).create();
        dialog.setOnDismissListener(ignored -> { if (preview != null) preview.close(); });
        dialog.show();
    }

    private LinearLayout buttonRow(Button first, Button second) {
        LinearLayout row = new LinearLayout(context);
        for (Button button : new Button[]{first, second}) row.addView(button,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void form(TmmDraft original) { form(original, false); }

    private void form(TmmDraft original, boolean chooseOnOpen) {
        if (closed || isBusy() || !localAccess()) return;
        LinearLayout fields = column();
        fields.addView(label("Lokalt utkast – delas inte. Använd endast syntetiska testdata.\n"
                + "Position anges i MGRS (5 + 5 siffror, 1 m ruta). Välj även plats på kartan."));
        Map<DraftForm.Field, EditText> inputs = new EnumMap<>(DraftForm.Field.class);
        EditText id = field(fields, inputs, DraftForm.Field.DISPLAY_ID, "Enhetens ID", original == null ? "" : original.displayId);
        Spinner type = spinner(fields, "Typ", new String[]{"Mini", "Scout", "PIR", "Gateway"});
        if (original != null) type.setSelection(Arrays.asList(TYPES).indexOf(original.type));
        Spinner status = spinner(fields, "Status", STATUSES);
        status.setSelection(original == null ? 0 : original.status.ordinal());
        GeoPoint center = map.getPoint().get();
        MgrsPositionEdit position = new MgrsPositionEdit(original == null
                ? new Position(center.getLatitude(), center.getLongitude()) : original.position, original != null);
        EditText mgrs = field(fields, inputs, DraftForm.Field.LATITUDE,
                "MGRS (zon, ruta, 5 + 5 siffror)", position.text());
        Button choosePosition = button("Välj plats på kartan", () -> { });
        fields.addView(choosePosition);
        BearingMilsEdit bearingEdit = new BearingMilsEdit(original == null ? null : original.bearingDeg);
        EditText bearing = field(fields, inputs, DraftForm.Field.BEARING, "Bäring", bearingEdit.text());
        bearing.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        bearing.setHint("0–6400 mils");
        View bearingLabel = fields.getChildAt(fields.indexOfChild(bearing) - 1);
        TextView bearingHint = label("Valfri · hela mils (6400 = 0) · från sann nord");
        fields.addView(bearingHint);
        BearingDirectionView direction = new BearingDirectionView(context, original == null ? null : original.bearingDeg);
        fields.addView(direction, new LinearLayout.LayoutParams(dp(80), dp(80)));
        bearing.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                try { direction.setBearing(bearingEdit.degrees(text.toString())); }
                catch (IllegalArgumentException invalid) { direction.setBearing(null); }
            }
        });
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
                boolean visible = TYPES[position].supportsBearing() || !value(bearing).trim().isEmpty();
                bearing.setVisibility(visible ? View.VISIBLE : View.GONE);
                bearingLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
                bearingHint.setVisibility(visible ? View.VISIBLE : View.GONE);
                direction.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        EditText owner = field(fields, inputs, DraftForm.Field.OWNER, "Ägare (grupp/roll)", original == null ? "" : original.owner);
        DeploymentTimeInput deployed = new DeploymentTimeInput(context, original == null ? null : original.deployedAt);
        fields.addView(deployed);
        EditText comment = field(fields, inputs, DraftForm.Field.COMMENT, "Kommentar (max 2000)", original == null ? "" : original.comment);
        comment.setSingleLine(false);
        comment.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        comment.setMinLines(2);
        comment.setMaxLines(3);
        TextView formError = label("");
        fields.addView(formError);
        dismiss();
        AlertDialog editor = new AlertDialog.Builder(context).setTitle(original == null ? "Skapa enhet" : "Redigera " + original.displayId)
                .setView(scroll(fields)).setPositiveButton("Spara lokalt", null)
                .setNegativeButton("Avbryt", (ignored, which) -> list()).create();
        dialog = editor;
        editor.setOnDismissListener(ignored -> deployed.close());
        editor.setCanceledOnTouchOutside(false);
        editor.show();
        choosePosition.setOnClickListener(ignored -> {
            if (closed || isBusy() || !localAccess()) return;
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(mgrs.getWindowToken(), 0);
            try {
                Position start = position.resolve(value(mgrs));
                MgrsCoordinates.format(start);
                map.getMapController().panZoomTo(new GeoPoint(start.latitude, start.longitude), 0.0001, false);
            } catch (IllegalArgumentException invalidText) {
                // The map can replace invalid coordinate text without discarding the other form fields.
            }
            editor.setCancelable(false);
            editor.hide();
            positionPicker = new MapPositionPicker(map, value(id).isEmpty() ? "ny enhet" : value(id), point -> {
                if (closed || !localAccess()) return;
                position.accept(point);
                mgrs.setText(position.text());
                mgrs.setError(null);
            }, () -> {
                positionPicker = null;
                if (!closed) { editor.setCancelable(true); editor.show(); }
            });
            try { positionPicker.show(); }
            catch (RuntimeException failure) {
                positionPicker.close();
                positionPicker = null;
                if (!closed) { editor.setCancelable(true); editor.show(); mgrs.setError("Kartvalet kunde inte öppnas. Positionen är oförändrad."); }
            }
        });
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (closed || !localAccess()) return;
            for (EditText input : inputs.values()) input.setError(null);
            formError.setText("");
            Position selected;
            try { selected = position.resolve(value(mgrs)); }
            catch (IllegalArgumentException invalid) {
                mgrs.setError("Ange giltig MGRS med zon, ruta och 5 + 5 siffror (80°S–84°N), eller välj plats på kartan.");
                mgrs.requestFocus();
                return;
            }
            String bearingDegrees;
            try { bearingDegrees = bearingEdit.degreesText(value(bearing)); }
            catch (IllegalArgumentException invalid) {
                bearing.setError("Ange ett heltal mellan 0 och 6400 mils, eller lämna tomt. 6400 betyder 0.");
                bearing.requestFocus();
                return;
            }
            DraftForm.Result result = DraftForm.validate(original, value(id), TYPES[type.getSelectedItemPosition()],
                    DeviceStatus.values()[status.getSelectedItemPosition()], value(owner), deployed.utcText(), value(comment),
                    DraftForm.decimalText(selected.latitude), DraftForm.decimalText(selected.longitude), bearingDegrees, Instant.now());
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
        if (chooseOnOpen) choosePosition.post(() -> choosePosition.performClick());
    }

    private void save(TmmDraft draft, AlertDialog editor, LinearLayout fields, TextView error) {
        if (closed || !editor.isShowing() || !localAccess()) return;
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
                            + "Öppna listan och välj Uppdatera innan du försöker igen; lagringen kan ha ändrats.");
                    editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                });
            }
        });
    }

    private void confirmDelete(TmmDraft draft) {
        if (closed || isBusy() || !localAccess()) return;
        dismiss();
        dialog = new AlertDialog.Builder(context).setTitle("Ta bort lokalt utkast?")
                .setMessage("Detta tar bara bort det lokala utkastet. Ingen server eller mottagare påverkas.")
                .setNegativeButton("Avbryt", (ignored, which) -> review(draft))
                .setPositiveButton("Ta bort", (ignored, which) -> {
                    if (closed || !localAccess()) return;
                    worker.execute(() -> {
                        try {
                            DraftAttachments.Result result = attachments.delete(draft);
                            ui.queueEvent(() -> { if (!closed) {
                                refresh(result.drafts); list();
                                if (result.cleanupFailed) ui.showToast("Utkastet är borttaget men bildkopian kunde inte tas bort");
                            } });
                        } catch (Exception failure) {
                            ui.queueEvent(() -> { if (!closed) alert("Borttagningen kunde inte bekräftas", "Välj Uppdatera i listan innan du fortsätter."); });
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
                           DraftForm.Field key, String title, String initial) {
        TextView label = label(title);
        parent.addView(label);
        EditText input = new EditText(context) {
            @Override public android.view.inputmethod.InputConnection onCreateInputConnection(EditorInfo info) {
                android.view.inputmethod.InputConnection connection = super.onCreateInputConnection(info);
                // Multiline comments still use Done on the soft keyboard; hardware Enter inserts a newline.
                info.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                return connection;
            }

            @Override public void getFocusedRect(android.graphics.Rect rect) {
                super.getFocusedRect(rect);
                // Include the adjacent label when the dialog scrolls for focus or a resized keyboard.
                rect.top = -label.getHeight();
            }
        };
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setContentDescription(title);
        input.setMinHeight(dp(48));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setOnEditorActionListener((view, action, event) -> {
            boolean enter = key != DraftForm.Field.COMMENT && event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;
            if (action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            if (enter && event.getAction() != android.view.KeyEvent.ACTION_UP) return true;
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            return true;
        });
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
        button.setOnClickListener(view -> { if (!closed && !isBusy() && localAccess()) action.run(); });
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
            case INVALID_TIMESTAMP: return "Välj ett giltigt datum och klockslag, eller rensa utplaceringstiden";
            case COMMENT_TOO_LONG: return "Kommentaren är för lång (max 2000)";
            case CLOCK_BEFORE_UPDATE: return "Klockan är före senaste ändringen. Kontrollera enhetens tid.";
            case REVISION_EXHAUSTED: return "Revisionen kan inte ökas. Utkastet har inte ändrats.";
            default: return "Kontrollera fältet";
        }
    }

    private static String positionText(Position position) {
        try { return MgrsCoordinates.format(position); }
        catch (IllegalArgumentException outsideUtm) { return "Utanför MGRS-området 80°S–84°N"; }
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

    private boolean isBusy() { return positionPicker != null || imagesUi.isBusy() || sharing.isBusy(); }

    void cancelSharing() { sharing.stopSending(); }

    void disableSharing() { sharing.close(); }

    void close() {
        closed = true;
        if (positionPicker != null) { positionPicker.close(); positionPicker = null; }
        dismiss();
        sharing.close();
        imagesUi.close();
        worker.shutdown();
        if (layer != null) layer.close();
    }
}
