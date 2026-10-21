package se.tmm.sensorplanner.plugin.images;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LocalImagePreviewTest {
    @Test public void closingReleasesTheDisplayedBitmapAndDropsAStaleWorkerResult() {
        Queue<Runnable> jobs = new ArrayDeque<>();
        byte[] source = png();
        LocalImagePreview preview = new LocalImagePreview(RuntimeEnvironment.getApplication(), jobs::add,
                () -> source, () -> true, "Synthetic preview", () -> { });
        jobs.remove().run();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        ImageView image = (ImageView) preview.getChildAt(0);
        Bitmap loaded = ((BitmapDrawable) image.getDrawable()).getBitmap();
        assertEquals(Color.RED, loaded.getPixel(0, 0));
        preview.close();
        assertNull(image.getDrawable());
        assertTrue(loaded.isRecycled());
        preview.close();
        LocalImagePreview stale = new LocalImagePreview(RuntimeEnvironment.getApplication(), jobs::add,
                () -> source, () -> true, "Synthetic preview", () -> { });
        jobs.remove().run();
        stale.close();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull(((ImageView) stale.getChildAt(0)).getDrawable());
    }

    @Test public void missingImageAndUnavailableOwnerNeverAttachPixelsOrDiscardSourceData() {
        Queue<Runnable> jobs = new ArrayDeque<>();
        LocalImagePreview missing = new LocalImagePreview(RuntimeEnvironment.getApplication(), jobs::add,
                () -> { throw new IOException("synthetic-private-reference"); }, () -> true, "Synthetic", () -> { });
        jobs.remove().run();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull(((ImageView) missing.getChildAt(0)).getDrawable());
        String message = ((TextView) missing.getChildAt(1)).getText().toString();
        assertTrue(message.contains("Visa bild"));
        assertFalse(message.contains("synthetic-private-reference"));
        byte[] source = png(), before = source.clone();
        int[] reads = {0};
        LocalImagePreview unavailable = new LocalImagePreview(RuntimeEnvironment.getApplication(), jobs::add,
                () -> { reads[0]++; return source; }, () -> false, "Synthetic", () -> { });
        jobs.remove().run();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull(((ImageView) unavailable.getChildAt(0)).getDrawable());
        assertArrayEquals(before, source);
        assertEquals(0, reads[0]);
        missing.close(); unavailable.close();
    }

    private static byte[] png() {
        Bitmap image = Bitmap.createBitmap(new int[]{Color.RED, Color.BLUE}, 2, 1, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, encoded));
        image.recycle();
        return encoded.toByteArray();
    }
}
