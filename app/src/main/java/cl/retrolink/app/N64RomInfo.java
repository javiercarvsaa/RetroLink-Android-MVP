package cl.retrolink.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class N64RomInfo {
    public final String title;
    public final String gameCode;
    public final char regionCode;
    public final int version;
    public final String byteOrder;
    public final boolean marioKart64;

    private N64RomInfo(String title, String gameCode, char regionCode, int version, String byteOrder, boolean marioKart64) {
        this.title = title;
        this.gameCode = gameCode;
        this.regionCode = regionCode;
        this.version = version;
        this.byteOrder = byteOrder;
        this.marioKart64 = marioKart64;
    }

    public static N64RomInfo read(InputStream in) throws IOException {
        byte[] h = new byte[0x40];
        int off = 0;
        while (off < h.length) {
            int n = in.read(h, off, h.length - off);
            if (n < 0) break;
            off += n;
        }
        if (off < h.length) throw new IOException("Archivo ROM demasiado pequeño");
        String order;
        int magic = ((h[0] & 0xff) << 24) | ((h[1] & 0xff) << 16) | ((h[2] & 0xff) << 8) | (h[3] & 0xff);
        if (magic == 0x80371240) {
            order = "z64 / big-endian";
        } else if (magic == 0x37804012) {
            order = "v64 / byte-swapped";
            for (int i = 0; i < h.length; i += 2) {
                byte t = h[i]; h[i] = h[i + 1]; h[i + 1] = t;
            }
        } else if (magic == 0x40123780) {
            order = "n64 / word-swapped";
            for (int i = 0; i < h.length; i += 4) {
                byte a = h[i], b = h[i+1];
                h[i] = h[i+3]; h[i+1] = h[i+2]; h[i+2] = b; h[i+3] = a;
            }
        } else {
            throw new IOException("Cabecera N64 no reconocida");
        }
        String title = new String(h, 0x20, 0x14, StandardCharsets.US_ASCII).replace("\0", "").trim();
        String code = new String(h, 0x3b, 3, StandardCharsets.US_ASCII).trim();
        char region = (char) (h[0x3e] & 0xff);
        int version = h[0x3f] & 0xff;
        boolean mk = title.replace(" ", "").toUpperCase().contains("MARIOKART64") || code.equalsIgnoreCase("NKT");
        return new N64RomInfo(title, code, region, version, order, mk);
    }

    public String friendlyRegion() {
        if (regionCode == 'P') return "Europa / PAL";
        if (regionCode == 'E') return "USA";
        if (regionCode == 'J') return "Japón";
        return "Región " + regionCode;
    }

    public String revisionText() { return version == 0 ? "Original" : "Rev " + (char)('A' + version - 1); }

    public String summary() {
        return title + " · " + friendlyRegion() + " · " + revisionText() + " · " + byteOrder;
    }
}
