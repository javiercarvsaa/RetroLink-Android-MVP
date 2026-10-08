package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Almacenamiento local de RetroLink Adaptive Core.
 * Nunca guarda la ROM, BIOS, partida ni una ruta legible: cada juego se identifica
 * mediante un hash SHA-256 calculado en el dispositivo.
 */
public final class OptimizationProfileStore {
    private static final String PREFS = "retrolink_adaptive_optimizer_v1";
    private static final String K_MODE = "mode";
    private static final String K_CALIBRATION = "calibration";
    private static final String K_EXPERIMENTAL_TEMPORAL = "experimental_temporal";
    private static final String K_LAST_SUMMARY = "last_summary";

    public static final int MODE_OFF = 0;
    public static final int MODE_AUTO = 1;
    public static final int MODE_PERFORMANCE = 2;
    public static final int MODE_BALANCED = 3;
    public static final int MODE_QUALITY = 4;
    public static final int MODE_BATTERY = 5;

    private OptimizationProfileStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int mode(Context context) {
        return clamp(prefs(context).getInt(K_MODE, MODE_AUTO), MODE_OFF, MODE_BATTERY);
    }

    public static void setMode(Context context, int mode) {
        prefs(context).edit().putInt(K_MODE, clamp(mode, MODE_OFF, MODE_BATTERY)).apply();
    }

    public static int nextMode(Context context) {
        int current = mode(context);
        int next = current >= MODE_BATTERY ? MODE_OFF : current + 1;
        setMode(context, next);
        return next;
    }

    public static String modeLabel(int mode) {
        switch (mode) {
            case MODE_OFF: return "DESACTIVADO";
            case MODE_PERFORMANCE: return "RENDIMIENTO";
            case MODE_BALANCED: return "EQUILIBRADO";
            case MODE_QUALITY: return "CALIDAD";
            case MODE_BATTERY: return "AHORRO DE BATERÍA";
            default: return "AUTOMÁTICO";
        }
    }

    public static String modeLabel(Context context) {
        return modeLabel(mode(context));
    }

    public static boolean calibrationEnabled(Context context) {
        return prefs(context).getBoolean(K_CALIBRATION, true);
    }

    public static void setCalibrationEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_CALIBRATION, enabled).apply();
    }

    /** Temporal reconstruction remains opt-in while it is evaluated per device/game. */
    public static boolean experimentalTemporalEnabled(Context context) {
        return prefs(context).getBoolean(K_EXPERIMENTAL_TEMPORAL, false);
    }

    public static void setExperimentalTemporalEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(K_EXPERIMENTAL_TEMPORAL, enabled).apply();
    }

    public static String gameKey(CoreRegistry.Core core, String romPath) {
        String coreId = core == null ? "unknown" : core.id;
        String fileIdentity = "none";
        if (romPath != null && !romPath.trim().isEmpty()) {
            try {
                File file = new File(romPath);
                fileIdentity = file.getCanonicalPath() + "|" + file.length();
            } catch (Exception e) {
                fileIdentity = romPath;
            }
        }
        return sha256(coreId + "|" + fileIdentity).substring(0, 24);
    }

    public static int stableLevel(Context context, String key, int fallback) {
        if (key == null || key.isEmpty()) return clamp(fallback, 0, 2);
        return clamp(prefs(context).getInt("stable_" + key, fallback), 0, 2);
    }

    public static boolean hasStableLevel(Context context, String key) {
        return key != null && !key.isEmpty() && prefs(context).contains("stable_" + key);
    }

    public static void setStableLevel(Context context, String key, int level) {
        if (key == null || key.isEmpty()) return;
        prefs(context).edit().putInt("stable_" + key, clamp(level, 0, 2)).apply();
    }

    public static void recordSnapshot(Context context, String key,
                                      FrameTimeMetrics.Snapshot snapshot,
                                      int thermalStatus, int appliedLevel, int pssMb,
                                      String outcome) {
        if (snapshot == null) return;
        String summary = String.format(Locale.US,
                "%s · %s · %.1f FPS · %.0f%% velocidad · p95 %.2f ms · térmico %d · PSS %d MB",
                outcome == null ? "MEDIDO" : outcome,
                levelLabel(appliedLevel), snapshot.realFps, snapshot.speedPercent,
                snapshot.p95IntervalMs, thermalStatus, Math.max(0, pssMb));
        SharedPreferences.Editor editor = prefs(context).edit().putString(K_LAST_SUMMARY, summary);
        if (key != null && !key.isEmpty()) editor.putString("metrics_" + key, summary);
        editor.apply();
    }

    public static String lastSummary(Context context) {
        return prefs(context).getString(K_LAST_SUMMARY, "Sin calibraciones registradas");
    }

    public static String metricsFor(Context context, String key) {
        if (key == null || key.isEmpty()) return "Sin datos";
        return prefs(context).getString("metrics_" + key, "Sin datos");
    }

    public static void resetStableProfiles(Context context) {
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("stable_") || key.startsWith("metrics_")) editor.remove(key);
        }
        editor.remove(K_LAST_SUMMARY).apply();
    }

    public static String levelLabel(int level) {
        switch (clamp(level, 0, 2)) {
            case 0: return "PERFORMANCE";
            case 2: return "QUALITY";
            default: return "BALANCED";
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            return String.format(Locale.US, "%064x", (long)value.hashCode() & 0xffffffffL);
        }
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
