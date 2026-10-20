package se.tmm.sensorplanner.images;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Displays a caller-owned bitmap; this view never changes or recycles it. */
@TargetApi(26)
public final class ZoomImageView extends View {
    private Bitmap bitmap;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final GestureDetector gestures;
    private final ScaleGestureDetector scales;
    private float scale;
    private float fitScale;
    private float left;
    private float top;

    public ZoomImageView(Context context, Bitmap bitmap) {
        super(context);
        this.bitmap = bitmap;
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        scales = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) { return performClick(); }
            @Override public boolean onDoubleTap(MotionEvent event) {
                if (scale > fitScale * 1.01f) fit();
                else zoom(2f, event.getX(), event.getY());
                return true;
            }
            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (!scales.isInProgress()) {
                    left -= dx;
                    top -= dy;
                    constrain();
                    invalidate();
                }
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) { fit(); }

    public void fit() {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        fitScale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        scale = fitScale;
        left = (getWidth() - bitmap.getWidth() * scale) / 2;
        top = (getHeight() - bitmap.getHeight() * scale) / 2;
        invalidate();
    }

    public void zoomBy(float factor) { zoom(factor, getWidth() / 2f, getHeight() / 2f); }

    private void zoom(float factor, float x, float y) {
        if (bitmap == null || fitScale <= 0 || !Float.isFinite(factor) || factor <= 0) return;
        float next = Math.max(fitScale, Math.min(fitScale * 8, scale * factor));
        float ratio = next / scale;
        left = x - (x - left) * ratio;
        top = y - (y - top) * ratio;
        scale = next;
        constrain();
        invalidate();
    }

    private void constrain() {
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        left = width <= getWidth() ? (getWidth() - width) / 2 : Math.max(getWidth() - width, Math.min(0, left));
        top = height <= getHeight() ? (getHeight() - height) / 2 : Math.max(getHeight() - height, Math.min(0, top));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || scale <= 0) return;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) return false;
        scales.onTouchEvent(event);
        gestures.onTouchEvent(event);
        return true;
    }

    public void clearImage() { bitmap = null; invalidate(); }

    @Override public boolean performClick() { return super.performClick(); }
}
