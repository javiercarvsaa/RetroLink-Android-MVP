package cl.retrolink.app;

import android.app.ActivityManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Configuración PPSSPP controlada por RetroLink. */
public final class PspIniManager {
    public enum Mode { SINGLE, HOST, CLIENT }

    private PspIniManager() {}

    public static synchronized File configure(Context c, Mode mode, String hostIp) throws Exception {
        return configure(c, mode, hostIp, null);
    }

    public static synchronized File configure(Context c, Mode mode, String hostIp,
                                              String gamePath) throws Exception {
        File memstick = ensureMemstick(c);
        File system = new File(memstick, "PSP/SYSTEM");
        if (!system.exists() && !system.mkdirs())
            throw new IllegalStateException("No se pudo preparar PSP/SYSTEM");

        File ini = new File(system, "ppsspp.ini");
        IniDocument doc = IniDocument.read(ini);

        boolean linkedSession = mode == Mode.HOST || mode == Mode.CLIENT;
        AdaptiveOptimizationEngine.Plan adaptivePlan = AdaptiveOptimizationEngine.resolve(
                c, CoreRegistry.PSP, gamePath, linkedSession);
        int internalResolution = adaptivePlan.pspInternalResolution;
        doc.put("General", "FirstRun", "False");
        doc.put("General", "AutoRun", "True");
        doc.put("General", "CheckForNewVersion", "False");
        doc.put("General", "Language", "es_ES");
        doc.put("General", "EnableLogging", "False");

        doc.put("Graphics", "InternalResolution", Integer.toString(internalResolution));
        doc.put("Graphics", "HardwareTransform", "True");
        doc.put("Graphics", "SoftwareSkinning", "True");
        doc.put("Graphics", "TextureFiltering", "1");
        doc.put("Graphics", "Smart2DTexFiltering", "True");
        doc.put("Graphics", "AnisotropyLevel", Integer.toString(adaptivePlan.pspAnisotropy));
        doc.put("Graphics", "HighQualityDepth", adaptivePlan.pspHighQualityDepth ? "True" : "False");
        doc.put("Graphics", "FrameSkip", "0");
        doc.put("Graphics", "AutoFrameSkip", "False");
        doc.put("Graphics", "TexScalingLevel", "1");
        doc.put("Graphics", "TexDeposterize", "False");
        doc.put("Graphics", "VerticalSync", "True");
        doc.put("Graphics", "LowLatencyPresent", "True");
        doc.put("Graphics", "SustainedPerformanceMode", adaptivePlan.mode == OptimizationProfileStore.MODE_BATTERY ? "False" : "True");

        doc.put("Control", "ShowTouchControls", "True");
        doc.put("Control", "HapticFeedback", "True");

        boolean network = mode == Mode.HOST || mode == Mode.CLIENT;
        doc.put("Network", "EnableWlan", network ? "True" : "False");
        doc.put("Network", "EnableAdhocServer", mode == Mode.HOST ? "True" : "False");
        doc.put("Network", "AdhocServerRelayMode", "False");
        doc.put("Network", "EnableUPnP", "False");
        doc.put("Network", "PortOffset", "0");
        if (mode == Mode.HOST) doc.put("Network", "proAdhocServer", "127.0.0.1");
        else if (mode == Mode.CLIENT) doc.put("Network", "proAdhocServer", hostIp == null ? "" : hostIp.trim());
        else doc.put("Network", "proAdhocServer", "socom.cc");

        doc.put("SystemParam", "NickName", mode == Mode.CLIENT ? "RetroLinkP2" : "RetroLinkP1");
        doc.put("SystemParam", "WlanAdhocChannel", "0");

        doc.writeAtomic(ini);
        return ini;
    }

    public static String status(Context c) {
        try {
            File memstick = ensureMemstick(c);
            return "PPSSPP 1.20.4 integrado · " + AdaptiveOptimizationEngine.resolve(c, CoreRegistry.PSP, "", false).pspInternalResolution + "× interno · Memory Stick persistente";
        } catch (Exception e) {
            return "PPSSPP integrado · configuración pendiente";
        }
    }

    public static File ensureMemstick(Context c) throws Exception {
        File memstick = c.getExternalFilesDir(null);
        if (memstick == null) memstick = new File(c.getFilesDir(), "ppsspp_memstick");
        if (!memstick.exists() && !memstick.mkdirs())
            throw new IllegalStateException("No se pudo crear el Memory Stick PSP");

        File marker = new File(c.getFilesDir(), "memstick_dir.txt");
        byte[] value = memstick.getAbsolutePath().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(marker, false)) {
            out.write(value); out.getFD().sync();
        }
        return memstick;
    }

    private static int qualityScale(Context c) {
        long total = 0L;
        try {
            ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            if (am != null) { am.getMemoryInfo(info); total = info.totalMem; }
        } catch (Throwable ignored) {}
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return total >= 7L * 1024L * 1024L * 1024L && cores >= 6 ? 3 : 2;
    }

    private static final class IniDocument {
        private final LinkedHashMap<String, LinkedHashMap<String, String>> sections = new LinkedHashMap<>();

        static IniDocument read(File file) {
            IniDocument doc = new IniDocument();
            if (file == null || !file.isFile()) return doc;
            String section = "General";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith(";") || t.startsWith("#")) continue;
                    if (t.startsWith("[") && t.endsWith("]") && t.length() > 2) {
                        section = t.substring(1, t.length() - 1).trim();
                        doc.sections.computeIfAbsent(section, k -> new LinkedHashMap<>());
                        continue;
                    }
                    int eq = t.indexOf('=');
                    if (eq <= 0) continue;
                    String key = t.substring(0, eq).trim();
                    String value = t.substring(eq + 1).trim();
                    doc.put(section, key, value);
                }
            } catch (Exception ignored) {}
            return doc;
        }

        void put(String section, String key, String value) {
            sections.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, value);
        }

        void writeAtomic(File file) throws Exception {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmp = new File(parent, file.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp, false)) {
                StringBuilder text = new StringBuilder();
                text.append("; RetroLink PSP profile · generated safely\n");
                for (Map.Entry<String, LinkedHashMap<String, String>> section : sections.entrySet()) {
                    text.append('\n').append('[').append(section.getKey()).append("]\n");
                    for (Map.Entry<String, String> e : section.getValue().entrySet())
                        text.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
                }
                out.write(text.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (file.exists() && !file.delete())
                throw new IllegalStateException("No se pudo actualizar ppsspp.ini");
            if (!tmp.renameTo(file))
                throw new IllegalStateException("No se pudo guardar ppsspp.ini");
        }
    }
}
