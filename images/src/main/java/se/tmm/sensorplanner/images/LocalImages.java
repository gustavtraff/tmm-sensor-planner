package se.tmm.sensorplanner.images;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/** Local-only, API 26+ image copies. Call off the UI thread with a private no-backup directory. */
@TargetApi(26)
public final class LocalImages {
    public static final int MAX_COPY_EDGE = 1920;
    private final Path directory;

    public LocalImages(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** Reads but does not close the caller's stream; no original bytes are written to storage. */
    public Pending prepare(InputStream source) throws IOException {
        byte[] normalized;
        try {
            normalized = normalize(readBounded(source, ImageFormat.MAX_BYTES));
        } catch (OutOfMemoryError e) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new ImagePreparationException(ImagePreparationException.Reason.SAVE_FAILED);
        }
        String reference = UUID.randomUUID() + ".png";
        Path destination = resolve(reference);
        boolean created = false;
        try (FileChannel output = FileChannel.open(destination,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            ByteBuffer bytes = ByteBuffer.wrap(normalized);
            while (bytes.hasRemaining()) output.write(bytes);
            output.force(true);
        } catch (IOException e) {
            if (created) Files.deleteIfExists(destination);
            throw new ImagePreparationException(ImagePreparationException.Reason.SAVE_FAILED);
        }
        return new Pending(reference);
    }

    /** Reopens a bounded owned PNG for preview; absence/corruption never clears draft metadata. */
    public byte[] read(String reference) throws IOException {
        Path file = resolve(reference);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The local image is missing or unavailable");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = readBounded(input, ImageFormat.MAX_OWNED_BYTES);
        }
        ImageFormat.checkOwnedPng(bytes);
        return bytes;
    }

    /** Only after a successful metadata detach/replacement and a fresh unreferenced check. */
    public void deleteUnreferenced(String reference) throws IOException {
        Path file = resolve(reference);
        if (Files.isSymbolicLink(file)) throw new IOException("The local image is unavailable");
        Files.deleteIfExists(file);
    }

    @FunctionalInterface
    public interface Commit {
        void save(String reference) throws IOException;
    }

    public final class Pending implements AutoCloseable {
        private final String reference;
        private boolean retained;
        private boolean closed;

        private Pending(String reference) { this.reference = reference; }

        /** Preview the normalized copy, then either commit or close to cancel. */
        public synchronized byte[] preview() throws IOException {
            if (closed) throw new IOException("Image preview is closed");
            return read(reference);
        }

        /** On an ambiguous metadata-save failure retain both copies; reload before any cleanup. */
        public synchronized void commit(Commit commit) throws IOException {
            if (closed || retained) throw new IOException("Image preview is already finished");
            Objects.requireNonNull(commit, "commit");
            retained = true;
            commit.save(reference);
        }

        @Override public synchronized void close() throws IOException {
            if (closed) return;
            if (!retained) deleteUnreferenced(reference);
            closed = true;
        }
    }

    private Path resolve(String reference) throws IOException {
        if (reference == null || !reference.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) {
            throw new IOException("Invalid local image reference");
        }
        return directory.resolve(reference);
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk, 0, Math.min(chunk.length,
                limit + 1 - bytes.size()))) != -1) {
            if (bytes.size() + count > limit) {
                throw new ImagePreparationException(ImagePreparationException.Reason.INPUT_TOO_LARGE);
            }
            bytes.write(chunk, 0, count);
        }
        return bytes.toByteArray();
    }

    private static byte[] normalize(byte[] source) throws IOException {
        int primaryLength = ImageFormat.primaryLength(source);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, primaryLength, bounds);
        ImageFormat.dimensions(bounds.outWidth, bounds.outHeight);
        if (!"image/jpeg".equals(bounds.outMimeType) && !"image/png".equals(bounds.outMimeType)) {
            throw ImageFormat.invalid();
        }
        int orientation = new ExifInterface(new ByteArrayInputStream(source, 0, primaryLength))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (orientation < 0 || orientation > 8) throw ImageFormat.invalid();
        Bitmap decoded = null;
        Bitmap clean = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            // Decode near the copy size, then draw orientation/scaling into one clean bitmap.
            while (longest / (options.inSampleSize * 2) >= MAX_COPY_EDGE) options.inSampleSize *= 2;
            decoded = BitmapFactory.decodeByteArray(source, 0, primaryLength, options);
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0
                    || decoded.getWidth() > (bounds.outWidth + options.inSampleSize - 1) / options.inSampleSize
                    || decoded.getHeight() > (bounds.outHeight + options.inSampleSize - 1) / options.inSampleSize) {
                throw ImageFormat.invalid();
            }
            Matrix transform = orientation(orientation);
            RectF area = new RectF(0, 0, decoded.getWidth(), decoded.getHeight());
            transform.mapRect(area);
            float scale = Math.min(1f, MAX_COPY_EDGE / Math.max(area.width(), area.height()));
            int width = Math.max(1, Math.round(area.width() * scale));
            int height = Math.max(1, Math.round(area.height() * scale));
            clean = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            transform.postTranslate(-area.left, -area.top);
            transform.postScale(width / area.width(), height / area.height());
            new Canvas(clean).drawBitmap(decoded, transform, new Paint(Paint.FILTER_BITMAP_FLAG));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            boolean[] outputTooLarge = {false};
            OutputStream bounded = new OutputStream() {
                @Override public void write(int value) throws IOException {
                    if (bytes.size() == ImageFormat.MAX_OWNED_BYTES) {
                        outputTooLarge[0] = true;
                        throw new ImagePreparationException(ImagePreparationException.Reason.OUTPUT_TOO_LARGE);
                    }
                    bytes.write(value);
                }
                @Override public void write(byte[] chunk, int offset, int length) throws IOException {
                    if (length > ImageFormat.MAX_OWNED_BYTES - bytes.size()) {
                        outputTooLarge[0] = true;
                        throw new ImagePreparationException(ImagePreparationException.Reason.OUTPUT_TOO_LARGE);
                    }
                    bytes.write(chunk, offset, length);
                }
            };
            if (!clean.compress(Bitmap.CompressFormat.PNG, 100, bounded)) {
                // Some Android codecs return false instead of propagating the stream exception.
                if (outputTooLarge[0]) throw new ImagePreparationException(ImagePreparationException.Reason.OUTPUT_TOO_LARGE);
                throw ImageFormat.invalid();
            }
            byte[] result = bytes.toByteArray();
            ImageFormat.checkOwnedPng(result);
            return result;
        } finally {
            if (clean != null) clean.recycle();
            if (decoded != null) decoded.recycle();
        }
    }

    private static Matrix orientation(int value) {
        float[][] values = {
            {1, 0, 0, 0, 1, 0, 0, 0, 1},
            {-1, 0, 0, 0, 1, 0, 0, 0, 1},
            {-1, 0, 0, 0, -1, 0, 0, 0, 1},
            {1, 0, 0, 0, -1, 0, 0, 0, 1},
            {0, 1, 0, 1, 0, 0, 0, 0, 1},
            {0, -1, 0, 1, 0, 0, 0, 0, 1},
            {0, -1, 0, -1, 0, 0, 0, 0, 1},
            {0, 1, 0, -1, 0, 0, 0, 0, 1}
        };
        Matrix matrix = new Matrix();
        matrix.setValues(values[value <= 1 ? 0 : value - 1]);
        return matrix;
    }
}
