package se.tmm.sensorplanner.plugin.local;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
public class BearingDirectionViewTest {
    @Test public void trueNorthUpAndClockwiseCardinalsRenderAtTheirExpectedEnds() {
        BearingDirectionView view = new BearingDirectionView(RuntimeEnvironment.getApplication(), 0d);
        view.layout(0, 0, 100, 100);
        int[][] points = {{50, 32}, {76, 58}, {50, 84}, {24, 58}};
        for (int i = 0; i < 4; i++) {
            view.setBearing(i * 90d);
            Bitmap output = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(output));
            int color = output.getPixel(points[i][0], points[i][1]);
            assertTrue(Color.alpha(color) > 200 && Color.red(color) < 10 && Color.green(color) > 100 && Color.blue(color) > 100);
            output.recycle();
        }
        view.setBearing(null);
        Bitmap empty = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(empty));
        assertEquals(Color.TRANSPARENT, empty.getPixel(50, 58));
        empty.recycle();
    }
}
