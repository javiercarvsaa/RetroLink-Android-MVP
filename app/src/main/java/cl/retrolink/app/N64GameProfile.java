package cl.retrolink.app;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Lightweight per-ROM compatibility identification. Never modifies or bundles the ROM. */
public final class N64GameProfile {
    public static final int GENERIC = 0;
    public static final int DK64 = 1;

    public final int id;
    public final String internalName;
    public final long crc1;
    public final long crc2;
    public final int country;
    public final int revision;
    public final boolean exactDk64Us10;

    private N64GameProfile(int id, String internalName, long crc1, long crc2,
                           int country, int revision, boolean exactDk64Us10) {
        this.id = id;
        this.internalName = internalName;
        this.crc1 = crc1;
        this.crc2 = crc2;
        this.country = country;
        this.revision = revision;
        this.exactDk64Us10 = exactDk64Us10;
    }

    public static N64GameProfile detect(String path) {
        byte[] h = new byte[64];
        try (FileInputStream in = new FileInputStream(new File(path))) {
            int n = 0;
            while (n < h.length) {
                int r = in.read(h, n, h.length - n);
                if (r < 0) break;
                n += r;
            }
            if (n < 64) return generic();
        } catch (Throwable ignored) { return generic(); }

        long c1 = u32be(h, 0x10);
        long c2 = u32be(h, 0x14);
        String name;
        try { name = new String(h, 0x20, 20, StandardCharsets.US_ASCII).trim(); }
        catch (Throwable t) { name = ""; }
        int country = h[0x3e] & 0xff;
        int revision = h[0x3f] & 0xff;

        boolean exactUs10 = c1 == 0xEC58EABFL && c2 == 0xAD7C7169L
                && country == 0x45 && revision == 0 && "DONKEY KONG 64".equals(name);
        boolean dk64 = exactUs10 || "DONKEY KONG 64".equals(name);
        return new N64GameProfile(dk64 ? DK64 : GENERIC, name, c1, c2, country, revision, exactUs10);
    }

    private static N64GameProfile generic() {
        return new N64GameProfile(GENERIC, "", 0, 0, 0, 0, false);
    }

    private static long u32be(byte[] b, int o) {
        return ((long)(b[o] & 0xff) << 24) | ((long)(b[o+1] & 0xff) << 16)
                | ((long)(b[o+2] & 0xff) << 8) | (long)(b[o+3] & 0xff);
    }

    public boolean isDk64() { return id == DK64; }

    public String shortLabel() {
        if (!isDk64()) return "N64 AUTO";
        return exactDk64Us10 ? "DK64 US 1.0 · VI/FB + PSYNC" : "DK64 · VI/FB + PSYNC";
    }

    public String diagnostic() {
        return String.format(Locale.US, "%s · CRC %08X-%08X · C:%02X R:%d",
                shortLabel(), crc1, crc2, country, revision);
    }
}
