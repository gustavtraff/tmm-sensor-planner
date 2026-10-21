package se.tmm.sensorplanner.plugin.images;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LocalImageBitmapsTest {
    @Test public void inspectionKeepsStoredDetailWhileLegacyDecodesRemainBounded() throws Exception {
        for (int width : new int[]{1600, 4096}) {
            Bitmap original = Bitmap.createBitmap(width, 8, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, bytes));
            original.recycle();
            Bitmap preview = LocalImageBitmaps.preview(bytes.toByteArray());
            Bitmap inspection = LocalImageBitmaps.inspection(bytes.toByteArray());
            assertEquals(width == 1600 ? 800 : 1024, preview.getWidth());
            assertEquals(width == 1600 ? 1600 : 2048, inspection.getWidth());
            assertTrue(inspection.getAllocationByteCount() <= 16 * 1024 * 1024);
            preview.recycle();
            inspection.recycle();
        }
        Assert.assertThrows(IOException.class, () -> LocalImageBitmaps.inspection(new byte[0]));
    }
}
