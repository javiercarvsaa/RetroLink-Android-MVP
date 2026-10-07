package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Manual per-player viewport tuning layered on top of automatic split-screen focus. */
public final class PlayerViewportPreferences {
    private static final String PREFS = "retrolink_player_viewport_v068";
    private static final float MAX_OFFSET = 1.0f;
    private static final float MIN_ZOOM = 1.00f;
    private static final float MAX_ZOOM = 1.22f;

    private PlayerViewportPreferences() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String prefix(boolean remote, int player) {
        int safe = Math.max(1, Math.min(4, player));
        return (remote ? "remote_p" : "local_p") + safe + ".";
    }

    public static float offsetX(Context c, boolean remote, int player) {
        return clamp(p(c).getFloat(prefix(remote, player) + "x", 0f), -MAX_OFFSET, MAX_OFFSET);
    }

    public static float offsetY(Context c, boolean remote, int player) {
        return clamp(p(c).getFloat(prefix(remote, player) + "y", 0f), -MAX_OFFSET, MAX_OFFSET);
    }

    public static float zoom(Context c, boolean remote, int player) {
        return clamp(p(c).getFloat(prefix(remote, player) + "zoom", 1.00f), MIN_ZOOM, MAX_ZOOM);
    }

    public static void set(Context c, boolean remote, int player, float x, float y, float zoom) {
        String k = prefix(remote, player);
        p(c).edit()
                .putFloat(k + "x", clamp(x, -MAX_OFFSET, MAX_OFFSET))
                .putFloat(k + "y", clamp(y, -MAX_OFFSET, MAX_OFFSET))
                .putFloat(k + "zoom", clamp(zoom, MIN_ZOOM, MAX_ZOOM))
                .apply();
    }

    public static void reset(Context c, boolean remote, int player) {
        set(c, remote, player, 0f, 0f, 1.00f);
    }

    public static void resetAll(Context c) {
        p(c).edit().clear().apply();
    }

    public static String label(Context c, boolean remote, int player) {
        int x = Math.round(offsetX(c, remote, player) * 100f);
        int y = Math.round(offsetY(c, remote, player) * 100f);
        int z = Math.round(zoom(c, remote, player) * 100f);
        return "P" + Math.max(1, Math.min(4, player)) + " · X " + signed(x) + "% · Y " + signed(y) + "% · Z " + z + "%";
    }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
