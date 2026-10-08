package cl.retrolink.app;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

/** Local catalogue metadata. Does not read ROM contents or initialize a core. */
public final class HubGame {
    public final String coreId, platform, title, key;
    public final File file, cover;
    final Object original;
    private final String searchText, sortTitle;

    public HubGame(String coreId, String platform, String title, File file, File cover, Object original) {
        if (coreId == null || file == null) throw new IllegalArgumentException("Missing game identity");
        this.coreId = coreId;
        this.platform = platform;
        this.title = title == null || title.trim().isEmpty() ? file.getName() : title.trim();
        this.file = file;
        this.cover = cover;
        this.original = original;
        this.sortTitle = normalized(this.title);
        this.searchText = normalized(this.title + " " + this.platform);
        // Never persist a readable filename/path or try to infer a console from .iso/.bin.
        this.key = identity(coreId, file);
    }

    static String identity(String coreId, File file) {
        try {
            String path;
            try { path = file.getCanonicalPath(); }
            catch (java.io.IOException e) { path = file.getAbsolutePath(); }
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((coreId + "\n" + path).getBytes(StandardCharsets.UTF_8));
            char[] digits = "0123456789abcdef".toCharArray();
            char[] out = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                out[i * 2] = digits[(hash[i] & 255) >>> 4];
                out[i * 2 + 1] = digits[hash[i] & 15];
            }
            return new String(out);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    public static List<HubGame> filter(List<HubGame> source, String coreId, String query,
                                       boolean favoritesOnly, boolean recentOnly,
                                       Set<String> favorites, Map<String, Long> opened) {
        String[] words = normalized(query).split("\\s+");
        List<HubGame> result = new ArrayList<>();
        for (HubGame game : source) {
            if (coreId != null && !coreId.isEmpty() && !coreId.equals(game.coreId)) continue;
            if (favoritesOnly && !favorites.contains(game.key)) continue;
            if (recentOnly && opened.getOrDefault(game.key, 0L) <= 0L) continue;
            String haystack = game.searchText;
            boolean matches = true;
            for (String word : words) if (!haystack.contains(word)) { matches = false; break; }
            if (matches) result.add(game);
        }
        Comparator<HubGame> byName = Comparator.comparing((HubGame g) -> g.sortTitle)
                .thenComparing(g -> g.coreId).thenComparing(g -> g.key);
        if (recentOnly) result.sort(Comparator.comparingLong((HubGame g) -> opened.getOrDefault(g.key, 0L))
                .reversed().thenComparing(byName));
        else result.sort(byName);
        return result;
    }
}
