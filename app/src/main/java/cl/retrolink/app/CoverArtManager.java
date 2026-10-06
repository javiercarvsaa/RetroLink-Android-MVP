package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Búsqueda y caché automática de carátulas.
 *
 * v0.6.4 usa el catálogo público de Libretro Thumbnails para Nintendo 64.
 * No necesita API key, no modifica las ROM y guarda únicamente una copia local
 * de la imagen encontrada. Si no hay red o coincidencia suficiente, la biblioteca
 * continúa funcionando con su placeholder local.
 */
public final class CoverArtManager {
    public interface Callback {
        void onResult(File coverFile, String matchedTitle);
    }

    private static final String PREFS = "retrolink_cover_art_v064";
    private static final String SYSTEM = "Nintendo - Nintendo 64";
    private static final String BASE_URL = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%2064/Named_Boxarts/";
    private static final long INDEX_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int MAX_INDEX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final Pattern PNG_LINK = Pattern.compile("href=\\\"([^\\\"]+\\.png)\\\"", Pattern.CASE_INSENSITIVE);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RetroLinkCoverArt");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile List<Entry> memoryIndex;

    private CoverArtManager() {}

    private static final class Entry {
        final String href;
        final String title;
        final String base;
        final String normalized;
        final String relaxed;

        Entry(String href, String title) {
            this.href = href;
            this.title = title;
            this.base = stripTags(title);
            this.normalized = normalize(base);
            this.relaxed = relax(normalized);
        }
    }

    public static File cachedCover(Context context, N64RomRepository.ImportedRom rom) {
        if (context == null || rom == null || rom.file == null) return null;
        File f = new File(coverDir(context), safeStem(rom.file.getName()) + ".png");
        return validImage(f) ? f : null;
    }

    public static String cachedMatchTitle(Context context, N64RomRepository.ImportedRom rom) {
        if (context == null || rom == null || rom.file == null) return "";
        return prefs(context).getString("match." + rom.file.getName(), "");
    }

    public static int cachedCount(Context context, List<N64RomRepository.ImportedRom> roms) {
        int count = 0;
        if (roms == null) return 0;
        for (N64RomRepository.ImportedRom rom : roms) if (cachedCover(context, rom) != null) count++;
        return count;
    }

    /** Busca automáticamente solo cuando no hay una carátula válida en caché. */
    public static void request(Context context, N64RomRepository.ImportedRom rom, Callback callback) {
        if (context == null || rom == null) {
            post(callback, null, "");
            return;
        }
        Context app = context.getApplicationContext();
        File cached = cachedCover(app, rom);
        if (cached != null) {
            post(callback, cached, cachedMatchTitle(app, rom));
            return;
        }
        IO.execute(() -> {
            File result = null;
            String matched = "";
            try {
                List<Entry> entries = loadIndex(app, false);
                Entry best = findBest(entries, rom);
                if (best != null) {
                    result = downloadCover(app, rom, best);
                    if (result != null) {
                        matched = best.title;
                        prefs(app).edit().putString("match." + rom.file.getName(), matched).apply();
                    }
                }
            } catch (Throwable ignored) {
                // La carátula es opcional. Nunca debe impedir abrir/jugar una ROM.
            }
            post(callback, result, matched);
        });
    }

    /** Fuerza que la próxima búsqueda actualice primero el índice remoto. */
    public static void invalidateIndex(Context context) {
        memoryIndex = null;
        File index = indexFile(context);
        if (index.isFile()) //noinspection ResultOfMethodCallIgnored
            index.delete();
    }

    public static String sourceLabel() {
        return "Libretro Thumbnails · " + SYSTEM;
    }

    private static void post(Callback callback, File file, String title) {
        if (callback == null) return;
        MAIN.post(() -> callback.onResult(file, title == null ? "" : title));
    }

    private static synchronized List<Entry> loadIndex(Context c, boolean force) throws IOException {
        if (!force && memoryIndex != null && !memoryIndex.isEmpty()) return memoryIndex;
        File cache = indexFile(c);
        String html = null;
        if (!force && cache.isFile() && System.currentTimeMillis() - cache.lastModified() < INDEX_TTL_MS) {
            html = readText(cache, MAX_INDEX_BYTES);
        }
        if (html == null || html.isEmpty()) {
            try {
                html = downloadText(BASE_URL, MAX_INDEX_BYTES);
                writeText(cache, html);
            } catch (IOException e) {
                if (cache.isFile()) html = readText(cache, MAX_INDEX_BYTES);
                else throw e;
            }
        }
        List<Entry> parsed = parseIndex(html);
        if (parsed.isEmpty()) throw new IOException("Índice de carátulas vacío");
        memoryIndex = parsed;
        return parsed;
    }

    private static List<Entry> parseIndex(String html) {
        List<Entry> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = PNG_LINK.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            if (href == null || href.contains("/") || href.contains("..")) continue;
            String decoded;
            try { decoded = URLDecoder.decode(href, StandardCharsets.UTF_8.name()); }
            catch (Exception e) { decoded = href; }
            decoded = decoded.replace("&amp;", "&");
            if (decoded.toLowerCase(Locale.US).endsWith(".png")) decoded = decoded.substring(0, decoded.length() - 4);
            out.add(new Entry(href, decoded));
        }
        return out;
    }

    private static Entry findBest(List<Entry> entries, N64RomRepository.ImportedRom rom) {
        List<String> targets = new ArrayList<>();
        if (rom.sourceName != null && !rom.sourceName.trim().isEmpty()) targets.add(stripExtension(rom.sourceName));
        if (rom.info != null && rom.info.title != null && !rom.info.title.trim().isEmpty()) targets.add(rom.info.title.trim());
        if (targets.isEmpty()) return null;

        Entry best = null;
        double bestScore = 0d;
        String region = rom.info == null ? "" : rom.info.friendlyRegion().toLowerCase(Locale.US);
        for (Entry e : entries) {
            double score = 0d;
            for (String target : targets) score = Math.max(score, similarity(target, e));
            String lowerTitle = e.title.toLowerCase(Locale.US);
            if (region.contains("usa") && lowerTitle.contains("(usa")) score += 0.08d;
            else if (region.contains("europa") && lowerTitle.contains("(europe")) score += 0.08d;
            else if (region.contains("jap") && lowerTitle.contains("(japan")) score += 0.08d;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        // Conservador: una coincidencia dudosa es peor que mostrar placeholder.
        return bestScore >= 0.55d ? best : null;
    }

    private static double similarity(String target, Entry e) {
        String n = normalize(stripTags(stripExtension(target)));
        if (n.isEmpty()) return 0d;
        if (n.equals(e.normalized)) return 1.0d;
        if (n.replace(" ", "").equals(e.normalized.replace(" ", ""))) return 0.99d;
        String r = relax(n);
        if (!r.isEmpty() && r.equals(e.relaxed)) return 0.98d;
        double token = Math.max(jaccard(n, e.normalized), jaccard(r, e.relaxed));
        double gram = Math.max(diceBigrams(n, e.normalized), diceBigrams(r, e.relaxed));
        double contains = (e.normalized.contains(n) || n.contains(e.normalized) || (!r.isEmpty() && (e.relaxed.contains(r) || r.contains(e.relaxed)))) ? 0.08d : 0d;
        return Math.min(1d, token * 0.55d + gram * 0.45d + contains);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.US)
                .replace("brothers", "bros")
                .replace("brother", "bros")
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return s;
    }

    private static String relax(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String token : normalized.split(" ")) {
            if (token.isEmpty()) continue;
            if (token.equals("the") || token.equals("super") || token.equals("game") || token.equals("nintendo")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(token);
        }
        return b.toString();
    }

    private static double jaccard(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0d;
        Set<String> aa = new HashSet<>();
        Set<String> bb = new HashSet<>();
        for (String s : a.split(" ")) if (!s.isEmpty()) aa.add(s);
        for (String s : b.split(" ")) if (!s.isEmpty()) bb.add(s);
        if (aa.isEmpty() || bb.isEmpty()) return 0d;
        Set<String> intersection = new HashSet<>(aa); intersection.retainAll(bb);
        Set<String> union = new HashSet<>(aa); union.addAll(bb);
        return (double) intersection.size() / (double) union.size();
    }

    private static double diceBigrams(String a, String b) {
        String x = a == null ? "" : a.replace(" ", "");
        String y = b == null ? "" : b.replace(" ", "");
        if (x.equals(y) && !x.isEmpty()) return 1d;
        if (x.length() < 2 || y.length() < 2) return 0d;
        List<String> ax = bigrams(x);
        List<String> by = bigrams(y);
        boolean[] used = new boolean[by.size()];
        int same = 0;
        for (String g : ax) {
            for (int i = 0; i < by.size(); i++) {
                if (!used[i] && g.equals(by.get(i))) { used[i] = true; same++; break; }
            }
        }
        return (2d * same) / (double) (ax.size() + by.size());
    }

    private static List<String> bigrams(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 1 < s.length(); i++) out.add(s.substring(i, i + 2));
        return out;
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s*\\([^)]*\\)", " ")
                .replaceAll("\\s*\\[[^]]*\\]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String stripExtension(String s) {
        if (s == null) return "";
        int dot = s.lastIndexOf('.');
        return dot > 0 ? s.substring(0, dot) : s;
    }

    private static File downloadCover(Context c, N64RomRepository.ImportedRom rom, Entry e) throws IOException {
        File dst = new File(coverDir(c), safeStem(rom.file.getName()) + ".png");
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        URL url = new URL(new URL(BASE_URL), e.href);
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) { conn.disconnect(); return null; }
        int total = 0;
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp, false))) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_IMAGE_BYTES) throw new IOException("Carátula demasiado grande");
                out.write(buf, 0, n);
            }
        } finally {
            conn.disconnect();
        }
        if (!validPngSignature(tmp)) { //noinspection ResultOfMethodCallIgnored
            tmp.delete(); return null;
        }
        if (dst.exists()) //noinspection ResultOfMethodCallIgnored
            dst.delete();
        if (!tmp.renameTo(dst)) {
            copy(tmp, dst);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        return validImage(dst) ? dst : null;
    }

    private static String downloadText(String urlText, int maxBytes) throws IOException {
        HttpURLConnection conn = open(new URL(urlText));
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) { conn.disconnect(); throw new IOException("HTTP " + code); }
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int total = 0, n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("Índice demasiado grande");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "RetroLink/0.6.4 Android; cover-art lookup");
        c.setRequestProperty("Accept", "text/html,image/png,*/*;q=0.8");
        return c;
    }

    private static File coverDir(Context c) {
        File d = new File(c.getFilesDir(), "covers/n64");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    private static File metaDir(Context c) {
        File d = new File(c.getFilesDir(), "metadata/n64");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    private static File indexFile(Context c) { return new File(metaDir(c), "libretro_boxarts_index.html"); }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static String safeStem(String name) {
        String s = name == null ? "game" : name.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (s.length() > 80) s = s.substring(0, 80);
        return s;
    }

    private static boolean validImage(File f) {
        return f != null && f.isFile() && f.length() > 128 && validPngSignature(f);
    }

    private static boolean validPngSignature(File f) {
        if (f == null || !f.isFile() || f.length() < 8) return false;
        byte[] sig = new byte[8];
        try (FileInputStream in = new FileInputStream(f)) {
            if (in.read(sig) != 8) return false;
            return (sig[0] & 0xff) == 0x89 && sig[1] == 0x50 && sig[2] == 0x4e && sig[3] == 0x47
                    && sig[4] == 0x0d && sig[5] == 0x0a && sig[6] == 0x1a && sig[7] == 0x0a;
        } catch (IOException e) { return false; }
    }

    private static String readText(File f, int maxBytes) throws IOException {
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int total = 0, n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("Caché demasiado grande");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copy(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst, false)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
