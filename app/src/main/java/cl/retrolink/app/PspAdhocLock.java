package cl.retrolink.app;

import android.content.Context;
import android.net.wifi.WifiManager;

/** Mantiene habilitado el tráfico multicast durante sesiones PSP Ad Hoc. */
public final class PspAdhocLock {
    private static WifiManager.MulticastLock lock;
    private PspAdhocLock() {}

    public static synchronized void acquire(Context context) {
        if (lock != null && lock.isHeld()) return;
        try {
            WifiManager wifi = (WifiManager)context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            lock = wifi.createMulticastLock("RetroLink-PSP-AdHoc");
            lock.setReferenceCounted(false);
            lock.acquire();
        } catch (Throwable ignored) {}
    }

    public static synchronized void release() {
        try { if (lock != null && lock.isHeld()) lock.release(); }
        catch (Throwable ignored) {}
        lock = null;
    }
}
