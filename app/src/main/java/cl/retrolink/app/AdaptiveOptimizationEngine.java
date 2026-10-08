package cl.retrolink.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;

import java.util.Locale;

/**
 * RetroLink Adaptive Core v1.5.
 *
 * This increment is intentionally rule-based. It selects only core options already
 * supported by each emulator and does not claim to be neural inference or AI.
 */
public final class AdaptiveOptimizationEngine {
    public static final int LEVEL_PERFORMANCE = 0;
    public static final int LEVEL_BALANCED = 1;
    public static final int LEVEL_QUALITY = 2;

    private AdaptiveOptimizationEngine() {}

    public static Plan resolve(Context context, CoreRegistry.Core core,
                               String romPath, boolean linkedMultiplayer) {
        DeviceCapabilities capabilities = DeviceCapabilities.detect(context);
        int mode = OptimizationProfileStore.mode(context);
        String key = OptimizationProfileStore.gameKey(core, romPath);

        if (mode == OptimizationProfileStore.MODE_OFF) {
            return manualCompatibilityPlan(context, core, key, linkedMultiplayer, capabilities);
        }

        int requestedLevel;
        switch (mode) {
            case OptimizationProfileStore.MODE_PERFORMANCE:
            case OptimizationProfileStore.MODE_BATTERY:
                requestedLevel = LEVEL_PERFORMANCE;
                break;
            case OptimizationProfileStore.MODE_BALANCED:
                requestedLevel = LEVEL_BALANCED;
                break;
            case OptimizationProfileStore.MODE_QUALITY:
                requestedLevel = LEVEL_QUALITY;
                break;
            default:
                requestedLevel = capabilities.tier;
                if (OptimizationProfileStore.hasStableLevel(context, key)) {
                    requestedLevel = OptimizationProfileStore.stableLevel(
                            context, key, requestedLevel);
                }
                break;
        }

        int thermal = capabilities.thermalStatus;
        if (thermal >= thermalSevere()) requestedLevel = LEVEL_PERFORMANCE;
        else if (thermal >= thermalModerate()) requestedLevel = Math.min(requestedLevel, LEVEL_BALANCED);

        if (linkedMultiplayer && (core == CoreRegistry.N64 || core == CoreRegistry.PS1
                || core == CoreRegistry.PSP)) {
            requestedLevel = Math.min(requestedLevel, LEVEL_BALANCED);
        }

        int level = clamp(requestedLevel, LEVEL_PERFORMANCE, LEVEL_QUALITY);
        boolean battery = mode == OptimizationProfileStore.MODE_BATTERY;
        boolean experimentalTemporal = OptimizationProfileStore.experimentalTemporalEnabled(context);
        boolean calibration = OptimizationProfileStore.calibrationEnabled(context)
                && mode == OptimizationProfileStore.MODE_AUTO;

        Plan plan = new Plan(core, mode, key, capabilities, level, linkedMultiplayer,
                calibration, true);
        applyAdapter(context, plan, battery, experimentalTemporal);
        return plan;
    }

    private static Plan manualCompatibilityPlan(Context context, CoreRegistry.Core core,
                                                String key, boolean linkedMultiplayer,
                                                DeviceCapabilities capabilities) {
        int manual = clamp(RetroPreferences.graphicsProfile(context), 0, 2);
        Plan plan = new Plan(core, OptimizationProfileStore.MODE_OFF, key, capabilities,
                manual, linkedMultiplayer, false, false);
        plan.n64RenderProfile = manual;
        plan.pspInternalResolution = capabilities.tier >= LEVEL_QUALITY ? 3 : 2;
        plan.pspAnisotropy = 4;
        plan.pspHighQualityDepth = true;
        plan.ps1EnhancedResolution = true;
        plan.ps1Spatial = RetroPreferences.retroSrEnabled(context);
        plan.ps1Temporal = RetroPreferences.retroSrTemporalEnabled(context);
        plan.ps1Sharpness = RetroPreferences.retroSrSharpness(context);
        plan.faithfulPixelArt = true;
        plan.reason = "Módulo desactivado · ajustes estables previos conservados";
        return plan;
    }

    private static void applyAdapter(Context context, Plan plan, boolean battery,
                                     boolean experimentalTemporal) {
        CoreRegistry.Core core = plan.core;
        if (core == CoreRegistry.N64) {
            plan.n64RenderProfile = plan.level;
            plan.reason = "N64 · resolución interna segura seleccionada antes de iniciar el core";
            return;
        }
        if (core == CoreRegistry.PS1) {
            plan.ps1EnhancedResolution = plan.level >= LEVEL_BALANCED;
            // Keep the already validated GPU spatial presentation in every adaptive mode.
            // Lower modes reduce internal work; they do not replace the stable renderer.
            plan.ps1Spatial = true;
            plan.ps1Temporal = plan.level == LEVEL_QUALITY
                    && plan.capabilities.tier == LEVEL_QUALITY
                    && !plan.linkedMultiplayer && experimentalTemporal && !battery;
            plan.ps1Sharpness = plan.level == LEVEL_QUALITY ? 0.32f
                    : plan.level == LEVEL_BALANCED ? 0.24f : 0.12f;
            plan.reason = plan.ps1Temporal
                    ? "PS1 · 2× + espacial + temporal experimental"
                    : plan.ps1EnhancedResolution ? "PS1 · 2× + espacial estable" : "PS1 · resolución base + espacial estable";
            return;
        }
        if (core == CoreRegistry.PSP) {
            if (plan.level == LEVEL_PERFORMANCE) plan.pspInternalResolution = battery ? 1 : 2;
            else if (plan.level == LEVEL_QUALITY && plan.capabilities.tier == LEVEL_QUALITY
                    && !plan.linkedMultiplayer) plan.pspInternalResolution = 3;
            else plan.pspInternalResolution = 2;
            plan.pspAnisotropy = plan.level == LEVEL_QUALITY ? 8
                    : plan.level == LEVEL_BALANCED ? 4 : 2;
            plan.pspHighQualityDepth = plan.level >= LEVEL_BALANCED;
            plan.reason = "PSP · perfil PPSSPP " + plan.pspInternalResolution
                    + "× sin frameskip" + (plan.linkedMultiplayer ? " · Ad Hoc protegido" : "");
            return;
        }
        if (core == CoreRegistry.SNES || core == CoreRegistry.ATARI_2600
                || core == CoreRegistry.GAME_BOY) {
            plan.faithfulPixelArt = true;
            plan.reason = core.shortSystem + " · pixel art fiel · sin filtrado borroso";
            return;
        }
        plan.reason = "Compatibilidad prioritaria";
    }

    public static int lowerLevel(int current) {
        return Math.max(LEVEL_PERFORMANCE, current - 1);
    }

    public static int thermalModerate() {
        return Build.VERSION.SDK_INT >= 29 ? PowerManager.THERMAL_STATUS_MODERATE : 2;
    }

    public static int thermalSevere() {
        return Build.VERSION.SDK_INT >= 29 ? PowerManager.THERMAL_STATUS_SEVERE : 3;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    public static final class Plan {
        public final CoreRegistry.Core core;
        public final int mode;
        public final String gameKey;
        public final DeviceCapabilities capabilities;
        public final int level;
        public final boolean linkedMultiplayer;
        public final boolean calibrationEnabled;
        public final boolean runtimeHintsEnabled;

        public int n64RenderProfile = LEVEL_BALANCED;
        public int pspInternalResolution = 2;
        public int pspAnisotropy = 4;
        public boolean pspHighQualityDepth = true;
        public boolean ps1EnhancedResolution = true;
        public boolean ps1Spatial = true;
        public boolean ps1Temporal;
        public float ps1Sharpness = 0.24f;
        public boolean faithfulPixelArt = true;
        public String reason = "Compatibilidad prioritaria";

        Plan(CoreRegistry.Core core, int mode, String gameKey,
             DeviceCapabilities capabilities, int level, boolean linkedMultiplayer,
             boolean calibrationEnabled, boolean runtimeHintsEnabled) {
            this.core = core == null ? CoreRegistry.N64 : core;
            this.mode = mode;
            this.gameKey = gameKey;
            this.capabilities = capabilities;
            this.level = level;
            this.linkedMultiplayer = linkedMultiplayer;
            this.calibrationEnabled = calibrationEnabled;
            this.runtimeHintsEnabled = runtimeHintsEnabled;
        }

        public String shortLabel() {
            return "ADAPTIVE " + OptimizationProfileStore.levelLabel(level);
        }

        public String detailedLabel() {
            return shortLabel() + " · " + reason;
        }
    }

    public static final class DeviceCapabilities {
        public final int sdk;
        public final int cpuCores;
        public final long totalRamMb;
        public final boolean lowRam;
        public final int glEsMajor;
        public final int glEsMinor;
        public final boolean vulkan;
        public final float displayHz;
        public final int thermalStatus;
        public final boolean performanceHints;
        public final int tier;

        private DeviceCapabilities(int sdk, int cpuCores, long totalRamMb, boolean lowRam,
                                   int glEsMajor, int glEsMinor, boolean vulkan,
                                   float displayHz, int thermalStatus,
                                   boolean performanceHints, int tier) {
            this.sdk = sdk;
            this.cpuCores = cpuCores;
            this.totalRamMb = totalRamMb;
            this.lowRam = lowRam;
            this.glEsMajor = glEsMajor;
            this.glEsMinor = glEsMinor;
            this.vulkan = vulkan;
            this.displayHz = displayHz;
            this.thermalStatus = thermalStatus;
            this.performanceHints = performanceHints;
            this.tier = tier;
        }

        static DeviceCapabilities detect(Context context) {
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            long ramMb = 0L;
            boolean lowRam = false;
            int glMajor = 3;
            int glMinor = 0;
            try {
                ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(info);
                    ramMb = info.totalMem / (1024L * 1024L);
                    lowRam = am.isLowRamDevice();
                    ConfigurationInfo configuration = am.getDeviceConfigurationInfo();
                    if (configuration != null) {
                        int raw = configuration.reqGlEsVersion;
                        glMajor = Math.max(2, (raw >> 16) & 0xffff);
                        glMinor = raw & 0xffff;
                    }
                }
            } catch (Throwable ignored) {}

            boolean vulkan = false;
            try {
                PackageManager pm = context.getPackageManager();
                vulkan = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);
            } catch (Throwable ignored) {}

            float refresh = 60f;
            try {
                DisplayManager dm = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
                Display display = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null && display.getRefreshRate() > 0f) refresh = display.getRefreshRate();
            } catch (Throwable ignored) {}

            int thermal = 0;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) thermal = pm.getCurrentThermalStatus();
                } catch (Throwable ignored) {}
            }

            int score = 0;
            if (cores >= 8) score += 2; else if (cores >= 6) score += 1;
            if (ramMb >= 7500L) score += 2; else if (ramMb >= 4500L) score += 1;
            if (glMajor > 3 || (glMajor == 3 && glMinor >= 2)) score += 1;
            if (vulkan) score += 1;
            if (refresh >= 90f) score += 1;
            if (Build.VERSION.SDK_INT >= 31) score += 1;
            if (lowRam || ramMb > 0L && ramMb < 3500L) score -= 2;
            int tier = score >= 6 ? LEVEL_QUALITY : score >= 2 ? LEVEL_BALANCED : LEVEL_PERFORMANCE;

            return new DeviceCapabilities(Build.VERSION.SDK_INT, cores, ramMb, lowRam,
                    glMajor, glMinor, vulkan, refresh, thermal,
                    Build.VERSION.SDK_INT >= 31, tier);
        }

        public String summary() {
            return String.format(Locale.US,
                    "%d núcleos · %d MB RAM · GLES %d.%d · %s · %.0f Hz · térmico %d · %s",
                    cpuCores, totalRamMb, glEsMajor, glEsMinor,
                    vulkan ? "Vulkan" : "sin Vulkan declarado", displayHz,
                    thermalStatus, performanceHints ? "ADPF hints" : "sin hints ADPF");
        }
    }
}
