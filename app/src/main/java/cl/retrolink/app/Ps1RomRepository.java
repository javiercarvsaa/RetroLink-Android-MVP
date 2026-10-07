package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Biblioteca local PlayStation.
 * CHD/PBP son el formato recomendado. CUE+BIN se importa seleccionando todos
 * los archivos del juego en una sola operación.
 */
public final class Ps1RomRepository {
    private static final long MAX_SINGLE_FILE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final String PREFS = "retrolink_ps1_library_v100";
    private static final String K_LAST = "last_path";
    private static final String LAUNCH_FILE = "launch.txt";

    public static final class ImportedGame {
        public final File file;
        public final String title;
        public final String systemLabel;
        public final String sourceName;

        ImportedGame(File file, String title, String sourceName) {
            this.file = file;
            this.title = title;
            this.systemLabel = "PlayStation";
            this.sourceName = sourceName == null ? "" : sourceName;
        }
    }

    private Ps1RomRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File romDir(Context c) {
        File d = new File(c.getFilesDir(), "roms/ps1");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static ImportedGame lastGame(Context c) {
        String path = prefs(c).getString(K_LAST, "");
        if (path == null || path.trim().isEmpty()) return null;
        File f = new File(path);
        return f.isFile() ? describe(f, f.getName()) : null;
    }

    public static void select(Context c, ImportedGame game) {
        if (game == null || game.file == null || !game.file.isFile()) return;
        File folder = game.file.getParentFile();
        if (folder != null) folder.setLastModified(System.currentTimeMillis());
        prefs(c).edit().putString(K_LAST, game.file.getAbsolutePath()).apply();
    }

    public static List<ImportedGame> listGames(Context c) {
        File[] folders = romDir(c).listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) return Collections.emptyList();

        List<ImportedGame> out = new ArrayList<>();
        for (File folder : folders) {
            File marker = new File(folder, LAUNCH_FILE);
            if (!marker.isFile()) continue;
            try {
                String name = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
                if (name.isEmpty()) continue;
                File launch = new File(folder, name);
                if (launch.isFile()) out.add(describe(launch, name));
            } catch (Exception ignored) {}
        }

        out.sort(Comparator.comparingLong((ImportedGame g) -> {
            File f = g.file.getParentFile();
            return f == null ? 0L : f.lastModified();
        }).reversed());
        return out;
    }

    public static ImportedGame importUris(Context c, List<Uri> uris) throws Exception {
        if (uris == null || uris.isEmpty())
            throw new IllegalArgumentException("Selecciona un juego PS1");

        List<Source> sources = new ArrayList<>();
        for (Uri uri : uris) {
            if (uri == null) continue;
            String display = resolveDisplayName(c, uri);
            String cleanName = sanitizeFileName(display);
            if (!isSupported(cleanName))
                throw new IllegalArgumentException("Formato no soportado: " + cleanName);
            sources.add(new Source(uri, cleanName));
        }
        if (sources.isEmpty())
            throw new IllegalArgumentException("No se encontraron archivos compatibles");

        Source primary = choosePrimary(sources);
        String title = stripExtension(primary.name);
        String safeTitle = title.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safeTitle.isEmpty()) safeTitle = "PlayStation";
        if (safeTitle.length() > 48) safeTitle = safeTitle.substring(0, 48);

        File folder = new File(romDir(c),
                safeTitle + "_" + Long.toHexString(System.currentTimeMillis()));
        if (!folder.mkdirs())
            throw new IllegalStateException("No se pudo crear la carpeta del juego");

        try {
            for (Source source : sources) copyUri(c, source.uri, new File(folder, source.name));
            Files.write(new File(folder, LAUNCH_FILE).toPath(),
                    primary.name.getBytes(StandardCharsets.UTF_8));

            File launch = new File(folder, primary.name);
            ImportedGame game = describe(launch, primary.name);
            select(c, game);
            return game;
        } catch (Exception e) {
            deleteRecursive(folder);
            throw e;
        }
    }

    private static Source choosePrimary(List<Source> src) {
        String[] order = {".cue", ".chd", ".pbp", ".m3u", ".iso", ".img", ".mdf", ".bin"};
        for (String ext : order)
            for (Source s : src)
                if (s.name.toLowerCase(Locale.US).endsWith(ext)) return s;
        return src.get(0);
    }

    private static void copyUri(Context c, Uri uri, File dst) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dst, false)) {
            if (in == null) throw new IllegalStateException("No se pudo abrir " + dst.getName());
            byte[] buf = new byte[256 * 1024];
            long total = 0L;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > MAX_SINGLE_FILE_BYTES)
                    throw new IllegalStateException("Archivo demasiado grande: " + dst.getName());
            }
            if (total <= 0) throw new IllegalStateException("Archivo vacío: " + dst.getName());
            out.getFD().sync();
        }
    }

    private static boolean isSupported(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".chd") || n.endsWith(".pbp") || n.endsWith(".cue")
                || n.endsWith(".bin") || n.endsWith(".iso") || n.endsWith(".img")
                || n.endsWith(".mdf") || n.endsWith(".m3u");
    }

    private static ImportedGame describe(File file, String sourceName) {
        String title = stripExtension(sourceName == null ? file.getName() : sourceName)
                .replace('_', ' ').trim();
        if (title.isEmpty()) title = "Juego PlayStation";
        return new ImportedGame(file, title, sourceName);
    }

    private static String stripExtension(String s) {
        if (s == null) return "";
        return s.replaceFirst("(?i)\\.(chd|pbp|cue|bin|iso|img|mdf|m3u)$", "");
    }

    private static String sanitizeFileName(String s) {
        if (s == null || s.trim().isEmpty()) return "game.chd";
        String v = new File(s).getName().replace('\u0000', '_');
        return v.length() > 180 ? v.substring(v.length() - 180) : v;
    }

    private static String resolveDisplayName(Context c, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.trim().isEmpty()) return name.trim();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String last = uri == null ? null : uri.getLastPathSegment();
        return last == null ? "game.chd" : last;
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    private static final class Source {
        final Uri uri;
        final String name;
        Source(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }
}
