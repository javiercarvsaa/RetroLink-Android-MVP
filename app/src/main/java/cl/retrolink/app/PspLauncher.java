package cl.retrolink.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.io.File;

/** Puente RetroLink -> motor PPSSPP embebido. */
public final class PspLauncher {
    public static final String ENGINE_ACTIVITY = "org.ppsspp.ppsspp.PpssppActivity";
    public static final String SHORTCUT_EXTRA = "org.ppsspp.ppsspp.Shortcuts";

    private PspLauncher() {}

    public static boolean engineAvailable() {
        try { Class.forName(ENGINE_ACTIVITY); return true; }
        catch (Throwable ignored) { return false; }
    }

    public static void launchMenu(Activity activity) throws Exception {
        PspIniManager.configure(activity, PspIniManager.Mode.SINGLE, null);
        PspAdhocLock.release();
        activity.startActivity(new Intent(activity, engineClass()));
    }

    public static void launchGame(Activity activity, File game,
                                  PspIniManager.Mode mode, String hostIp) throws Exception {
        if (game == null || !game.isFile()) throw new IllegalArgumentException("Juego PSP no disponible");
        if (mode == PspIniManager.Mode.CLIENT && !PspNetworkInfo.isValidIpv4(hostIp))
            throw new IllegalArgumentException("IP del host no válida");
        PspIniManager.configure(activity, mode, hostIp, game.getAbsolutePath());
        if (mode == PspIniManager.Mode.HOST || mode == PspIniManager.Mode.CLIENT)
            PspAdhocLock.acquire(activity);
        else PspAdhocLock.release();

        Intent intent = new Intent(activity, engineClass());
        intent.putExtra(SHORTCUT_EXTRA, game.getAbsolutePath());
        intent.putExtra("retrolink.psp.mode", mode.name());
        activity.startActivity(intent);
    }

    private static Class<?> engineClass() throws ClassNotFoundException {
        return Class.forName(ENGINE_ACTIVITY);
    }
}
