package cl.retrolink.app;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Biblioteca N64 local. RetroLink solo indexa/copias ROM entregadas por el usuario. */
public final class N64RomRepository {
    private static final long MAX_ROM_BYTES = 128L * 1024L * 1024L;

    public static final class ImportedRom {
        public final File file;
        public final N64RomInfo info;
        ImportedRom(File file, N64RomInfo info) {
            this.file = file;
            this.info = info;
        }
    }

    private N64RomRepository() {}

    private static File romDir(Context c) {
        File dir = new File(c.getFilesDir(), "roms/n64");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    public static File lastRom(Context c) {
        String path = RetroPreferences.lastRomPath(c);
        if (path == null || path.trim().isEmpty()) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    public static N64RomInfo readLastInfo(Context c) throws Exception {
        File f = lastRom(c);
        return f == null ? null : readInfo(f);
    }

    public static N64RomInfo readInfo(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            return N64RomInfo.read(in);
        }
    }

    /** Devuelve solo ROM realmente presentes y válidas. */
    public static List<ImportedRom> listRoms(Context c) {
        File[] files = romDir(c).listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".rom"));
        if (files == null || files.length == 0) return Collections.emptyList();
        List<ImportedRom> out = new ArrayList<>();
        for (File f : files) {
            if (!f.isFile()) continue;
            try { out.add(new ImportedRom(f, readInfo(f))); }
            catch (Exception ignored) {}
        }
        out.sort(Comparator.comparingLong((ImportedRom r) -> r.file.lastModified()).reversed());
        return out;
    }

    public static void select(Context c, ImportedRom rom) {
        if (rom == null || rom.file == null || !rom.file.isFile()) return;
        //noinspection ResultOfMethodCallIgnored
        rom.file.setLastModified(System.currentTimeMillis());
        RetroPreferences.setLastRom(c, rom.file.getAbsolutePath(), rom.info.summary());
    }

    public static ImportedRom importRom(Context c, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("Archivo no disponible");
        File dir = romDir(c);
        File tmp = new File(dir, "importing.tmp");

        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp, false)) {
            if (in == null) throw new IllegalStateException("No se pudo abrir el archivo");
            byte[] buf = new byte[256 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > MAX_ROM_BYTES) throw new IllegalStateException("ROM demasiado grande");
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }

        N64RomInfo info;
        try { info = readInfo(tmp); }
        catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }

        String safeTitle = info.title == null ? "N64" : info.title.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safeTitle.length() > 36) safeTitle = safeTitle.substring(0, 36);
        String code = info.gameCode == null || info.gameCode.trim().isEmpty() ? "UNK" : info.gameCode.trim();
        File dst = new File(dir, safeTitle + "_" + code + "_" + ((int) info.regionCode) + "_v" + info.version + ".rom");

        if (dst.exists() && !dst.delete()) {
            // Si no podemos reemplazar la ROM anterior, conservamos una variante temporal única.
            dst = new File(dir, safeTitle + "_" + System.currentTimeMillis() + ".rom");
        }
        if (!tmp.renameTo(dst)) {
            try (FileInputStream in = new FileInputStream(tmp);
                 FileOutputStream out = new FileOutputStream(dst, false)) {
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        dst.setLastModified(System.currentTimeMillis());
        ImportedRom imported = new ImportedRom(dst, info);
        select(c, imported);
        return imported;
    }
}
