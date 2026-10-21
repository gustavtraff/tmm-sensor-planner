package se.tmm.sensorplanner.images;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.InputDevice;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZoomImageViewTest {
    @Test public void fitZoomPanBoundsAndResizeKeepTheImageReachable() {
        Bitmap source = Bitmap.createBitmap(new int[]{Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW},
                4, 1, Bitmap.Config.ARGB_8888);
        ZoomImageView view = new ZoomImageView(RuntimeEnvironment.getApplication(), source);
        view.layout(0, 0, 200, 200);
        Bitmap rendered = render(view, 200, 200);
        assertEquals(Color.BLACK, rendered.getPixel(25, 10));
        assertEquals(Color.RED, rendered.getPixel(25, 100));
        rendered.recycle();
        view.zoomBy(2);
        rendered = render(view, 200, 200);
        assertEquals(Color.GREEN, rendered.getPixel(50, 100));
        assertEquals(Color.BLUE, rendered.getPixel(150, 100));
        rendered.recycle();
        touch(view, 0, MotionEvent.ACTION_DOWN, 100, 100);
        touch(view, 20, MotionEvent.ACTION_MOVE, 5000, 100);
        touch(view, 40, MotionEvent.ACTION_UP, 5000, 100);
        rendered = render(view, 200, 200);
        assertEquals(Color.RED, rendered.getPixel(50, 100));
        rendered.recycle();
        touch(view, 500, MotionEvent.ACTION_DOWN, 100, 100);
        touch(view, 520, MotionEvent.ACTION_MOVE, -5000, 100);
        touch(view, 540, MotionEvent.ACTION_UP, -5000, 100);
        rendered = render(view, 200, 200);
        assertEquals(Color.BLUE, rendered.getPixel(50, 100));
        rendered.recycle();
        view.fit();
        view.zoomBy(Float.NaN);
        view.layout(0, 0, 100, 300);
        rendered = render(view, 100, 300);
        assertEquals(Color.BLACK, rendered.getPixel(50, 20));
        assertEquals(Color.RED, rendered.getPixel(12, 150));
        rendered.recycle();
        view.clearImage();
        rendered = render(view, 100, 300);
        assertEquals(Color.BLACK, rendered.getPixel(50, 150));
        assertFalse(source.isRecycled());
        rendered.recycle();
        source.recycle();
    }

    @Test public void twoFingerPinchChangesTheRenderedScale() {
        Bitmap source = Bitmap.createBitmap(new int[]{Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW},
                4, 1, Bitmap.Config.ARGB_8888);
        ZoomImageView view = new ZoomImageView(RuntimeEnvironment.getApplication(), source);
        view.layout(0, 0, 400, 400);
        Bitmap before = render(view, 400, 400);
        pinch(view, 0, MotionEvent.ACTION_DOWN, 1, 120, 280);
        pinch(view, 20, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 2, 120, 280);
        pinch(view, 40, MotionEvent.ACTION_MOVE, 2, 80, 320);
        pinch(view, 60, MotionEvent.ACTION_MOVE, 2, 20, 380);
        pinch(view, 80, MotionEvent.ACTION_POINTER_UP | (1 << 8), 2, 20, 380);
        pinch(view, 100, MotionEvent.ACTION_UP, 1, 20, 380);
        Bitmap after = render(view, 400, 400);
        int[] original = new int[400 * 400];
        int[] zoomed = new int[original.length];
        before.getPixels(original, 0, 400, 0, 0, 400, 400);
        after.getPixels(zoomed, 0, 400, 0, 0, 400, 400);
        assertFalse(Arrays.equals(original, zoomed));
        before.recycle(); after.recycle(); source.recycle();
    }

    private static void pinch(ZoomImageView view, long time, int action, int count, float left, float right) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = i == 0 ? left : right;
            coords[i].y = 200;
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(0, time, action, count, properties, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    private static Bitmap render(ZoomImageView view, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(result));
        return result;
    }

    private static void touch(ZoomImageView view, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }
}
