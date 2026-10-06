package cl.retrolink.app;

import android.graphics.Bitmap;

public final class EmulatorFrameHub {
    private static final Object LOCK = new Object();
    private static Bitmap latest;
    private static long frameId;

    private EmulatorFrameHub() {}

    // Toma propiedad del bitmap recibido. El llamador no debe reciclarlo después.
    public static void publishOwned(Bitmap source) {
        if (source == null || source.isRecycled()) return;
        synchronized (LOCK) {
            Bitmap old = latest;
            latest = source;
            frameId++;
            if (old != null && old != source && !old.isRecycled()) old.recycle();
        }
    }

    public static Bitmap copyLatest() {
        synchronized (LOCK) {
            if (latest == null || latest.isRecycled()) return null;
            try { return latest.copy(Bitmap.Config.ARGB_8888, false); }
            catch (Throwable t) { return null; }
        }
    }

    public static long frameId() { synchronized (LOCK) { return frameId; } }

    public static void clear() {
        synchronized (LOCK) {
            if (latest != null && !latest.isRecycled()) latest.recycle();
            latest = null;
            frameId++;
        }
    }
}
