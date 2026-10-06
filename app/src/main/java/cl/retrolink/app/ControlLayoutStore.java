package cl.retrolink.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Persistencia de layout táctil con coordenadas normalizadas.
 * v0.6.0: independiente de resolución y tamaño de pantalla.
 */
public final class ControlLayoutStore {
    public static final String SCOPE_N64_LANDSCAPE = "n64_landscape";
    private static final String PREFS = "retrolink_control_layout_v060";

    public static final int[] CONTROL_IDS = new int[]{
            R.id.virtualStick,
            R.id.btnA, R.id.btnB, R.id.btnZ,
            R.id.btnL, R.id.btnR, R.id.btnStart,
            R.id.btnCUp, R.id.btnCDown, R.id.btnCLeft, R.id.btnCRight
    };

    private static final String[] CONTROL_KEYS = new String[]{
            "stick", "a", "b", "z", "l", "r", "start",
            "cup", "cdown", "cleft", "cright"
    };

    private ControlLayoutStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String scope, int index, String suffix) {
        return scope + "." + CONTROL_KEYS[index] + "." + suffix;
    }

    public static boolean hasSaved(Context c, String scope, int index) {
        return prefs(c).contains(key(scope, index, "x"));
    }

    public static void saveAll(Activity a, FrameLayout root, String scope) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        SharedPreferences.Editor e = prefs(a).edit();
        final float rw = root.getWidth();
        final float rh = root.getHeight();
        for (int i = 0; i < CONTROL_IDS.length; i++) {
            View v = a.findViewById(CONTROL_IDS[i]);
            if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) continue;
            float cx = v.getX() + v.getWidth() * 0.5f;
            float cy = v.getY() + v.getHeight() * 0.5f;
            e.putFloat(key(scope, i, "x"), clamp01(cx / rw));
            e.putFloat(key(scope, i, "y"), clamp01(cy / rh));
            e.putFloat(key(scope, i, "scale"), clamp(v.getScaleX(), 0.55f, 1.65f));
            e.putFloat(key(scope, i, "alpha"), clamp(v.getAlpha(), 0.25f, 1.0f));
        }
        e.apply();
    }

    public static void applyAll(Activity a, FrameLayout root, String scope) {
        if (root == null) return;
        root.post(() -> {
            final float rw = root.getWidth();
            final float rh = root.getHeight();
            if (rw <= 0 || rh <= 0) return;
            SharedPreferences p = prefs(a);
            for (int i = 0; i < CONTROL_IDS.length; i++) {
                View v = a.findViewById(CONTROL_IDS[i]);
                if (v == null || !hasSaved(a, scope, i) || v.getWidth() <= 0 || v.getHeight() <= 0) continue;
                float nx = p.getFloat(key(scope, i, "x"), 0.5f);
                float ny = p.getFloat(key(scope, i, "y"), 0.5f);
                float scale = clamp(p.getFloat(key(scope, i, "scale"), 1f), 0.55f, 1.65f);
                float alpha = clamp(p.getFloat(key(scope, i, "alpha"), v.getAlpha()), 0.25f, 1f);
                place(v, root, nx, ny, scale, alpha);
            }
        });
    }

    public static void place(View v, FrameLayout root, float nx, float ny, float scale, float alpha) {
        if (v == null || root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        scale = clamp(scale, 0.55f, 1.65f);
        alpha = clamp(alpha, 0.25f, 1f);
        float halfW = v.getWidth() * scale * 0.5f;
        float halfH = v.getHeight() * scale * 0.5f;
        float cx = clamp(nx * root.getWidth(), halfW, root.getWidth() - halfW);
        float cy = clamp(ny * root.getHeight(), halfH, root.getHeight() - halfH);
        v.setPivotX(v.getWidth() * 0.5f);
        v.setPivotY(v.getHeight() * 0.5f);
        v.setScaleX(scale);
        v.setScaleY(scale);
        v.setAlpha(alpha);
        v.setX(cx - v.getWidth() * 0.5f);
        v.setY(cy - v.getHeight() * 0.5f);
    }

    public static void reset(Context c, String scope) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; i < CONTROL_IDS.length; i++) {
            e.remove(key(scope, i, "x"));
            e.remove(key(scope, i, "y"));
            e.remove(key(scope, i, "scale"));
            e.remove(key(scope, i, "alpha"));
        }
        e.apply();
    }

    /** Preset rápido para invertir controles principales izquierda/derecha. */
    public static void mirrorHorizontally(Activity a, FrameLayout root) {
        if (root == null || root.getWidth() <= 0) return;
        for (int id : CONTROL_IDS) {
            View v = a.findViewById(id);
            if (v == null || v.getWidth() <= 0) continue;
            float center = v.getX() + v.getWidth() * 0.5f;
            float mirroredCenter = root.getWidth() - center;
            float half = v.getWidth() * v.getScaleX() * 0.5f;
            mirroredCenter = clamp(mirroredCenter, half, root.getWidth() - half);
            v.setX(mirroredCenter - v.getWidth() * 0.5f);
        }
    }

    public static int indexForId(int id) {
        for (int i = 0; i < CONTROL_IDS.length; i++) if (CONTROL_IDS[i] == id) return i;
        return -1;
    }

    public static String labelForIndex(int i) {
        if (i < 0 || i >= CONTROL_KEYS.length) return "CONTROL";
        return CONTROL_KEYS[i].toUpperCase();
    }

    private static float clamp01(float v) { return clamp(v, 0f, 1f); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
