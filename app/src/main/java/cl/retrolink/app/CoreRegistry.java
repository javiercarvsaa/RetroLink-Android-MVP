package cl.retrolink.app;

public final class CoreRegistry {
    public static final class Core {
        public final String id;
        public final String system;
        public final String libraryFile;
        public final String extensions;
        Core(String id, String system, String libraryFile, String extensions) {
            this.id = id; this.system = system; this.libraryFile = libraryFile; this.extensions = extensions;
        }
    }

    public static final Core N64 = new Core(
            "n64.mupen64plus-next",
            "Nintendo 64",
            "libretro_n64.so",
            "n64|v64|z64|bin|u1");

    private CoreRegistry() {}

    public static Core forN64Rom(N64RomInfo info) { return N64; }
}
