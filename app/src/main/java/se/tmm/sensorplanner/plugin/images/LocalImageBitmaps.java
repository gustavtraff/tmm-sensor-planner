package se.tmm.sensorplanner.plugin.images;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;

/** Decode limits apply to display memory, independently of stored-image dimensions. */
public final class LocalImageBitmaps {
    private LocalImageBitmaps() { }
    public static Bitmap preview(byte[] bytes) throws IOException { return decode(bytes, 1024); }
    public static Bitmap inspection(byte[] bytes) throws IOException { return decode(bytes, 2048); }

    private static Bitmap decode(byte[] bytes, int longestSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Invalid local image");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while (((long) Math.max(bounds.outWidth, bounds.outHeight) + options.inSampleSize - 1) / options.inSampleSize > longestSide) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Local image could not be decoded");
        if (bitmap.getWidth() > longestSide || bitmap.getHeight() > longestSide) {
            bitmap.recycle();
            throw new IOException("Local display image exceeds its memory bound");
        }
        return bitmap;
    }
}
