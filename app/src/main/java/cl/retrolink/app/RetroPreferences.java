package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;

import cl.retrolink.app.ble.BleProtocol;

public final class RetroPreferences {
    private static final String PREFS = "retrolink_settings";
    private static final String K_GFX = "gfx_profile";
    private static final String K_RETRO_SR = "retro_sr"; // legacy
    private static final String K_RETRO_SR_TEMPORAL = "retro_sr_temporal_v2"; // legacy
    private static final String K_RETRO_SR_MODE = "retro_sr_mode_v047";
    private static final String K_RETRO_SR_MIGRATION = "retro_sr_mode_migrated_v047";
    private static final String K_SHARP = "retro_sr_sharpness";
    private static final String K_VOL = "volume_buttons";
    private static final String K_VOL_UP = "volume_up_map";
    private static final String K_VOL_DOWN = "volume_down_map";
    private static final String K_BLOCK_VOL = "block_system_volume";
    private static final String K_HAPTIC = "hardware_haptic";
    private static final String K_STREAM_FPS = "stream_fps";
    private static final String K_STREAM_Q = "stream_quality";
    private static final String K_STREAM_TUNING = "stream_tuning_v047";
    private static final String K_LAST_ROM = "last_rom_path";
    private static final String K_LAST_ROM_SUMMARY = "last_rom_summary";
    private static final String K_GAME_HUD = "game_hud_visible";
    private static final String K_SPLIT_CROP = "split_crop_mode_v060";
    private static final String K_STICK_DEADZONE = "stick_deadzone_v061";
    private static final String K_STICK_SENSITIVITY = "stick_sensitivity_v061";
    private static final String K_STICK_INVERT_Y = "stick_invert_y_v061";

    public static final int RETRO_SR_OFF = 0;
    public static final int RETRO_SR_SPATIAL = 1;
    public static final int RETRO_SR_AUTO = 2;
    public static final int RETRO_SR_TEMPORAL = 3;

    private RetroPreferences() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // 0 rendimiento, 1 equilibrado, 2 calidad
    public static int graphicsProfile(Context c) { return p(c).getInt(K_GFX, 1); }
    public static void setGraphicsProfile(Context c, int v) { p(c).edit().putInt(K_GFX, Math.max(0, Math.min(2, v))).apply(); }

    public static String graphicsProfileLabel(Context c) {
        int v = graphicsProfile(c);
        return v == 0 ? "RENDIMIENTO · 320×240" : v == 2 ? "CALIDAD · 960×720" : "EQUILIBRADO · 640×480";
    }

    public static int renderWidth(Context c) {
        int v = graphicsProfile(c); return v == 0 ? 320 : v == 2 ? 960 : 640;
    }
    public static int renderHeight(Context c) {
        int v = graphicsProfile(c); return v == 0 ? 240 : v == 2 ? 720 : 480;
    }

    private static void ensureRetroSrMode(Context c) {
        SharedPreferences sp = p(c);
        if (sp.getBoolean(K_RETRO_SR_MIGRATION, false)) return;
        boolean enabled = sp.getBoolean(K_RETRO_SR, true);
        boolean temporal = sp.getBoolean(K_RETRO_SR_TEMPORAL, true);
        int mode = !enabled ? RETRO_SR_OFF : (temporal ? RETRO_SR_AUTO : RETRO_SR_SPATIAL);
        sp.edit().putInt(K_RETRO_SR_MODE, mode).putBoolean(K_RETRO_SR_MIGRATION, true).apply();
    }

    public static int retroSrMode(Context c) {
        ensureRetroSrMode(c);
        return Math.max(RETRO_SR_OFF, Math.min(RETRO_SR_TEMPORAL, p(c).getInt(K_RETRO_SR_MODE, RETRO_SR_AUTO)));
    }
    public static void setRetroSrMode(Context c, int mode) {
        int safe = Math.max(RETRO_SR_OFF, Math.min(RETRO_SR_TEMPORAL, mode));
        p(c).edit().putInt(K_RETRO_SR_MODE, safe).putBoolean(K_RETRO_SR_MIGRATION, true).apply();
    }
    public static int nextRetroSrMode(Context c) {
        int next = (retroSrMode(c) + 1) % 4;
        setRetroSrMode(c, next);
        return next;
    }
    public static String retroSrModeLabel(Context c) {
        switch (retroSrMode(c)) {
            case RETRO_SR_OFF: return "OFF";
            case RETRO_SR_SPATIAL: return "SPATIAL · CERO GHOSTING";
            case RETRO_SR_TEMPORAL: return "TEMPORAL · EXPERIMENTAL";
            default: return "AUTO CLEAR · RECOMENDADO";
        }
    }
    public static boolean retroSrEnabled(Context c) { return retroSrMode(c) != RETRO_SR_OFF; }
    public static void setRetroSrEnabled(Context c, boolean v) { setRetroSrMode(c, v ? RETRO_SR_AUTO : RETRO_SR_OFF); }
    public static boolean retroSrTemporalEnabled(Context c) {
        int m = retroSrMode(c); return m == RETRO_SR_AUTO || m == RETRO_SR_TEMPORAL;
    }
    public static void setRetroSrTemporalEnabled(Context c, boolean v) {
        if (!retroSrEnabled(c)) return;
        setRetroSrMode(c, v ? RETRO_SR_AUTO : RETRO_SR_SPATIAL);
    }
    public static float retroSrSharpness(Context c) { return p(c).getFloat(K_SHARP, 0.30f); }
    public static void setRetroSrSharpness(Context c, float v) { p(c).edit().putFloat(K_SHARP, Math.max(0f, Math.min(1f, v))).apply(); }

    public static boolean volumeButtonsEnabled(Context c) { return p(c).getBoolean(K_VOL, true); }
    public static void setVolumeButtonsEnabled(Context c, boolean v) { p(c).edit().putBoolean(K_VOL, v).apply(); }
    public static String volumeUpMapping(Context c) { return p(c).getString(K_VOL_UP, "A"); }
    public static String volumeDownMapping(Context c) { return p(c).getString(K_VOL_DOWN, "B"); }
    public static void setVolumeUpMapping(Context c, String v) { p(c).edit().putString(K_VOL_UP, v).apply(); }
    public static void setVolumeDownMapping(Context c, String v) { p(c).edit().putString(K_VOL_DOWN, v).apply(); }
    public static boolean blockSystemVolume(Context c) { return p(c).getBoolean(K_BLOCK_VOL, true); }
    public static void setBlockSystemVolume(Context c, boolean v) { p(c).edit().putBoolean(K_BLOCK_VOL, v).apply(); }
    public static boolean hardwareHaptic(Context c) { return p(c).getBoolean(K_HAPTIC, true); }
    public static void setHardwareHaptic(Context c, boolean v) { p(c).edit().putBoolean(K_HAPTIC, v).apply(); }

    private static void ensureStreamTuningV047(Context c) {
        SharedPreferences sp = p(c);
        if (sp.getInt(K_STREAM_TUNING, 0) >= 1) return;
        // v0.4.7 prioriza baja latencia y claridad de movimiento.
        sp.edit()
                .putInt(K_STREAM_FPS, 0)
                .putInt(K_STREAM_Q, 80)
                .putInt(K_STREAM_TUNING, 1)
                .apply();
    }

    // 0 = AUTO SMOOTH. Valores manuales soportados: 30 / 40 / 50 / 60.
    public static int streamFps(Context c) {
        ensureStreamTuningV047(c);
        return p(c).getInt(K_STREAM_FPS, 0);
    }
    public static void setStreamFps(Context c, int v) {
        int safe = v <= 0 ? 0 : Math.max(25, Math.min(60, v));
        p(c).edit().putInt(K_STREAM_FPS, safe).putInt(K_STREAM_TUNING, 1).apply();
    }
    public static int streamQuality(Context c) {
        ensureStreamTuningV047(c);
        return p(c).getInt(K_STREAM_Q, 80);
    }
    public static void setStreamQuality(Context c, int v) {
        p(c).edit().putInt(K_STREAM_Q, Math.max(65, Math.min(90, v))).putInt(K_STREAM_TUNING, 1).apply();
    }

    public static int resolvedStreamFps(Context c) {
        int manual = streamFps(c);
        if (manual > 0) return manual;
        int content = (int)Math.round(SessionState.getContentFps());
        if (content < 25 || content > 60) content = 60;
        if (graphicsProfile(c) == 2) return Math.min(content, 40);
        return Math.min(content, 60);
    }

    public static String streamProfileLabel(Context c) {
        int fps = streamFps(c);
        if (fps <= 0) return "AUTO SMOOTH · " + resolvedStreamFps(c) + " FPS · Q" + streamQuality(c);
        if (fps >= 50) return "ULTRA SUAVE · " + fps + " FPS · Q" + streamQuality(c);
        if (fps >= 40) return "SUAVE · " + fps + " FPS · Q" + streamQuality(c);
        return "CALIDAD · " + fps + " FPS · Q" + streamQuality(c);
    }

    public static int streamWidth(Context c) {
        int v = graphicsProfile(c); return v == 0 ? 640 : v == 2 ? 960 : 640;
    }
    public static int streamHeight(Context c) {
        int v = graphicsProfile(c); return v == 0 ? 480 : v == 2 ? 720 : 480;
    }

    public static void setLastRom(Context c, String path, String summary) {
        p(c).edit().putString(K_LAST_ROM, path == null ? "" : path)
                .putString(K_LAST_ROM_SUMMARY, summary == null ? "" : summary).apply();
    }
    public static String lastRomPath(Context c) { return p(c).getString(K_LAST_ROM, ""); }
    public static String lastRomSummary(Context c) { return p(c).getString(K_LAST_ROM_SUMMARY, ""); }

    // HUD de diagnóstico durante el juego. Por defecto queda oculto para una imagen limpia.
    public static boolean gameHudVisible(Context c) { return p(c).getBoolean(K_GAME_HUD, false); }
    public static void setGameHudVisible(Context c, boolean v) { p(c).edit().putBoolean(K_GAME_HUD, v).apply(); }


    // Split screen: 0 AUTO, 1 STANDARD, 2 SUAVE, 3 REFORZADO.
    public static int splitCropMode(Context c) { return Math.max(0, Math.min(3, p(c).getInt(K_SPLIT_CROP, 0))); }
    public static int nextSplitCropMode(Context c) {
        int n = (splitCropMode(c) + 1) % 4;
        p(c).edit().putInt(K_SPLIT_CROP, n).apply();
        return n;
    }
    public static String splitCropModeLabel(Context c) {
        switch (splitCropMode(c)) {
            case 1: return "ESTÁNDAR";
            case 2: return "RECORTE SUAVE";
            case 3: return "RECORTE REFORZADO";
            default: return "AUTOMÁTICO";
        }
    }
    public static float splitOverscan(Context c) {
        switch (splitCropMode(c)) {
            case 1: return 1.0f;
            case 2: return 1.015f;
            case 3: return 1.03f;
            default: return 1.01f;
        }
    }


    // Input v0.6.1: configuración común para stick táctil y mandos Android.
    public static float stickDeadzone(Context c) {
        return Math.max(0.02f, Math.min(0.30f, p(c).getFloat(K_STICK_DEADZONE, 0.12f)));
    }
    public static void setStickDeadzone(Context c, float v) {
        p(c).edit().putFloat(K_STICK_DEADZONE, Math.max(0.02f, Math.min(0.30f, v))).apply();
    }
    public static float stickSensitivity(Context c) {
        return Math.max(0.50f, Math.min(1.50f, p(c).getFloat(K_STICK_SENSITIVITY, 1.00f)));
    }
    public static void setStickSensitivity(Context c, float v) {
        p(c).edit().putFloat(K_STICK_SENSITIVITY, Math.max(0.50f, Math.min(1.50f, v))).apply();
    }
    public static boolean stickInvertY(Context c) { return p(c).getBoolean(K_STICK_INVERT_Y, false); }
    public static void setStickInvertY(Context c, boolean v) { p(c).edit().putBoolean(K_STICK_INVERT_Y, v).apply(); }

    public static int mappingToMask(String mapping) {
        if (mapping == null) return 0;
        switch (mapping) {
            case "A": return BleProtocol.A;
            case "B": return BleProtocol.B;
            case "Z": return BleProtocol.Z;
            case "L": return BleProtocol.L;
            case "R": return BleProtocol.R;
            case "START": return BleProtocol.START;
            case "C↑": return BleProtocol.C_UP;
            case "C↓": return BleProtocol.C_DOWN;
            case "C←": return BleProtocol.C_LEFT;
            case "C→": return BleProtocol.C_RIGHT;
            default: return 0;
        }
    }

    public static String[] mappings() {
        return new String[]{"A", "B", "Z", "R", "L", "START", "C↑", "C↓", "C←", "C→", "SIN ASIGNAR"};
    }
}
