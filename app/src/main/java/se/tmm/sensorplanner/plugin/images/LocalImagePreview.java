package se.tmm.sensorplanner.plugin.images;

import android.content.Context;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Owner-scoped read-only thumbnail, decoded on the existing serialized image worker. */
@TargetApi(26)
public final class LocalImagePreview extends LinearLayout implements AutoCloseable {
    private final ImageView image;
    private final TextView state;
    private Bitmap bitmap;
    private volatile boolean closed;

    public LocalImagePreview(Context context, Executor worker, Callable<byte[]> read,
                             BooleanSupplier available, String description, Runnable open) {
        super(context);
        setOrientation(VERTICAL);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(description);
        image.setVisibility(GONE);
        image.setOnClickListener(ignored -> { if (!closed && available.getAsBoolean()) open.run(); });
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT,
                Math.round(140 * getResources().getDisplayMetrics().density)));
        state = new TextView(context);
        state.setText("Laddar lokal förhandsvisning…");
        addView(state);
        Handler main = new Handler(Looper.getMainLooper());
        worker.execute(() -> {
            if (closed || !available.getAsBoolean()) return;
            try {
                Bitmap decoded = LocalImageBitmaps.preview(read.call());
                main.post(() -> {
                    if (closed || !available.getAsBoolean()) { decoded.recycle(); return; }
                    bitmap = decoded;
                    image.setImageBitmap(decoded);
                    image.setVisibility(View.VISIBLE);
                    state.setVisibility(View.GONE);
                });
            } catch (Exception | OutOfMemoryError failure) {
                main.post(() -> {
                    if (!closed) state.setText("Förhandsvisningen kunde inte visas. Försök med Visa bild.");
                });
            }
        });
    }

    @Override public void close() {
        closed = true;
        image.setImageDrawable(null);
        image.setOnClickListener(null);
        if (bitmap != null) { bitmap.recycle(); bitmap = null; }
    }
}
