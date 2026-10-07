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

/** Carátulas automáticas GB/GBC desde Libretro Thumbnails. */
public final class GameBoyCoverArtManager {
    public interface Callback { void onResult(File coverFile, String matchedTitle); }

    private static final String PREFS = "retrolink_cover_art_gb_v071";
    private static final String GB_BASE = "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Boxarts/";
    private static final String GBC_BASE = "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Color/Named_Boxarts/";
    private static final long INDEX_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int MAX_INDEX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Pattern PNG_LINK = Pattern.compile("href=\\\"([^\\\"]+\\.png)\\\"", Pattern.CASE_INSENSITIVE);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RetroLinkGameBoyCover");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile List<Entry> gbIndex;
    private static volatile List<Entry> gbcIndex;

    private GameBoyCoverArtManager() {}

    private static final class Entry {
        final String href, title, normalized, relaxed;
        Entry(String href, String title) {
            this.href = href;
            this.title = title;
            this.normalized = normalize(stripTags(title));
            this.relaxed = relax(normalized);
        }
    }

    public static File cachedCover(Context c, GameBoyRomRepository.ImportedGame game) {
        if (c == null || game == null || game.file == null) return null;
        File f = new File(coverDir(c), safeStem(game.file.getName()) + ".png");
        return validImage(f) ? f : null;
    }

    public static int cachedCount(Context c, List<GameBoyRomRepository.ImportedGame> games) {
        int n = 0;
        if (games != null) for (GameBoyRomRepository.ImportedGame g : games) if (cachedCover(c, g) != null) n++;
        return n;
    }

    public static void request(Context context, GameBoyRomRepository.ImportedGame game, Callback callback) {
        if (context == null || game == null) { post(callback, null, ""); return; }
        Context app = context.getApplicationContext();
        File cached = cachedCover(app, game);
        if (cached != null) {
            post(callback, cached, prefs(app).getString("match." + game.file.getName(), ""));
            return;
        }
        IO.execute(() -> {
            File result = null;
            String matched = "";
            try {
                boolean gbc = isColor(game);
                List<Entry> entries = loadIndex(app, gbc, false);
                Entry best = findBest(entries, game);
                if (best != null) {
                    result = downloadCover(app, game, best, gbc ? GBC_BASE : GB_BASE);
                    if (result != null) {
                        matched = best.title;
                        prefs(app).edit().putString("match." + game.file.getName(), matched).apply();
                    }
                }
            } catch (Throwable ignored) {}
            post(callback, result, matched);
        });
    }

    public static void invalidateIndexes(Context c) {
        gbIndex = null;
        gbcIndex = null;
        File a = indexFile(c, false), b = indexFile(c, true);
        if (a.isFile()) a.delete();
        if (b.isFile()) b.delete();
    }

    public static String sourceLabel(GameBoyRomRepository.ImportedGame game) {
        return "Libretro Thumbnails · " + (isColor(game) ? "Game Boy Color" : "Game Boy");
    }

    private static boolean isColor(GameBoyRomRepository.ImportedGame game) {
        return game != null && game.systemLabel != null && game.systemLabel.toLowerCase(Locale.US).contains("color");
    }

    private static void post(Callback cb, File f, String title) {
        if (cb != null) MAIN.post(() -> cb.onResult(f, title == null ? "" : title));
    }

    private static synchronized List<Entry> loadIndex(Context c, boolean gbc, boolean force) throws IOException {
        List<Entry> mem = gbc ? gbcIndex : gbIndex;
        if (!force && mem != null && !mem.isEmpty()) return mem;
        File cache = indexFile(c, gbc);
        String html = null;
        if (!force && cache.isFile() && System.currentTimeMillis() - cache.lastModified() < INDEX_TTL_MS)
            html = readText(cache, MAX_INDEX_BYTES);
        String base = gbc ? GBC_BASE : GB_BASE;
        if (html == null || html.isEmpty()) {
            try {
                html = downloadText(base, MAX_INDEX_BYTES);
                writeText(cache, html);
            } catch (IOException e) {
                if (cache.isFile()) html = readText(cache, MAX_INDEX_BYTES);
                else throw e;
            }
        }
        List<Entry> parsed = parseIndex(html);
        if (parsed.isEmpty()) throw new IOException("Índice vacío");
        if (gbc) gbcIndex = parsed; else gbIndex = parsed;
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

    private static Entry findBest(List<Entry> entries, GameBoyRomRepository.ImportedGame game) {
        List<String> targets = new ArrayList<>();
        if (game.sourceName != null && !game.sourceName.trim().isEmpty()) targets.add(stripExtension(game.sourceName));
        if (game.title != null && !game.title.trim().isEmpty()) targets.add(game.title);
        Entry best = null;
        double scoreBest = 0d;
        for (Entry e : entries) {
            double score = 0d;
            for (String t : targets) score = Math.max(score, similarity(t, e));
            if (score > scoreBest) { scoreBest = score; best = e; }
        }
        return scoreBest >= 0.54d ? best : null;
    }

    private static double similarity(String target, Entry e) {
        String n = normalize(stripTags(stripExtension(target)));
        if (n.isEmpty()) return 0d;
        if (n.equals(e.normalized)) return 1d;
        if (n.replace(" ", "").equals(e.normalized.replace(" ", ""))) return 0.99d;
        String r = relax(n);
        if (!r.isEmpty() && r.equals(e.relaxed)) return 0.98d;
        double token = Math.max(jaccard(n, e.normalized), jaccard(r, e.relaxed));
        double gram = Math.max(diceBigrams(n, e.normalized), diceBigrams(r, e.relaxed));
        double contains = e.normalized.contains(n) || n.contains(e.normalized) ? 0.08d : 0d;
        return Math.min(1d, token * 0.55d + gram * 0.45d + contains);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US).replace("&", " and ")
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String relax(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String t : s.split(" ")) {
            if (t.isEmpty() || t.equals("the") || t.equals("game") || t.equals("boy") || t.equals("color") || t.equals("nintendo")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(t);
        }
        return b.toString();
    }

    private static double jaccard(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0d;
        Set<String> aa = new HashSet<>(), bb = new HashSet<>();
        for (String s : a.split(" ")) if (!s.isEmpty()) aa.add(s);
        for (String s : b.split(" ")) if (!s.isEmpty()) bb.add(s);
        Set<String> i = new HashSet<>(aa); i.retainAll(bb);
        Set<String> u = new HashSet<>(aa); u.addAll(bb);
        return u.isEmpty() ? 0d : (double)i.size() / u.size();
    }

    private static double diceBigrams(String a, String b) {
        String x = a == null ? "" : a.replace(" ", "");
        String y = b == null ? "" : b.replace(" ", "");
        if (x.equals(y) && !x.isEmpty()) return 1d;
        if (x.length() < 2 || y.length() < 2) return 0d;
        List<String> ax = bigrams(x), by = bigrams(y);
        boolean[] used = new boolean[by.size()];
        int same = 0;
        for (String g : ax) for (int i = 0; i < by.size(); i++)
            if (!used[i] && g.equals(by.get(i))) { used[i] = true; same++; break; }
        return (2d * same) / (ax.size() + by.size());
    }

    private static List<String> bigrams(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 1 < s.length(); i++) out.add(s.substring(i, i + 2));
        return out;
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s*\\([^)]*\\)", " ").replaceAll("\\s*\\[[^]]*\\]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String stripExtension(String s) {
        if (s == null) return "";
        int dot = s.lastIndexOf('.');
        return dot > 0 ? s.substring(0, dot) : s;
    }

    private static File downloadCover(Context c, GameBoyRomRepository.ImportedGame game, Entry e, String base) throws IOException {
        File dst = new File(coverDir(c), safeStem(game.file.getName()) + ".png");
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        HttpURLConnection conn = open(new URL(new URL(base), e.href));
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) { conn.disconnect(); return null; }
        int total = 0;
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp, false))) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_IMAGE_BYTES) throw new IOException("Imagen demasiado grande");
                out.write(buf, 0, n);
            }
        } finally { conn.disconnect(); }
        if (!validPngSignature(tmp)) { tmp.delete(); return null; }
        if (dst.exists()) dst.delete();
        if (!tmp.renameTo(dst)) { copy(tmp, dst); tmp.delete(); }
        return validImage(dst) ? dst : null;
    }

    private static String downloadText(String url, int max) throws IOException {
        HttpURLConnection conn = open(new URL(url));
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) { conn.disconnect(); throw new IOException("HTTP " + code); }
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int total = 0, n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) throw new IOException("Índice demasiado grande");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { conn.disconnect(); }
    }

    private static HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "RetroLink/0.7.1 Android; cover-art lookup");
        c.setRequestProperty("Accept", "text/html,image/png,*/*;q=0.8");
        return c;
    }

    private static File coverDir(Context c) {
        File d = new File(c.getFilesDir(), "covers/gameboy");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File metaDir(Context c) {
        File d = new File(c.getFilesDir(), "metadata/gameboy");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File indexFile(Context c, boolean gbc) {
        return new File(metaDir(c), gbc ? "gbc_boxarts_index.html" : "gb_boxarts_index.html");
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static String safeStem(String s) {
        s = s == null ? "game" : s.replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private static boolean validImage(File f) { return f != null && f.isFile() && f.length() > 128 && validPngSignature(f); }

    private static boolean validPngSignature(File f) {
        if (f == null || !f.isFile() || f.length() < 8) return false;
        byte[] sig = new byte[8];
        try (FileInputStream in = new FileInputStream(f)) {
            if (in.read(sig) != 8) return false;
            return (sig[0] & 0xff) == 0x89 && sig[1] == 0x50 && sig[2] == 0x4e && sig[3] == 0x47
                    && sig[4] == 0x0d && sig[5] == 0x0a && sig[6] == 0x1a && sig[7] == 0x0a;
        } catch (IOException e) { return false; }
    }

    private static String readText(File f, int max) throws IOException {
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int total = 0, n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) throw new IOException("Caché demasiado grande");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeText(File f, String text) throws IOException {
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copy(File a, File b) throws IOException {
        try (FileInputStream in = new FileInputStream(a); FileOutputStream out = new FileOutputStream(b, false)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
