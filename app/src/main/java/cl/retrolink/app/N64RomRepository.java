package cl.retrolink.app;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Único punto de importación/lectura de la ROM N64 seleccionada.
 * Mantiene Biblioteca, Inicio y Sala usando exactamente el mismo contenido real.
 */
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

    public static File lastRom(Context c) {
        String path = RetroPreferences.lastRomPath(c);
        if (path == null || path.trim().isEmpty()) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    public static N64RomInfo readLastInfo(Context c) throws Exception {
        File f = lastRom(c);
        if (f == null) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            return N64RomInfo.read(in);
        }
    }

    public static ImportedRom importRom(Context c, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("Archivo no disponible");
        File dir = new File(c.getFilesDir(), "roms/n64");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear la biblioteca N64");
        File dst = new File(dir, "selected_n64.rom");

        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dst, false)) {
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
            dst.delete();
            throw e;
        }

        N64RomInfo info;
        try (FileInputStream in = new FileInputStream(dst)) {
            info = N64RomInfo.read(in);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            throw e;
        }

        RetroPreferences.setLastRom(c, dst.getAbsolutePath(), info.summary());
        return new ImportedRom(dst, info);
    }
}
