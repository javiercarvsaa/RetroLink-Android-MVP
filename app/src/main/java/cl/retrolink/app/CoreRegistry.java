package cl.retrolink.app;

import java.util.Locale;

/** Registro central de plataformas/cores. */
public final class CoreRegistry {
    public static final class Core {
        public final String id;
        public final String system;
        public final String shortSystem;
        public final String libraryFile;
        public final String extensions;
        public final boolean multiplayerLocal;
        public final boolean hardwareVideo;

        Core(String id, String system, String shortSystem, String libraryFile,
             String extensions, boolean multiplayerLocal, boolean hardwareVideo) {
            this.id = id;
            this.system = system;
            this.shortSystem = shortSystem;
            this.libraryFile = libraryFile;
            this.extensions = extensions;
            this.multiplayerLocal = multiplayerLocal;
            this.hardwareVideo = hardwareVideo;
        }

        public boolean supportsFileName(String name) {
            if (name == null) return false;
            String n = name.toLowerCase(Locale.US);
            for (String ext : extensions.split("\\|")) {
                if (n.endsWith("." + ext.toLowerCase(Locale.US))) return true;
            }
            return false;
        }
    }

    public static final Core N64 = new Core(
            "n64.mupen64plus-next", "Nintendo 64", "N64",
            "libretro_n64.so", "n64|v64|z64|bin|u1", true, true);

    public static final Core GAME_BOY = new Core(
            "gb.gambatte", "Game Boy / Game Boy Color", "GB/GBC",
            "libretro_gambatte.so", "gb|gbc", false, false);

    public static final Core SNES = new Core(
            "snes.snes9x", "Super Nintendo Entertainment System", "SNES",
            "libretro_snes9x.so", "sfc|smc", true, false);

    public static final Core ATARI_2600 = new Core(
            "atari2600.stella2014", "Atari 2600", "ATARI 2600",
            "libretro_atari2600.so", "a26", true, false);

    public static final Core PS1 = new Core(
            "ps1.pcsx_rearmed", "Sony PlayStation", "PS1",
            "libretro_ps1.so", "chd|pbp|cue|iso|img|mdf|m3u", true, false);

    private CoreRegistry() {}

    public static Core byId(String id) {
        if (GAME_BOY.id.equals(id)) return GAME_BOY;
        if (SNES.id.equals(id)) return SNES;
        if (ATARI_2600.id.equals(id)) return ATARI_2600;
        if (PS1.id.equals(id)) return PS1;
        return N64;
    }

    public static Core forFileName(String name) {
        if (GAME_BOY.supportsFileName(name)) return GAME_BOY;
        if (SNES.supportsFileName(name)) return SNES;
        if (ATARI_2600.supportsFileName(name)) return ATARI_2600;
        if (PS1.supportsFileName(name)) return PS1;
        if (N64.supportsFileName(name)) return N64;
        return null;
    }

    public static Core forN64Rom(N64RomInfo info) { return N64; }
}
