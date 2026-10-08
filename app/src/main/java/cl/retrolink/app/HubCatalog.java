package cl.retrolink.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit adapters for the actual cores in 9379168; no extension-based guessing. */
public final class HubCatalog {
    public static final class Platform {
        public final CoreRegistry.Core core;
        public final String label, engine, connection;
        public final boolean independent;
        final Class<? extends Activity> library, room;
        Platform(CoreRegistry.Core core, String label, String engine, String connection,
                 boolean independent, Class<? extends Activity> library, Class<? extends Activity> room) {
            this.core = core; this.label = label; this.engine = engine; this.connection = connection;
            this.independent = independent; this.library = library; this.room = room;
        }
    }
    public static final List<Platform> PLATFORMS;
    static {
        List<Platform> p = new ArrayList<>();
        p.add(new Platform(CoreRegistry.PSP, "PSP", "PPSSPP 1.20.4", "Ad Hoc · una PSP por teléfono", true,
                PspLibraryActivity.class, PspRoomActivity.class));
        p.add(new Platform(CoreRegistry.PS1, "PlayStation", "PCSX-ReARMed", "Consola anfitriona + mando remoto", false,
                Ps1LibraryActivity.class, Ps1RoomActivity.class));
        p.add(new Platform(CoreRegistry.N64, "Nintendo 64", "Mupen64Plus-Next", "Consola anfitriona + mandos remotos", false,
                LibraryActivity.class, HostActivity.class));
        p.add(new Platform(CoreRegistry.SNES, "Super Nintendo", "Snes9x", "Consola anfitriona + mando remoto", false,
                SnesLibraryActivity.class, SnesRoomActivity.class));
        p.add(new Platform(CoreRegistry.ATARI_2600, "Atari 2600", "Stella 2014", "Consola anfitriona + mando remoto", false,
                Atari2600LibraryActivity.class, Atari2600RoomActivity.class));
        p.add(new Platform(CoreRegistry.GAME_BOY, "Game Boy / Color", "Gambatte", "Game Boy Link · emulación enlazada", true,
                GameBoyLibraryActivity.class, GameBoyLinkActivity.class));
        PLATFORMS = Collections.unmodifiableList(p);
    }
    public static Platform platform(String coreId) {
        for (Platform p : PLATFORMS) if (p.core.id.equals(coreId)) return p;
        return null; // Do not silently route unknown consoles to N64.
    }
    public static final class Snapshot {
        public final List<HubGame> games = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }
    private HubCatalog() {}

    /** Invoke on a worker thread. Only existing indexes and cached cover paths are read. */
    public static Snapshot load(Context c) {
        Snapshot snapshot = new Snapshot();
        for (Platform p : PLATFORMS) {
            if (Thread.currentThread().isInterrupted()) break;
            try { snapshot.games.addAll(loadPlatform(c, p)); }
            catch (RuntimeException e) { snapshot.errors.add(p.label); }
        }
        return snapshot;
    }
    private static List<HubGame> loadPlatform(Context c, Platform p) {
        List<HubGame> out = new ArrayList<>();
        switch (p.core.id) {
            case "n64.mupen64plus-next":
                for (N64RomRepository.ImportedRom g : N64RomRepository.listRoms(c))
                    out.add(new HubGame(p.core.id, p.label, g.info.title, g.file, CoverArtManager.cachedCover(c, g), g));
                break;
            case "gb.gambatte":
                for (GameBoyRomRepository.ImportedGame g : GameBoyRomRepository.listGames(c))
                    out.add(new HubGame(p.core.id, p.label, g.title, g.file, GameBoyCoverArtManager.cachedCover(c, g), g));
                break;
            case "snes.snes9x":
                for (SnesRomRepository.ImportedGame g : SnesRomRepository.listGames(c))
                    out.add(new HubGame(p.core.id, p.label, g.title, g.file, SnesCoverArtManager.cachedCover(c, g), g));
                break;
            case "atari2600.stella2014":
                for (Atari2600RomRepository.ImportedGame g : Atari2600RomRepository.listGames(c))
                    out.add(new HubGame(p.core.id, p.label, g.title, g.file, Atari2600CoverArtManager.cachedCover(c, g), g));
                break;
            case "ps1.pcsx_rearmed":
                for (Ps1RomRepository.ImportedGame g : Ps1RomRepository.listGames(c))
                    out.add(new HubGame(p.core.id, p.label, g.title, g.file, Ps1CoverArtManager.cachedCover(c, g), g));
                break;
            case "psp.ppsspp":
                for (PspRomRepository.ImportedGame g : PspRomRepository.listGames(c))
                    out.add(new HubGame(p.core.id, p.label, g.title, g.file, PspCoverArtManager.cachedCover(c, g), g));
                break;
            default: throw new IllegalArgumentException("Unsupported core");
        }
        return out;
    }
    public static boolean enginePackaged(Context c, Platform p) {
        return new File(c.getApplicationInfo().nativeLibraryDir, p.core.libraryFile).isFile();
    }
    public static Intent libraryIntent(Context c, Platform p) { return new Intent(c, p.library); }
    public static Intent roomIntent(Context c, Platform p) { return new Intent(c, p.room); }

    /** Exact solo routes from existing library methods; leaves core, save and transport code untouched. */
    public static void launchSolo(Activity a, HubGame game) throws Exception {
        if (!game.file.isFile()) throw new java.io.IOException("El archivo del juego ya no está disponible");
        Platform p = platform(game.coreId);
        if (p == null) throw new IllegalArgumentException("Plataforma no reconocida");
        if (!enginePackaged(a, p)) throw new IllegalStateException("El núcleo no está incluido en esta instalación");
        Intent i;
        switch (game.coreId) {
            case "n64.mupen64plus-next":
                N64RomRepository.select(a, (N64RomRepository.ImportedRom) game.original);
                SessionState.reset(); SessionState.setConfiguredPlayers(1); InputHub.resetAll(); EmulatorFrameHub.clear();
                i = new Intent(a, IntegratedN64Activity.class);
                i.putExtra(IntegratedN64Activity.EXTRA_ROM_PATH, game.file.getAbsolutePath());
                i.putExtra(IntegratedN64Activity.EXTRA_PLAYERS, 1); break;
            case "gb.gambatte":
                GameBoyRomRepository.select(a, (GameBoyRomRepository.ImportedGame) game.original);
                i = new Intent(a, IntegratedGameActivity.class);
                i.putExtra(IntegratedGameActivity.EXTRA_CORE_ID, CoreRegistry.GAME_BOY.id);
                i.putExtra(IntegratedGameActivity.EXTRA_ROM_PATH, game.file.getAbsolutePath());
                i.putExtra(IntegratedGameActivity.EXTRA_GAME_TITLE, game.title); break;
            case "snes.snes9x":
                SnesRomRepository.select(a, (SnesRomRepository.ImportedGame) game.original);
                i = new Intent(a, SnesGameActivity.class);
                i.putExtra(SnesGameActivity.EXTRA_ROM_PATH, game.file.getAbsolutePath());
                i.putExtra(SnesGameActivity.EXTRA_GAME_TITLE, game.title);
                i.putExtra(SnesGameActivity.EXTRA_HOST_SESSION, false); break;
            case "atari2600.stella2014":
                Atari2600RomRepository.select(a, (Atari2600RomRepository.ImportedGame) game.original);
                i = new Intent(a, Atari2600GameActivity.class);
                i.putExtra(Atari2600GameActivity.EXTRA_ROM_PATH, game.file.getAbsolutePath());
                i.putExtra(Atari2600GameActivity.EXTRA_GAME_TITLE, game.title);
                i.putExtra(Atari2600GameActivity.EXTRA_HOST_SESSION, false); break;
            case "ps1.pcsx_rearmed":
                Ps1RomRepository.select(a, (Ps1RomRepository.ImportedGame) game.original);
                i = new Intent(a, Ps1GameActivity.class);
                i.putExtra(Ps1GameActivity.EXTRA_ROM_PATH, game.file.getAbsolutePath());
                i.putExtra(Ps1GameActivity.EXTRA_GAME_TITLE, game.title);
                i.putExtra(Ps1GameActivity.EXTRA_HOST_SESSION, false); break;
            case "psp.ppsspp":
                PspRomRepository.select(a, (PspRomRepository.ImportedGame) game.original);
                PspLauncher.launchGame(a, game.file, PspIniManager.Mode.SINGLE, null); return;
            default: throw new IllegalArgumentException("Plataforma no reconocida");
        }
        a.startActivity(i);
    }
}
