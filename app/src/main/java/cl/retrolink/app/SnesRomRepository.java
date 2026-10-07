package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Biblioteca local Super Nintendo (.sfc / .smc). */
public final class SnesRomRepository {
    private static final long MAX_ROM_BYTES = 32L * 1024L * 1024L;
    private static final String PREFS = "retrolink_snes_library_v080";
    private static final String K_LAST = "last_path";

    public static final class ImportedGame {
        public final File file;
        public final String title;
        public final String systemLabel;
        public final String sourceName;

        ImportedGame(File file, String title, String sourceName) {
            this.file = file;
            this.title = title;
            this.systemLabel = "Super Nintendo";
            this.sourceName = sourceName == null ? "" : sourceName;
        }
    }

    private SnesRomRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File romDir(Context c) {
        File d = new File(c.getFilesDir(), "roms/snes");
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
        game.file.setLastModified(System.currentTimeMillis());
        prefs(c).edit().putString(K_LAST, game.file.getAbsolutePath()).apply();
    }

    public static List<ImportedGame> listGames(Context c) {
        File[] files = romDir(c).listFiles((dir, name) -> {
            String n = name.toLowerCase(Locale.US);
            return n.endsWith(".sfc") || n.endsWith(".smc");
        });
        if (files == null || files.length == 0) return Collections.emptyList();

        List<ImportedGame> out = new ArrayList<>();
        for (File f : files) if (f.isFile()) out.add(describe(f, f.getName()));
        out.sort(Comparator.comparingLong((ImportedGame g) -> g.file.lastModified()).reversed());
        return out;
    }

    public static ImportedGame importRom(Context c, Uri uri) throws Exception {
        String source = resolveDisplayName(c, uri);
        String lower = source == null ? "" : source.toLowerCase(Locale.US);
        if (!lower.endsWith(".sfc") && !lower.endsWith(".smc"))
            throw new IllegalArgumentException("Selecciona un archivo .sfc o .smc");

        String ext = lower.endsWith(".smc") ? ".smc" : ".sfc";
        File dir = romDir(c);
        File tmp = new File(dir, "importing.tmp");

        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp, false)) {
            if (in == null) throw new IllegalStateException("No se pudo abrir el archivo");
            byte[] buf = new byte[128 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > MAX_ROM_BYTES) throw new IllegalStateException("ROM demasiado grande");
            }
            if (total < 0x8000) throw new IllegalStateException("ROM demasiado pequeña");
            out.getFD().sync();
        } catch (Exception e) {
            tmp.delete();
            throw e;
        }

        String title = stripExtension(source);
        String safe = title.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.isEmpty()) safe = "SNES";
        if (safe.length() > 48) safe = safe.substring(0, 48);

        File dst = new File(dir, safe + "_" + Integer.toHexString(source.hashCode()) + ext);
        if (dst.exists() && !dst.delete())
            dst = new File(dir, safe + "_" + System.currentTimeMillis() + ext);

        if (!tmp.renameTo(dst)) {
            try (FileInputStream in = new FileInputStream(tmp);
                 FileOutputStream out = new FileOutputStream(dst, false)) {
                byte[] buf = new byte[128 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.getFD().sync();
            }
            tmp.delete();
        }

        dst.setLastModified(System.currentTimeMillis());
        ImportedGame imported = describe(dst, source);
        select(c, imported);
        return imported;
    }

    private static ImportedGame describe(File f, String sourceName) {
        String title = stripExtension(sourceName == null ? f.getName() : sourceName)
                .replace('_', ' ').trim();
        if (title.isEmpty()) title = "Juego SNES";
        return new ImportedGame(f, title, sourceName);
    }

    private static String stripExtension(String s) {
        if (s == null) return "";
        return s.replaceFirst("(?i)\\.(sfc|smc)$", "");
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
        return last == null ? "game.sfc" : last;
    }
}
