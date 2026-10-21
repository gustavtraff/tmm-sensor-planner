package se.tmm.sensorplanner.plugin.local;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Read-only north-up cue. This is true-north bearing, independent of map/device rotation. */
public final class BearingDirectionView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Double degrees;

    public BearingDirectionView(Context context, Double degrees) {
        super(context);
        setBearing(degrees);
    }

    public void setBearing(Double degrees) {
        this.degrees = degrees;
        setContentDescription(degrees == null ? "Ingen bäring angiven" :
                "Bäring " + BearingMilsEdit.format(degrees) + " mils från sann nord. N uppåt.");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float x = getWidth() / 2f, y = getHeight() / 2f + size * 0.08f, radius = size * 0.32f;
        paint.setColor(Color.GRAY);
        paint.setStrokeWidth(size * 0.025f);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size * 0.17f);
        canvas.drawText("N", x, size * 0.18f, paint);
        if (degrees == null) return;
        paint.setColor(Color.rgb(0, 160, 200));
        paint.setStrokeWidth(size * 0.035f);
        canvas.save();
        canvas.rotate(degrees.floatValue(), x, y);
        canvas.drawLine(x, y, x, y - radius * 0.85f, paint);
        canvas.drawLine(x, y - radius * 0.85f, x - radius * 0.2f, y - radius * 0.55f, paint);
        canvas.drawLine(x, y - radius * 0.85f, x + radius * 0.2f, y - radius * 0.55f, paint);
        canvas.restore();
    }
}
