package cl.retrolink.app;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real JVM tests for the Android-independent catalogue model, not phone/emulator tests. */
public final class HubGameTest {
    private static int passed;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++;
    }
    public static void main(String[] args) {
        HubGame psp = new HubGame("psp.ppsspp", "PSP", "Café Rally", new File("/test/psp/game.iso"), null, null);
        HubGame ps1 = new HubGame("ps1.pcsx_rearmed", "PlayStation", "Café Rally", new File("/test/ps1/game.iso"), null, null);
        HubGame snes = new HubGame("snes.snes9x", "Super Nintendo", "Árbol", new File("/test/snes/a.sfc"), null, null);
        HubGame atari = new HubGame("atari2600.stella2014", "Atari 2600", "Pac Test", new File("/test/atari/p.a26"), null, null);
        HubGame n64 = new HubGame("n64.mupen64plus-next", "Nintendo 64", "Zeta", new File("/test/n64/z.z64"), null, null);
        HubGame gb = new HubGame("gb.gambatte", "Game Boy / Color", "Beta", new File("/test/gb/b.gbc"), null, null);
        List<HubGame> all = Arrays.asList(psp, ps1, snes, atari, n64, gb);
        Set<String> stars = new HashSet<>(Arrays.asList(psp.key, gb.key));
        Map<String, Long> recent = new HashMap<>(); recent.put(ps1.key, 10L); recent.put(psp.key, 30L); recent.put(atari.key, 20L);
        check(psp.key.length() == 64, "full SHA-256 identity");
        check(psp.key.matches("[a-f0-9]+"), "hex identity");
        check(!psp.key.equals(ps1.key), "ISO games remain separate across systems");
        check(!psp.key.equals(HubGame.identity("ps1.pcsx_rearmed", psp.file)), "core ID contributes to identity");
        check(psp.key.equals(HubGame.identity(psp.coreId, psp.file)), "deterministic identity");
        check(psp.key.equals(HubGame.identity(psp.coreId, new File("/test/psp/../psp/game.iso"))), "canonical path");
        check(!psp.key.contains("game"), "no readable path in stored key");
        check("cafe".equals(HubGame.normalized("  CAFÉ  ")), "accent and case normalization");
        check("".equals(HubGame.normalized(null)), "null query normalization");
        check(HubGame.filter(all, "", "", false, false, stars, recent).size() == 6, "all platforms");
        check(HubGame.filter(all, null, null, false, false, stars, recent).size() == 6, "null filter");
        check(HubGame.filter(all, "missing", "", false, false, stars, recent).isEmpty(), "unknown ID never defaults to N64");
        check(HubGame.filter(all, psp.coreId, "", false, false, stars, recent).get(0) == psp, "exact PSP filter");
        check(HubGame.filter(all, ps1.coreId, "", false, false, stars, recent).get(0) == ps1, "exact PS1 filter");
        check(HubGame.filter(all, "", "CAFÉ", false, false, stars, recent).size() == 2, "case insensitive title");
        check(HubGame.filter(all, "", "cafe", false, false, stars, recent).size() == 2, "accent insensitive title");
        check(HubGame.filter(all, "", "rally psp", false, false, stars, recent).equals(Collections.singletonList(psp)), "combined name/console tokens");
        check(HubGame.filter(all, "", " PSP   rally ", false, false, stars, recent).size() == 1, "word order / spacing");
        check(HubGame.filter(all, "", "atari 2600", false, false, stars, recent).get(0) == atari, "Atari system label");
        check(HubGame.filter(all, "", "arbol", false, false, stars, recent).get(0) == snes, "accent normalization in game name");
        check(HubGame.filter(all, "", "nonexistent", false, false, stars, recent).isEmpty(), "search empty state");
        check(HubGame.filter(all, "", "", true, false, stars, recent).size() == 2, "favorites independent of source");
        check(HubGame.filter(all, psp.coreId, "", true, false, stars, recent).size() == 1, "favorites plus platform");
        check(HubGame.filter(all, ps1.coreId, "", true, false, stars, recent).isEmpty(), "favorite empty state");
        List<HubGame> opened = HubGame.filter(all, "", "", false, true, stars, recent);
        check(opened.size() == 3, "recents not fabricated from import dates");
        check(opened.get(0) == psp && opened.get(1) == atari && opened.get(2) == ps1, "recent descending order");
        check(HubGame.filter(all, "", "", true, true, stars, recent).equals(Collections.singletonList(psp)), "favorite/recent intersection");
        check(HubGame.filter(all, "", "", false, true, stars, Collections.emptyMap()).isEmpty(), "new install has no false history");
        check(HubGame.filter(all, "", "", false, false, stars, recent).get(0) == snes, "stable accent-insensitive sorting");
        check(all.get(0) == psp, "source order remains unchanged");
        check(HubGame.filter(Collections.emptyList(), "", "", false, false, stars, recent).isEmpty(), "empty library");
        check(new HubGame("core", "Platform", null, new File("/a/game.bin"), null, null).title.equals("game.bin"), "null title fallback");
        check(new HubGame("core", "Platform", "  ", new File("/a/game.bin"), null, null).title.equals("game.bin"), "blank title fallback");
        try { new HubGame(null, "", "", new File("/a"), null, null); throw new AssertionError("accepted null core"); }
        catch (IllegalArgumentException expected) { passed++; }
        try { new HubGame("core", "", "", null, null, null); throw new AssertionError("accepted null file"); }
        catch (IllegalArgumentException expected) { passed++; }
        List<HubGame> many = new ArrayList<>();
        for (int i = 0; i < 1200; i++) many.add(new HubGame("snes.snes9x", "Super Nintendo", "Game " + i, new File("/test/" + i + ".sfc"), null, null));
        check(HubGame.filter(many, "", "", false, false, Collections.emptySet(), Collections.emptyMap()).size() == 1200, "large catalogue, no truncation");
        check(HubGame.filter(many, "", "Game 1199", false, false, Collections.emptySet(), Collections.emptyMap()).size() == 1, "large catalogue search");
        check(HubGame.filter(all, "", "", false, false, stars, recent).equals(HubGame.filter(all, "", "", false, false, stars, recent)), "repeatable order");
        System.out.println("HubGame: " + passed + "/" + passed + " JVM checks PASS");
    }
}
