package cl.retrolink.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Importa BIOS PS1 al system directory usado por PCSX-ReARMed. */
public final class Ps1BiosManager {
    private static final long BIOS_SIZE = 512L * 1024L;

    private Ps1BiosManager() {}

    public static File systemDir(Context c) {
        File d = new File(c.getFilesDir(), "cores/ps1.pcsx_rearmed/system");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File importBios(Context c, Uri uri) throws Exception {
        String name = displayName(c, uri);
        if (name == null || name.trim().isEmpty()) name = "scph5501.bin";
        name = new File(name).getName();
        if (!name.toLowerCase(java.util.Locale.US).endsWith(".bin"))
            throw new IllegalArgumentException("La BIOS debe ser un archivo .bin");

        File tmp = new File(systemDir(c), "bios_import.tmp");
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp, false)) {
            if (in == null) throw new IllegalStateException("No se pudo abrir la BIOS");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > BIOS_SIZE)
                    throw new IllegalArgumentException("BIOS mayor a 512 KiB");
            }
            out.getFD().sync();
        } catch (Exception e) {
            tmp.delete();
            throw e;
        }

        if (total != BIOS_SIZE) {
            tmp.delete();
            throw new IllegalArgumentException("La BIOS PS1 debe tener exactamente 512 KiB");
        }

        File dst = new File(systemDir(c), name);
        if (dst.exists()) dst.delete();
        if (!tmp.renameTo(dst))
            throw new IllegalStateException("No se pudo guardar la BIOS");
        return dst;
    }

    public static File detectedBios(Context c) {
        File[] files = systemDir(c).listFiles((dir, name) ->
                name.toLowerCase(java.util.Locale.US).endsWith(".bin"));
        if (files == null) return null;
        for (File f : files)
            if (f.isFile() && f.length() == BIOS_SIZE) return f;
        return null;
    }

    public static String status(Context c) {
        File f = detectedBios(c);
        return f == null
                ? "BIOS: HLE disponible · importa BIOS oficial para mejor compatibilidad"
                : "BIOS: " + f.getName() + " · 512 KiB";
    }

    private static String displayName(Context c, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri == null ? null : uri.getLastPathSegment();
    }
}
