package cl.retrolink.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, local-only thumbnail loading. No request to a cover service is made here. */
public final class HubCoverLoader implements AutoCloseable {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 2, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(24), new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile boolean closed;
    public void load(ImageView view, File file, String identity) {
        view.setTag(identity);
        view.setImageResource(R.drawable.hub_brand);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setAlpha(0.60f);
        if (closed || file == null) return;
        String key = identity + ":" + file.getAbsolutePath();
        Bitmap ready = cache.get(key);
        if (ready != null) { display(view, ready); return; }
        worker.execute(() -> {
            if (closed || Thread.currentThread().isInterrupted()) return;
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (options.outWidth <= 0 || options.outHeight <= 0) return;
                // Reject pathological or malformed metadata before allocating pixel buffers.
                if (options.outWidth > 32768 || options.outHeight > 32768) return;
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 320 || options.outHeight / options.inSampleSize > 240)
                    options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            } catch (RuntimeException | OutOfMemoryError ignored) { /* Keep the small bundled placeholder. */ }
            if (bitmap == null || closed) return;
            cache.put(key, bitmap);
            final Bitmap result = bitmap;
            main.post(() -> {
                if (!closed && identity.equals(view.getTag())) display(view, result);
            });
        });
    }
    private void display(ImageView view, Bitmap bitmap) {
        view.setImageBitmap(bitmap);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER); // Do not crop cover text.
        view.setAlpha(1f);
    }
    public void trim() { cache.evictAll(); worker.getQueue().clear(); }
    @Override public void close() { closed = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); cache.evictAll(); }
}
