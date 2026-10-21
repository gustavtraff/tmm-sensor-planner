package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import se.tmm.sensorplanner.plugin.images.ImagePreparationMessages;
import se.tmm.sensorplanner.plugin.images.LocalImageBitmaps;
import se.tmm.sensorplanner.images.ZoomImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistryOwner;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import se.tmm.sensorplanner.core.TmmDraft;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.DraftAttachments;

/** Saved-draft image actions; all file work shares the planner's serialized worker. */
@TargetApi(26)
final class LocalImageUi {
    interface Listener {
        boolean available();
        void committed(DraftAttachments.Result result, UUID uid);
        void failed(String message);
    }

    private final Context context;
    private final String imageCopyNotice;
    private final ExecutorService worker;
    private final LocalImages images;
    private final DraftAttachments attachments;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> launcher;
    private volatile boolean closed;
    private volatile boolean busy;
    private TmmDraft pickerTarget;
    private AlertDialog dialog;
    private Dialog viewer;
    private boolean viewerLoading;
    private LocalImages.Pending pending;

    LocalImageUi(Context context, String imageCopyNotice, ExecutorService worker, LocalImages images,
                 DraftAttachments attachments, Listener listener) {
        this.context = context;
        this.imageCopyNotice = imageCopyNotice;
        this.worker = worker;
        this.images = images;
        this.attachments = attachments;
        this.listener = listener;
        Context host = context;
        while (!(host instanceof ActivityResultRegistryOwner) && host instanceof ContextWrapper) {
            Context base = ((ContextWrapper) host).getBaseContext();
            if (base == host) break;
            host = base;
        }
        launcher = host instanceof ActivityResultRegistryOwner
                ? ((ActivityResultRegistryOwner) host).getActivityResultRegistry().register(
                    "se.tmm.sensorplanner.image." + UUID.randomUUID(),
                    new ActivityResultContracts.StartActivityForResult(), result -> {
                        TmmDraft target = pickerTarget;
                        pickerTarget = null;
                        if (closed || target == null) return;
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            busy = false;
                            return;
                        }
                        Intent data = result.getData();
                        Uri uri = data == null ? null : data.getData();
                        if (!listener.available() || uri == null || !"content".equals(uri.getScheme())) {
                            fail("Bilden kunde inte öppnas. Välj en lokal JPEG- eller PNG-fil.");
                            return;
                        }
                        prepare(target, uri);
                    }) : null;
    }

    boolean isBusy() { return busy; }

    void pick(TmmDraft draft) {
        if (!begin()) return;
        if (launcher == null) {
            fail("Bildväljaren stöds inte av denna ATAK-installation.");
            return;
        }
        pickerTarget = draft;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"})
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            launcher.launch(intent);
        } catch (RuntimeException failure) {
            pickerTarget = null;
            fail("Bildväljaren kunde inte öppnas.");
        }
    }

    private void prepare(TmmDraft draft, Uri uri) {
        worker.execute(() -> {
            LocalImages.Pending prepared = null;
            Bitmap bitmap = null;
            try {
                if (closed || !listener.available()) throw new IOException();
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException();
                    prepared = images.prepare(input);
                }
                bitmap = LocalImageBitmaps.preview(prepared.preview());
                synchronized (this) {
                    if (closed) {
                        bitmap.recycle();
                        prepared.close();
                        return;
                    }
                    pending = prepared;
                }
                Bitmap preview = bitmap;
                main.post(() -> {
                    if (closed) { preview.recycle(); return; }
                    if (!listener.available()) { releasePending(); preview.recycle(); busy = false; return; }
                    showPreview(draft, preview);
                });
            } catch (Exception | OutOfMemoryError failure) {
                if (bitmap != null) bitmap.recycle();
                closePending(prepared);
                String message = ImagePreparationMessages.forFailure(failure);
                main.post(() -> fail(message));
            }
        });
    }

    void view(TmmDraft draft) {
        if (!begin()) return;
        worker.execute(() -> {
            try {
                Bitmap bitmap = LocalImageBitmaps.inspection(images.read(draft.imageRef));
                main.post(() -> {
                    if (closed || !listener.available()) { bitmap.recycle(); busy = false; return; }
                    showViewer(draft, bitmap, null);
                });
            } catch (Exception | OutOfMemoryError failure) {
                main.post(() -> fail("Den lokala bilden saknas eller kan inte visas. Enhetens uppgifter och bildreferens behålls."));
            }
        });
    }

    private void showPreview(TmmDraft draft, Bitmap bitmap) {
        ImageView image = new ImageView(context);
        image.setContentDescription("Lokal bild för " + draft.displayId);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(context.getResources().getDisplayMetrics().heightPixels / 2);
        image.setImageBitmap(bitmap);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView notice = new TextView(context);
        notice.setText(imageCopyNotice);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        notice.setPadding(padding, padding, padding, padding);
        content.addView(notice);
        content.addView(image);
        image.setOnClickListener(ignored -> enlargePreview(draft));
        Button enlarge = new Button(context);
        enlarge.setText("Visa större bild");
        enlarge.setOnClickListener(ignored -> enlargePreview(draft));
        content.addView(enlarge);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("Granska lokal bild – delas inte")
                .setView(scroll).setNegativeButton("Avbryt", null);
        builder.setPositiveButton("Spara bilden lokalt", (ignored, which) -> {
            if (closed || !listener.available()) return;
            LocalImages.Pending selected;
            synchronized (this) { selected = pending; pending = null; }
            if (selected == null) return;
            worker.execute(() -> {
                try (LocalImages.Pending owned = selected) {
                    if (closed || !listener.available()) throw new IOException();
                    DraftAttachments.Result result = attachments.attach(draft, owned, Instant.now());
                    main.post(() -> complete(result, draft.uid));
                } catch (Exception failure) {
                    main.post(() -> saveFailed());
                }
            });
        });
        dialog = builder.create();
        dialog.setOnDismissListener(ignored -> {
            closeViewer();
            viewerLoading = false;
            image.setImageDrawable(null);
            bitmap.recycle();
            boolean cancelled;
            synchronized (this) { cancelled = pending != null; }
            if (cancelled) releasePending();
            if (cancelled) busy = false;
            dialog = null;
        });
        dialog.show();
    }

    void detach(TmmDraft draft) {
        if (!begin()) return;
        final boolean[] confirmed = {false};
        dialog = new AlertDialog.Builder(context).setTitle("Ta bort lokal bild?")
                .setMessage("Bildkopplingen tas bort från enheten. Originalfilen påverkas inte.")
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Ta bort bilden", (ignored, which) -> {
                    if (closed || !listener.available()) return;
                    confirmed[0] = true;
                    worker.execute(() -> {
                        try {
                            if (closed || !listener.available()) throw new IOException();
                            DraftAttachments.Result result = attachments.detach(draft, Instant.now());
                            main.post(() -> complete(result, draft.uid));
                        } catch (Exception failure) { main.post(() -> saveFailed()); }
                    });
                }).create();
        dialog.setOnDismissListener(ignored -> {
            dialog = null;
            if (!confirmed[0]) busy = false;
        });
        dialog.show();
    }

    private boolean begin() {
        if (closed || busy || !listener.available()) return false;
        busy = true;
        return true;
    }

    private void complete(DraftAttachments.Result result, UUID uid) {
        busy = false;
        if (!closed && listener.available()) listener.committed(result, uid);
    }

    private void saveFailed() {
        fail("Bildändringen kunde inte bekräftas. Öppna listan igen för att läsa aktuell lagring innan du försöker igen. Tidigare filer har bevarats om resultatet är osäkert.");
    }

    private void fail(String message) {
        busy = false;
        if (!closed) listener.failed(message);
    }

    private void enlargePreview(TmmDraft draft) {
        if (closed || viewer != null || viewerLoading || !listener.available()) return;
        AlertDialog owner = dialog;
        LocalImages.Pending selected;
        synchronized (this) { selected = pending; }
        if (owner == null || selected == null) return;
        viewerLoading = true;
        worker.execute(() -> {
            try {
                Bitmap bitmap = LocalImageBitmaps.inspection(selected.preview());
                main.post(() -> {
                    if (closed || dialog != owner || !listener.available()) { bitmap.recycle(); return; }
                    viewerLoading = false;
                    showViewer(draft, bitmap, owner);
                });
            } catch (Exception | OutOfMemoryError failure) {
                main.post(() -> {
                    if (closed || dialog != owner) return;
                    viewerLoading = false;
                    Toast.makeText(context, "Bilden kunde inte öppnas större. Förhandsgranskningen finns kvar.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showViewer(TmmDraft draft, Bitmap bitmap, AlertDialog owner) {
        Dialog full = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        full.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.BLACK);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(context);
        title.setText("Lokal bild – " + draft.displayId + "\nNyp eller dubbeltryck för zoom. Dra för att flytta bilden.");
        title.setTextColor(Color.WHITE);
        int padding = Math.round(12 * context.getResources().getDisplayMetrics().density);
        title.setPadding(padding, padding, padding, padding);
        content.addView(title);
        ZoomImageView image = new ZoomImageView(context, bitmap);
        image.setContentDescription("Lokal bild för " + draft.displayId);
        content.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout controls = new LinearLayout(context);
        for (String label : new String[]{"Zooma in", "Zooma ut", "Anpassa", "Stäng"}) {
            Button button = new Button(context);
            button.setText(label);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setOnClickListener(ignored -> {
                if (label.equals("Zooma in")) image.zoomBy(2);
                else if (label.equals("Zooma ut")) image.zoomBy(0.5f);
                else if (label.equals("Anpassa")) image.fit();
                else full.dismiss();
            });
            controls.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        content.addView(controls);
        full.setContentView(content);
        full.setOnDismissListener(ignored -> {
            image.clearImage();
            bitmap.recycle();
            if (viewer == full) viewer = null;
            if (owner == null) busy = false;
        });
        viewer = full;
        full.show();
        full.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        content.requestApplyInsets();
    }

    private void closeViewer() {
        Dialog current = viewer;
        viewer = null;
        if (current != null) current.dismiss();
    }

    private void releasePending() {
        LocalImages.Pending discarded;
        synchronized (this) { discarded = pending; pending = null; }
        if (discarded != null) worker.execute(() -> closePending(discarded));
    }

    private static void closePending(LocalImages.Pending discarded) {
        if (discarded != null) try { discarded.close(); } catch (IOException ignored) { }
    }

    void close() {
        synchronized (this) { closed = true; }
        pickerTarget = null;
        if (launcher != null) launcher.unregister();
        closeViewer();
        if (dialog != null) dialog.dismiss();
        releasePending();
    }
}
