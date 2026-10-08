package cl.retrolink.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** UI-only storage, separate from all libraries, save files and optimization profiles. */
public final class HubStore {
    private static final String PREFS = "retrolink_hub_ui_v1";
    private final SharedPreferences prefs;
    public HubStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public Set<String> favorites() { return new HashSet<>(prefs.getStringSet("favorites", new HashSet<>())); }
    public boolean isFavorite(String key) { return favorites().contains(key); }
    public boolean toggleFavorite(String key) {
        Set<String> keys = favorites();
        boolean added = !keys.remove(key);
        if (added) keys.add(key);
        prefs.edit().putStringSet("favorites", keys).apply();
        return added;
    }
    public void recordOpen(String key) {
        // A launch request accepted by Android, NOT proof that the game reached a playable frame.
        prefs.edit().putLong("open_" + key, System.currentTimeMillis()).apply();
    }
    public Map<String, Long> opened() {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet())
            if (e.getKey().startsWith("open_") && e.getValue() instanceof Long)
                result.put(e.getKey().substring(5), (Long) e.getValue());
        return result;
    }
}
